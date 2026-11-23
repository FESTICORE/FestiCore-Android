package io.festicore.feature.profile.main

import io.festicore.domain.auth.entity.AuthProfile
import io.festicore.domain.auth.entity.AuthUser
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.profile.AccountState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `ProfileFeatureTests` 19건 중 팩 · 로그인 · 설정 13건(같은 이름). 기수 4건은 설계 쟁점 M7로 기수 문서 PR에서,
 * `settingsAccountRequestsAreForwardedToApp`(설정 홈 계정 요청 전달)은 Coordinator가 받는 T1 · A1에서,
 * `dismissingSettingsHomeStillSendsUnsavedNotificationChanges`(알림 설정)는 S6에서 옮긴다. 팩 · 계정 값은 테스트 가짜 값이다.
 */
class ProfileViewModelTest {
    private class FakeReceivedPackUseCase(
        var receivedPacks: suspend () -> List<FestivalReceivedPack> = { emptyList() },
        var refreshReceivedPacks: suspend () -> List<FestivalReceivedPack> = { emptyList() },
    ) : FestivalReceivedPackUseCase {
        var receivedCalls = 0

        override suspend fun receivedPacks(): List<FestivalReceivedPack> {
            receivedCalls += 1
            return receivedPacks.invoke()
        }

        override suspend fun refreshReceivedPacks(): List<FestivalReceivedPack> = refreshReceivedPacks.invoke()
    }

