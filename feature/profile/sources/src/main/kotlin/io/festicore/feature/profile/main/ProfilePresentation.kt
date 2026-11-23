package io.festicore.feature.profile.main

import io.festicore.domain.festival.entity.FestivalReceivedPack
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 마이 화면 문구. iOS `ProfileView.packMeta`. */
internal object ProfilePresentation {
    // iOS `AppDateFormat.monthDayTime`(`MM/dd HH:mm`, en_US_POSIX, 서울 시간대). feature는 core를 의존하지 않아 같은 패턴을 둔다.
    private val monthDayTime = DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US).withZone(ZoneId.of("Asia/Seoul"))

    /** 새 버전이 있으면 버전을, 아니면 마지막 확인 시각을 보여 준다. [changedSets]는 그 팩의 바뀐 공연 수다. */
    fun packMeta(pack: FestivalReceivedPack, changedSets: Int?): String {
        val latest = pack.latestPackVersion
        if (pack.autoUpdateFailed && latest != null) return "새 버전을 받지 못했어요 · v$latest"
        if (pack.hasUpdate && changedSets != null && changedSets > 0) return "새 버전이 있어요 · 시간표 ${changedSets}개 변경"
        if (pack.hasUpdate && latest != null) return "새 버전이 있어요 · v$latest"
        val checkedAt = pack.checkedAt ?: return "최신 버전 · v${pack.packVersion}"
        return "최신 버전 · ${monthDayTime.format(checkedAt)} 확인"
    }
}
