"""斗鱼直播间解析：稳定房间号转换为带防盗链参数的临时播放地址。"""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import time
import urllib.parse
from dataclasses import dataclass

import requests


USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)
MOBILE_USER_AGENT = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 "
    "Safari/604.1"
)
DEVICE_ID = "10000000000000000000000000001501"


class DouyuResolverError(RuntimeError):
    """斗鱼未开播、接口变更或无可用 CDN 时抛出的可展示错误。"""


@dataclass(frozen=True)
class DouyuStreamCandidate:
    """同一清晰度下由斗鱼 API 返回的一条 CDN 线路。"""

    cdn: str
    url: str
    protocol: str


@dataclass(frozen=True)
class DouyuLiveParentCategory:
    id: str
    name: str
    cover: str


@dataclass(frozen=True)
class DouyuLiveCategory:
    id: str
    name: str
    parent_id: str
    parent_name: str
    cover: str


@dataclass(frozen=True)
class DouyuLiveRoom:
    room_id: str
    title: str
    anchor: str
    cover: str
    online: int
    category_id: str
    category_name: str


@dataclass(frozen=True)
class ResolvedDouyuStream:
    room_id: str
    title: str
    anchor: str
    quality: str
    candidates: tuple[DouyuStreamCandidate, ...]
    is_loop: bool

    @property
    def url(self) -> str:
        """兼容单线路调用方：返回首选候选线路。"""

        return self.candidates[0].url

    @property
    def protocol(self) -> str:
        """兼容单线路调用方：返回首选候选线路协议。"""

        return self.candidates[0].protocol

    @property
    def headers(self) -> dict[str, str]:
        return {
            "Referer": f"https://www.douyu.com/{self.room_id}",
            "User-Agent": USER_AGENT,
        }


def parse_room_id(value: str) -> str:
    """接收房间号或斗鱼页面 URL，返回安全的数字房间号。"""

    text = value.strip()
    match = re.search(r"(?:www|m)\.douyu\.com/(\d+)", text)
    room_id = match.group(1) if match else text
    if not room_id.isdigit():
        raise DouyuResolverError("斗鱼房间号必须是数字或完整的斗鱼直播间链接")
    return room_id


