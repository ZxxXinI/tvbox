"""虎牙直播解析：网页房间信息、anti-code 和 TUP3 CDN token。"""

from __future__ import annotations

import base64
import hashlib
import json
import random
import re
import struct
import time
from urllib.parse import parse_qs, unquote

import requests

from platform_common import (
    LiveCategory,
    LiveParentCategory,
    LiveRoom,
    LiveRoomsPage,
    LiveStreamCandidate,
    PlatformResolverError,
    ResolvedLiveStream,
    positive_int,
    text,
    unique_candidates,
)


USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36 Edg/117.0.0.0"
)
HYSDK_UA = "HYSDK(Windows, 30000002)_APP(pc_exe&7060000&official)_SDK(trans&2.32.3.5646)"
PARENTS = (("1", "网游"), ("2", "单机"), ("3", "手游"), ("8", "娱乐"))


class HuyaResolver:
    site_id = "huya"
    site_name = "虎牙"
    description = "分类直播、反盗链解析与多线路播放"

    def __init__(self, timeout_seconds: int = 10, cookie: str = ""):
        self.timeout_seconds = timeout_seconds
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": USER_AGENT})
        self.cookie = cookie.strip()

    def _headers(self, *, mobile: bool = False) -> dict[str, str]:
        headers = {
            "User-Agent": USER_AGENT if mobile else HYSDK_UA,
            "Referer": "https://m.huya.com/" if mobile else "https://www.huya.com/",
        }
        if self.cookie:
            headers["Cookie"] = self.cookie
        return headers

    def _get_json(self, url: str, *, params: dict[str, object] | None = None, mobile: bool = False) -> object:
        try:
            response = self.session.get(
                url,
                params=params,
                headers=self._headers(mobile=mobile),
                timeout=self.timeout_seconds,
            )
        except requests.RequestException as error:
            raise PlatformResolverError(f"虎牙请求失败：{error}") from error
        try:
            response.raise_for_status()
        except requests.RequestException as error:
            raise PlatformResolverError(f"虎牙请求失败：{error}") from error
        try:
            payload = response.json()
        except ValueError as error:
            raise PlatformResolverError("虎牙接口返回格式异常") from error
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except json.JSONDecodeError as error:
                raise PlatformResolverError("虎牙接口 JSON 字符串无效") from error
        return payload

    def get_category_tree(self) -> tuple[list[LiveParentCategory], list[LiveCategory]]:
        parents = [LiveParentCategory(id=id_, name=name) for id_, name in PARENTS]
        categories: list[LiveCategory] = []
        for parent_id, parent_name in PARENTS:
            payload = self._get_json(
                "https://live.cdn.huya.com/liveconfig/game/bussLive",
                params={"bussType": parent_id},
            )
            values = payload.get("data") if isinstance(payload, dict) else []
            if not isinstance(values, list):
                continue
            for item in values:
                if not isinstance(item, dict):
                    continue
                category_id = self._game_id(item.get("gid"))
                name = text(item.get("gameFullName"))
                if not category_id or not name:
                    continue
                categories.append(
                    LiveCategory(
                        id=category_id,
                        name=name,
                        parent_id=parent_id,
                        parent_name=parent_name,
                        cover=f"https://huyaimg.msstatic.com/cdnimage/game/{category_id}-MS.jpg",
                    ),
                )
        return parents, categories

    def get_category_rooms(self, category_id: str, page: int) -> LiveRoomsPage:
        normalized_page = max(1, page)
        payload = self._get_json(
            "https://www.huya.com/cache.php",
            params={
                "m": "LiveList",
                "do": "getLiveListByPage",
                "tagAll": 0,
                "gameId": text(category_id),
                "page": normalized_page,
            },
        )
        data = payload.get("data") if isinstance(payload, dict) else None
        values = data.get("datas") if isinstance(data, dict) else []
        if not isinstance(values, list):
            values = []
        rooms: list[LiveRoom] = []
        page_count = positive_int(data.get("totalPage"), normalized_page) if isinstance(data, dict) else normalized_page
        for item in values:
            if not isinstance(item, dict):
                continue
            room_id = text(item.get("profileRoom"))
            if not room_id:
                continue
            rooms.append(
                LiveRoom(
                    room_id=room_id,
                    title=text(item.get("introduction") or item.get("roomName"), f"虎牙直播 {room_id}"),
                    anchor=text(item.get("nick")),
                    cover=text(item.get("screenshot")),
                    online=positive_int(item.get("totalCount")),
                    category_id=text(category_id),
                    category_name="",
                ),
            )
        return LiveRoomsPage(tuple(rooms), max(normalized_page, page_count))

    def resolve(self, room_input: str) -> ResolvedLiveStream:
        room_id = self._parse_room_id(room_input)
        html, room_info = self._get_room_info(room_id)
        root = room_info.get("roomInfo") if isinstance(room_info, dict) else None
        if not isinstance(root, dict):
            raise PlatformResolverError("虎牙未返回有效的房间信息")
        live_info = root.get("tLiveInfo") if isinstance(root.get("tLiveInfo"), dict) else {}
        profile_info = root.get("tProfileInfo") if isinstance(root.get("tProfileInfo"), dict) else {}
        if positive_int(root.get("eLiveStatus")) != 2:
            raise PlatformResolverError(f"房间未开播：{text(live_info.get('sIntroduction'), room_id)}")
        stream_info = live_info.get("tLiveStreamInfo") if isinstance(live_info.get("tLiveStreamInfo"), dict) else {}
        raw_lines = stream_info.get("vStreamInfo", {}).get("value") if isinstance(stream_info.get("vStreamInfo"), dict) else []
        raw_rates = stream_info.get("vBitRateInfo", {}).get("value") if isinstance(stream_info.get("vBitRateInfo"), dict) else []
        top_sid = self._positive_from_html(html, r'lChannelId":([0-9]+)')
        sub_sid = self._positive_from_html(html, r'lSubChannelId":([0-9]+)')
        presenter_uid = top_sid or sub_sid
        rates = []
        if isinstance(raw_rates, list):
            for item in raw_rates:
                if not isinstance(item, dict) or "HDR" in text(item.get("sDisplayName")):
                    continue
                rates.append((positive_int(item.get("iBitRate")), text(item.get("sDisplayName"), "原画")))
        if not rates:
            rates = [(0, "原画")]
        rates.sort(key=lambda item: item[0], reverse=True)
        rate, quality = rates[0]
        candidates: list[LiveStreamCandidate] = []
        if isinstance(raw_lines, list):
            for line in raw_lines:
                if not isinstance(line, dict):
                    continue
                base_url = text(line.get("sFlvUrl")).rstrip("/")
                stream_name = text(line.get("sStreamName"))
                anti_code = text(line.get("sFlvAntiCode"))
                if not base_url or not stream_name or not anti_code:
                    continue
                try:
                    try:
                        token = self._get_cdn_token(stream_name)
                    except PlatformResolverError:
                        # 某些网络环境会拦截 wup.huya.com 的 TARS POST；页面
                        # 自带的 anti-code 仍可生成可用候选，作为保守回退。
                        token = anti_code
                    signed = self._build_anti_code(stream_name, presenter_uid, token)
                except (PlatformResolverError, ValueError, KeyError):
                    continue
                url = f"{base_url}/{stream_name}.flv?{signed}&codec=264"
                if rate > 0:
                    url += f"&ratio={rate}"
                candidates.append(LiveStreamCandidate(text(line.get("sCdnType"), "默认"), url, "flv"))
        candidates = list(unique_candidates(candidates))
        if not candidates:
            raise PlatformResolverError("虎牙未返回可播放地址")
        title = text(live_info.get("sIntroduction") or live_info.get("sRoomName"), f"虎牙直播 {room_id}")
        return ResolvedLiveStream(
            room_id=text(live_info.get("lProfileRoom"), room_id),
            title=title,
            anchor=text(profile_info.get("sNick")),
            quality=quality,
            candidates=tuple(candidates),
            headers={"Referer": f"https://www.huya.com/{room_id}", "User-Agent": HYSDK_UA},
        )

    def _get_room_info(self, room_id: str) -> tuple[str, dict]:
        try:
            response = self.session.get(
                f"https://m.huya.com/{room_id}",
                headers=self._headers(mobile=True),
                timeout=self.timeout_seconds,
            )
        except requests.RequestException as error:
            raise PlatformResolverError(f"虎牙房间请求失败：{error}") from error
        try:
            response.raise_for_status()
        except requests.RequestException as error:
            raise PlatformResolverError(f"虎牙 CDN token 请求失败：{error}") from error
        html = response.text
        marker = re.search(r"window\.HNF_GLOBAL_INIT\s*=\s*", html)
        if not marker:
            raise PlatformResolverError("虎牙房间页面结构已变化")
        raw = self._extract_json_value(html, marker.end())
        if not raw:
            raise PlatformResolverError("虎牙房间信息 JSON 无效")
        raw = re.sub(r"function.*?\(.*?\)\.\{[\s\S]*?\}", '""', raw)
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as error:
            raise PlatformResolverError("虎牙房间信息 JSON 无效") from error
        if not isinstance(payload, dict):
            raise PlatformResolverError("虎牙房间信息格式异常")
        return html, payload

    @staticmethod
    def _extract_json_value(source: str, start: int) -> str:
        opening = next((index for index in range(start, len(source)) if source[index] in "[{"), -1)
        if opening < 0:
            return ""
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
            elif char in "[{":
                stack.append(char)
            elif char in "]}":
                if not stack or (char == "]" and stack[-1] != "[") or (char == "}" and stack[-1] != "{"):
                    return ""
                stack.pop()
                if not stack:
                    return source[opening:index + 1]
        return ""

    def _get_cdn_token(self, stream_name: str) -> str:
        request_body = self._encode_tup_request(stream_name)
        try:
            response = self.session.post(
                "https://wup.huya.com",
                data=request_body,
                headers={
                    "Content-Type": "application/x-wup",
                    "User-Agent": HYSDK_UA,
                    "Origin": "https://m.huya.com/",
                    "Referer": "https://m.huya.com/",
                },
                timeout=self.timeout_seconds,
            )
        except requests.RequestException as error:
            raise PlatformResolverError(f"虎牙 CDN token 请求失败：{error}") from error
        try:
            response.raise_for_status()
        except requests.RequestException as error:
            raise PlatformResolverError(f"虎牙 CDN token 请求失败：{error}") from error
        try:
            token = self._decode_tup_response(response.content)
        except (ValueError, IndexError, KeyError, struct.error) as error:
            raise PlatformResolverError("虎牙 TARS token 响应解析失败") from error
        if not token:
            raise PlatformResolverError("虎牙未返回 CDN token")
        return token

    @staticmethod
    def _build_anti_code(stream: str, presenter_uid: int, anti_code: str) -> str:
        values = parse_qs(anti_code, keep_blank_values=True)
        if "fm" not in values or "wsTime" not in values:
            raise PlatformResolverError("虎牙 anti-code 缺少必要参数")
        ctype = values.get("ctype", ["huya_pc_exe"])[0]
        platform_id = positive_int(values.get("t", ["0"])[0])
        is_wap = platform_id == 103
        now = int(time.time() * 1000)
        seq_id = presenter_uid + now
        convert_uid = HuyaResolver._rotl64(presenter_uid)
        calc_uid = presenter_uid if is_wap else convert_uid
        try:
            secret_prefix = base64.b64decode(unquote(values["fm"][0])).decode().split("_", 1)[0]
        except (ValueError, UnicodeDecodeError) as error:
            raise PlatformResolverError("虎牙 anti-code fm 无效") from error
        ws_time = values["wsTime"][0]
        secret_hash = hashlib.md5(f"{seq_id}|{ctype}|{platform_id}".encode()).hexdigest()
        ws_secret = hashlib.md5(
            f"{secret_prefix}_{calc_uid}_{stream}_{secret_hash}_{ws_time}".encode(),
        ).hexdigest()
        ct = int((int(ws_time, 16) + random.random()) * 1000)
        uuid = int((((ct % 10_000_000_000) + random.random()) * 1000) % 0xFFFFFFFF)
        result = {
            "wsSecret": ws_secret,
            "wsTime": ws_time,
            "seqid": str(seq_id),
            "ctype": ctype,
            "ver": "1",
            "fs": values.get("fs", [""])[0],
            "fm": values["fm"][0],
            "t": str(platform_id),
        }
        if is_wap:
            result.update({"uid": str(presenter_uid), "uuid": str(uuid)})
        else:
            result["u"] = str(convert_uid)
        return "&".join(f"{key}={value}" for key, value in result.items())

    @staticmethod
    def _rotl64(value: int) -> int:
        value &= 0xFFFFFFFFFFFFFFFF
        return ((value << 1) | (value >> 63)) & 0xFFFFFFFFFFFFFFFF

    @staticmethod
    def _encode_head(type_id: int, tag: int) -> bytes:
        if tag < 15:
            return bytes([(type_id << 4) | tag])
        return bytes([(type_id << 4) | 0x0F, tag])

    @classmethod
    def _encode_int(cls, value: int, tag: int) -> bytes:
        if value == 0:
            return cls._encode_head(12, tag)
        if -128 <= value <= 127:
            return cls._encode_head(0, tag) + struct.pack(">b", value)
        if -32768 <= value <= 32767:
            return cls._encode_head(1, tag) + struct.pack(">h", value)
        if -2147483648 <= value <= 2147483647:
            return cls._encode_head(2, tag) + struct.pack(">i", value)
        return cls._encode_head(3, tag) + struct.pack(">q", value)

    @classmethod
    def _encode_string(cls, value: str, tag: int) -> bytes:
        data = value.encode()
        if len(data) <= 255:
            return cls._encode_head(6, tag) + bytes([len(data)]) + data
        return cls._encode_head(7, tag) + struct.pack(">i", len(data)) + data

    @classmethod
    def _encode_bytes(cls, value: bytes, tag: int) -> bytes:
        return (
            cls._encode_head(13, tag)
            + cls._encode_head(0, 0)
            + cls._encode_int(len(value), 1)
            + value
        )

    @classmethod
    def _encode_struct(cls, body: bytes, tag: int) -> bytes:
        return cls._encode_head(10, tag) + body + cls._encode_head(11, 0)

    @classmethod
    def _encode_map_bytes(cls, values: dict[str, bytes], tag: int) -> bytes:
        body = cls._encode_int(len(values), 0)
        for key, value in values.items():
            body += cls._encode_string(key, 0) + cls._encode_bytes(value, 1)
        return cls._encode_head(8, tag) + body

    @classmethod
    def _encode_user_id(cls) -> bytes:
        body = b""
        body += cls._encode_int(0, 0)
        body += cls._encode_string("", 1)
        body += cls._encode_string("", 2)
        body += cls._encode_string("pc_exe&7060000&official", 3)
        body += cls._encode_string("", 4)
        body += cls._encode_int(0, 5)
        body += cls._encode_string("", 6)
        body += cls._encode_string("", 7)
        return cls._encode_struct(body, 3)

    @classmethod
    def _encode_tup_request(cls, stream_name: str) -> bytes:
        req = b""
        req += cls._encode_string("", 0)
        req += cls._encode_string(stream_name, 1)
        req += cls._encode_int(0, 2)
        req += cls._encode_user_id()
        req += cls._encode_int(66, 4)
        encoded_req = cls._encode_struct(req, 0)
        new_data = cls._encode_map_bytes({"tReq": encoded_req}, 0)
        packet = b""
        packet += cls._encode_int(3, 1)
        packet += cls._encode_int(0, 2)
        packet += cls._encode_int(0, 3)
        packet += cls._encode_int(0, 4)
        packet += cls._encode_string("liveui", 5)
        packet += cls._encode_string("getCdnTokenInfoEx", 6)
        packet += cls._encode_bytes(new_data, 7)
        packet += cls._encode_int(0, 8)
        packet += cls._encode_map_strings({}, 9)
        packet += cls._encode_map_strings({}, 10)
        return struct.pack(">i", len(packet) + 4) + packet

    @classmethod
    def _encode_map_strings(cls, values: dict[str, str], tag: int) -> bytes:
        body = cls._encode_int(len(values), 0)
        for key, value in values.items():
            body += cls._encode_string(key, 0) + cls._encode_string(value, 1)
        return cls._encode_head(8, tag) + body

    @classmethod
    def _decode_tup_response(cls, data: bytes) -> str:
        if len(data) < 4:
            raise ValueError("TUP packet too short")
        reader = _TarsReader(data, 4)
        buffer = b""
        while not reader.done:
            type_id, tag = reader.head()
            if tag == 7:
                buffer = reader.bytes_value(type_id)
            else:
                reader.skip(type_id)
        if not buffer:
            raise ValueError("TUP response has no payload")
        inner = _TarsReader(buffer)
        values = inner.map_bytes()
        response = _TarsReader(values.get("tRsp", b""))
        token = ""
        while not response.done:
            type_id, tag = response.head()
            if tag == 0 and type_id != 11:
                token = response.first_string(type_id)
            else:
                response.skip(type_id)
        return token

    @staticmethod
    def _game_id(value: object) -> str:
        if isinstance(value, dict):
            value = value.get("value")
        return text(value).split(",", 1)[0]

    @staticmethod
    def _positive_from_html(html: str, pattern: str) -> int:
        match = re.search(pattern, html)
        return positive_int(match.group(1) if match else 0)

    @staticmethod
    def _parse_room_id(value: str) -> str:
        raw = text(value)
        match = re.search(r"(?:www|m)\.huya\.com/(\w+)", raw)
        if match:
            raw = match.group(1)
        if not raw or not re.fullmatch(r"[A-Za-z0-9_-]+", raw):
            raise PlatformResolverError("虎牙房间号格式无效")
        return raw


