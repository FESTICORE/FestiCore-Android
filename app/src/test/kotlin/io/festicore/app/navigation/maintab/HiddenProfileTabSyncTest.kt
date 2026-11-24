package io.festicore.app.navigation.maintab

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.festicore.domain.auth.auth.CurrentUserProvider
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.profile.AccountState
import io.festicore.feature.profile.ProfileCoordinator
import io.festicore.feature.profile.ProfileRoute
import io.festicore.feature.profile.main.ProfileViewModel
import io.festicore.feature.profile.rememberProfileFlowEntries
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 마이 탭이 안 보일 때(스택 항목만 메인 탭 범위에 있고 화면은 그리지 않음) 세션을 잃으면 계정 경로를 바로 닫는다(이슈 #200 TAB-001).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HiddenProfileTabSyncTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeCurrentUserProvider : CurrentUserProvider {
        override val currentUserId = MutableStateFlow<String?>(null)
    }

    private object NoPacks : FestivalReceivedPackUseCase {
        override suspend fun receivedPacks(): List<FestivalReceivedPack> = emptyList()

        override suspend fun refreshReceivedPacks(): List<FestivalReceivedPack> = emptyList()
    }

    private object NoPackDetail : FestivalPackDetailUseCase {
        override suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges = error("unused")

        override suspend fun packDetail(festivalId: String): FestivalPackDetail? = null

        override suspend fun updatePack(festivalId: String): FestivalPackDetail? = null

        override suspend fun deletePack(festivalId: String) = Unit

        override suspend fun checklist(festivalId: String) = FestivalChecklist()

        override suspend fun saveChecklist(checklist: FestivalChecklist, festivalId: String) = Unit

        override suspend fun autoDownloadOnWiFi() = true

        override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) = Unit
    }

    @Test
    fun `숨은 마이 탭에서 세션을 잃으면 계정 경로를 닫는다`() {
        val signedIn = AccountState(hasSession = true)
        val account = MutableStateFlow(signedIn)
        val coordinator = ProfileCoordinator(FakeCurrentUserProvider())
        val profileViewModel = ProfileViewModel(signedIn, NoPacks, NoPackDetail)
        composeRule.setContent {
            rememberProfileFlowEntries(
                account = account,
                onLoginRequested = {},
                onGoogleLoginRequested = {},
                onFestivalListRequested = {},
                onLogoutRequested = {},
                onWithdrawRequested = {},
                onRefreshRequested = {},
                onNicknameUpdated = {},
                legalDocument = { _, _ -> },
                coordinator = coordinator,
                profileViewModel = profileViewModel,
            )
        }
        composeRule.runOnIdle {
            coordinator.onSettingsRequested()
            coordinator.onAccountRequested()
        }
        composeRule.waitForIdle()
        assertEquals(
            listOf(ProfileRoute.Profile, ProfileRoute.Settings, ProfileRoute.Account),
            coordinator.container.stateFlow.value.routes,
        )

        composeRule.runOnIdle { account.value = AccountState(hasSession = false) }
        composeRule.waitForIdle()

        assertEquals(listOf(ProfileRoute.Profile), coordinator.container.stateFlow.value.routes)
    }
}
