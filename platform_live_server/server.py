"""TVBox 斗鱼平台直播测试服务。

服务只解析和短暂缓存播放地址；电视端仍直接请求斗鱼 CDN，不经由本服务转发视频。
"""

from __future__ import annotations

import argparse
import json
import os
import threading
import time
import traceback
from dataclasses import asdict, dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import requests

from bilibili_resolver import BilibiliResolver
from douyin_resolver import DouyinResolver
from douyu_resolver import DouyuResolver, DouyuResolverError, parse_room_id
from huya_resolver import HuyaResolver
from kuaishou_resolver import KuaishouResolver
from platform_common import (
    LiveCategory,
    LiveParentCategory,
    LiveRoom,
    LiveRoomsPage,
    LiveStreamCandidate,
    PlatformResolverError,
    ResolvedLiveStream,
)


CATALOG_PATH = Path(__file__).with_name("catalog.json")
SERVICE_NAME = "tvbox-platform-live"
SERVICE_VERSION = 4


@dataclass(frozen=True)
class CatalogChannel:
    id: str
    name: str
    site: str
    roomId: str
    group: str = "默认"


@dataclass(frozen=True)
class PlatformSite:
    id: str
    name: str
    description: str


class StreamCache:
    def __init__(self, cache_seconds: int):
        self.cache_seconds = cache_seconds
        self._values: dict[tuple[str, str], tuple[float, ResolvedLiveStream]] = {}
        self._lock = threading.Lock()

    def get_or_resolve(
        self,
        site: str,
        room_id: str,
        resolve,
    ) -> tuple[ResolvedLiveStream, int]:
        now = time.time()
        key = (site.lower(), room_id)
        with self._lock:
            cached = self._values.get(key)
            if cached is not None and now - cached[0] < self.cache_seconds:
                remaining = max(0, int(self.cache_seconds - (now - cached[0])))
                return cached[1], remaining

        resolved = resolve(room_id)
        with self._lock:
            self._values[key] = (now, resolved)
        return resolved, self.cache_seconds

    def resolve_fresh(self, site: str, room_id: str, resolve) -> tuple[ResolvedLiveStream, int]:
        resolved = resolve(room_id)
        with self._lock:
            self._values[(site.lower(), room_id)] = (time.time(), resolved)
        return resolved, self.cache_seconds


class DouyuAdapter:
    site_id = "douyu"
    site_name = "斗鱼"
    description = "分类直播、房间浏览与多 CDN 播放"

    def __init__(self, timeout_seconds: int):
        self.resolver = DouyuResolver(timeout_seconds=timeout_seconds)

    def normalize_room_id(self, value: str) -> str:
        return parse_room_id(value)

    def get_category_tree(self) -> tuple[list[LiveParentCategory], list[LiveCategory]]:
        try:
            parents, categories = self.resolver.get_category_tree()
        except requests.RequestException as error:
            raise DouyuResolverError(f"斗鱼请求失败：{error}") from error
        return (
            [LiveParentCategory(item.id, item.name, item.cover) for item in parents],
            [LiveCategory(item.id, item.name, item.parent_id, item.parent_name, item.cover) for item in categories],
        )

    def get_category_rooms(self, category_id: str, page: int) -> LiveRoomsPage:
        try:
            rooms, page_count = self.resolver.get_category_rooms(category_id, page)
        except requests.RequestException as error:
            raise DouyuResolverError(f"斗鱼请求失败：{error}") from error
        return LiveRoomsPage(
            tuple(
                LiveRoom(
                    room_id=item.room_id,
                    title=item.title,
                    anchor=item.anchor,
                    cover=item.cover,
                    online=item.online,
                    category_id=item.category_id,
                    category_name=item.category_name,
                )
                for item in rooms
            ),
            page_count,
        )

    def resolve(self, room_id: str) -> ResolvedLiveStream:
        try:
            stream = self.resolver.resolve(room_id)
        except requests.RequestException as error:
            raise DouyuResolverError(f"斗鱼请求失败：{error}") from error
        return ResolvedLiveStream(
            room_id=stream.room_id,
            title=stream.title,
            anchor=stream.anchor,
            quality=stream.quality,
            candidates=tuple(
                LiveStreamCandidate(item.cdn, item.url, item.protocol)
                for item in stream.candidates
            ),
            headers=stream.headers,
            is_loop=stream.is_loop,
        )