class _TarsReader:
    def __init__(self, data: bytes, pos: int = 0):
        self.data = data
        self.pos = pos

    @property
    def done(self) -> bool:
        return self.pos >= len(self.data)

    def head(self) -> tuple[int, int]:
        if self.done:
            raise ValueError("TARS head missing")
        value = self.data[self.pos]
        self.pos += 1
        type_id, tag = value >> 4, value & 0x0F
        if tag == 15:
            tag = self.data[self.pos]
            self.pos += 1
        return type_id, tag

    def skip(self, type_id: int) -> None:
        if type_id in (11, 12):
            return
        if type_id == 0:
            self.pos += 1
        elif type_id == 1:
            self.pos += 2
        elif type_id == 2:
            self.pos += 4
        elif type_id == 3:
            self.pos += 8
        elif type_id == 6:
            self.pos += self.data[self.pos] + 1
        elif type_id == 7:
            length = struct.unpack_from(">i", self.data, self.pos)[0]
            self.pos += 4 + length
        elif type_id == 13:
            self.head()
            length = self.int_value(self.head()[0])
            self.pos += length
        elif type_id == 8:
            size = self.int_value(self.head()[0])
            for _ in range(size * 2):
                self.skip(self.head()[0])
        elif type_id == 9:
            size = self.int_value(self.head()[0])
            for _ in range(size):
                self.skip(self.head()[0])
        elif type_id == 10:
            while True:
                child_type, _ = self.head()
                if child_type == 11:
                    break
                self.skip(child_type)
        else:
            raise ValueError(f"unsupported TARS type {type_id}")

    def int_value(self, type_id: int) -> int:
        if type_id == 12:
            return 0
        sizes = {0: (1, ">b"), 1: (2, ">h"), 2: (4, ">i"), 3: (8, ">q")}
        size, fmt = sizes[type_id]
        value = struct.unpack_from(fmt, self.data, self.pos)[0]
        self.pos += size
        return value

    def string_value(self, type_id: int) -> str:
        if type_id == 6:
            length = self.data[self.pos]
            self.pos += 1
        elif type_id == 7:
            length = struct.unpack_from(">i", self.data, self.pos)[0]
            self.pos += 4
        else:
            raise ValueError("not a TARS string")
        value = self.data[self.pos:self.pos + length]
        self.pos += length
        return value.decode(errors="replace")

    def first_string(self, type_id: int) -> str:
        if type_id in (6, 7):
            return self.string_value(type_id)
        if type_id != 10:
            raise ValueError("not a TARS string or struct")
        while True:
            child_type, child_tag = self.head()
            if child_type == 11:
                return ""
            if child_tag == 0 and child_type in (6, 7):
                return self.string_value(child_type)
            self.skip(child_type)

    def bytes_value(self, type_id: int) -> bytes:
        if type_id != 13:
            raise ValueError("not a TARS simple list")
        subtype, _ = self.head()
        if subtype != 0:
            raise ValueError("TARS simple list is not byte array")
        length_type, _ = self.head()
        length = self.int_value(length_type)
        value = self.data[self.pos:self.pos + length]
        self.pos += length
        return value

    def map_bytes(self) -> dict[str, bytes]:
        type_id, _ = self.head()
        if type_id != 8:
            raise ValueError("not a TARS map")
        size_type, _ = self.head()
        size = self.int_value(size_type)
        result: dict[str, bytes] = {}
        for _ in range(size):
            key_type, _ = self.head()
            key = self.string_value(key_type)
            value_type, _ = self.head()
            result[key] = self.bytes_value(value_type)
        return result
