package io.festicore.feature.profile.main

import androidx.lifecycle.ViewModel
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.profile.AccountState
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsScreen
import io.festicore.service.analytics.usecase.AnalyticsUseCase
import kotlin.coroutines.cancellation.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 마이 화면. iOS `ProfileFeature`(기수 · 내 깃발 · 검색 제외).
 * 하위 화면(팩 상세 · 설정 홈)은 SideEffect로 요청하고 Coordinator가 연다. 계정 상태는 앱이 [onAccountChanged]로 알린다.
 *
 * @param initialAccount 화면을 만들 때의 계정 상태. 로그인 상태에서 로그인 카드가 잠깐 보이지 않게 한다.
 */
class ProfileViewModel(
    initialAccount: AccountState,
    private val receivedPackUseCase: FestivalReceivedPackUseCase,
    private val packDetailUseCase: FestivalPackDetailUseCase,
    private val analyticsUseCase: AnalyticsUseCase,
) : ViewModel(),
    OrbitContainerHost<ProfileState, ProfileState, ProfileSideEffect> {
    override val container = orbitContainer<ProfileState, ProfileSideEffect>(ProfileState(account = initialAccount))

    // view

    /** 앱 코디네이터가 계정 상태가 바뀔 때 보내는 입력. */
    fun onAccountChanged(account: AccountState) = intent {
        val lostSession = state.account.hasSession && !account.hasSession
        reduce { state.copy(account = account) }
        if (lostSession) postSideEffect(ProfileSideEffect.SessionLost)
    }

    fun onAppear() = intent {
        analyticsUseCase.track(AnalyticsEvent.ScreenView(screen = AnalyticsScreen.Profile))
        if (state.packLoad == ProfilePackLoad.Loading) return@intent
        // 이미 보여 준 목록은 다시 읽는 동안 그대로 둔다.
        if (state.packLoad != ProfilePackLoad.Loaded) reduce { state.copy(packLoad = ProfilePackLoad.Loading) }
        fetchPacks()
    }

    fun onPacksRetryTapped() = intent {
        if (state.packLoad != ProfilePackLoad.Failed) return@intent
        reduce { state.copy(packLoad = ProfilePackLoad.Loading) }
        fetchPacks()
    }

    fun onLoginTapped() = intent { postSideEffect(ProfileSideEffect.LoginRequested) }

    fun onGoogleLoginTapped() = intent { postSideEffect(ProfileSideEffect.GoogleLoginRequested) }

    fun onPackTapped(id: String) = intent {
        val pack = state.packs.firstOrNull { it.id == id } ?: return@intent
        postSideEffect(ProfileSideEffect.PackDetailRequested(festivalId = pack.id, title = pack.name))
    }

    /** 라이브 보드 "가는 길" 경계. 받은 팩 목록과 상관없이 그 축제의 팩 상세를 연다. */
    fun onOpenPackDirections(festivalId: String, title: String) = intent {
        postSideEffect(ProfileSideEffect.PackDetailRequested(festivalId = festivalId, title = title))
    }

    fun onSettingsTapped() = intent { postSideEffect(ProfileSideEffect.SettingsRequested) }

    fun onBrowseFestivalsTapped() = intent { postSideEffect(ProfileSideEffect.FestivalListRequested) }

    // scope: 팩 상세 결과(iOS `PackDetailDelegate`). 상세를 닫는 것은 Coordinator다.

    /** 팩을 지웠다. 목록을 다시 읽는다. */
    fun onPackDeleted() = intent { fetchPacks() }

    /** 팩을 새로 받았거나 확인했다. 목록을 다시 읽는다. */
    fun onPackChanged() = intent { fetchPacks() }

    // async

    private suspend fun fetchPacks() = subIntent {
        val local =
            try {
                receivedPackUseCase.receivedPacks()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onPacksFailed()
                return@subIntent
            }
        onPacksLoaded(local)
        // 서버 목록을 받지 못하면 저장된 목록 기준으로 보여 준 값을 유지한다.
        val refreshed =
            try {
                receivedPackUseCase.refreshReceivedPacks()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@subIntent
            }
        onPacksRefreshed(refreshed)
    }

    private suspend fun fetchPackChanges(festivalId: String, fromVersion: Int) = subIntent {
        // 실패 · 오프라인이면 아무것도 바꾸지 않아 버전 표시를 그대로 둔다.
        val changes =
            try {
                packDetailUseCase.packChanges(festivalId = festivalId, fromVersion = fromVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@subIntent
            }
        onPackChangesLoaded(festivalId, changes.changedSets)
    }

    // inner

    private suspend fun onPacksLoaded(packs: List<FestivalReceivedPack>) = subIntent {
        reduce { state.copy(packs = packs, packLoad = ProfilePackLoad.Loaded) }
    }

    private suspend fun onPacksRefreshed(packs: List<FestivalReceivedPack>) = subIntent {
        // 새 버전이 있는 팩만 바뀐 공연 수를 묻는다(시안 More: "새 버전이 있어요 · 시간표 N개 변경").
        val updatable = packs.filter { it.hasUpdate }
        val updatableIds = updatable.map { it.id }.toSet()
        reduce {
            state.copy(
                packs = packs,
                packLoad = ProfilePackLoad.Loaded,
                packChangedSets = state.packChangedSets.filterKeys { it in updatableIds },
            )
        }
        updatable.forEach { fetchPackChanges(festivalId = it.id, fromVersion = it.packVersion) }
    }

    private suspend fun onPackChangesLoaded(festivalId: String, changedSets: Int?) = subIntent {
        reduce {
            val changed =
                if (changedSets == null) state.packChangedSets - festivalId else state.packChangedSets + (festivalId to changedSets)
            state.copy(packChangedSets = changed)
        }
    }

    private suspend fun onPacksFailed() = subIntent {
        // 이미 보여 준 목록이 있으면 실패로 바꾸지 않는다.
        if (state.packLoad != ProfilePackLoad.Loaded) reduce { state.copy(packLoad = ProfilePackLoad.Failed) }
    }
}