class LiveService:
    def __init__(self, catalog_path: Path, cache_seconds: int, request_timeout_seconds: int):
        self.catalog_path = catalog_path
        self.cache = StreamCache(cache_seconds=cache_seconds)
        self.adapters = {
            "douyu": DouyuAdapter(request_timeout_seconds),
            "huya": HuyaResolver(
                timeout_seconds=request_timeout_seconds,
                cookie=os.environ.get("TVBOX_PLATFORM_LIVE_HUYA_COOKIE", ""),
            ),
            "bilibili": BilibiliResolver(
                timeout_seconds=request_timeout_seconds,
                cookie=os.environ.get("TVBOX_PLATFORM_LIVE_BILIBILI_COOKIE", ""),
            ),
            "douyin": DouyinResolver(
                timeout_seconds=request_timeout_seconds,
                cookie=os.environ.get("TVBOX_PLATFORM_LIVE_DOUYIN_COOKIE", ""),
            ),
            "kuaishou": KuaishouResolver(
                timeout_seconds=request_timeout_seconds,
                cookie=os.environ.get("TVBOX_PLATFORM_LIVE_KUAISHOU_COOKIE", ""),
                kww=os.environ.get("TVBOX_PLATFORM_LIVE_KUAISHOU_KWW", ""),
            ),
        }

    def sites(self) -> list[PlatformSite]:
        return [
            PlatformSite(adapter.site_id, adapter.site_name, adapter.description)
            for adapter in self.adapters.values()
        ]

    def _adapter(self, site: str):
        normalized = site.strip().lower()
        adapter = self.adapters.get(normalized)
        if adapter is None:
            raise PlatformResolverError(f"暂不支持直播平台：{site}")
        return adapter

    def categories(self, site: str) -> dict[str, object]:
        adapter = self._adapter(site)
        parent_categories, categories = adapter.get_category_tree()
        return {
            "site": adapter.site_id,
            "parentCategories": [
                {
                    "id": category.id,
                    "name": category.name,
                    "cover": category.cover,
                }
                for category in parent_categories
            ],
            "categories": [
                {
                    "id": category.id,
                    "name": category.name,
                    "parentId": category.parent_id,
                    "parentName": category.parent_name,
                    "cover": category.cover,
                }
                for category in categories
            ],
        }

    def rooms(self, site: str, category_id: str, page: int) -> dict[str, object]:
        adapter = self._adapter(site)
        result = adapter.get_category_rooms(category_id, page)
        normalized_page = max(1, page)
        return {
            "site": adapter.site_id,
            "categoryId": category_id,
            "page": normalized_page,
            "pageCount": result.page_count,
            "hasMore": normalized_page < result.page_count,
            "rooms": [
                {
                    "roomId": room.room_id,
                    "title": room.title,
                    "anchor": room.anchor,
                    "cover": room.cover,
                    "online": room.online,
                    "categoryId": room.category_id,
                    "categoryName": room.category_name,
                }
                for room in result.rooms
            ],
        }

    def catalog(self) -> list[CatalogChannel]:
        try:
            raw_channels = json.loads(self.catalog_path.read_text(encoding="utf-8-sig"))
        except FileNotFoundError as error:
            raise DouyuResolverError(f"频道目录不存在：{self.catalog_path.name}") from error
        except json.JSONDecodeError as error:
            raise DouyuResolverError(f"频道目录 JSON 格式错误：{error.msg}") from error
        if not isinstance(raw_channels, list):
            raise DouyuResolverError("频道目录必须是 JSON 数组")

        channels: list[CatalogChannel] = []
        seen_ids: set[str] = set()
        for item in raw_channels:
            if not isinstance(item, dict):
                continue
            site = str(item.get("site") or "").lower()
            if site != "douyu":
                continue
            channel_id = str(item.get("id") or "").strip()
            name = str(item.get("name") or "").strip()
            try:
                room_id = parse_room_id(str(item.get("roomId") or ""))
            except DouyuResolverError:
                continue
            if not channel_id or not name or channel_id in seen_ids:
                continue
            seen_ids.add(channel_id)
            channels.append(
                CatalogChannel(
                    id=channel_id,
                    name=name,
                    site="douyu",
                    roomId=room_id,
                    group=str(item.get("group") or "默认").strip() or "默认",
                ),
            )
        return channels

    def resolve(self, site: str, room_input: str, force_refresh: bool = False) -> dict[str, object]:
        adapter = self._adapter(site)
        normalizer = getattr(adapter, "normalize_room_id", None) or getattr(adapter, "_parse_room_id", None)
        if normalizer is None:
            raise PlatformResolverError(f"{adapter.site_name}未提供房间号归一化方法")
        room_id = normalizer(room_input)
        stream, cache_remaining = (
            self.cache.resolve_fresh(adapter.site_id, room_id, adapter.resolve)
            if force_refresh
            else self.cache.get_or_resolve(adapter.site_id, room_id, adapter.resolve)
        )
        return {
            "live": True,
            "roomId": stream.room_id,
            "title": stream.title,
            "anchor": stream.anchor,
            "quality": stream.quality,
            "protocol": stream.protocol,
            "isLoop": stream.is_loop,
            "url": stream.url,
            "headers": stream.headers,
            "streams": [
                {
                    "cdn": candidate.cdn,
                    "url": candidate.url,
                    "protocol": candidate.protocol,
                }
                for candidate in stream.candidates
            ],
            "cacheSecondsRemaining": cache_remaining,
        }


