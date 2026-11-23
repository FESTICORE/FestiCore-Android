package io.festicore.feature.festival.crewgather

import io.festicore.domain.festival.entity.FestivalPackDocument
import java.time.Instant

/**
 * 모여 시트를 여는 값(iOS `FestivalCrewGatherFeature.State` 초기값). 보드 · 크루 메인이 만든다.
 * [presentation]은 시트를 열 때마다 바뀌는 번호다. 시트 ViewModel 키로 써서 다시 열면 새 상태로 시작한다.
 */
data class CrewGatherInput(
    val presentation: Int,
    val crewId: String,
    val zones: List<FestivalPackDocument.Zone>,
    val availableAt: Instant?,
    /** 소리 알림을 받을 크루원 수(나 제외). 모르면 null. */
    val recipientCount: Int?,
)

/** 다시 시도로 풀리지 않는 안내(iOS `FCAlertState`). 확인만 있다. */
data class CrewGatherAlert(
    val title: String,
    val message: String,
)

data class CrewGatherState(
    val crewId: String,
    val zones: List<FestivalPackDocument.Zone>,
    val selectedZoneId: String? = null,
    val isSubmitting: Boolean = false,
    val availableAt: Instant? = null,
    val recipientCount: Int? = null,
    val alert: CrewGatherAlert? = null,
) {
    /** iOS `FestivalCrewGatherView.subtitle`. */
    val subtitle: String
        get() {
            val count = recipientCount?.takeIf { it > 0 } ?: return "누르면 바로 크루원에게 소리 알림이 가요"
            return "누르면 바로 크루원 ${count}명에게 소리 알림이 가요"
        }
}

sealed interface CrewGatherSideEffect {
    /** 요청을 보냈다(iOS `delegate(.gathered)`). 부모가 시트를 닫고 크루 상태를 다시 받는다. */
    data object Gathered : CrewGatherSideEffect
}
