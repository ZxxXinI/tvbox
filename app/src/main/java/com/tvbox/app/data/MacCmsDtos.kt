package com.tvbox.app.data

import com.tvbox.app.domain.ApiLine
import com.tvbox.app.domain.Category
import com.tvbox.app.domain.Movie
import com.tvbox.app.domain.PagedMovies
import com.tvbox.app.domain.cleanHtml
import com.tvbox.app.domain.isBlockedContent
import com.tvbox.app.domain.parsePlaySources
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MacCmsResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 1,
    val pagecount: Int = 1,
    val total: Int = 0,
    @SerialName("class")
    val categories: List<CategoryDto> = emptyList(),
    val list: List<VodDto> = emptyList(),
) {
    fun toPagedMovies(apiLine: ApiLine): PagedMovies {
        return PagedMovies(
            page = page.coerceAtLeast(1),
            pageCount = pagecount.coerceAtLeast(1),
            total = total.coerceAtLeast(0),
            apiLine = apiLine,
            categories = categories.mapNotNull { it.toDomainOrNull() },
            movies = list.mapNotNull { it.toDomainOrNull(apiLine) },
        )
    }
}

@Serializable
data class CategoryDto(
    @SerialName("type_id")
    val typeId: Int = 0,
    @SerialName("type_name")
    val typeName: String = "",
) {
    fun toDomainOrNull(): Category? {
        if (typeId <= 0 || typeName.isBlank()) return null
        val name = cleanHtml(typeName)
        if (isBlockedContent(name)) return null
        return Category(id = typeId, name = name)
    }
}

@Serializable
data class VodDto(
    @SerialName("vod_id")
    val vodId: Int = 0,
    @SerialName("type_id")
    val typeId: Int = 0,
    @SerialName("type_name")
    val typeName: String = "",
    @SerialName("vod_name")
    val vodName: String = "",
    @SerialName("vod_sub")
    val vodSub: String = "",
    @SerialName("vod_class")
    val vodClass: String = "",
    @SerialName("vod_tag")
    val vodTag: String = "",
    @SerialName("vod_pic")
    val vodPic: String = "",
    @SerialName("vod_actor")
    val vodActor: String = "",
    @SerialName("vod_director")
    val vodDirector: String = "",
    @SerialName("vod_content")
    val vodContent: String = "",
    @SerialName("vod_blurb")
    val vodBlurb: String = "",
    @SerialName("vod_remarks")
    val vodRemarks: String = "",
    @SerialName("vod_area")
    val vodArea: String = "",
    @SerialName("vod_lang")
    val vodLang: String = "",
    @SerialName("vod_year")
    val vodYear: String = "",
    @SerialName("vod_duration")
    val vodDuration: String = "",
    @SerialName("vod_play_from")
    val vodPlayFrom: String = "",
    @SerialName("vod_play_url")
    val vodPlayUrl: String = "",
) {
    fun toDomainOrNull(apiLine: ApiLine): Movie? {
        if (vodId <= 0 || vodName.isBlank()) return null
        if (
            isBlockedContent(
                vodName,
                vodSub,
                typeName,
                vodClass,
                vodTag,
                vodRemarks,
                vodContent,
                vodBlurb,
            )
        ) {
            return null
        }
        return Movie(
            id = vodId,
            apiLineId = apiLine.id,
            apiLineName = apiLine.name,
            name = cleanHtml(vodName),
            typeId = typeId,
            typeName = cleanHtml(typeName),
            posterUrl = normalizePosterUrl(vodPic),
            remarks = cleanHtml(vodRemarks),
            year = cleanHtml(vodYear),
            area = cleanHtml(vodArea),
            language = cleanHtml(vodLang),
            actor = cleanHtml(vodActor),
            director = cleanHtml(vodDirector),
            duration = cleanHtml(vodDuration),
            description = cleanHtml(vodContent.ifBlank { vodBlurb }),
            playSources = parsePlaySources(vodPlayFrom, vodPlayUrl),
        )
    }
}

private fun normalizePosterUrl(raw: String): String {
    return raw.trim()
        .removePrefix("[")
        .substringBefore("]")
        .ifBlank { raw.trim() }
}
