import unittest
from unittest.mock import Mock

from douyu_resolver import DouyuResolver, DouyuResolverError, parse_room_id


class ParseRoomIdTest(unittest.TestCase):
    def test_accepts_numeric_room_id(self):
        self.assertEqual(parse_room_id("36252"), "36252")

    def test_extracts_room_id_from_desktop_or_mobile_url(self):
        self.assertEqual(parse_room_id("https://www.douyu.com/36252"), "36252")
        self.assertEqual(parse_room_id("https://m.douyu.com/4258555"), "4258555")

    def test_rejects_non_douyu_input(self):
        with self.assertRaises(DouyuResolverError):
            parse_room_id("not-a-room")

    def test_orders_scdn_lines_last(self):
        self.assertEqual(
            DouyuResolver._order_cdns(
                [
                    {"cdn": "scdn-late"},
                    {"cdn": "hw-h5"},
                    {"cdn": "hw-h5"},
                    {"cdn": "tx-h5"},
                ],
            ),
            ["hw-h5", "tx-h5", "scdn-late"],
        )

    def test_reuses_one_signature_for_all_cdn_candidates(self):
        resolver = DouyuResolver()
        resolver._get_play_args = Mock(return_value={"sign": "token"})
        resolver._get_play_info = Mock(
            side_effect=[
                {
                    "multirates": [{"rate": 4, "name": "原画"}],
                    "cdnsWithName": [{"cdn": "hw-h5"}, {"cdn": "tx-h5"}],
                },
                {"rtmp_url": "https://hw.example", "rtmp_live": "live.flv?token=one"},
                {"rtmp_url": "https://tx.example", "rtmp_live": "live.flv?token=two"},
            ],
        )

        quality, candidates = resolver._get_best_play_urls("36252")

        self.assertEqual(quality, "原画")
        self.assertEqual([candidate.cdn for candidate in candidates], ["hw-h5", "tx-h5"])
        self.assertEqual(
            [candidate.url for candidate in candidates],
            [
                "https://hw.example/live.flv?token=one",
                "https://tx.example/live.flv?token=two",
            ],
        )
        self.assertEqual(resolver._get_play_args.call_count, 1)

    def test_prioritizes_highest_quality_rate(self):
        resolver = DouyuResolver()
        resolver._get_play_args = Mock(return_value={"sign": "token"})
        resolver._get_play_info = Mock(
            side_effect=[
                {
                    "multirates": [
                        {"rate": 1, "name": "高清"},
                        {"rate": 4, "name": "原画"},
                        {"rate": 2, "name": "超清"},
                    ],
                    "cdnsWithName": [{"cdn": "hw-h5"}],
                },
                {"rtmp_url": "https://hw.example", "rtmp_live": "live.flv"},
            ],
        )

        quality, candidates = resolver._get_best_play_urls("36252")

        self.assertEqual(quality, "原画")
        self.assertEqual(len(candidates), 1)
        self.assertEqual(resolver._get_play_info.call_args_list[1].kwargs["rate"], 4)

    def test_maps_category_metadata_and_parent_name(self):
        resolver = DouyuResolver()
        response = Mock()
        response.json.return_value = {
            "data": {
                "cate1Info": [{"cate1Id": 2, "cate1Name": "娱乐"}],
                "cate2Info": [
                    {
                        "cate1Id": 2,
                        "cate2Id": 183,
                        "cate2Name": "原创IP",
                        "icon": "https://example.com/ip.jpg",
                    },
                ],
            },
        }
        resolver.session.get = Mock(return_value=response)

        parent_categories, categories = resolver.get_category_tree()

        self.assertEqual(parent_categories[0].id, "2")
        self.assertEqual(parent_categories[0].name, "娱乐")
        self.assertEqual(categories[0].id, "183")
        self.assertEqual(categories[0].name, "原创IP")
        self.assertEqual(categories[0].parent_name, "娱乐")

    def test_maps_only_live_rooms_from_category_page(self):
        resolver = DouyuResolver()
        response = Mock()
        response.json.return_value = {
            "data": {
                "pgcnt": 7,
                "rl": [
                    {
                        "type": 1,
                        "rid": 4258555,
                        "rn": "房间标题",
                        "nn": "主播",
                        "rs16": "https://example.com/cover.jpg",
                        "ol": "13742",
                        "c2name": "原创IP",
                    },
                    {"type": 0, "rid": 1},
                ],
            },
        }
        resolver.session.get = Mock(return_value=response)

        rooms, page_count = resolver.get_category_rooms("183", 1)

        self.assertEqual(page_count, 7)
        self.assertEqual(len(rooms), 1)
        self.assertEqual(rooms[0].room_id, "4258555")
        self.assertEqual(rooms[0].online, 13742)


if __name__ == "__main__":
    unittest.main()

