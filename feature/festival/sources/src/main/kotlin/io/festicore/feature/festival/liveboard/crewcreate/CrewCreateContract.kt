package io.festicore.feature.festival.liveboard.crewcreate

import io.festicore.domain.festival.entity.FestivalCrew
import io.festicore.feature.festival.crew.withIGa

/** 이름 입력 → 만든 뒤 초대 공유(iOS `FestivalCrewCreateFeature.State.Step`). */
sealed interface CrewCreateStep {
    data object Naming : CrewCreateStep

    data class Created(val crew: FestivalCrew) : CrewCreateStep
}

data class CrewCreateState(
    val festivalId: String,
    /** 안내 "원더리벳 2026이 끝나면 기록만 남아요"에 넣는 축제 이름. */
    val festivalName: String? = null,
    val name: String = "",
    val isSubmitting: Boolean = false,
    val step: CrewCreateStep = CrewCreateStep.Naming,
    /** 다시 시도로 풀리지 않는 실패(이미 크루가 있음 · 끝난 페스티벌 등) 안내. */
    val errorMessage: String? = null,
    /** "크루를 만들지 못했어요" 다시 시도 다이얼로그. */
    val isRetryDialogPresented: Boolean = false,
) {
    val canSubmit: Boolean
        get() = name.trim().length in 1..FestivalCrew.MAX_NAME_LENGTH && !isSubmitting

    /** 크루장 전용 초대 링크(`festicore://crew/<코드>`). S08 참여 확인 경로와 같다. */
    val inviteUrl: String?
        get() {
            val crew = (step as? CrewCreateStep.Created)?.crew ?: return null
            return crew.inviteCode?.let { "festicore://crew/$it" }
        }

    /** Figma S07 B 안내 세 줄. 마지막 줄에 축제 이름을 넣는다. */
    val rules: List<String>
        get() {
            val festival = festivalName?.withIGa() ?: "페스티벌이"
            return listOf(
                "· 최대 ${FestivalCrew.MAX_MEMBERS}명 · 페스티벌마다 크루 1개",
                "· 초대 링크는 48시간 동안 쓸 수 있어요",
                "· $festival 끝나면 기록만 남아요",
            )
        }
}

sealed interface CrewCreateSideEffect {
    /** 크루 화면(S07)으로 간다(iOS `openCrewRequested`). */
    data class OpenCrewRequested(val crewId: String) : CrewCreateSideEffect

    /** 세션이 끊겼다(iOS `loginRequested`). 부모가 화면을 닫고 크루 로그인을 요청한다. */
    data object LoginRequested : CrewCreateSideEffect

    data object CloseRequested : CrewCreateSideEffect
}
