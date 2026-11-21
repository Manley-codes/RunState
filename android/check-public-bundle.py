#!/usr/bin/env python3
"""Validate the public AAB and inspect an APK generated from it, entirely offline.

Supply an independently verified standalone bundletool JAR with --bundletool.
Java/keytool use explicit paths, JAVA_HOME or PATH. The APK checker needs Android
SDK build-tools and readelf; --build-tools and --readelf are passed through.
Only a disposable test key is used. Nothing is installed, uploaded or published.
A universal APK does not prove final Play splits/signing or device runtime behavior.
"""

import argparse
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import zipfile


class CheckFailure(Exception):
    pass


def require(condition, message):
    if not condition:
        raise CheckFailure(message)


def executable(name, explicit=None, java=None):
    suffix = ".exe" if os.name == "nt" else ""
    if explicit:
        candidates = [explicit]
    else:
        candidates = []
        if java:
            candidates.append(Path(java).parent / (name + suffix))
        if os.environ.get("JAVA_HOME"):
            candidates.append(Path(os.environ["JAVA_HOME"]) / "bin" / (name + suffix))
        candidates.append(name)
    for candidate in candidates:
        found = shutil.which(str(candidate))
        if found:
            return Path(found).resolve()
    raise CheckFailure(f"Cannot find {name}; pass --{name}, set JAVA_HOME or update PATH.")


def diagnostic(text, password):
    # Tool output is useful on failure, but never echo even the disposable password.
    if password:
        text = text.replace(password, "[redacted]")
    text = re.sub(r"\x1b\[[0-?]*[ -/]*[@-~]", "", text)
    text = "".join(char for char in text if char in "\n\t" or ord(char) >= 32)
    return text[-3000:].strip()


