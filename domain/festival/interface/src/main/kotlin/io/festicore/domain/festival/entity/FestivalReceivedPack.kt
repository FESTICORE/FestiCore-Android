package io.festicore.domain.festival.entity

import java.time.Instant

/** 기기에 받아 둔 축제 팩 요약. iOS `FestivalReceivedPack`. */
data class FestivalReceivedPack(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    /** 기기에 저장된 팩 버전. */
    val packVersion: Int,
    /** 축제 목록이 알려 준 최신 발행 버전. 목록에 없으면 null. */
    val latestPackVersion: Int? = null,
    /** 서버와 마지막으로 버전을 맞춘 시각. */
    val checkedAt: Instant? = null,
    /** 저장된 팩 JSON 크기(바이트). */
    val sizeBytes: Int = 0,
    /** 자동 받기를 시도했지만 새 버전을 받지 못했는지. */
    val autoUpdateFailed: Boolean = false,
) {
    /** 서버에 더 새 버전이 발행됐는지. */
    val hasUpdate: Boolean get() = (latestPackVersion ?: packVersion) > packVersion
}
