#!/usr/bin/env python3
"""Fetch original, sourced product photos for the private Android owner build.

Uses only Python's standard library. Does not crop, resize, transcode, or edit
images. Public release assets deliberately exclude these unlicensed photos.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

ANDROID = Path(__file__).resolve().parents[1]
MAX_BYTES = 4 * 1024 * 1024
TIMEOUT_SECONDS = 20
ASSET_PATTERN = re.compile(r"shoes/photos/[0-9a-f]{16}\.img\Z")


def valid_https(url: str) -> bool:
    parts = urllib.parse.urlsplit(url)
    return (parts.scheme == "https" and bool(parts.hostname)
            and parts.username is None and parts.password is None)


class HttpsRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, newurl):
        if not valid_https(newurl):
            raise ValueError("Refused a redirect outside HTTPS")
        return super().redirect_request(request, response, code, message, headers, newurl)


def image_type(data: bytes) -> str | None:
    if data.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "png"
    if len(data) >= 12 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path,
                        default=ANDROID / "app/src/main/assets/shoes/catalog.json")
    parser.add_argument("--output", type=Path,
                        default=ANDROID / "app/src/debug/assets/shoes/photos")
    parser.add_argument("--receipt", type=Path,
                        default=ANDROID / "build/nov23-shoe-brands/shoe-photo-receipt.json")
    parser.add_argument("--workers", type=int, default=6, choices=range(1, 9))
    args = parser.parse_args()
    rows = json.loads(args.catalog.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    previous = {}
    if args.receipt.exists():
        previous = {r["imageAsset"]: r for r in
                    json.loads(args.receipt.read_text(encoding="utf-8")).get("photos", [])
                    if r.get("status") in ("downloaded", "cached")}

    def fetch(row):
        record = {key: row.get(key) for key in
                  ("brand", "model", "imageAsset", "imageUrl", "imageSourceUrl")}
        asset, url = record["imageAsset"], record["imageUrl"]
        try:
            if not isinstance(asset, str) or not ASSET_PATTERN.fullmatch(asset):
                raise ValueError("Unexpected asset path")
            if not isinstance(url, str) or not valid_https(url):
                raise ValueError("Expected an HTTPS photo URL")
            destination = args.output / Path(asset).name
            old = previous.get(asset, {})
            if destination.is_file() and destination.stat().st_size <= MAX_BYTES:
                data = destination.read_bytes()
                digest = hashlib.sha256(data).hexdigest()
                if (old.get("imageUrl") == url and old.get("sha256") == digest
                        and image_type(data)):
                    return dict(record, status="cached", bytes=len(data), sha256=digest,
                                format=image_type(data))
            # Official product URLs sometimes contain a literal trademark glyph.
            # Encode its UTF-8 URL bytes while preserving existing escapes.
            request_url = urllib.parse.quote(url, safe="/:?#[]@!$&'()*+,;=%")
            request = urllib.request.Request(request_url, headers={
                "User-Agent": "Mozilla/5.0 (RunState private shoe catalog validation)",
                "Accept": "image/jpeg,image/png,image/webp,*/*;q=0.1",
            })
            opener = urllib.request.build_opener(HttpsRedirects())
            with opener.open(request, timeout=TIMEOUT_SECONDS) as response:
                if not valid_https(response.geturl()):
                    raise ValueError("Response did not remain HTTPS")
                size = response.headers.get("Content-Length")
                if size and int(size) > MAX_BYTES:
                    raise ValueError("Photo exceeds the 4 MiB size limit")
                data = response.read(MAX_BYTES + 1)
            if len(data) > MAX_BYTES:
                raise ValueError("Photo exceeds the 4 MiB size limit")
            kind = image_type(data)
            if kind is None:
                raise ValueError("Response is not a JPEG, PNG, or WebP image")
            temporary = destination.with_suffix(".tmp")
            temporary.write_bytes(data)
            temporary.replace(destination)
            return dict(record, status="downloaded", bytes=len(data), format=kind,
                        sha256=hashlib.sha256(data).hexdigest())
        except (OSError, ValueError, urllib.error.URLError) as error:
            return dict(record, status="failed", error=str(error))

    targets = [row for row in rows if row.get("imageUrl")]
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        results = list(pool.map(fetch, targets))
    success = [r for r in results if r["status"] != "failed"]
    failed = [r for r in results if r["status"] == "failed"]
    receipt = {
        "catalogSha256": hashlib.sha256(args.catalog.read_bytes()).hexdigest(),
        "catalogModels": len(rows), "photoUrls": len(targets),
        "downloadedOrCached": len(success), "failed": len(failed),
        "withoutPhotoUrl": len(rows) - len(targets),
        "originalBytes": sum(r["bytes"] for r in success),
        "maxPhotoBytes": MAX_BYTES, "timeoutSeconds": TIMEOUT_SECONDS,
        "photos": results,
    }
    args.receipt.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(f"{len(rows)} models; {len(success)}/{len(targets)} original photos prepared; "
          f"{len(failed)} failed; {len(rows) - len(targets)} have no photo URL.")
    print(f"Total original photo bytes: {receipt['originalBytes']}")
    for item in failed:
        print(f"FAILED {item['brand']} {item['model']}: {item['error']}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
