package io.festicore.feature.profile.main

import io.festicore.domain.bearer.entity.BearerOwnedFlag
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.feature.profile.AccountState

/**
 * 마이 화면 상태. iOS `ProfileFeature.State`(기수 · 내 깃발 · 검색 제외, 설계 쟁점 M7).
 *
 * @param account 앱이 알려 준 계정 상태(`onAccountChanged`).
 * @param packs 기기에 받아 둔 팩.
 * @param packChangedSets 새 버전이 있는 팩의 바뀐 공연 수(내 버전 기준). 읽지 못하면 비워 두고 버전만 보인다.
 */
data class ProfileState(
    val account: AccountState = AccountState(),
    val packs: List<FestivalReceivedPack> = emptyList(),
    val bearerFlags: List<BearerOwnedFlag> = emptyList(),
    val bearerFlagsLoading: Boolean = false,
    val bearerFlagsLoaded: Boolean = false,
    val packChangedSets: Map<String, Int> = emptyMap(),
    val packLoad: ProfilePackLoad = ProfilePackLoad.Idle,
) {
    /** 로그인하지 않았으면 맨 위에 로그인 카드를 보인다. 계정 상태를 확인하는 동안에는 숨긴다. */
    val showsLoginPrompt: Boolean
        get() = account.profile == null && !account.hasSession && !account.isLoading

    /** 세션이 없을 때 상단 바 아래에 보이는 계정 문구. */
    val guestMessage: String?
        get() = account.message?.takeIf { !account.hasSession && it.isNotEmpty() }
}

/** 받은 팩 목록을 읽는 상태. 처음 읽는 동안만 스켈레톤을 보인다. iOS `ProfileFeature.PackLoad`. */
enum class ProfilePackLoad {
    Idle,
    Loading,
    Loaded,
    Failed,
}

/** iOS `ProfileDelegate`(기수 · 검색 · 설정 홈이 보내는 계정 요청 제외). */
sealed interface ProfileSideEffect {
    /** 로그인 시트(문맥 `LoginContext.Profile`)를 연다. */
    data object LoginRequested : ProfileSideEffect

    /** 로그인 카드의 Google 버튼. 시트 없이 Google 로그인을 바로 시작한다(설계 쟁점 M12). iOS `socialLoginRequested(.google)`. */
    data object GoogleLoginRequested : ProfileSideEffect

    /** 받은 팩이 없을 때 "페스티벌 둘러보기". 라이브 탭 축제 목록을 연다. */
    data object FestivalListRequested : ProfileSideEffect

    /** 설정 홈을 연다. */
    data object SettingsRequested : ProfileSideEffect

    /** 팩 상세를 연다. iOS `destination = .packDetail(festivalID:title:)`. */
    data class PackDetailRequested(
        val festivalId: String,
        val title: String,
    ) : ProfileSideEffect

    /** 새 깃발 등록 흐름을 연다. */
    data object BearerEntryRequested : ProfileSideEffect

    /** 내 깃발의 프로필 또는 보드를 연다. */
    data class BearerFlagRequested(
        val flag: BearerOwnedFlag,
        val opensBoard: Boolean,
    ) : ProfileSideEffect

    /** 세션이 있다가 없어졌다. 계정 화면이 열려 있으면 Coordinator가 설정 홈째 닫는다(iOS `PF:211-216`). */
    data object SessionLost : ProfileSideEffect
}
