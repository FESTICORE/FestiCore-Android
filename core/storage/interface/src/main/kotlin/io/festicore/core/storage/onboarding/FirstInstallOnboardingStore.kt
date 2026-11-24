package io.festicore.core.storage.onboarding

/** 첫 설치 온보딩 완료 여부. iOS `FirstInstallOnboardingStore`. */
interface FirstInstallOnboardingStore {
    suspend fun hasCompleted(): Boolean

    suspend fun markCompleted()
}