def create_handler(service: LiveService):
    class LiveRequestHandler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
            request = urlparse(self.path)
            query = parse_qs(request.query)
            try:
                if request.path == "/health":
                    self._write_json(
                        HTTPStatus.OK,
                        {"status": "ok", "service": SERVICE_NAME, "version": SERVICE_VERSION},
                    )
                    return
                if request.path == "/v1/live/catalog":
                    self._write_json(
                        HTTPStatus.OK,
                        {"channels": [asdict(channel) for channel in service.catalog()]},
                    )
                    return
                if request.path == "/v1/live/sites":
                    self._write_json(
                        HTTPStatus.OK,
                        {"sites": [asdict(site) for site in service.sites()]},
                    )
                    return
                if request.path == "/v1/live/categories":
                    self._write_json(
                        HTTPStatus.OK,
                        service.categories(site=self._single_query(query, "site")),
                    )
                    return
                if request.path == "/v1/live/rooms":
                    self._write_json(
                        HTTPStatus.OK,
                        service.rooms(
                            site=self._single_query(query, "site"),
                            category_id=self._single_query(query, "categoryId"),
                            page=self._page_query(query),
                        ),
                    )
                    return
                if request.path == "/v1/live/resolve":
                    site = self._single_query(query, "site")
                    room_id = self._single_query(query, "roomId")
                    force_refresh = query.get("refresh", [""])[0].lower() in {"1", "true"}
                    self._write_json(
                        HTTPStatus.OK,
                        service.resolve(
                            site=site,
                            room_input=room_id,
                            force_refresh=force_refresh,
                        ),
                    )
                    return
                self._write_json(HTTPStatus.NOT_FOUND, {"error": "接口不存在"})
            except (DouyuResolverError, PlatformResolverError) as error:
                self._write_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
            except Exception:  # noqa: BLE001 - 服务端详细错误仅写入本机日志
                traceback.print_exc()
                self._write_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "解析服务内部错误，请查看电脑终端日志"})

        def _single_query(self, query: dict[str, list[str]], name: str) -> str:
            value = query.get(name, [""])[0].strip()
            if not value:
                raise PlatformResolverError(f"缺少参数：{name}")
            return value

        def _page_query(self, query: dict[str, list[str]]) -> int:
            value = query.get("page", ["1"])[0].strip()
            try:
                return max(1, int(value))
            except ValueError as error:
                raise PlatformResolverError("页码必须为正整数") from error

        def _write_json(self, status: HTTPStatus, body: dict[str, object]) -> None:
            data = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            self.send_response(status.value)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, format: str, *args: object) -> None:
            print(f"[{self.log_date_time_string()}] {format % args}", flush=True)

    return LiveRequestHandler


def main() -> None:
    parser = argparse.ArgumentParser(description="TVBox 多平台直播解析服务")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址，默认允许局域网访问")
    parser.add_argument("--port", default=8868, type=int, help="监听端口，默认 8868")
    parser.add_argument("--catalog", default=str(CATALOG_PATH), help="频道目录 JSON 文件路径")
    parser.add_argument("--cache-seconds", default=30, type=int, help="成功直链的短缓存时间")
    parser.add_argument("--timeout", default=10, type=int, help="请求斗鱼及验证 CDN 的超时秒数")
    args = parser.parse_args()

    service = LiveService(
        catalog_path=Path(args.catalog),
        cache_seconds=max(0, args.cache_seconds),
        request_timeout_seconds=max(1, args.timeout),
    )
    server = ThreadingHTTPServer((args.host, args.port), create_handler(service))
    print(f"{SERVICE_NAME} 已启动：http://{args.host}:{args.port}", flush=True)
    print("健康检查：GET /health", flush=True)
    print("频道目录：GET /v1/live/catalog", flush=True)
    print("平台列表：GET /v1/live/sites", flush=True)
    print("分类目录：GET /v1/live/categories?site=douyu|huya|bilibili|douyin|kuaishou", flush=True)
    print("分类房间：GET /v1/live/rooms?site=douyu&categoryId=183&page=1", flush=True)
    print("播放解析：GET /v1/live/resolve?site=douyu&roomId=36252", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止。", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
