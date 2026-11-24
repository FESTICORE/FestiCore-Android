package io.festicore.domain.bearer.sources.model.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 받은 팩에서 기수 화면이 쓰는 값만 읽는다. 원소 하나의 형식이 틀려도 그 값만 null로 두고 나머지는 읽는다.
 * 발행 팩은 공연을 `timetable`에, 예전 팩은 `sets`에만 담는다. iOS `BearerPackDTO`.
 */
internal data class BearerPackDto(
    val name: String?,
    val zones: List<Zone>?,
    val stages: List<Stage>?,
    val performances: List<Performance>?,
) {
    data class Zone(
        val id: String?,
        val name: String?,
    )

    data class Stage(
        val id: String?,
        val name: String?,
    )

    data class Performance(
        val id: String?,
        val artist: String?,
        val stageId: String?,
        val stageName: String?,
        val startsAt: String?,
        val endsAt: String?,
    )

    companion object {
        /** JSON 객체가 아니면 null. */
        fun parse(bytes: ByteArray): BearerPackDto? {
            val root = runCatching { Json.parseToJsonElement(bytes.decodeToString()) }.getOrNull() as? JsonObject ?: return null
            val timetable = root.objects("timetable")?.map { it.performance() }
            return BearerPackDto(
                name = root.string("name"),
                zones = root.objects("zones")?.map { Zone(it.string("id"), it.string("name")) },
                stages = root.objects("stages")?.map { Stage(it.string("id"), it.string("name")) },
                performances = timetable?.takeIf { it.isNotEmpty() } ?: root.objects("sets")?.map { it.performance() },
            )
        }

        private fun JsonObject.performance() = Performance(
            id = string("id"),
            artist = string("artist"),
            stageId = string("stageId"),
            stageName = string("stageName"),
            startsAt = string("startsAt"),
            endsAt = string("endsAt"),
        )

        private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

        /** 배열의 객체 원소만 남긴다. 배열이 아니면 null. */
        private fun JsonObject.objects(key: String): List<JsonObject>? = (get(key) as? JsonArray)?.map {
            it as? JsonObject
                ?: JsonObject(emptyMap())
        }
    }
}