def run_tool(command, label, *, timeout=120, password=None):
    # Keep JVM-injected options out of this self-contained offline verification.
    env = {key: value for key, value in os.environ.items()
           if key not in {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"}}
    env["LC_ALL"] = "C"
    with tempfile.TemporaryFile(mode="w+", encoding="utf-8", errors="replace") as output:
        with tempfile.TemporaryFile(mode="w+", encoding="utf-8", errors="replace") as errors:
            try:
                result = subprocess.run(
                    [str(part) for part in command], stdin=subprocess.DEVNULL,
                    stdout=output, stderr=errors, env=env, timeout=timeout,
                )
            except subprocess.TimeoutExpired:
                raise CheckFailure(f"{label} exceeded its {timeout}-second limit.") from None
            if result.returncode:
                # Bound reads as well as displayed diagnostics; do not load a huge tool log.
                output.seek(max(0, output.tell() - 6000))
                errors.seek(max(0, errors.tell() - 6000))
                details = diagnostic(output.read() + "\n" + errors.read(), password)
                raise CheckFailure(f"{label} failed (exit {result.returncode})."
                                   + (f"\n{details}" if details else ""))
            output.seek(0)
            text = output.read(1_048_577)
            require(len(text) <= 1_048_576, f"Unexpectedly large {label} output.")
            return text


def check_bundle_archive(bundle):
    require(bundle.is_file() and bundle.stat().st_size > 0,
            "AAB must be an existing, nonempty regular file.")
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "AAB contains duplicate ZIP entries.")
        manifests = {name for name in names if name.endswith("/manifest/AndroidManifest.xml")}
        require(manifests == {"base/manifest/AndroidManifest.xml"},
                "Expected only the base module; additional modules need separate split-APK checks.")


def check_alignment(config_text):
    try:
        config = json.loads(config_text)
        native = config["optimizations"]["uncompressNativeLibraries"]
        require(native["enabled"] is True and native["alignment"] == "PAGE_ALIGNMENT_16K",
                "AAB must request uncompressed native libraries with PAGE_ALIGNMENT_16K.")
    except (json.JSONDecodeError, KeyError, TypeError):
        raise CheckFailure("Cannot read native-library alignment from bundletool config JSON.") from None


def inspect(args):
    bundle = args.bundle.resolve()
    jar = args.bundletool.resolve()
    check_bundle_archive(bundle)
    require(jar.is_file() and jar.stat().st_size > 0,
            "--bundletool must name a verified, nonempty standalone JAR.")
    java = executable("java", args.java)
    keytool = executable("keytool", args.keytool, java)
    tool = [java, "-Xmx512m", "-jar", jar]
    checker = Path(__file__).resolve().with_name("check-public-apk.py")
    require(checker.is_file(), "The existing check-public-apk.py is missing.")

    run_tool([*tool, "validate", f"--bundle={bundle}"], "Bundle validation")
    print("PASS: bundletool validates the single-base-module AAB.", flush=True)
    config = run_tool([*tool, "dump", "config", f"--bundle={bundle}"], "Bundle configuration dump")
    check_alignment(config)
    print("PASS: AAB requests PAGE_ALIGNMENT_16K for uncompressed native libraries.", flush=True)

    with tempfile.TemporaryDirectory(prefix="runstate-bundle-check-") as temporary:
        directory = Path(temporary)
        key = directory / "disposable-test.p12"
        password_file = directory / "test-password.txt"
        password = secrets.token_urlsafe(32)
        password_file.write_text(password + "\n", encoding="utf-8")
        password_file.chmod(0o600)
        run_tool([
            keytool, "-J-Xmx512m", "-genkeypair", "-storetype", "PKCS12",
            "-keystore", key, "-alias", "runstate-bundle-check", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "2", "-dname", "CN=RunState Disposable Test",
            "-storepass:file", password_file, "-keypass:file", password_file, "-noprompt",
        ], "Disposable test-key generation", timeout=60, password=password)
        key.chmod(0o600)

        apks = directory / "public-check.apks"
        command = [
            *tool, "build-apks", f"--bundle={bundle}", f"--output={apks}",
            "--mode=universal", f"--ks={key}", "--ks-key-alias=runstate-bundle-check",
            f"--ks-pass=file:{password_file}", f"--key-pass=file:{password_file}",
        ]
        if args.build_tools:
            aapt2 = Path(args.build_tools).resolve() / ("aapt2.exe" if os.name == "nt" else "aapt2")
            require(aapt2.is_file(), "The supplied --build-tools directory lacks aapt2.")
            command.append(f"--aapt2={aapt2}")
        run_tool(command, "Universal APK generation", timeout=180, password=password)

        apk = directory / "universal.apk"
        with zipfile.ZipFile(apks) as archive:
            entries = [entry for entry in archive.infolist() if entry.filename == "universal.apk"]
            require(len(entries) == 1 and not entries[0].is_dir() and entries[0].file_size > 0,
                    "bundletool did not produce exactly one nonempty universal.apk.")
            # Extract only this known member to our chosen filename, never archive paths.
            with archive.open(entries[0]) as source, apk.open("wb") as target:
                shutil.copyfileobj(source, target)
        print("PASS: generated an AAB-derived universal APK with a disposable test key.", flush=True)

        command = [sys.executable, checker, apk]
        for flag, value in (("--build-tools", args.build_tools), ("--readelf", args.readelf)):
            if value:
                command.extend([flag, value])
        output = run_tool(command, "Public APK packaging checks", timeout=300, password=password)
        print(output.strip(), flush=True)
    print("Public bundle checks passed; temporary key and APK files removed.")
    print("This universal APK does not verify final Play splits/signing or device runtime behavior.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path, help="unsigned or signed public .aab (never modified)")
    parser.add_argument("--bundletool", type=Path, required=True, help="verified standalone bundletool JAR")
    parser.add_argument("--java", help="Java executable (default: JAVA_HOME/bin, then PATH)")
    parser.add_argument("--keytool", help="keytool executable (default: beside Java, JAVA_HOME/bin, then PATH)")
    parser.add_argument("--build-tools", help="SDK aapt2/dexdump/zipalign directory; passed to the APK checker")
    parser.add_argument("--readelf", help="llvm-readelf/readelf executable; passed to the APK checker")
    args = parser.parse_args()
    try:
        inspect(args)
    except (CheckFailure, OSError, zipfile.BadZipFile) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
