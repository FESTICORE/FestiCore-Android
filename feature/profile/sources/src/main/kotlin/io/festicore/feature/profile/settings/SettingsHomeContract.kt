package io.festicore.feature.profile.settings

import io.festicore.feature.profile.AccountState
import io.festicore.feature.profile.appearance.AppearanceMode
import io.festicore.feature.web.WebPage

/**
 * 설정 홈 상태. iOS `SettingsHomeFeature.State`. 하위 화면은 Coordinator가 경로로 열고(T1), 이 화면은 팝업만 가진다.
 *
 * @param account 앱이 알려 준 계정 상태(`onAccountChanged`, 설계 쟁점 M13).
 * @param appearanceMode '화면 모드' 줄 값. 저장소 값을 따른다.
 * @param alert 떠 있는 팝업. iOS `destination = .alert`.
 */
data class SettingsHomeState(
    val account: AccountState = AccountState(),
    val appearanceMode: AppearanceMode = AppearanceMode.Dark,
    val alert: SettingsHomeAlert? = null,
) {
    /** 로그인하지 않았으면 계정 그룹을 숨긴다. 로그인은 마이의 로그인 카드에서 한다. */
    val showsAccount: Boolean
        get() = account.profile != null || account.hasSession
}

/** 설정 홈 팝업. iOS `FCConfirmAlert`. */
enum class SettingsHomeAlert(
    val title: String,
    val message: String,
) {
    /** 문서 기준 주소를 쓸 수 없다. iOS `legalLinkUnavailable()`. */
    LegalLinkUnavailable("문서를 열 수 없어요", "잠시 후 다시 시도해 주세요."),
}

/** 설정 홈 '정보' 그룹의 문서. 경로는 서버 `GET api/v1/legal/{path}`, 제목은 줄 이름 그대로(iOS `SettingsHomeFeature.showLegalDocument`). */
enum class SettingsLegalDocument(
    val path: String,
    val title: String,
) {
    Sources("sources", "정보 출처"),
    Terms("terms", "이용약관"),
    Privacy("privacy", "개인정보 처리방침"),
    LocationTerms("location", "위치기반서비스 이용약관"),
}

/** iOS `SettingsHomeDelegate`와 `destination` 이동. Android 알림 설정은 서버 값만 편집하고 잠금화면 행은 표시하지 않는다. */
sealed interface SettingsHomeSideEffect {
    data object NotificationsRequested : SettingsHomeSideEffect

    /** 마이로 돌아간다. */
    data object BackRequested : SettingsHomeSideEffect

    /** 화면 모드를 연다. */
    data object AppearanceRequested : SettingsHomeSideEffect

    /** Android 홈 위젯 추가 안내를 연다. */
    data object HomeWidgetRequested : SettingsHomeSideEffect

    /** 계정 화면을 연다(S3). */
    data object AccountRequested : SettingsHomeSideEffect

    /** 공지사항 목록을 연다(S4). */
    data object NoticeListRequested : SettingsHomeSideEffect

    /** 약관 · 정보 출처 문서를 앱 안 웹 화면으로 연다(설계 쟁점 M11). */
    data class LegalDocumentRequested(val page: WebPage) : SettingsHomeSideEffect

    /** 세션이 있다가 없어졌다. 계정 화면이 열려 있으면 Coordinator가 닫는다(iOS `SF` `accountChanged` lostSession). */
    data object SessionLost : SettingsHomeSideEffect
}
