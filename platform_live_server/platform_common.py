"""通用平台直播数据模型和解析辅助函数。"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Iterable
from urllib.parse import urlparse


class PlatformResolverError(RuntimeError):
    """平台接口、风控或直播状态异常。"""


@dataclass(frozen=True)
class LiveParentCategory:
    id: str
    name: str
    cover: str = ""


@dataclass(frozen=True)
class LiveCategory:
    id: str
    name: str
    parent_id: str
    parent_name: str
    cover: str = ""


@dataclass(frozen=True)
class LiveRoom:
    room_id: str
    title: str
    anchor: str
    cover: str
    online: int
    category_id: str
    category_name: str


@dataclass(frozen=True)
class LiveRoomsPage:
    rooms: tuple[LiveRoom, ...]
    page_count: int


@dataclass(frozen=True)
class LiveStreamCandidate:
    cdn: str
    url: str
    protocol: str


@dataclass(frozen=True)
class ResolvedLiveStream:
    room_id: str
    title: str
    anchor: str
    quality: str
    candidates: tuple[LiveStreamCandidate, ...]
    headers: dict[str, str]
    is_loop: bool = False

    @property
    def url(self) -> str:
        return self.candidates[0].url

    @property
    def protocol(self) -> str:
        return self.candidates[0].protocol


def text(value: object, fallback: str = "") -> str:
    if value is None:
        return fallback
    result = str(value).strip()
    return result or fallback


def positive_int(value: object, fallback: int = 0) -> int:
    try:
        result = int(value or 0)
    except (TypeError, ValueError):
        return fallback
    return result if result > 0 else fallback


def infer_protocol(url: str) -> str:
    lower = url.lower()
    if ".m3u8" in lower or "hls" in lower:
        return "hls"
    if ".flv" in lower:
        return "flv"
    if lower.startswith("rtmp://"):
        return "rtmp"
    return "http"


def unique_candidates(items: Iterable[LiveStreamCandidate]) -> tuple[LiveStreamCandidate, ...]:
    result: list[LiveStreamCandidate] = []
    seen: set[str] = set()
    for item in items:
        url = text(item.url)
        if not url or url in seen:
            continue
        seen.add(url)
        result.append(
            LiveStreamCandidate(
                cdn=text(item.cdn, "默认"),
                url=url,
                protocol=text(item.protocol, infer_protocol(url)),
            ),
        )
    return tuple(result)


def parse_room_input(value: str, hosts: tuple[str, ...], pattern: str = r"[A-Za-z0-9_-]+") -> str:
    raw = text(value)
    if not raw:
        raise PlatformResolverError("直播间号不能为空")
    parsed = urlparse(raw)
    if parsed.scheme and parsed.netloc:
        if parsed.netloc.lower().split(":", 1)[0] not in hosts:
            raise PlatformResolverError("直播间链接不属于当前平台")
        path_parts = [part for part in parsed.path.split("/") if part]
        if path_parts:
            raw = path_parts[-1]
    if not re.fullmatch(pattern, raw):
        raise PlatformResolverError("直播间号格式无效")
    return raw