class DouyuResolver:
    def __init__(self, timeout_seconds: int = 10):
        self.timeout_seconds = timeout_seconds
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": USER_AGENT})

    def resolve(self, room_input: str) -> ResolvedDouyuStream:
        requested_room_id = parse_room_id(room_input)
        detail = self._get_room_detail(requested_room_id)
        room_id = str(detail.get("room_id") or requested_room_id)
        show_status = int(detail.get("show_status") or 0)
        is_loop = int(detail.get("videoLoop") or 0) == 1
        if show_status != 1:
            title = str(detail.get("room_name") or room_id)
            raise DouyuResolverError(f"房间未开播：{title}")

        title = str(detail.get("room_name") or f"斗鱼直播 {room_id}")
        anchor = str(detail.get("owner_name") or "")
        quality, candidates = self._get_best_play_urls(room_id)
        if not candidates:
            raise DouyuResolverError("未能获取到可播放的斗鱼直播地址")
        return ResolvedDouyuStream(
            room_id=room_id,
            title=title,
            anchor=anchor,
            quality=quality or "自动",
            candidates=candidates,
            is_loop=is_loop,
        )

    def get_category_tree(self) -> tuple[list[DouyuLiveParentCategory], list[DouyuLiveCategory]]:
        response = self.session.get(
            "https://m.douyu.com/api/cate/list",
            headers={
                "Referer": "https://m.douyu.com/",
                "User-Agent": MOBILE_USER_AGENT,
            },
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        data = response.json().get("data")
        if not isinstance(data, dict):
            raise DouyuResolverError("斗鱼未返回有效的直播分类")

        parent_categories: list[DouyuLiveParentCategory] = []
        parent_names = {
            str(item.get("cate1Id")): str(item.get("cate1Name") or "其他")
            for item in data.get("cate1Info", [])
            if isinstance(item, dict) and item.get("cate1Id") is not None
        }
        for item in data.get("cate1Info", []):
            if not isinstance(item, dict):
                continue
            parent_id = str(item.get("cate1Id") or "").strip()
            name = str(item.get("cate1Name") or "").strip()
            if not parent_id or not name:
                continue
            parent_categories.append(
                DouyuLiveParentCategory(
                    id=parent_id,
                    name=name,
                    cover=str(item.get("icon") or item.get("cate1Icon") or "").strip(),
                ),
            )

        categories: list[DouyuLiveCategory] = []
        for item in data.get("cate2Info", []):
            if not isinstance(item, dict):
                continue
            category_id = str(item.get("cate2Id") or "").strip()
            name = str(item.get("cate2Name") or "").strip()
            parent_id = str(item.get("cate1Id") or "").strip()
            if not category_id or not name:
                continue
            categories.append(
                DouyuLiveCategory(
                    id=category_id,
                    name=name,
                    parent_id=parent_id,
                    parent_name=parent_names.get(parent_id, "其他"),
                    cover=str(item.get("icon") or ""),
                ),
            )
        return parent_categories, categories

    def get_categories(self) -> list[DouyuLiveCategory]:
        """兼容旧调用方：只返回二级分类。"""

        return self.get_category_tree()[1]

    def get_category_rooms(self, category_id: str, page: int) -> tuple[list[DouyuLiveRoom], int]:
        normalized_category_id = category_id.strip()
        if not normalized_category_id.isdigit():
            raise DouyuResolverError("斗鱼分类 ID 必须为数字")
        normalized_page = max(1, page)
        response = self.session.get(
            "https://www.douyu.com/gapi/rkc/directory/mixList/"
            f"2_{normalized_category_id}/{normalized_page}",
            headers={
                "Referer": "https://www.douyu.com/directory",
                "User-Agent": USER_AGENT,
            },
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        payload = response.json()
        data = payload.get("data")
        if not isinstance(data, dict):
            raise DouyuResolverError("斗鱼未返回有效的分类房间列表")

        rooms: list[DouyuLiveRoom] = []
        for item in data.get("rl", []):
            if not isinstance(item, dict) or str(item.get("type")) != "1":
                continue
            room_id = str(item.get("rid") or "").strip()
            if not room_id:
                continue
            rooms.append(
                DouyuLiveRoom(
                    room_id=room_id,
                    title=str(item.get("rn") or "").strip(),
                    anchor=str(item.get("nn") or "").strip(),
                    cover=str(item.get("rs16") or "").strip(),
                    online=self._parse_online(item.get("ol")),
                    category_id=normalized_category_id,
                    category_name=str(item.get("c2name") or "").strip(),
                ),
            )
        page_count = max(1, int(data.get("pgcnt") or 1))
        return rooms, page_count

    def _get_room_detail(self, room_id: str) -> dict:
        response = self.session.get(
            f"https://www.douyu.com/betard/{room_id}",
            headers={"Referer": f"https://www.douyu.com/{room_id}"},
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        payload = response.json()
        room = payload.get("room")
        if not isinstance(room, dict):
            raise DouyuResolverError("斗鱼未返回有效的房间信息")
        return room

    def _get_best_play_urls(
        self,
        room_id: str,
    ) -> tuple[str | None, tuple[DouyuStreamCandidate, ...]]:
        """按斗鱼返回的 CDN 顺序收集同一清晰度的全部可用线路。

        签名仅计算一次，随后复用到每个 CDN 的 getH5Play 请求；这是
        dart_simple_live 的核心取流方式，也避免在逐线路解析时让临时签名过快失效。
        """

        play_args = self._get_play_args(room_id)
        first_play_info = self._get_play_info(
            room_id,
            play_args=play_args,
            cdn="",
            rate=-1,
        )
        qualities = self._parse_qualities(first_play_info)
        cdns = self._order_cdns(first_play_info.get("cdnsWithName", []))
        if not qualities:
            qualities = [(0, "自动")]
        if not cdns:
            cdns = [""]

        for rate, quality_name in qualities:
            candidates: list[DouyuStreamCandidate] = []
            seen_urls: set[str] = set()
            for cdn in cdns:
                try:
                    play_info = self._get_play_info(
                        room_id,
                        play_args=play_args,
                        cdn=cdn,
                        rate=rate,
                    )
                    url = self._build_stream_url(play_info)
                except (DouyuResolverError, KeyError, TypeError, ValueError):
                    continue
                if not url or url in seen_urls:
                    continue
                seen_urls.add(url)
                candidates.append(
                    DouyuStreamCandidate(
                        cdn=cdn or "默认",
                        url=url,
                        protocol="hls" if ".m3u8" in url.lower() else "flv",
                    ),
                )
            if candidates:
                return quality_name, tuple(candidates)
        return None, ()

    def _get_play_info(
        self,
        room_id: str,
        *,
        play_args: dict[str, str],
        cdn: str,
        rate: int,
    ) -> dict:
        form_data = {
            **play_args,
            "cdn": cdn,
            "rate": str(rate),
            "ver": "Douyu_223061205",
            "iar": "1",
            "ive": "1",
            "hevc": "0",
            "fa": "0",
        }
        response = self.session.post(
            f"https://www.douyu.com/lapi/live/getH5Play/{room_id}",
            data=form_data,
            headers={
                "Referer": f"https://m.douyu.com/{room_id}",
                "User-Agent": MOBILE_USER_AGENT,
            },
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        payload = response.json()
        if payload.get("error") != 0:
            raise DouyuResolverError(
                f"斗鱼取流失败：{payload.get('msg') or payload.get('error')}",
            )
        data = payload.get("data")
        if not isinstance(data, dict):
            raise DouyuResolverError("斗鱼未返回播放信息")
        return data

    def _get_play_args(self, room_id: str) -> dict[str, str]:
        response = self.session.get(
            f"https://www.douyu.com/swf_api/homeH5Enc?rids={room_id}",
            headers={
                "Referer": f"https://m.douyu.com/{room_id}",
                "User-Agent": MOBILE_USER_AGENT,
            },
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        ciphertext = response.json().get("data", {}).get(f"room{room_id}")
        if not isinstance(ciphertext, str) or not ciphertext:
            raise DouyuResolverError("斗鱼未返回签名脚本")

        match = re.search(
            r"(vdwdae325w_64we[\s\S]*?function ub98484234[\s\S]*?)function",
            ciphertext,
        )
        if match is None:
            raise DouyuResolverError("斗鱼签名脚本格式已变化")
        runtime_js = re.sub(r"eval.*?;\}", "strc;}", match.group(1))

        try:
            signature_template = self._run_node(runtime_js, "ub98484234()")
        except (OSError, subprocess.SubprocessError, ValueError) as error:
            raise DouyuResolverError(f"执行斗鱼签名脚本失败：{error}") from error

        version_match = re.search(r"v=(\d+)", signature_template)
        if version_match is None:
            raise DouyuResolverError("斗鱼签名脚本未包含版本号")
        timestamp = str(int(time.time()))
        version = version_match.group(1)
        md5_value = hashlib.md5(
            f"{room_id}{DEVICE_ID}{timestamp}{version}".encode(),
        ).hexdigest()

        sign_js = re.sub(r"return rt;\}\);?", "return rt;}", signature_template)
        sign_js = re.sub(r"\(function \(", "function sign(", sign_js)
        sign_js = re.sub(
            r"CryptoJS\.MD5\(cb\)\.toString\(\)",
            f'"{md5_value}"',
            sign_js,
        )
        try:
            signed_query = self._run_node(
                f"{runtime_js};{sign_js}",
                "sign(" + ",".join(
                    json.dumps(value)
                    for value in (room_id, DEVICE_ID, timestamp)
                ) + ")",
            )
        except (OSError, subprocess.SubprocessError, ValueError) as error:
            raise DouyuResolverError(f"计算斗鱼播放签名失败：{error}") from error

        if not isinstance(signed_query, str) or not signed_query:
            raise DouyuResolverError("斗鱼未生成有效播放签名")
        return {
            key: value
            for key, value in urllib.parse.parse_qsl(signed_query, keep_blank_values=True)
        }

    def _run_node(self, script: str, expression: str) -> str:
        node_script = (
            f"{script}\n"
            f"const tvboxResult = ({expression});\n"
            "process.stdout.write(JSON.stringify(tvboxResult));\n"
        )
        completed = subprocess.run(
            ["node", "-"],
            input=node_script,
            capture_output=True,
            text=True,
            timeout=self.timeout_seconds,
            check=False,
        )
        if completed.returncode != 0:
            detail = completed.stderr.strip() or "Node.js 返回非零状态"
            raise DouyuResolverError(detail)
        value = json.loads(completed.stdout)
        if not isinstance(value, str):
            raise DouyuResolverError("Node.js 未返回文本签名")
        return value

    @staticmethod
    def _parse_qualities(play_info: dict) -> list[tuple[int, str]]:
        qualities = [
            (int(item.get("rate") or 0), str(item.get("name") or "自动"))
            for item in play_info.get("multirates", [])
            if isinstance(item, dict)
        ]
        # 斗鱼 rate 数值越大代表越高画质；优先尝试最高档位，失败后再降级。
        return sorted(qualities, key=lambda item: item[0], reverse=True)

    @staticmethod
    def _order_cdns(cdn_items: object) -> list[str]:
        cdns = [
            str(item.get("cdn") or "")
            for item in cdn_items
            if isinstance(item, dict) and item.get("cdn")
        ] if isinstance(cdn_items, list) else []
        return sorted(
            dict.fromkeys(cdns),
            key=lambda cdn: cdn.startswith("scdn"),
        )

    @staticmethod
    def _build_stream_url(play_info: dict) -> str:
        base_url = str(play_info.get("rtmp_url") or "").rstrip("/")
        stream_path = urllib.parse.unquote(str(play_info.get("rtmp_live") or "")).lstrip("/")
        return f"{base_url}/{stream_path}" if base_url and stream_path else ""

    @staticmethod
    def _parse_online(value: object) -> int:
        try:
            return int(value or 0)
        except (TypeError, ValueError):
            return 0
