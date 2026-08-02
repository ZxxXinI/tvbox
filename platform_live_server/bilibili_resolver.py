"""哔哩哔哩直播分类、房间和播放地址解析。"""

from __future__ import annotations

import hashlib
import re
import threading
import time
from urllib.parse import quote, urlparse

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
    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
)
REFERER = "https://live.bilibili.com/"
MIXIN_KEY_TABLE = [
    46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
    27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
    37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
    22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
]


class BilibiliResolver:
    site_id = "bilibili"
    site_name = "哔哩哔哩"
    description = "分类直播、房间浏览与多线路播放"

    def __init__(self, timeout_seconds: int = 10, cookie: str = ""):
        self.timeout_seconds = timeout_seconds
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": USER_AGENT})
        self.cookie = cookie.strip()
        self._wbi_keys: tuple[str, str] | None = None
        self._buvid: tuple[str, str] | None = None
        self._play_lock = threading.Lock()
        self._last_play_request = 0.0

    def _headers(self) -> dict[str, str]:
        headers = {"User-Agent": USER_AGENT, "Referer": REFERER}
        cookie = self.cookie
        if "buvid3=" not in cookie:
            buvid3, buvid4 = self._get_buvid()
            extra = ";".join(item for item in (f"buvid3={buvid3}" if buvid3 else "", f"buvid4={buvid4}" if buvid4 else "") if item)
            if extra:
                cookie = f"{cookie};{extra}" if cookie else extra
        if cookie:
            headers["Cookie"] = cookie
        return headers

    def _get_buvid(self) -> tuple[str, str]:
        if self._buvid is not None:
            return self._buvid
        try:
            response = self.session.get(
                "https://api.bilibili.com/x/frontend/finger/spi",
                headers={"User-Agent": USER_AGENT, "Referer": REFERER},
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
            payload = response.json()
            data = payload.get("data") if isinstance(payload, dict) else {}
            if isinstance(data, dict):
                self._buvid = (text(data.get("b_3")), text(data.get("b_4")))
            else:
                self._buvid = ("", "")
        except (requests.RequestException, ValueError):
            self._buvid = ("", "")
        return self._buvid

    def _get_json(self, url: str, *, params: dict[str, object] | None = None) -> dict:
        retry_delays = (0.8, 1.6)
        for attempt in range(len(retry_delays) + 1):
            try:
                response = self.session.get(
                    url,
                    params=params,
                    headers=self._headers(),
                    timeout=self.timeout_seconds,
                )
            except requests.RequestException as error:
                raise PlatformResolverError(f"B站请求失败：{error}") from error
            if response.status_code == 429 and attempt < len(retry_delays):
                time.sleep(retry_delays[attempt])
                continue
            response.raise_for_status()
            try:
                payload = response.json()
            except ValueError as error:
                raise PlatformResolverError("B站接口返回格式异常") from error
            if not isinstance(payload, dict):
                raise PlatformResolverError("B站接口返回格式异常")
            raw_code = payload.get("code", 0)
            try:
                code = int(raw_code)
            except (TypeError, ValueError):
                code = 0
            if code != 0:
                raise PlatformResolverError(
                    f"B站接口失败：{text(payload.get('message'), str(raw_code))}",
                )
            return payload
        raise PlatformResolverError("B站接口重试失败")

    def get_category_tree(self) -> tuple[list[LiveParentCategory], list[LiveCategory]]:
        payload = self._get_json(
            "https://api.live.bilibili.com/room/v1/Area/getList",
            params={"need_entrance": 1, "parent_id": 0},
        )
        parents: list[LiveParentCategory] = []
        categories: list[LiveCategory] = []
        data = payload.get("data")
        if not isinstance(data, list):
            raise PlatformResolverError("B站未返回有效的直播分类")
        for parent in data:
            if not isinstance(parent, dict):
                continue
            parent_id = text(parent.get("id"))
            parent_name = text(parent.get("name"))
            if not parent_id or not parent_name:
                continue
            parents.append(
                LiveParentCategory(
                    id=parent_id,
                    name=parent_name,
                    cover=text(parent.get("pic")),
                ),
            )
            children = parent.get("list")
            if not isinstance(children, list):
                continue
            for child in children:
                if not isinstance(child, dict):
                    continue
                category_id = text(child.get("id"))
                name = text(child.get("name"))
                if not category_id or not name:
                    continue
                cover = text(child.get("pic"))
                if cover and "@" not in cover:
                    cover += "@100w.png"
                categories.append(
                    LiveCategory(
                        id=f"{text(child.get('parent_id'), parent_id)}:{category_id}",
                        name=name,
                        parent_id=text(child.get("parent_id"), parent_id),
                        parent_name=parent_name,
                        cover=cover,
                    ),
                )
        return parents, categories

    def get_category_rooms(self, category_id: str, page: int) -> LiveRoomsPage:
        normalized_page = max(1, page)
        parent_id, area_id = self._split_category_id(category_id)
        payload = self._get_json(
            "https://api.live.bilibili.com/room/v1/Area/getRoomList",
            params={
                "platform": "web",
                "parent_area_id": parent_id,
                "area_id": area_id,
                "page": normalized_page,
                "page_size": 30,
            },
        )
        data = payload.get("data")
        if not isinstance(data, list):
            data = []
        rooms: list[LiveRoom] = []
        for item in data:
            if not isinstance(item, dict):
                continue
            room_id = text(item.get("roomid"))
            if not room_id:
                continue
            cover = text(item.get("cover") or item.get("user_cover") or item.get("system_cover"))
            if cover and "@" not in cover:
                cover += "@400w.jpg"
            rooms.append(
                LiveRoom(
                    room_id=room_id,
                    title=text(item.get("title"), f"B站直播 {room_id}"),
                    anchor=text(item.get("uname")),
                    cover=cover,
                    online=positive_int(item.get("online")),
                    category_id=f"{parent_id}:{area_id}",
                    category_name="",
                ),
            )
        return LiveRoomsPage(tuple(rooms), normalized_page + (1 if len(data) >= 30 else 0))

    def resolve(self, room_input: str) -> ResolvedLiveStream:
        room_id = self._parse_room_id(room_input)
        room_info = self._get_room_info(room_id)
        room = room_info.get("room_info") if isinstance(room_info, dict) else None
        if not isinstance(room, dict):
            raise PlatformResolverError("B站未返回有效的直播间信息")
        if positive_int(room.get("live_status")) != 1:
            raise PlatformResolverError(f"房间未开播：{text(room.get('title'), room_id)}")

        play_url = self._get_play_url(room_id, qn=None)
        quality_map = {
            positive_int(item.get("qn")): text(item.get("desc"), "未知清晰度")
            for item in play_url.get("g_qn_desc", [])
            if isinstance(item, dict) and positive_int(item.get("qn")) > 0
        }
        accepted = self._accepted_qns(play_url)
        if not accepted:
            accepted = sorted(quality_map, reverse=True)
        if not accepted:
            raise PlatformResolverError("B站未返回可用画质")
        selected_qn = max(accepted)
        selected = self._get_play_url(room_id, qn=selected_qn)
        candidates = self._extract_candidates(selected)
        if not candidates:
            raise PlatformResolverError("B站未返回可播放地址")
        return ResolvedLiveStream(
            room_id=room_id,
            title=text(room.get("title"), f"B站直播 {room_id}"),
            anchor=self._anchor_name(room_info),
            quality=quality_map.get(selected_qn, "未知清晰度"),
            candidates=candidates,
            headers={"Referer": f"https://live.bilibili.com/{room_id}", "User-Agent": USER_AGENT},
        )

    def _get_room_info(self, room_id: str) -> dict:
        try:
            payload = self._signed_get(
                "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom",
                {"room_id": room_id},
            )
        except PlatformResolverError:
            payload = self._get_json(
                "https://api.live.bilibili.com/room/v1/Room/get_info",
                params={"room_id": room_id},
            )
        data = payload.get("data")
        if not isinstance(data, dict):
            raise PlatformResolverError("B站房间信息格式异常")
        if "room_info" not in data and "live_status" in data:
            data = {"room_info": data}
        return data

    def _get_play_url(self, room_id: str, qn: int | None) -> dict:
        params: dict[str, object] = {
            "room_id": room_id,
            "protocol": "0,1",
            "format": "0,1,2" if qn is None else "0,2",
            "codec": "0,1" if qn is None else "0",
            "platform": "web",
        }
        if qn is not None:
            params["qn"] = qn
        with self._play_lock:
            elapsed = time.monotonic() - self._last_play_request
            if elapsed < 0.45:
                time.sleep(0.45 - elapsed)
            self._last_play_request = time.monotonic()
        payload = self._signed_get(
            "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo",
            params,
        )
        try:
            return payload["data"]["playurl_info"]["playurl"]
        except (KeyError, TypeError) as error:
            raise PlatformResolverError("B站播放信息响应异常") from error

    @staticmethod
    def _accepted_qns(play_url: dict) -> list[int]:
        streams = play_url.get("stream")
        if not isinstance(streams, list) or not streams:
            return []
        formats = streams[0].get("format") if isinstance(streams[0], dict) else None
        codecs = formats[0].get("codec") if isinstance(formats, list) and formats else None
        accepted = codecs[0].get("accept_qn") if isinstance(codecs, list) and codecs else None
        if not isinstance(accepted, list):
            return []
        return sorted({positive_int(value) for value in accepted if positive_int(value) > 0}, reverse=True)

    @staticmethod
    def _extract_candidates(play_url: dict) -> tuple[LiveStreamCandidate, ...]:
        streams = play_url.get("stream")
        if not isinstance(streams, list):
            return ()
        raw: list[LiveStreamCandidate] = []
        for stream in streams:
            if not isinstance(stream, dict):
                continue
            for fmt in stream.get("format", []) if isinstance(stream.get("format"), list) else []:
                if not isinstance(fmt, dict):
                    continue
                for codec in fmt.get("codec", []) if isinstance(fmt.get("codec"), list) else []:
                    if not isinstance(codec, dict):
                        continue
                    base = text(codec.get("base_url"))
                    for info in codec.get("url_info", []) if isinstance(codec.get("url_info"), list) else []:
                        if not isinstance(info, dict):
                            continue
                        url = f"{text(info.get('host')).rstrip('/')}{base}{text(info.get('extra'))}"
                        if url:
                            host = urlparse(url).hostname or "默认"
                            raw.append(LiveStreamCandidate(host, url, infer_protocol(url)))
        normal = [item for item in raw if "mcdn" not in item.url.lower()]
        delayed = [item for item in raw if "mcdn" in item.url.lower()]
        return unique_candidates([*normal, *delayed])

    def _signed_get(self, url: str, params: dict[str, object]) -> dict:
        try:
            query = self._wbi_signed_params(params)
            return self._get_json(url, params=query)
        except PlatformResolverError:
            # WBI 接口偶尔把匿名请求误判为未登录；直播播放接口仍支持
            # 不带 WBI 的公开参数，失败时回退以保持默认无 Cookie 可用。
            return self._get_json(url, params=params)

    def _wbi_signed_params(self, params: dict[str, object]) -> dict[str, str]:
        img_key, sub_key = self._get_wbi_keys()
        mixin = "".join((img_key + sub_key)[index] for index in MIXIN_KEY_TABLE)[:32]
        result = {str(key): str(value) for key, value in params.items()}
        result["wts"] = str(int(time.time()))
        cleaned = {
            key: re.sub(r"[!'()*]", "", value)
            for key, value in sorted(result.items())
        }
        query = "&".join(f"{key}={quote(value, safe='~')}" for key, value in cleaned.items())
        result["w_rid"] = hashlib.md5(f"{query}{mixin}".encode()).hexdigest()
        return result

    @staticmethod
    def _anchor_name(room_info: dict) -> str:
        anchor = room_info.get("anchor_info")
        if not isinstance(anchor, dict):
            return ""
        base = anchor.get("base_info")
        if isinstance(base, dict):
            return text(base.get("uname") or base.get("username") or base.get("uid"))
        return text(anchor.get("uname") or anchor.get("username"))

    def _get_wbi_keys(self) -> tuple[str, str]:
        if self._wbi_keys:
            return self._wbi_keys
        payload = self._get_json("https://api.bilibili.com/x/web-interface/nav")
        try:
            img_url = payload["data"]["wbi_img"]["img_url"]
            sub_url = payload["data"]["wbi_img"]["sub_url"]
            img_key = text(img_url).rsplit("/", 1)[-1].split(".", 1)[0]
            sub_key = text(sub_url).rsplit("/", 1)[-1].split(".", 1)[0]
        except (KeyError, TypeError) as error:
            raise PlatformResolverError("B站 WBI 密钥获取失败") from error
        if not img_key or not sub_key:
            raise PlatformResolverError("B站 WBI 密钥为空")
        self._wbi_keys = (img_key, sub_key)
        return self._wbi_keys

    @staticmethod
    def _split_category_id(category_id: str) -> tuple[str, str]:
        values = [part.strip() for part in category_id.split(":", 1)]
        if len(values) == 2 and all(values):
            return values[0], values[1]
        parts = [part.strip() for part in category_id.split(",", 1)]
        if len(parts) == 2 and all(parts):
            return parts[0], parts[1]
        raise PlatformResolverError("B站分类 ID 格式无效")

    @staticmethod
    def _parse_room_id(value: str) -> str:
        raw = text(value)
        parsed = urlparse(raw)
        if parsed.netloc:
            if parsed.netloc.lower().split(":", 1)[0] not in {"live.bilibili.com", "b23.tv"}:
                raise PlatformResolverError("直播间链接不属于 B 站")
            raw = next((part for part in parsed.path.split("/") if part), "")
        if not raw or not re.fullmatch(r"\d+", raw):
            raise PlatformResolverError("B站房间号必须为数字或直播间链接")
        return raw
