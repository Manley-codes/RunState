#!/usr/bin/env python3
"""Inspect the assembled public APK offline, using Python 3 and Android SDK tools.

Usage: python3 android/check-public-apk.py path/to/app-release-unsigned.apk
Set ANDROID_SDK_ROOT/ANDROID_HOME, or pass --build-tools /path/to/build-tools/version.
This checks packaging, not signing, provider permissions, or device/UI behavior.
"""

import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile


ANDROID = "http://schemas.android.com/apk/res/android:"
BUILD_CONFIG = "Lcom/runstate/mobile/BuildConfig;"
EXCLUSIONS = {
    ("database", "."),
    ("sharedpref", "reflection_connection.xml"),
    *(("file", "mbgl-offline.db" + suffix) for suffix in ("", "-wal", "-shm", "-journal")),
}


class CheckFailure(Exception):
    pass


def require(condition, message):
    if not condition:
        raise CheckFailure(message)


def sdk_tools(explicit):
    suffix = ".exe" if os.name == "nt" else ""
    if explicit:
        candidates = [Path(explicit)]
    else:
        sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        require(sdk, "Set ANDROID_SDK_ROOT/ANDROID_HOME or pass --build-tools.")
        candidates = sorted(
            (p for p in (Path(sdk) / "build-tools").iterdir()
             if re.fullmatch(r"\d+\.\d+\.\d+", p.name)),
            key=lambda p: tuple(map(int, p.name.split("."))), reverse=True,
        )
    for directory in candidates:
        tools = [directory / (name + suffix) for name in ("aapt2", "dexdump")]
        if all(tool.is_file() for tool in tools):
            return tools
    raise CheckFailure("No complete SDK build-tools directory containing aapt2 and dexdump.")


def run_tool(command, output=None):
    result = subprocess.run(
        [str(arg) for arg in command], stdout=output or subprocess.PIPE,
        stderr=subprocess.PIPE, encoding="utf-8", errors="replace", timeout=60,
    )
    require(result.returncode == 0, f"{Path(command[0]).name} failed (exit {result.returncode}).")
    return result.stdout


