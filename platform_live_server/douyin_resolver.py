"""抖音直播分类、房间和播放地址解析。

抖音网页接口需要 a_bogus 签名。签名算法位于同目录的
``douyin_sign.js``，本模块通过 Node.js 标准输入输出调用它，服务端不依赖
Dart、QuickJS 或移动端运行时。
"""

from __future__ import annotations

import json
import os
import random
import re
import secrets
import string
import subprocess
from pathlib import Path
from urllib.parse import quote, urlencode, urlparse

import requests

from platform_common import (
    LiveCategory,
    LiveParentCategory,
    LiveRoom,
    LiveRoomsPage,
    LiveStreamCandidate,
    PlatformResolverError,
    ResolvedLiveStream,
    infer_protocol,
    positive_int,
    text,
    unique_candidates,
)


USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/116.0.5845.97 Safari/537.36 "
    "Core/1.116.567.400 QQBrowser/19.7.6764.400"
)
REFERER = "https://live.douyin.com"
DEFAULT_COOKIE = (
    "ttwid=1%7CB1qls3GdnZhUov9o2NxOMxxYS2ff6OSvEWbv0ytbES4%7C1680522049%7C"
    "280d802d6d478e3e78d0c807f7c487e7ffec0ae4e5fdd6a0fe74c3c6af149511"
)
API_BASE = "https://live.douyin.com"
SIGN_HELPER = Path(__file__).with_name("douyin_sign.js")


