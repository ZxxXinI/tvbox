"""Local deterministic MacCMS/live fixtures for the debug AcceptanceActivity."""
import argparse
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

BASE = "http://127.0.0.1:8766"
CLASSES = [{"type_id": 2, "type_pid": 0, "type_name": "电视剧"},
           {"type_id": 13, "type_pid": 2, "type_name": "国产剧"}]

def movie(index):
    episodes = "#".join(f"第{i}集${BASE}/video.mp4?episode={i}" for i in range(1, 4))
    alternate = "#".join(f"第{i}集${BASE}/video.mp4?line=b&episode={i}" for i in range(1, 4))
    return {"vod_id": index, "vod_name": f"验收剧集{index:02d}", "type_id": 13,
            "type_name": "国产剧", "vod_pic": "", "vod_remarks": "全3集", "vod_year": "2026",
            "vod_content": "Local generated video for playback acceptance.",
            "vod_play_from": "验收线路A$$$验收线路B", "vod_play_url": episodes + "$$$" + alternate}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

    def do_GET(self):
        url = urlsplit(self.path)
        query = parse_qs(url.query)
        if url.path == "/video.mp4":
            size = self.server.video.stat().st_size
            match = re.match(r"bytes=(\d+)-(\d*)", self.headers.get("Range", ""))
            start = int(match[1]) if match else 0
            end = min(int(match[2]) if match and match[2] else size - 1, size - 1)
            self.send_response(206 if match else 200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(end - start + 1))
            if match:
                self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
            self.end_headers()
            try:
                with self.server.video.open("rb") as stream:
                    stream.seek(start)
                    remaining = end - start + 1
                    while remaining:
                        data = stream.read(min(remaining, 65536))
                        if not data: break
                        self.wfile.write(data)
                        remaining -= len(data)
            except (BrokenPipeError, ConnectionResetError):
                pass
            return
        if url.path == "/live.txt":
            body = f"验收频道,#genre#\n验收一台,{BASE}/video.mp4?live=1\n验收二台,{BASE}/video.mp4?live=2".encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if url.path.startswith(("/api", "/alt-api")):
            records = [movie(i) for i in range(1, 25)]
            if url.path.startswith("/alt-api"):
                time.sleep(self.server.alt_delay)
                for item in records:
                    item["vod_play_from"] = "验收线路B"
                    item["vod_play_url"] = item["vod_play_url"].split("$$$")[1]
            if "ids" in query:
                ids = query["ids"][0].split(",")
                records = [item for item in records if str(item["vod_id"]) in ids]
            if "wd" in query:
                records = [item for item in records if query["wd"][0] in item["vod_name"]]
            result = {"code": 1, "msg": "ok", "page": 1, "pagecount": 1, "limit": 24,
                      "total": len(records), "class": CLASSES, "list": records}
        elif url.path.endswith("/sites"):
            result = {"sites": [{"id": "bilibili", "name": "验收平台"}]}
        elif url.path.endswith("/categories"):
            result = {"parentCategories": [{"id": "1", "name": "验收分类"}],
                      "categories": [{"id": "11", "name": "验收子分类", "parentId": "1", "parentName": "验收分类"}]}
        elif url.path.endswith("/rooms"):
            result = {"page": 1, "pageCount": 1, "rooms": [{"roomId": "1", "title": "验收直播间", "anchor": "本地验收"}]}
        elif url.path.endswith("/resolve"):
            result = {"live": True, "title": "验收直播间", "quality": "test", "protocol": "mp4", "url": BASE + "/video.mp4?room=1"}
        else:
            self.send_error(404)
            return
        body = json.dumps(result, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--video", required=True, type=Path)
    parser.add_argument("--alt-delay", type=float, default=0)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", 8766), Handler)
    server.video = args.video
    server.alt_delay = args.alt_delay
    server.serve_forever()
