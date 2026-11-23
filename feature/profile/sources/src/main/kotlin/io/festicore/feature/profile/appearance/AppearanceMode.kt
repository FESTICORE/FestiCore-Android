package io.festicore.feature.profile.appearance

import kotlinx.coroutines.flow.Flow

/** 화면 모드 선택지와 문구. iOS `Feature/Profile/Sources/Appearance/Model/AppearanceMode`. 저장 값은 iOS · `core/storage`와 같다. */
enum class AppearanceMode(
    val storedValue: String,
    /** 화면 모드 화면의 줄 이름. */
    val title: String,
    /** 설정 홈 '화면 모드' 줄에 보이는 값. */
    val summary: String,
) {
    System("system", "시스템 설정과 같게", "시스템 설정"),
    Light("light", "라이트", "라이트"),
    Dark("dark", "다크", "다크"),
    ;

    companion object {
        /** 알 수 없는 값은 iOS처럼 다크로 본다(iOS `AppearanceMode(storedValue:)`). */
        fun fromStoredValue(value: String?): AppearanceMode = entries.firstOrNull { it.storedValue == value } ?: Dark
    }
}

/**
 * 화면 모드 저장소. iOS는 `@Shared(.appearanceMode)`를 화면이 직접 읽지만, Android feature는 `core`를 의존하지 않으므로
 * app이 `core/storage` `AppearanceModeStore`를 감싸 등록한다(`domain/auth/sources` `ActivityProvider`와 같은 방식).
 */
interface AppearanceModeSettings {
    /** 지금 값과 이후 바뀐 값. 저장된 값이 없으면 [AppearanceMode.Dark]다. */
    val appearanceMode: Flow<AppearanceMode>

    suspend fun saveAppearanceMode(mode: AppearanceMode)
}
