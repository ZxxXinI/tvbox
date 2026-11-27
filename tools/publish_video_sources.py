import argparse
import base64
import shlex
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "app/src/main/resources/tvbox/default-video-sources.json"
REMOTE = """
import base64
import fcntl
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.parse import urlsplit

target = Path("/srv/tvbox-catalog/sources.json")
key = "/etc/tvbox-catalog/signing-key.pem"
raw = sys.stdin.buffer.read(65_537)
catalog = json.loads(raw)
if len(raw) > 65_536 or type(catalog["revision"]) is not int or catalog["revision"] <= 0:
    raise SystemExit("Invalid catalog revision or size")
sources = catalog["sources"]
if not 1 <= len(sources) <= 100:
    raise SystemExit("Invalid source count")
known = set()
for line in sources:
    line_id, name, urls = line["id"], line["name"], line["baseUrls"]
    if (not re.fullmatch(r"[a-z0-9_-]{1,48}", line_id) or line_id.startswith("custom_") or line_id in known
            or not name.strip() or len(name) > 60 or any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in name)
            or not 1 <= len(urls) <= 4):
        raise SystemExit("Invalid source record: " + line_id)
    for raw_url in urls:
        url = urlsplit(raw_url)
        if (len(raw_url) > 512 or url.scheme != "https" or not url.hostname or url.username is not None
                or url.password is not None or url.query or url.fragment or not raw_url.endswith("/")):
            raise SystemExit("Invalid HTTPS address for: " + line_id)
        _ = url.port
    known.add(line_id)

signing_key = open(key, "rb")
fcntl.flock(signing_key, fcntl.LOCK_EX)
previous_mtime = 0
if target.exists():
    previous_mtime = int(target.stat().st_mtime)
    current = json.loads(base64.b64decode(json.loads(target.read_bytes())["payload"]))
    if catalog == current:
        print("Catalog is unchanged")
        raise SystemExit(0)
    if catalog["revision"] <= current["revision"]:
        raise SystemExit("Increase the revision before publishing an update")
signature = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", key], input=raw)
document = json.dumps({
    "payload": base64.b64encode(raw).decode("ascii"),
    "signature": base64.b64encode(signature).decode("ascii"),
}, separators=(",", ":")).encode("ascii") + bytes([10])
target.parent.mkdir(mode=0o755, exist_ok=True)
with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temp:
    temp.write(document)
    temp.flush()
    os.fsync(temp.fileno())
temporary = Path(temp.name)
try:
    temporary.chmod(0o644)
    mtime = max(int(time.time()), previous_mtime + 1)
    os.utime(temporary, (mtime, mtime))
    temporary.replace(target)
finally:
    temporary.unlink(missing_ok=True)
print("Published revision", catalog["revision"], "with", len(catalog["sources"]), "sources")
"""

def main():
    parser = argparse.ArgumentParser(description="Sign and publish the default TVBox source list on flexus")
    parser.add_argument("--catalog", type=Path, default=CATALOG)
    args = parser.parse_args()
    payload = args.catalog.read_bytes()
    command = "python3 -c " + shlex.quote(REMOTE)
    subprocess.run(["ssh", "-o", "BatchMode=yes", "flexus", command], input=payload, check=True)

if __name__ == "__main__":
    main()
