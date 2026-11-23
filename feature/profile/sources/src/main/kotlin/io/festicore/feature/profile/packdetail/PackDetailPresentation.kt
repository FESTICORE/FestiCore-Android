package io.festicore.feature.profile.packdetail

import io.festicore.domain.festival.entity.FestivalIsoTime
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 팩 상세 문구. iOS `PackDetailView`의 문구 함수. */
internal object PackDetailPresentation {
    private val seoul = ZoneId.of("Asia/Seoul")

    // iOS `AppDateFormat.time`(축제 시간대 서울의 24시간 HH:mm).
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(seoul)

    // iOS `AppDateFormat.monthDayTime`(`MM/dd HH:mm`, 서울 시간대). feature는 core를 의존하지 않아 같은 패턴을 둔다.
    private val monthDayTime = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US).withZone(seoul)

    /** ISO 시각이면 서울 시간대의 24시간 HH:mm, 아니면 서버 값 그대로. 없으면 "-". */
    fun timeText(value: String?): String {
        if (value == null) return "-"
        return FestivalIsoTime.parse(value)?.let(time::format) ?: value
    }

    fun dateText(instant: Instant): String = monthDayTime.format(instant)

    fun sourceText(source: FestivalPackDetail.Source?): String? {
        if (source == null) return null
        val verifiedAt = source.verifiedAt ?: return "출처: ${source.name}"
        return "출처: ${source.name} · 확인 ${dateText(verifiedAt)}"
    }

    /** 가까운 역 안내 뒤에 본문을 붙인다. 둘 다 없으면 null. */
    fun sectionBody(section: FestivalPackDetail.Section): String? {
        val lines = mutableListOf<String>()
        section.stationName?.let { station ->
            lines +=
                section.walkMinutes?.let { "가장 가까운 역은 ${station}이에요. 걸어서 ${it}분 정도 걸려요." }
                    ?: "가장 가까운 역은 ${station}이에요."
        }
        section.body?.let { lines += it }
        return if (lines.isEmpty()) null else lines.joinToString(" ")
    }

    fun packStatus(pack: FestivalReceivedPack): String {
        val latest = pack.latestPackVersion
        if (pack.hasUpdate && latest != null) return "새 버전이 있어요 · v$latest"
        val checkedAt = pack.checkedAt ?: return "최신 버전 · v${pack.packVersion}"
        return "최신 버전 · ${dateText(checkedAt)} 확인"
    }

    fun versionText(pack: FestivalReceivedPack): String {
        val latest = pack.latestPackVersion
        if (!pack.hasUpdate || latest == null) return "v${pack.packVersion}"
        return "v${pack.packVersion} (새 버전 v$latest)"
    }

    fun updateTitle(isUpdating: Boolean, changes: FestivalPackChanges?): String {
        if (isUpdating) return "받는 중…"
        val size = changes?.sizeBytes ?: return "업데이트 받기"
        return "업데이트 받기 · ${sizeText(size)}"
    }

    fun changeSummary(changes: FestivalPackChanges?): String? {
        if (changes == null) return null
        val parts = mutableListOf<String>()
        changes.changedSets?.takeIf { it > 0 }?.let { parts += "시간표 ${it}개 변경" }
        changes.lastShuttleAt?.let { parts += "셔틀 막차 ${timeText(it.before)} → ${timeText(it.after)}" }
        changes.lastTrainAt?.let { parts += "막차 ${timeText(it.before)} → ${timeText(it.after)}" }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    /** 0.1MB 이상이면 "x.xMB", 아니면 "nKB"(최소 1). */
    fun sizeText(bytes: Int): String {
        val megabytes = bytes.toDouble() / 1_048_576
        return if (megabytes >= 0.1) String.format(Locale.US, "%.1fMB", megabytes) else "${maxOf(1, bytes / 1024)}KB"
    }
}
