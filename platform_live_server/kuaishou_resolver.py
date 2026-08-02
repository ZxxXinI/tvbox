"""快手直播分类、网页状态和播放地址解析。"""

from __future__ import annotations

import json
import re
from collections import defaultdict
from urllib.parse import unquote, urlparse

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
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
PARENT_CATEGORIES = (
    ("1", "热门"),
    ("2", "网游"),
    ("3", "单机"),
    ("4", "手游"),
    ("5", "棋牌"),
    ("6", "娱乐"),
    ("7", "综合"),
    ("8", "文化"),
)


class KuaishouResolver:
    site_id = "kuaishou"
    site_name = "快手"
    description = "分类直播、网页解析与多画质播放"

    def __init__(self, timeout_seconds: int = 10, cookie: str = "", kww: str = ""):
        self.timeout_seconds = timeout_seconds
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": USER_AGENT})
        self.custom_cookie = cookie.strip()
        self.cookie = self.custom_cookie
        self.cookie_obj = self._parse_cookie(self.custom_cookie)
        self.kww = kww.strip() or self._resolve_server_kww(self.cookie_obj)

    def _headers(self, *, referer: str = "https://live.kuaishou.com/") -> dict[str, str]:
        headers = {
            "User-Agent": USER_AGENT,
            "Accept": "application/json, text/plain, */*",
            "Referer": referer,
        }
        cookie = self._cookie_header()
        if cookie:
            headers["Cookie"] = cookie
        if self.kww:
            headers["Kww"] = self.kww
        return headers

    def _cookie_header(self) -> str:
        return "; ".join(f"{key}={value}" for key, value in self.cookie_obj.items())

    def _get_text(self, url: str, *, params: dict[str, object] | None = None, referer: str | None = None) -> str:
        try:
            response = self.session.get(
                url,
                params=params,
                headers=self._headers(referer=referer or "https://live.kuaishou.com/"),
                timeout=self.timeout_seconds,
            )
        except requests.RequestException as error:
            raise PlatformResolverError(f"快手请求失败：{error}") from error
        self._merge_response_cookies(response)
        response.raise_for_status()
        return response.text

    def _get_json(self, url: str, *, params: dict[str, object] | None = None, referer: str | None = None) -> dict:
        text_body = self._get_text(url, params=params, referer=referer)
        try:
            payload = json.loads(text_body)
        except json.JSONDecodeError as error:
            raise PlatformResolverError("快手接口返回格式异常") from error
        if not isinstance(payload, dict):
            raise PlatformResolverError("快手接口返回格式异常")
        return payload

    def get_category_tree(self) -> tuple[list[LiveParentCategory], list[LiveCategory]]:
        parents = [LiveParentCategory(id=id_, name=name) for id_, name in PARENT_CATEGORIES]
        categories: list[LiveCategory] = []
        for parent_id, parent_name in PARENT_CATEGORIES:
            page = 1
            while page <= 100:
                payload = self._get_json(
                    "https://live.kuaishou.com/live_api/category/data",
                    params={"type": parent_id, "page": page, "size": 30},
                )
                data = payload.get("data")
                values = data.get("list") if isinstance(data, dict) else []
                if not isinstance(values, list) or not values:
                    break
                for item in values:
                    if not isinstance(item, dict):
                        continue
                    category_id = text(item.get("id"))
                    name = text(item.get("name"))
                    if not category_id or not name:
                        continue
                    categories.append(
                        LiveCategory(
                            id=category_id,
                            name=name,
                            parent_id=parent_id,
                            parent_name=parent_name,
                            cover=text(item.get("poster")),
                        ),
                    )
                if len(values) < 30:
                    break
                page += 1
        return parents, categories

    def get_category_rooms(self, category_id: str, page: int) -> LiveRoomsPage:
        normalized_page = max(1, page)
        api = (
            "https://live.kuaishou.com/live_api/gameboard/list"
            if len(text(category_id)) < 7
            else "https://live.kuaishou.com/live_api/non-gameboard/list"
        )
        payload = self._get_json(
            api,
            params={
                "filterType": 0,
                "pageSize": 20,
                "gameId": text(category_id),
                "page": normalized_page,
            },
        )
        data = payload.get("data")
        values = data.get("list") if isinstance(data, dict) else []
        if not isinstance(values, list):
            values = []
        rooms: list[LiveRoom] = []
        for item in values:
            if not isinstance(item, dict):
                continue
            author = item.get("author") if isinstance(item.get("author"), dict) else {}
            room_id = text(author.get("id") or item.get("authorId") or item.get("userId"))
            if not room_id:
                continue
            cover = text(item.get("poster"))
            if cover and not self._is_image(cover):
                cover += ".jpg"
            rooms.append(
                LiveRoom(
                    room_id=room_id,
                    title=text(item.get("caption") or item.get("title"), f"快手直播 {room_id}"),
                    anchor=text(author.get("name")),
                    cover=cover,
                    online=positive_int(item.get("watchingCount")),
                    category_id=text(category_id),
                    category_name="",
                ),
            )
        return LiveRoomsPage(tuple(rooms), normalized_page + (1 if len(values) >= 20 else 0))

    def resolve(self, room_input: str) -> ResolvedLiveStream:
        room_id = self._parse_room_id(room_input)
        url = f"https://live.kuaishou.com/u/{room_id}"
        html = self._get_text(url, referer="https://live.kuaishou.com/")
        state = self._parse_initial_state(html)
        live_room = state.get("liveroom") if isinstance(state, dict) else None
        if not isinstance(live_room, dict):
            raise PlatformResolverError("快手直播间状态解析失败")
        play_list = live_room.get("playList")
        if not isinstance(play_list, list) or not play_list:
            raise PlatformResolverError("快手直播间未返回播放信息")
        rooms = [item for item in play_list if isinstance(item, dict)]
        selected = next((item for item in rooms if self._is_live(item)), rooms[0] if rooms else {})
        if not self._is_live(selected):
            raise PlatformResolverError(f"房间未开播：{self._room_title(selected) or room_id}")
        stream = selected.get("liveStream") if isinstance(selected.get("liveStream"), dict) else selected
        author = selected.get("author") if isinstance(selected.get("author"), dict) else {}
        candidates_by_quality: dict[tuple[int, str], list[LiveStreamCandidate]] = defaultdict(list)
        self._collect_urls(stream.get("playUrls") or selected.get("playUrls"), candidates_by_quality)
        if not candidates_by_quality:
            raise PlatformResolverError("快手未返回可播放地址")
        quality_key = max(candidates_by_quality, key=lambda key: key[0])
        candidates = unique_candidates(candidates_by_quality[quality_key])
        if not candidates:
            raise PlatformResolverError("快手未返回有效播放地址")
        return ResolvedLiveStream(
            room_id=room_id,
            title=self._room_title(selected) or f"快手直播 {room_id}",
            anchor=text(author.get("name")),
            quality=quality_key[1],
            candidates=candidates,
            headers={"Referer": url, "User-Agent": USER_AGENT},
        )

    def _collect_urls(
        self,
        value: object,
        groups: dict[tuple[int, str], list[LiveStreamCandidate]],
        inherited_name: str = "默认",
        inherited_sort: int = 0,
    ) -> None:
        if isinstance(value, str):
            url = value.strip()
            if url.startswith(("http://", "https://", "rtmp://")):
                groups.setdefault((inherited_sort, inherited_name), []).append(
                    LiveStreamCandidate(urlparse(url).hostname or "默认", url, infer_protocol(url)),
                )
            return
        if isinstance(value, list):
            for item in value:
                self._collect_urls(item, groups, inherited_name, inherited_sort)
            return
        if not isinstance(value, dict):
            return
        name = text(value.get("name") or value.get("shortName"), inherited_name)
        sort = value.get("level")
        try:
            sort_value = int(sort) if sort is not None else inherited_sort
        except (TypeError, ValueError):
            sort_value = inherited_sort
        direct_url = value.get("url")
        if isinstance(direct_url, str):
            self._collect_urls(direct_url, groups, name, sort_value)
        for key, child in value.items():
            if key != "url":
                self._collect_urls(child, groups, name, sort_value)

    @staticmethod
    def _parse_initial_state(html: str) -> dict:
        marker = re.search(r"window\.__INITIAL_STATE__\s*=\s*", html)
        if not marker:
            raise PlatformResolverError("快手直播页面结构已变化")
        start = marker.end()
        raw = KuaishouResolver._extract_json_value(html, start)
        if raw is None:
            raise PlatformResolverError("快手直播状态 JSON 无效")
        raw = raw.replace("undefined", "null")
        try:
            state = json.loads(raw)
        except json.JSONDecodeError as error:
            raise PlatformResolverError("快手直播状态 JSON 无效") from error
        if not isinstance(state, dict):
            raise PlatformResolverError("快手直播状态格式异常")
        return state

    @staticmethod
    def _extract_json_value(source: str, start: int) -> str | None:
        opening = next((index for index in range(start, len(source)) if source[index] in "[{"), None)
        if opening is None:
            return None
        stack: list[str] = []
        in_string = False
        escaped = False
        for index in range(opening, len(source)):
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
                continue
            if char in "[{":
                stack.append(char)
            elif char in "]}":
                if not stack or (char == "]" and stack[-1] != "[") or (char == "}" and stack[-1] != "{"):
                    return None
                stack.pop()
                if not stack:
                    return source[opening:index + 1]
        return None

    @staticmethod
    def _is_live(room: dict) -> bool:
        stream = room.get("liveStream") if isinstance(room.get("liveStream"), dict) else room
        return bool(
            text(stream.get("id"))
            or stream.get("isLiving") is True
            or stream.get("living") is True
            or stream.get("isLiving") == 1
            or stream.get("living") == 1
        )

    @staticmethod
    def _room_title(room: dict) -> str:
        stream = room.get("liveStream") if isinstance(room.get("liveStream"), dict) else {}
        game = room.get("gameInfo") if isinstance(room.get("gameInfo"), dict) else {}
        author = room.get("author") if isinstance(room.get("author"), dict) else {}
        for value in (room.get("caption"), room.get("title"), stream.get("caption"), stream.get("title"), game.get("name"), author.get("name")):
            if text(value):
                return text(value)
        return ""

    def _merge_response_cookies(self, response: requests.Response) -> None:
        for key, value in response.cookies.items():
            self.cookie_obj[key] = value
        self.cookie = self._cookie_header()
        if not self.kww:
            self.kww = self._resolve_server_kww(self.cookie_obj)

    @staticmethod
    def _parse_cookie(cookie: str) -> dict[str, str]:
        values: dict[str, str] = {}
        for item in cookie.split(";"):
            if "=" not in item:
                continue
            key, value = item.split("=", 1)
            key, value = key.strip(), value.strip()
            if key and value:
                values[key] = value
        return values

    @staticmethod
    def _resolve_server_kww(cookie_obj: dict[str, str]) -> str:
        value = text(cookie_obj.get("kwfv1"))
        if not value:
            return ""
        try:
            value = unquote(value)
        except Exception:  # noqa: BLE001 - malformed cookie falls back to raw value
            pass
        return f"{value}###ssrc"

    @staticmethod
    def _is_image(url: str) -> bool:
        return bool(re.search(r"\.(?:png|jpe?g|webp|gif|svg|avif)(?:\?|$)", url, re.I))

    @staticmethod
    def _parse_room_id(value: str) -> str:
        raw = text(value)
        parsed = urlparse(raw)
        if parsed.netloc:
            if parsed.netloc.lower().split(":", 1)[0] not in {"live.kuaishou.com", "kuaishou.com"}:
                raise PlatformResolverError("直播间链接不属于快手")
            raw = next((part for part in parsed.path.split("/") if part and part != "u"), "")
        if not raw or not re.fullmatch(r"[A-Za-z0-9_-]+", raw):
            raise PlatformResolverError("快手房间号格式无效")
        return raw
