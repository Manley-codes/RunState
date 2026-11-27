#!/usr/bin/env python3
"""Check an already-built backend image with disposable storage and no external network."""

import json
import subprocess
import sys
import time
import uuid


def docker(*arguments, **options):
    try:
        return subprocess.run(["docker", *arguments], check=True, text=True,
                              capture_output=True, timeout=45, **options).stdout.strip()
    except subprocess.CalledProcessError as failure:
        print(failure.stdout + failure.stderr, file=sys.stderr)
        raise


def main(image):
    settings = json.loads(docker("image", "inspect", image))[0]["Config"]
    if settings["User"] != "10001:10001":
        raise RuntimeError("The packaged backend must use its non-root runtime identity")
    suffix = uuid.uuid4().hex
    volume = "runstate-check-" + suffix
    server = "runstate-server-check-" + suffix
    mount = "type=volume,src=" + volume + ",dst=/var/data"
    # SQLite extracts its bundled native library to /tmp; it must be loadable there.
    common = ["--network", "none", "--read-only", "--tmpfs", "/tmp:rw,nosuid,nodev,exec,size=64m,mode=1777",
              "--cap-drop", "ALL", "--security-opt", "no-new-privileges"]
    environment = {
        "RUNSTATE_REFLECTION_PUBLIC_HOST": "runstate-ci.example",
        "RUNSTATE_REFLECTION_BIND_ADDRESS": "127.0.0.1",
        "RUNSTATE_DEMO_TOKEN": "container-test-only-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789",
        "RUNSTATE_SPEECH_REPLAY_DIR": "/var/data/runstate/speech",
        "RUNSTATE_RUNNER_MONTHLY_USD": "0",
        "RUNSTATE_GLOBAL_MONTHLY_USD": "0",
        # Let the bounded request estimate pass its per-call ceiling so this check
        # reaches the zero monthly allowance, rather than an undersized-config refusal.
        "RUNSTATE_ANTHROPIC_MAX_CALL_USD": "5.00",
        # Deliberately invalid stand-in, never inherited from the machine or CI secrets.
        "ANTHROPIC_API_KEY": "ci-placeholder-not-a-provider-key",
        "RUNSTATE_LIVE_SONG_TRIAL": "0",
    }
    variables = [argument for pair in environment.items() for argument in ("--env", "=".join(pair))]
    check = ["java", "-Djdk.httpclient.redirects.retrylimit=1", "-cp", "/app/classes:/app/lib/*",
             "com.runstate.reflection.ReflectionBackendMain", "--check-config"]
    created = False
    launched = False
    helpers = set()

    def run(*command, readonly=False, root=False):
        name = "runstate-helper-check-" + uuid.uuid4().hex
        flags = [*common, "--mount", mount + (",readonly" if readonly else ""), *variables]
        if root:
            flags += ["--user", "0:0", "--cap-add", "CHOWN", "--cap-add", "FOWNER", "--cap-add", "DAC_OVERRIDE"]
        helpers.add(name)
        result = docker("run", "--rm", "--name", name, *flags, image, *command)
        helpers.remove(name)
        return result

    def expect_storage_failure(readonly=False):
        try:
            run(*check, readonly=readonly)
        except subprocess.CalledProcessError as failure:
            if failure.returncode != 1 or "FAIL storage access" not in failure.stdout:
                raise RuntimeError("Storage negative check failed for an unexpected reason") from failure
        else:
            raise RuntimeError("Unsafe or unwritable storage unexpectedly passed")

    def request(method, path, body="", authenticated=False):
        headers = [f"{method} {path} HTTP/1.1", "Host: runstate-ci.example", "Connection: close",
                   "Content-Type: application/json", f"Content-Length: {len(body.encode('utf-8'))}"]
        if authenticated:
            headers.append("Authorization: Bearer " + environment["RUNSTATE_DEMO_TOKEN"])
        message = "\r\n".join(headers) + "\r\n\r\n" + body
        wire = docker("exec", "-i", server, "timeout", "5", "bash", "-c",
                      "set -e; exec 3<>/dev/tcp/127.0.0.1/8787; cat >&3; cat <&3", input=message)
        # subprocess normalizes CRLF; the server uses fixed-length Connection: close replies.
        head, content = wire.split("\n\n", 1)
        return int(head.splitlines()[0].split()[1]), json.loads(content)

    try:
        created = True  # Also clean up if the CLI times out after creating the volume.
        docker("volume", "create", "--label", "runstate.disposable-check=true", volume)
        run("sh", "-ec", "mkdir -p /var/data/runstate/speech; "
            "chown 10001:10001 /var/data/runstate/speech; chmod 700 /var/data/runstate/speech", root=True)
        identity = run("sh", "-ec", "printf '%s:%s' \"$(id -u)\" \"$(id -g)\"")
        if identity != "10001:10001":
            raise RuntimeError("Unexpected runtime user/group")
        print("PASS image and process both use UID/GID 10001:10001", flush=True)
        if "Configuration check passed." not in run(*check):
            raise RuntimeError("Expected a passing packaged configuration check")
        print("PASS packaged Java/SQLite configuration check with zero budgets", flush=True)
        marker = uuid.uuid4().hex
        run("sh", "-ec", 'printf "%s" "$1" > /var/data/runstate/speech/ci-marker', "sh", marker)

        launched = True
        docker("run", "--detach", "--name", server, *common, "--mount", mount, *variables, image)
        for attempt in range(30):
            try:
                if request("GET", "/health") == (200, {"status": "ok"}):
                    break
            except (subprocess.CalledProcessError, ValueError):
                pass
            if docker("inspect", "--format", "{{.State.Running}}", server) != "true":
                raise RuntimeError("The production container command exited during startup")
            time.sleep(1)
        else:
            raise RuntimeError("The production container command did not become healthy")
        print("PASS unmodified production command starts and serves health", flush=True)
        status, _ = request("POST", "/api/reflections/candidates", "{}")
        if status != 401:
            raise RuntimeError("Unauthenticated work was not refused")
        body = json.dumps({
            "requestId": str(uuid.uuid4()), "runId": str(uuid.uuid4()),
            "evidence": {"officialStartEpochMillis": 1780000000000, "finishEpochMillis": 1780001800000,
                         "startTimezoneId": "America/Chicago", "activeDurationMillis": 1650000,
                         "metrics": {"distanceMeters": 5000.25, "source": "FIXTURE",
                                     "coverage": "COMPLETE", "displayUnit": "MILES"},
                         "preRunEnergy": "LOW", "musicContext": {"state": "MUSIC",
                             "trackTitle": "Controlled track", "artistName": "Controlled artist",
                             "artistDescription": "Controlled artist description"}}})
        status, refusal = request("POST", "/api/reflections/candidates", body, authenticated=True)
        if status != 429 or refusal.get("error", {}).get("code") != "spend_limit_reached":
            raise RuntimeError("Zero-budget request did not stop at the spending gate: " + str((status, refusal)))
        print("PASS authentication and zero-budget refusal through the packaged server", flush=True)
        docker("stop", "--time", "10", server)
        docker("rm", server)
        launched = False

        if "Configuration check passed." not in run(*check):
            raise RuntimeError("Replacement container could not reopen the same stores")
        if run("cat", "/var/data/runstate/speech/ci-marker") != marker:
            raise RuntimeError("The mounted marker did not survive container replacement")
        run("sh", "-ec", 'test "$(stat -c %a /var/data/runstate/speech)" = 700; '
            'for name in written-reflections reflection-speech reflection-allowances provider-spend; do '
            'file="/var/data/runstate/speech/$name.sqlite3"; test -s "$file"; '
            'test "$(stat -c %u:%g:%a "$file")" = 10001:10001:600; done; '
            'test ! -d /app/test-classes; '
            'test -z "$(find /app/classes -name \"*Test*.class\" -print -quit)"')
        print("PASS replacement reopens four private stores; marker and file ownership remain", flush=True)
        expect_storage_failure(readonly=True)
        run("chmod", "755", "/var/data/runstate/speech")
        expect_storage_failure()
        run("chmod", "700", "/var/data/runstate/speech")
        print("PASS read-only and publicly readable storage are rejected", flush=True)
        print("Container check passed. No external network, real credentials, publication or deployment.", flush=True)
    except Exception:
        if launched:
            try:
                logs = subprocess.run(["docker", "logs", server], text=True, capture_output=True, timeout=15)
                print(logs.stdout + logs.stderr, file=sys.stderr)
            except subprocess.TimeoutExpired:
                print("Container log retrieval timed out; continuing cleanup.", file=sys.stderr)
        raise
    finally:
        # A CLI timeout can leave its container running; remove only our own unique names.
        failed = sys.exc_info()[0] is not None
        cleanup = [["rm", "--force", name] for name in helpers]
        if launched:
            cleanup.append(["rm", "--force", server])
        if created:
            cleanup.append(["volume", "rm", volume])
        problems = []
        for command in cleanup:
            try:
                result = subprocess.run(["docker", *command], text=True, capture_output=True, timeout=20)
                if result.returncode and "no such" not in result.stderr.lower():
                    problems.append(result.stderr.strip())
            except subprocess.TimeoutExpired:
                problems.append("Timed out removing an owned disposable check resource")
        if problems:
            print("Cleanup problems: " + "; ".join(problems), file=sys.stderr)
            if not failed:
                raise RuntimeError("Disposable check resources were not fully removed")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 backend/check-container.py LOCAL_IMAGE_TAG")
    main(sys.argv[1])