    private class FakePackDetailUseCase(
        var packChanges: suspend (String, Int) -> FestivalPackChanges = { _, _ -> throw IOException("unused") },
    ) : FestivalPackDetailUseCase {
        val changeRequests = mutableListOf<Pair<String, Int>>()

        override suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges {
            changeRequests += festivalId to fromVersion
            return packChanges.invoke(festivalId, fromVersion)
        }

        override suspend fun packDetail(festivalId: String): FestivalPackDetail? = null

        override suspend fun updatePack(festivalId: String): FestivalPackDetail? = null

        override suspend fun deletePack(festivalId: String) = Unit

        override suspend fun checklist(festivalId: String) = FestivalChecklist()

        override suspend fun saveChecklist(checklist: FestivalChecklist, festivalId: String) = Unit

        override suspend fun autoDownloadOnWiFi() = true

        override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) = Unit
    }

    private fun viewModel(
        packs: FakeReceivedPackUseCase = FakeReceivedPackUseCase(),
        detail: FakePackDetailUseCase = FakePackDetailUseCase(),
        account: AccountState = AccountState(),
    ) = ProfileViewModel(initialAccount = account, receivedPackUseCase = packs, packDetailUseCase = detail)

    private val signedIn = AccountState(hasSession = true)

    @Test
    fun appearLoadsLocalPacksThenRefreshesUpdates() = runTest {
        val local = listOf(FestivalReceivedPack(id = "fst_a", name = "원더리벳 2026", packVersion = 12, latestPackVersion = 12))
        val refreshed = listOf(FestivalReceivedPack(id = "fst_a", name = "원더리벳 2026", packVersion = 12, latestPackVersion = 13))
        val detail =
            FakePackDetailUseCase(packChanges = { _, _ ->
                FestivalPackChanges(fromVersion = 12, toVersion = 13, sizeBytes = 1_024, changedSets = 2)
            })
        viewModel(FakeReceivedPackUseCase(receivedPacks = { local }, refreshReceivedPacks = { refreshed }), detail).test(this) {
            containerHost.onAppear()
            expectState { copy(packLoad = ProfilePackLoad.Loading) }
            expectState { copy(packs = local, packLoad = ProfilePackLoad.Loaded) }
            expectState { copy(packs = refreshed) }
            // 새 버전이 있는 팩은 내 버전 기준 바뀐 공연 수를 읽는다(시안 "시간표 N개 변경").
            expectState { copy(packChangedSets = mapOf("fst_a" to 2)) }
        }
        assertEquals(listOf("fst_a" to 12), detail.changeRequests)
    }

    @Test
    fun packChangesFailureKeepsVersionText() = runTest {
        val refreshed = listOf(FestivalReceivedPack(id = "fst_a", name = "원더리벳 2026", packVersion = 12, latestPackVersion = 13))
        val detail = FakePackDetailUseCase(packChanges = { _, _ -> throw IOException("offline") })
        val packs = FakeReceivedPackUseCase(receivedPacks = { refreshed }, refreshReceivedPacks = { refreshed })
        viewModel(packs, detail).test(this, initialState = ProfileState(packLoad = ProfilePackLoad.Loaded)) {
            containerHost.onPackChanged().join()
            expectState { copy(packs = refreshed) }
        }
        assertEquals(listOf("fst_a" to 12), detail.changeRequests)
    }

    @Test
    fun refreshFailureKeepsLocalPacks() = runTest {
        val local = listOf(FestivalReceivedPack(id = "fst_a", name = "A", packVersion = 1))
        val packs = FakeReceivedPackUseCase(receivedPacks = { local }, refreshReceivedPacks = { throw IOException("offline") })
        viewModel(packs).test(this) {
            containerHost.onAppear().join()
            expectState { copy(packLoad = ProfilePackLoad.Loading) }
            expectState { copy(packs = local, packLoad = ProfilePackLoad.Loaded) }
        }
    }

    @Test
    fun localPackFailureShowsRetry() = runTest {
        var attempts = 0
        val packs =
            FakeReceivedPackUseCase(receivedPacks = {
                attempts += 1
                if (attempts == 1) throw IOException("cannot open")
                emptyList()
            })
        viewModel(packs).test(this) {
            containerHost.onAppear().join()
            expectState { copy(packLoad = ProfilePackLoad.Loading) }
            expectState { copy(packLoad = ProfilePackLoad.Failed) }
            containerHost.onPacksRetryTapped().join()
            expectState { copy(packLoad = ProfilePackLoad.Loading) }
            expectState { copy(packLoad = ProfilePackLoad.Loaded) }
        }
        assertEquals(2, attempts)
    }

    @Test
    fun reappearKeepsShownPacksWhileReloading() = runTest {
        val packs = FakeReceivedPackUseCase(receivedPacks = { throw IOException("cannot open") })
        val initial =
            ProfileState(packs = listOf(FestivalReceivedPack(id = "fst_a", name = "A", packVersion = 1)), packLoad = ProfilePackLoad.Loaded)
        viewModel(packs).test(this, initialState = initial) {
            containerHost.onAppear().join()
        }
        assertEquals(1, packs.receivedCalls)
    }

    @Test
    fun browseFestivalsRequestsFestivalList() = runTest {
        viewModel().test(this) {
            containerHost.onBrowseFestivalsTapped()
            expectSideEffect(ProfileSideEffect.FestivalListRequested)
        }
    }

    @Test
    fun loginPromptShowsOnlyForGuest() {
        assertTrue(ProfileState().showsLoginPrompt)
        assertFalse(ProfileState(account = signedIn).showsLoginPrompt)
        assertFalse(ProfileState(account = AccountState(isLoading = true)).showsLoginPrompt)
        val profile = AuthProfile(
            user = AuthUser(
                id = "user-a",
                provider = "google",
                email = null,
                nickname = "테스트",
                nicknameConfirmed = true,
                termsAcceptedAt = "2026-10-01",
                termsVersion = "2026-10",
                pushOptIn = false,
                profileImageUrl = null,
            ),
            currentTermsVersion = "2026-10",
        )
        assertFalse(ProfileState(account = AccountState(profile = profile)).showsLoginPrompt)
    }

    @Test
    fun guestLoginPromptRequestsLogin() = runTest {
        viewModel().test(this) {
            containerHost.onLoginTapped()
            expectSideEffect(ProfileSideEffect.LoginRequested)
        }
    }

    /** Android 로그인 카드는 Google 버튼만 있다(설계 쟁점 M1). 시트 없이 바로 로그인을 요청한다(M12). */
    @Test
    fun guestSocialLoginRequestsProvider() = runTest {
        viewModel().test(this) {
            containerHost.onGoogleLoginTapped()
            expectSideEffect(ProfileSideEffect.GoogleLoginRequested)
        }
    }

    @Test
    fun packTapOpensPackDetailAndDeletionReloadsPacks() = runTest {
        val packs = FakeReceivedPackUseCase(receivedPacks = { emptyList() }, refreshReceivedPacks = { emptyList() })
        val initial =
            ProfileState(
                packs = listOf(FestivalReceivedPack(id = "fst_a", name = "원더리벳 2026", packVersion = 12)),
                packLoad = ProfilePackLoad.Loaded,
            )
        viewModel(packs).test(this, initialState = initial) {
            containerHost.onPackTapped("fst_unknown")
            containerHost.onPackTapped("fst_a")
            expectSideEffect(ProfileSideEffect.PackDetailRequested(festivalId = "fst_a", title = "원더리벳 2026"))
            // 상세를 닫는 것은 Coordinator다. 마이는 목록을 다시 읽는다.
            containerHost.onPackDeleted().join()
            expectState { copy(packs = emptyList()) }
        }
    }

    @Test
    fun settingsTapOpensSettingsHome() = runTest {
        viewModel(account = signedIn).test(this) {
            containerHost.onSettingsTapped()
            expectSideEffect(ProfileSideEffect.SettingsRequested)
        }
    }

    /** Android는 설정 홈이 같은 계정 입력을 직접 받는다(`ProfileFlow`, T1). 마이는 받은 값을 그대로 반영한다. */
    @Test
    fun accountChangesAreForwardedToSettingsHome() = runTest {
        viewModel(account = signedIn).test(this) {
            containerHost.onAccountChanged(AccountState(hasSession = true, isLoading = true))
            expectState { copy(account = AccountState(hasSession = true, isLoading = true)) }
        }
    }

    /** 세션을 잃으면 알리고, 계정 화면이 열려 있으면 Coordinator가 설정 홈째 닫는다(T1). */
    @Test
    fun losingSessionOnAccountScreenClosesSettingsHome() = runTest {
        viewModel(account = signedIn).test(this) {
            containerHost.onAccountChanged(AccountState())
            expectState { copy(account = AccountState()) }
            expectSideEffect(ProfileSideEffect.SessionLost)
            // 처음부터 세션이 없던 계정 입력은 알리지 않는다.
            containerHost.onAccountChanged(AccountState(message = "문구"))
            expectState { copy(account = AccountState(message = "문구")) }
        }
    }

    /** Android 추가: 라이브 보드 "가는 길" 경계는 받은 팩 목록과 상관없이 팩 상세를 요청한다(iOS `openPackDirections`). */
    @Test
    fun openPackDirectionsRequestsPackDetail() = runTest {
        viewModel().test(this) {
            containerHost.onOpenPackDirections(festivalId = "fst_b", title = "가는 길")
            expectSideEffect(ProfileSideEffect.PackDetailRequested(festivalId = "fst_b", title = "가는 길"))
        }
    }

    /** Android 추가: iOS `ProfileView.packMeta` 문구 우선순위와 서울 시간대 `MM/dd HH:mm`. */
    @Test
    fun packMetaFollowsIosOrder() {
        val failed = FestivalReceivedPack(id = "a", name = "A", packVersion = 5, latestPackVersion = 6, autoUpdateFailed = true)
        assertEquals("새 버전을 받지 못했어요 · v6", ProfilePresentation.packMeta(failed, changedSets = 3))
        val update = FestivalReceivedPack(id = "a", name = "A", packVersion = 5, latestPackVersion = 6)
        assertEquals("새 버전이 있어요 · 시간표 3개 변경", ProfilePresentation.packMeta(update, changedSets = 3))
        assertEquals("새 버전이 있어요 · v6", ProfilePresentation.packMeta(update, changedSets = 0))
        assertEquals("새 버전이 있어요 · v6", ProfilePresentation.packMeta(update, changedSets = null))
        val latest = FestivalReceivedPack(id = "a", name = "A", packVersion = 5)
        assertEquals("최신 버전 · v5", ProfilePresentation.packMeta(latest, changedSets = null))
        val checked = latest.copy(checkedAt = Instant.parse("2026-11-04T15:05:00Z"))
        assertEquals("최신 버전 · 11/05 00:05 확인", ProfilePresentation.packMeta(checked, changedSets = null))
    }
}
