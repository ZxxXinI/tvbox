package com.tvbox.app.data

import com.tvbox.app.domain.PlatformLiveRoom
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformLiveFavoritesRepositoryTest {
    @Test
    fun togglesByPlatformAndRoomIdWithoutMergingDifferentPlatforms() {
        val douyu = room("douyu", "42")
        val bilibili = room("bilibili", "42")

        val first = toggleFavoriteRecords(emptyList(), douyu)
        val second = toggleFavoriteRecords(first, bilibili)
        assertEquals(listOf("bilibili:42", "douyu:42"), second.map { it.toRoom().id })

        val third = toggleFavoriteRecords(second, douyu)
        assertEquals(listOf("bilibili:42"), third.map { it.toRoom().id })
    }

    @Test
    fun restoredRoomUsesSavedMetadataWithoutStaleOnlineCount() {
        val saved = toggleFavoriteRecords(emptyList(), room("douyu", "123"))
        val restored = saved.single().toRoom()

        assertEquals("douyu:123", restored.id)
        assertEquals("主播甲", restored.anchor)
        assertEquals("房间标题", restored.title)
        assertEquals(0, restored.online)
    }

    @Test
    fun savedRecordsSurviveJsonRoundTrip() {
        val original = toggleFavoriteRecords(emptyList(), room("DouYu", "123"))
        val serialized = Json.encodeToString(original)
        val restored = Json.decodeFromString<List<FavoriteRoomRecord>>(serialized)

        assertEquals("douyu:123", restored.single().toRoom().id)
        assertEquals(emptyList<FavoriteRoomRecord>(), toggleFavoriteRecords(restored, room("douyu", "123")))
    }

    private fun room(site: String, roomId: String) = PlatformLiveRoom(
        id = "$site:$roomId",
        site = site,
        roomId = roomId,
        title = "房间标题",
        anchor = "主播甲",
        cover = "https://example.com/cover.jpg",
        online = 1234,
        categoryId = "game",
        categoryName = "游戏",
    )
}