class DouyinResolver:
    site_id = "douyin"
    site_name = "抖音"
    description = "分类直播、稳定房间号与多画质播放"

    def __init__(self, timeout_seconds: int = 10, cookie: str = ""):
        self.timeout_seconds = timeout_seconds
        self.session = requests.Session()
        self.cookie = cookie.strip() or DEFAULT_COOKIE
        self.node = os.environ.get("TVBOX_PLATFORM_LIVE_NODE", "node").strip() or "node"

    def _headers(self, *, referer: str = REFERER) -> dict[str, str]:
        return {
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Referer": referer,
            "User-Agent": USER_AGENT,
            "Cookie": self.cookie,
        }

    def _get_text(self, url: str, *, headers: dict[str, str] | None = None) -> str:
        try:
            response = self.session.get(
                url,
                headers=headers or self._headers(),
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
        except requests.RequestException as error:
            raise PlatformResolverError(f"抖音请求失败：{error}") from error
        if not response.text.strip():
            raise PlatformResolverError("抖音接口返回为空")
        return response.text

    def _get_json(self, url: str, *, headers: dict[str, str] | None = None) -> dict:
        body = self._get_text(url, headers=headers)
        try:
            payload = json.loads(body)
        except json.JSONDecodeError as error:
            raise PlatformResolverError("抖音接口返回格式异常") from error
        if not isinstance(payload, dict):
            raise PlatformResolverError("抖音接口返回格式异常")
        status_code = payload.get("status_code")
        if status_code not in (None, 0, "0"):
            raise PlatformResolverError(f"抖音接口失败：{text(payload.get('status_msg'), str(status_code))}")
        return payload

    def get_category_tree(self) -> tuple[list[LiveParentCategory], list[LiveCategory]]:
        html = self._get_text("https://live.douyin.com/", headers=self._headers())
        try:
            render_data = self._extract_category_render_data(html)
        except (ValueError, json.JSONDecodeError) as error:
            raise PlatformResolverError("抖音分类数据解析失败") from error
        raw_categories = render_data.get("categoryData")
        if not isinstance(raw_categories, list):
            raise PlatformResolverError("抖音未返回有效直播分类")
        parents: list[LiveParentCategory] = []
        categories: list[LiveCategory] = []
        for item in raw_categories:
            if not isinstance(item, dict):
                continue
            partition = item.get("partition") if isinstance(item.get("partition"), dict) else {}
            parent_id = self._partition_id(partition)
            parent_type = text(partition.get("type"), "1")
            parent_name = text(partition.get("title") or partition.get("name"))
            if not parent_id or not parent_name:
                continue
            parent_key = f"{parent_id},{parent_type}"
            parents.append(LiveParentCategory(parent_key, parent_name, self._partition_image(partition)))
            categories.append(
                LiveCategory(
                    id=parent_key,
                    name=parent_name,
                    parent_id=parent_key,
                    parent_name=parent_name,
                    cover=self._partition_image(partition),
                ),
            )
            children = item.get("sub_partition")
            if not isinstance(children, list):
                continue
            for child in children:
                if not isinstance(child, dict):
                    continue
                child_partition = child.get("partition") if isinstance(child.get("partition"), dict) else {}
                child_id = self._partition_id(child_partition)
                child_type = text(child_partition.get("type"), parent_type)
                child_name = text(child_partition.get("title") or child_partition.get("name"))
                if not child_id or not child_name:
                    continue
                categories.append(
                    LiveCategory(
                        id=f"{child_id},{child_type}",
                        name=child_name,
                        parent_id=parent_key,
                        parent_name=parent_name,
                        cover=self._partition_image(child_partition) or self._partition_image(partition),
                    ),
                )
        return self._dedupe_parents(parents), self._dedupe_categories(categories)

    def get_category_rooms(self, category_id: str, page: int) -> LiveRoomsPage:
        partition_id, partition_type = self._split_category_id(category_id)
        normalized_page = max(1, page)
        params = {
            "aid": "6383",
            "app_name": "douyin_web",
            "live_id": "1",
            "device_platform": "web",
            "language": "zh-CN",
            "enter_from": "link_share",
            "cookie_enabled": "true",
            "screen_width": "1980",
            "screen_height": "1080",
            "browser_language": "zh-CN",
            "browser_platform": "Win32",
            "browser_name": "Edge",
            "browser_version": "125.0.0.0",
            "browser_online": "true",
            "count": "15",
            "offset": str((normalized_page - 1) * 15),
            "partition": partition_id,
            "partition_type": partition_type,
            "req_from": "2",
        }
        payload = self._get_json(self._signed_url(f"{API_BASE}/webcast/web/partition/detail/room/v2/", params))
        data = payload.get("data")
        raw_rooms = data.get("data") if isinstance(data, dict) else []
        if not isinstance(raw_rooms, list):
            raise PlatformResolverError("抖音分类接口返回异常，可能已触发访问限制")
        rooms: list[LiveRoom] = []
        for item in raw_rooms:
            if not isinstance(item, dict):
                continue
            room = item.get("room") if isinstance(item.get("room"), dict) else item
            owner = room.get("owner") if isinstance(room.get("owner"), dict) else {}
            web_rid = text(item.get("web_rid") or room.get("web_rid") or owner.get("web_rid"))
            if not web_rid:
                continue
            rooms.append(
                LiveRoom(
                    room_id=web_rid,
                    title=text(room.get("title"), f"抖音直播 {web_rid}"),
                    anchor=text(owner.get("nickname")),
                    cover=self._first_image(room.get("cover")),
                    online=self._online_value(room.get("room_view_stats")),
                    category_id=category_id,
                    category_name="",
                ),
            )
        return LiveRoomsPage(tuple(rooms), normalized_page + (1 if len(raw_rooms) >= 15 else 0))

    def resolve(self, room_input: str) -> ResolvedLiveStream:
        room_id = self._parse_room_id(room_input)
        detail: dict = {}
        web_rid = room_id
        if len(room_id) > 16:
            try:
                detail = self._get_room_data_by_room_id(room_id)
                room = self._nested_room(detail)
                web_rid = text(room.get("owner", {}).get("web_rid"), room_id) if isinstance(room, dict) else room_id
                if self._status(room) == 4 and web_rid != room_id:
                    detail = {}
            except PlatformResolverError:
                detail = {}
        if not detail:
            try:
                detail = self._get_room_data_by_api(web_rid)
            except PlatformResolverError:
                detail = self._get_room_data_by_html(web_rid)
        room, user = self._room_and_user(detail)
        if not room:
            raise PlatformResolverError("抖音直播间数据为空，可能是房间不存在或被风控限制")
        if self._status(room) != 2:
            raise PlatformResolverError(f"房间未开播：{text(room.get('title'), web_rid)}")
        candidates, quality = self._extract_play_candidates(room)
        if not candidates:
            raise PlatformResolverError("抖音未返回可播放地址")
        owner = room.get("owner") if isinstance(room.get("owner"), dict) else user
        resolved_room_id = text(owner.get("web_rid"), web_rid) if isinstance(owner, dict) else web_rid
        return ResolvedLiveStream(
            room_id=resolved_room_id,
            title=text(room.get("title"), f"抖音直播 {resolved_room_id}"),
            anchor=text(owner.get("nickname")) if isinstance(owner, dict) else "",
            quality=quality,
            candidates=unique_candidates(candidates),
            headers={"Referer": f"https://live.douyin.com/{resolved_room_id}", "User-Agent": USER_AGENT},
        )

    def _get_room_data_by_api(self, web_rid: str) -> dict:
        params = {
            "aid": "6383",
            "app_name": "douyin_web",
            "live_id": "1",
            "device_platform": "web",
            "language": "zh-CN",
            "browser_language": "zh-CN",
            "browser_platform": "Win32",
            "browser_name": "Chrome",
            "browser_version": "125.0.0.0",
            "web_rid": web_rid,
            "msToken": "",
        }
        payload = self._get_json(
            self._signed_url(f"{API_BASE}/webcast/room/web/enter/", params),
            headers=self._headers(referer=f"{API_BASE}/{web_rid}"),
        )
        data = payload.get("data")
        if not isinstance(data, dict) or not isinstance(data.get("data"), list) or not data["data"]:
            raise PlatformResolverError("抖音直播间 API 数据为空")
        return data

    def _get_room_data_by_room_id(self, room_id: str) -> dict:
        params = {
            "type_id": 0,
            "live_id": 1,
            "room_id": room_id,
            "sec_user_id": "",
            "version_code": "99.99.99",
            "app_id": 6383,
        }
        query = urlencode(params)
        return self._get_json(f"https://webcast.amemv.com/webcast/room/reflow/info/?{query}")

    def _get_room_data_by_html(self, web_rid: str) -> dict:
        html = self._get_text(f"{API_BASE}/{quote(web_rid, safe='')}", headers=self._headers(referer=REFERER))
        normalized_html = html.replace(r'\"', '"').replace(r'\/', '/').replace("\\\\", "\\")
        marker = normalized_html.find('"state":')
        if marker < 0:
            raise PlatformResolverError("抖音直播间页面数据不可用")
        start = normalized_html.find("{", marker)
        raw = self._extract_balanced(normalized_html, start)
        if not raw:
            raise PlatformResolverError("抖音直播间页面数据解析失败")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError as error:
            raise PlatformResolverError("抖音直播间状态 JSON 无效") from error
        state = parsed.get("state") if isinstance(parsed, dict) else None
        if not isinstance(state, dict):
            raise PlatformResolverError("抖音直播间状态数据异常")
        return state

    def _signed_url(self, base_url: str, params: dict[str, object]) -> str:
        query = urlencode([(key, str(value)) for key, value in params.items()])
        token = self._random_token(107)
        signed_query = f"{query}&msToken={quote(token, safe='')}"
        try:
            result = subprocess.run(
                [self.node, str(SIGN_HELPER)],
                input=json.dumps({"params": signed_query, "userAgent": USER_AGENT}) + "\n",
                text=True,
                capture_output=True,
                timeout=max(5, self.timeout_seconds),
                check=False,
            )
        except (OSError, subprocess.SubprocessError) as error:
            raise PlatformResolverError(f"抖音签名助手启动失败：{error}") from error
        if result.returncode != 0:
            raise PlatformResolverError("抖音签名助手执行失败")
        try:
            response = json.loads(result.stdout.strip().splitlines()[-1])
        except (json.JSONDecodeError, IndexError) as error:
            raise PlatformResolverError("抖音签名助手返回格式异常") from error
        signature = text(response.get("a_bogus"))
        if not signature:
            raise PlatformResolverError(f"抖音签名失败：{text(response.get('error'), '未知错误')}")
        return f"{base_url}?{signed_query}&a_bogus={quote(signature, safe='')}"

    def _extract_play_candidates(self, room: dict) -> tuple[list[LiveStreamCandidate], str]:
        stream_url = room.get("stream_url") if isinstance(room.get("stream_url"), dict) else {}
        live_core = stream_url.get("live_core_sdk_data") if isinstance(stream_url.get("live_core_sdk_data"), dict) else {}
        pull_data = live_core.get("pull_data") if isinstance(live_core.get("pull_data"), dict) else {}
        options = pull_data.get("options") if isinstance(pull_data.get("options"), dict) else {}
        qualities = options.get("qualities") if isinstance(options.get("qualities"), list) else []
        groups: dict[int, tuple[str, list[LiveStreamCandidate]]] = {}
        stream_data = pull_data.get("stream_data")
        decoded_stream: dict = {}
        if isinstance(stream_data, str) and stream_data.startswith("{"):
            try:
                parsed = json.loads(stream_data)
                decoded_stream = parsed.get("data", parsed) if isinstance(parsed, dict) else {}
            except json.JSONDecodeError:
                decoded_stream = {}
        for quality in qualities:
            if not isinstance(quality, dict):
                continue
            level = self._quality_level(quality)
            name = text(quality.get("name") or quality.get("desc"), f"画质 {level}")
            sdk_key = text(quality.get("sdk_key"))
            source = decoded_stream.get(sdk_key, {}) if sdk_key else {}
            main = source.get("main", {}) if isinstance(source, dict) else {}
            urls = [main.get("flv"), main.get("hls")]
            candidates = self._url_candidates(urls)
            if candidates:
                groups[level] = (name, candidates)
        if not groups:
            groups = self._fallback_quality_groups(room, qualities)
        if not groups:
            groups = self._recursive_quality_groups(stream_url)
        if not groups:
            return [], ""
        best_level = max(groups)
        name, candidates = groups[best_level]
        return candidates, name

    def _fallback_quality_groups(self, room: dict, qualities: list[object]) -> dict[int, tuple[str, list[LiveStreamCandidate]]]:
        stream_url = room.get("stream_url") if isinstance(room.get("stream_url"), dict) else {}
        flv = list(self._url_values(stream_url.get("flv_pull_url") or room.get("flv_pull_url")))
        hls = list(self._url_values(stream_url.get("hls_pull_url_map") or room.get("hls_pull_url_map")))
        if not flv and not hls:
            return {}
        result: dict[int, tuple[str, list[LiveStreamCandidate]]] = {}
        for quality in qualities or [{"level": 1, "name": "原画"}]:
            if not isinstance(quality, dict):
                continue
            level = self._quality_level(quality)
            name = text(quality.get("name") or quality.get("desc"), f"画质 {level}")
            urls: list[object] = []
            index = len(flv) - level
            if 0 <= index < len(flv):
                urls.append(flv[index])
            index = len(hls) - level
            if 0 <= index < len(hls):
                urls.append(hls[index])
            candidates = self._url_candidates(urls)
            if candidates:
                result[level] = (name, candidates)
        return result

    def _recursive_quality_groups(self, value: object, level: int = 0, name: str = "原画") -> dict[int, tuple[str, list[LiveStreamCandidate]]]:
        groups: dict[int, tuple[str, list[LiveStreamCandidate]]] = {}
        if isinstance(value, str):
            candidates = self._url_candidates([value])
            if candidates:
                groups[level] = (name, candidates)
            return groups
        if isinstance(value, list):
            for item in value:
                groups.update(self._recursive_quality_groups(item, level, name))
            return groups
        if not isinstance(value, dict):
            return groups
        current_level = self._quality_level(value, level)
        current_name = text(value.get("name") or value.get("shortName"), name)
        direct = self._url_candidates(value.get("url"))
        if direct:
            groups[current_level] = (current_name, direct)
        for key, child in value.items():
            if key != "url":
                groups.update(self._recursive_quality_groups(child, current_level, current_name))
        return groups

    def _room_and_user(self, data: dict) -> tuple[dict, dict]:
        if isinstance(data.get("data"), list) and data["data"]:
            room = data["data"][0] if isinstance(data["data"][0], dict) else {}
            user = data.get("user") if isinstance(data.get("user"), dict) else {}
            return room, user
        nested = self._nested_room(data)
        return nested, {}

    @staticmethod
    def _nested_room(data: dict) -> dict:
        if isinstance(data.get("room"), dict):
            return data["room"]
        room_store = data.get("roomStore") if isinstance(data.get("roomStore"), dict) else {}
        info = room_store.get("roomInfo") if isinstance(room_store.get("roomInfo"), dict) else {}
        return info.get("room") if isinstance(info.get("room"), dict) else {}

    @staticmethod
    def _status(room: dict) -> int:
        for key in ("status", "live_status", "room_status"):
            value = room.get(key)
            if isinstance(value, dict):
                value = value.get("status") or value.get("live_status")
            try:
                return int(value)
            except (TypeError, ValueError):
                continue
        return 0

    @staticmethod
    def _quality_level(value: dict, fallback: int = 0) -> int:
        try:
            return int(value.get("level", fallback))
        except (TypeError, ValueError):
            return fallback

    @staticmethod
    def _url_values(value: object) -> tuple[str, ...]:
        if isinstance(value, dict):
            return tuple(str(item) for item in value.values() if isinstance(item, str) and item.strip())
        if isinstance(value, list):
            return tuple(item for item in value if isinstance(item, str) and item.strip())
        return (value,) if isinstance(value, str) and value.strip() else ()

    @staticmethod
    def _url_candidates(values: object) -> list[LiveStreamCandidate]:
        result: list[LiveStreamCandidate] = []
        for value in values if isinstance(values, (list, tuple)) else [values]:
            for url in DouyinResolver._url_values(value):
                if url.startswith(("http://", "https://", "rtmp://")):
                    result.append(LiveStreamCandidate(urlparse(url).hostname or "默认", url, infer_protocol(url)))
        return list(unique_candidates(result))

    @staticmethod
    def _partition_id(value: dict) -> str:
        return text(value.get("id_str") or value.get("id") or value.get("partition_id") or value.get("partition"))

    @staticmethod
    def _partition_image(value: object) -> str:
        if isinstance(value, str):
            return value.strip()
        if isinstance(value, list):
            for item in value:
                result = DouyinResolver._partition_image(item)
                if result:
                    return result
            return ""
        if isinstance(value, dict):
            for key in ("icon", "icons", "cover", "background", "avatar_thumb", "image", "image_url", "url", "url_list", "static_icon"):
                result = DouyinResolver._partition_image(value.get(key))
                if result:
                    return result
            for child in value.values():
                result = DouyinResolver._partition_image(child)
                if result:
                    return result
        return ""

    @staticmethod
    def _first_image(value: object) -> str:
        if isinstance(value, dict):
            values = value.get("url_list")
            if isinstance(values, list) and values:
                return text(values[0])
            return DouyinResolver._partition_image(value)
        return text(value)

    @staticmethod
    def _online_value(value: object) -> int:
        if isinstance(value, dict):
            raw = value.get("display_value") or value.get("user_count") or value.get("total_user")
            return positive_int(raw)
        return positive_int(value)

    @staticmethod
    def _split_category_id(value: str) -> tuple[str, str]:
        parts = [item.strip() for item in value.split(",", 1)]
        if len(parts) == 2 and all(parts):
            return parts[0], parts[1]
        parts = [item.strip() for item in value.split(":", 1)]
        if len(parts) == 2 and all(parts):
            return parts[0], parts[1]
        raise PlatformResolverError("抖音分类 ID 格式无效")

    @staticmethod
    def _parse_room_id(value: str) -> str:
        raw = text(value)
        parsed = urlparse(raw)
        if parsed.netloc:
            host = parsed.netloc.lower().split(":", 1)[0]
            if host not in {"live.douyin.com", "douyin.com", "www.douyin.com"}:
                raise PlatformResolverError("直播间链接不属于抖音")
            raw = next((part for part in parsed.path.split("/") if part), "")
        if not raw or not re.fullmatch(r"[A-Za-z0-9_-]+", raw):
            raise PlatformResolverError("抖音房间号格式无效")
        return raw

    @staticmethod
    def _extract_category_render_data(html: str) -> dict:
        normalized_html = html.replace(r'\"', '"').replace(r'\/', '/').replace("\\\\", "\\")
        marker = normalized_html.find('"categoryData":')
        if marker < 0:
            raise PlatformResolverError("抖音分类数据解析失败")
        start = normalized_html.find("[", marker)
        raw = DouyinResolver._extract_balanced(normalized_html, start)
        if not raw:
            raise PlatformResolverError("抖音分类数据解析失败")
        return json.loads(f'{{"categoryData":{raw}}}')

    @staticmethod
    def _extract_balanced(source: str, start: int) -> str:
        if start < 0:
            return ""
        stack: list[str] = []
        in_string = False
        escaped = False
        for index in range(start, len(source)):
            char = source[index]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
                continue
            if char == '"':
                in_string = True
            elif char in "[{":
                stack.append(char)
            elif char in "]}":
                if not stack or (char == "]" and stack[-1] != "[") or (char == "}" and stack[-1] != "{"):
                    return ""
                stack.pop()
                if not stack:
                    return source[start:index + 1]
        return ""

    @staticmethod
    def _random_token(length: int) -> str:
        alphabet = string.ascii_letters + string.digits
        return "".join(secrets.choice(alphabet) for _ in range(length))

    @staticmethod
    def _dedupe_parents(items: list[LiveParentCategory]) -> list[LiveParentCategory]:
        return list({item.id: item for item in items}.values())

    @staticmethod
    def _dedupe_categories(items: list[LiveCategory]) -> list[LiveCategory]:
        return list({item.id: item for item in items}.values())
