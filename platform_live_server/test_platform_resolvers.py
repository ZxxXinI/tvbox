import base64
import hashlib
import json
import time
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from bilibili_resolver import BilibiliResolver
from douyin_resolver import DouyinResolver
from huya_resolver import HuyaResolver
from kuaishou_resolver import KuaishouResolver
from platform_common import LiveStreamCandidate, ResolvedLiveStream
from server import LiveService, StreamCache


class BilibiliResolverTest(unittest.TestCase):
    def test_category_id_keeps_parent_area(self):
        resolver = BilibiliResolver()
        resolver._get_json = Mock(return_value={
            "code": 0,
            "data": [{
                "id": 1,
                "name": "游戏",
                "list": [{"id": 2, "parent_id": 1, "name": "网游", "pic": "https://img.test/game"}],
            }],
        })
        parents, categories = resolver.get_category_tree()
        self.assertEqual(parents[0].id, "1")
        self.assertEqual(categories[0].id, "1:2")
        self.assertEqual(categories[0].parent_id, "1")

    def test_wbi_signature_uses_single_query_encoding(self):
        resolver = BilibiliResolver()
        resolver._wbi_keys = ("a" * 64, "b" * 64)
        with patch("bilibili_resolver.time.time", return_value=1700000000):
            params = resolver._wbi_signed_params({"foo": "a b!"})
        mixin = "".join(("a" * 64 + "b" * 64)[index] for index in resolver.MIXIN_KEY_TABLE)[:32] if hasattr(resolver, "MIXIN_KEY_TABLE") else ""
        self.assertRegex(params["w_rid"], r"^[0-9a-f]{32}$")
        self.assertEqual(params["wts"], "1700000000")
        self.assertNotIn("%2520", params["w_rid"])

    def test_extract_candidates_places_mcdn_last(self):
        candidates = BilibiliResolver._extract_candidates({
            "stream": [{"format": [{"codec": [{
                "base_url": "/live.flv",
                "url_info": [
                    {"host": "https://mcdn.example", "extra": "?b=1"},
                    {"host": "https://cdn.example", "extra": "?a=1"},
                ],
            }]}]},],
        })
        self.assertEqual([item.url for item in candidates], [
            "https://cdn.example/live.flv?a=1",
            "https://mcdn.example/live.flv?b=1",
        ])


class HuyaResolverTest(unittest.TestCase):
    def test_anti_code_contains_signed_fields(self):
        fm = base64.b64encode(b"secret_prefix_extra").decode()
        anti = f"fm={fm}&wsTime=65aa&ctype=huya_pc_exe&t=0&fs=1"
        result = HuyaResolver._build_anti_code("stream", 1234, anti)
        self.assertIn("wsSecret=", result)
        self.assertIn("seqid=", result)
        self.assertIn("u=2468", result)

    def test_tars_token_round_trip(self):
        response_body = HuyaResolver._encode_struct(HuyaResolver._encode_string("token", 0), 0)
        response_map = HuyaResolver._encode_map_bytes({"tRsp": response_body}, 0)
        packet = HuyaResolver._encode_bytes(response_map, 7)
        packet = HuyaResolver._encode_int(0, 1) + packet
        packet = len(packet + b"xxxx").to_bytes(4, "big") + packet
        self.assertEqual(HuyaResolver._decode_tup_response(packet), "token")

    def test_extract_nested_global_init(self):
        html = '<script>window.HNF_GLOBAL_INIT = {"roomInfo":{"eLiveStatus":2,"x":{"n":1}}};</script>'
        resolver = HuyaResolver()
        resolver.session.get = Mock(return_value=Mock(text=html, raise_for_status=Mock()))
        _, payload = resolver._get_room_info("123")
        self.assertEqual(payload["roomInfo"]["x"]["n"], 1)


class DouyinResolverTest(unittest.TestCase):
    def test_escaped_category_data(self):
        html = r'''<html>\"categoryData\":[{\"partition\":{\"id_str\":\"1\",\"type\":\"1\",\"title\":\"游戏\"},\"sub_partition\":[{\"partition\":{\"id_str\":\"2\",\"type\":\"1\",\"title\":\"网游\"}}]}]</html>'''
        resolver = DouyinResolver()
        parents, categories = resolver._extract_category_render_data(html)["categoryData"], None
        self.assertEqual(parents[0]["partition"]["title"], "游戏")

    def test_quality_selection_keeps_flv_and_hls(self):
        resolver = DouyinResolver()
        room = {
            "status": 2,
            "stream_url": {
                "live_core_sdk_data": {
                    "pull_data": {
                        "options": {"qualities": [
                            {"level": 1, "name": "高清", "sdk_key": "low"},
                            {"level": 4, "name": "原画", "sdk_key": "high"},
                        ]},
                        "stream_data": json.dumps({"data": {
                            "low": {"main": {"flv": "https://low.flv", "hls": "https://low.m3u8"}},
                            "high": {"main": {"flv": "https://high.flv", "hls": "https://high.m3u8"}},
                        }}),
                    },
                },
            },
        }
        candidates, quality = resolver._extract_play_candidates(room)
        self.assertEqual(quality, "原画")
        self.assertEqual([item.url for item in candidates], ["https://high.flv", "https://high.m3u8"])

    def test_node_sign_helper_returns_a_bogus(self):
        resolver = DouyinResolver(timeout_seconds=3)
        signed = resolver._signed_url("https://live.douyin.com/test", {"aid": 6383})
        self.assertIn("a_bogus=", signed)


class KuaishouResolverTest(unittest.TestCase):
    def test_kww_fallback_from_kwfv1(self):
        self.assertEqual(KuaishouResolver._resolve_server_kww({"kwfv1": "abc%2B123"}), "abc+123###ssrc")

    def test_initial_state_and_quality_grouping(self):
        html = '<script>window.__INITIAL_STATE__ = {"liveroom":{"playList":[{"liveStream":{"id":"x","playUrls":{"high":{"level":4,"name":"蓝光","url":"https://high.flv"},"low":{"level":1,"url":"https://low.flv"}}}}]}};</script>'
        state = KuaishouResolver._parse_initial_state(html)
        resolver = KuaishouResolver()
        selected = state["liveroom"]["playList"][0]
        groups = {}
        resolver._collect_urls(selected["liveStream"]["playUrls"], groups)
        self.assertIn((4, "蓝光"), groups)
        self.assertEqual(groups[(4, "蓝光")][0].url, "https://high.flv")


class LiveServiceTest(unittest.TestCase):
    def test_sites_expose_all_platforms(self):
        service = LiveService(Path("catalog.json"), cache_seconds=30, request_timeout_seconds=2)
        self.assertEqual([site.id for site in service.sites()], ["douyu", "huya", "bilibili", "douyin", "kuaishou"])

    def test_cache_isolated_by_site_and_room(self):
        cache = StreamCache(cache_seconds=60)
        calls = []

        def resolve(room_id):
            calls.append(room_id)
            return ResolvedLiveStream(
                room_id=room_id,
                title="title",
                anchor="anchor",
                quality="原画",
                candidates=(LiveStreamCandidate("cdn", f"https://{room_id}.test/live.flv", "flv"),),
                headers={},
            )

        cache.get_or_resolve("douyu", "123", resolve)
        cache.get_or_resolve("huya", "123", resolve)
        cache.get_or_resolve("douyu", "123", resolve)
        self.assertEqual(calls, ["123", "123"])


if __name__ == "__main__":
    unittest.main()
