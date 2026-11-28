package io.festicore.feature.profile

import androidx.navigation3.runtime.NavKey
import io.festicore.feature.web.WebPage
import kotlinx.serialization.Serializable

/**
 * 마이 탭 흐름의 화면. iOS `ProfileScreen`(App `Navigation/Profile`).
 * iOS는 `ProfileFeature.destination`과 설정 홈 `destination`을 경로로 투영한다. Android는 `ProfileCoordinator`가 경로를 직접 가진다.
 * 스택 모양은 iOS와 같다: 마이 → (팩 상세 | 설정 홈 → (계정 | 화면 모드 | 문서 | 공지 목록 → 공지 상세)).
 */
@Serializable
sealed interface ProfileRoute : NavKey {
    /** 마이(iOS `profile`). 마이 탭의 첫 화면이다. */
    @Serializable
    data object Profile : ProfileRoute

    /** app이 제공하는 기수 흐름 슬롯. feature끼리 직접 의존하지 않는다. */
    @Serializable
    data object BearerEntry : ProfileRoute

    /** 팩 상세(iOS `packDetail`). 받은 팩 행 · 라이브 보드 "가는 길"에서 연다. 탭 바를 보인다. */
    @Serializable
    data class PackDetail(
        val festivalId: String,
        val title: String,
    ) : ProfileRoute

    /** 설정 홈(iOS `settings`). 이 화면부터 그 아래는 탭 바를 숨긴다. */
    @Serializable
    data object Settings : ProfileRoute

    /** 계정 · 닉네임(iOS `account`). */
    @Serializable
    data object Account : ProfileRoute

    /** 화면 모드(iOS `appearance`). */
    @Serializable
    data object Appearance : ProfileRoute

    @Serializable
    data object Notifications : ProfileRoute

    /** Android 홈 화면 위젯 추가와 실제 설치 상태. iOS 잠금화면과 구분한다. */
    @Serializable
    data object HomeWidget : ProfileRoute

    /** 공지 목록(iOS `noticeList`). */
    @Serializable
    data object NoticeList : ProfileRoute

    /** 공지 상세(iOS `noticeDetail`). 공지 목록 위에만 쌓는다. */
    @Serializable
    data class NoticeDetail(
        val id: String,
        val title: String,
    ) : ProfileRoute

    /** 약관 · 정보 출처 문서(iOS `legalDocument`). 앱 안 웹 문서로 연다. */
    @Serializable
    data class LegalDocument(val page: WebPage) : ProfileRoute
}