def xml_tree(dump):
    """Read aapt2's indented XML output, retaining attribute namespaces and hierarchy."""
    stack = []
    root = None
    for line in dump.splitlines():
        element = re.fullmatch(r"(\s*)E: (\S+)(?: .*)?", line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = ET.Element(element[2])
            if stack:
                stack[-1][1].append(node)
            else:
                require(root is None, "Unexpected multiple XML roots.")
                root = node
            stack.append((depth, node))
        attribute = re.fullmatch(r"\s*A: ([^=]+)=(.*)", line)
        if attribute:
            require(stack, "XML attribute has no element.")
            key = re.sub(r"\(0x[0-9a-f]+\)$", "", attribute[1])
            value = attribute[2]
            if value.startswith('"'):
                value = value.split('"', 2)[1]
            require(key not in stack[-1][1].attrib, "Duplicate packaged XML attribute.")
            stack[-1][1].set(key, value)
    require(root is not None, "Unable to read packaged XML.")
    return root


def check_manifest(manifest):
    require(manifest.tag == "manifest" and manifest.get("package") == "com.runstate.mobile",
            "APK is not the RunState application.")
    applications = manifest.findall("application")
    require(len(applications) == 1, "Expected one packaged application element.")
    application = applications[0]
    for flag in ("debuggable", "testOnly"):
        require(application.get(ANDROID + flag, "false") == "false",
                f"Public application must not enable {flag}.")
    require(application.get(ANDROID + "usesCleartextTraffic") == "false",
            "Public application must explicitly disable cleartext traffic.")
    require(application.get(ANDROID + "networkSecurityConfig") is None,
            "New network security configuration needs review; it can override cleartext policy.")
    return application


def backup_paths(resource_dump, resource_id, expected_name):
    blocks = re.findall(
        r"^\s*resource (0x[0-9a-f]+) (\S+)[^\n]*\n(.*?)(?=^\s*resource |\Z)",
        resource_dump, re.MULTILINE | re.DOTALL,
    )
    matches = [(name, body) for identifier, name, body in blocks if "@" + identifier == resource_id]
    require(len(matches) == 1 and matches[0][0] == expected_name,
            f"Manifest must reference packaged {expected_name}.")
    paths = re.findall(r"\(file\) (\S+) type=XML", matches[0][1])
    require(paths, f"No compiled XML found for {expected_name}.")
    return paths


def check_exclusions(section, label):
    actual = {(node.get("domain"), node.get("path")) for node in section.findall("exclude")}
    missing = EXCLUSIONS - actual
    require(not missing, f"{label} is missing private-data exclusions: {sorted(missing)}")


def check_backup(tree, modern):
    if not modern:
        require(tree.tag == "full-backup-content", "Unexpected legacy backup XML root.")
        check_exclusions(tree, "Legacy backup")
        return
    require(tree.tag == "data-extraction-rules", "Unexpected modern backup XML root.")
    for channel in ("cloud-backup", "device-transfer"):
        sections = tree.findall(channel)
        require(len(sections) == 1, f"Expected one {channel} backup section.")
        check_exclusions(sections[0], channel)


def build_config_fields(lines):
    """Read only SDK-decoded static fields, never interpret DEX bytecode ourselves."""
    fields = None
    in_class = False
    in_static = False
    name = None
    for line in lines:
        descriptor = re.fullmatch(r"\s*Class descriptor\s*: '([^']+)'\s*", line)
        if descriptor:
            in_class = descriptor[1] == BUILD_CONFIG
            if in_class:
                require(fields is None, "Duplicate RunState BuildConfig in DEX output.")
                fields = {}
            in_static = False
        if not in_class:
            continue
        if line.strip() == "Static fields     -":
            in_static = True
        if line.strip() == "Instance fields   -":
            in_static = False
        if in_static:
            field = re.fullmatch(r"\s*name\s*: '([^']+)'\s*", line)
            if field:
                name = field[1]
            value = re.fullmatch(r"\s*value\s*: (.*?)\s*", line)
            if value and name:
                fields[name] = value[1]
                name = None
    return fields


def check_build_config(fields):
    require(fields is not None, "RunState BuildConfig was not found; review if shrinking changes.")
    for flag in ("DEBUG", "OWNER_TOOLS_ENABLED"):
        require(fields.get(flag) == "false", f"Packaged BuildConfig.{flag} must be false.")
    require(fields.get("BUILD_TYPE") == '"release"', "Packaged build type must be release.")


def check_assets(archive, demo_cover):
    require(demo_cover.is_file(), "Owner demo source image is missing; exclusion proof needs it.")
    original = demo_cover.read_bytes()
    digest = hashlib.sha256(original).digest()
    for entry in archive.infolist():
        require("demo_lizzo_cover" not in entry.filename.lower(), "Owner demo cover is packaged publicly.")
        if entry.file_size == len(original):
            require(hashlib.sha256(archive.read(entry)).digest() != digest,
                    "Owner demo image bytes are packaged under a different filename.")
    notices = "assets/third_party_notices.txt"
    require(notices in archive.namelist() and archive.getinfo(notices).file_size > 0,
            "Bundled third-party notices are missing or empty.")


def inspect(apk, aapt2, dexdump):
    require(apk.is_file(), "APK does not exist.")
    application = check_manifest(xml_tree(run_tool([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk])))
    print("PASS: public manifest disables debugging, test-only installation and cleartext traffic.")
    resources = run_tool([aapt2, "dump", "resources", apk])
    for attr, name, modern in (("fullBackupContent", "xml/backup_rules", False),
                               ("dataExtractionRules", "xml/data_extraction_rules", True)):
        for path in backup_paths(resources, application.get(ANDROID + attr), name):
            check_backup(xml_tree(run_tool([aapt2, "dump", "xmltree", "--file", path, apk])), modern)
    print("PASS: database, connection and map-cache exclusions cover all three backup/transfer channels.")
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="runstate-apk-check-") as temp:
        check_assets(archive, Path(__file__).resolve().parent / "app/src/debug/res/drawable-nodpi/demo_lizzo_cover.jpg")
        print("PASS: owner demo artwork is absent; bundled notices are present.")
        dex_entries = [entry for entry in archive.infolist() if re.fullmatch(r"classes\d*\.dex", entry.filename)]
        require(dex_entries, "APK has no DEX files.")
        matches = []
        for entry in dex_entries:
            path = Path(temp) / entry.filename
            with archive.open(entry) as source, path.open("wb") as target:
                shutil.copyfileobj(source, target)
            # Keep the SDK's potentially large dump on disk, not in Python memory or CI logs.
            with tempfile.TemporaryFile(mode="w+", encoding="utf-8", errors="replace") as output:
                run_tool([dexdump, "-e", "-n", path], output)
                output.seek(0)
                fields = build_config_fields(output)
                if fields is not None:
                    matches.append(fields)
        require(len(matches) == 1, "Expected exactly one packaged RunState BuildConfig.")
        check_build_config(matches[0])
    print("PASS: packaged release BuildConfig disables DEBUG and OWNER_TOOLS_ENABLED.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", help="SDK directory containing aapt2 and dexdump")
    args = parser.parse_args()
    try:
        inspect(args.apk.resolve(), *sdk_tools(args.build_tools))
    except (CheckFailure, OSError, subprocess.TimeoutExpired, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print("Public APK packaging checks passed. Signing, device behavior and release approval remain separate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
