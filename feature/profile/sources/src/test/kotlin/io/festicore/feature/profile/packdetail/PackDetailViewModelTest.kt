package io.festicore.feature.profile.packdetail

import io.festicore.core.location.client.MapApp
import io.festicore.core.location.client.MapAppClient
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.orbitmvi.orbit.test.test

/**
 * iOS `PackDetailFeatureTests` 15건(같은 이름, `updateFailureKeepsPackAndShowsReason`의 인자 3개는 한 테스트 안에서 돈다)과
 * Android 지도 앱 링크 1건. iOS 확인 팝업 `destination`은 `isDeleteConfirmationPresented`로 본다. 팩 값은 테스트 가짜 값이다.
 */
class PackDetailViewModelTest {
    private class FakePackDetailUseCase(
        var packDetail: suspend (String) -> FestivalPackDetail? = { null },
        var packChanges: suspend (String, Int) -> FestivalPackChanges = { _, _ -> throw IOException("unused") },
        var updatePack: suspend (String) -> FestivalPackDetail? = { null },
        var checklist: FestivalChecklist = FestivalChecklist(),
        var autoDownloadOnWiFi: Boolean = true,
    ) : FestivalPackDetailUseCase {
        val changeRequests = mutableListOf<Pair<String, Int>>()
        val deleted = mutableListOf<String>()
        val saved = mutableListOf<FestivalChecklist>()
        val storedAutoDownload = mutableListOf<Boolean>()

        override suspend fun packDetail(festivalId: String) = packDetail.invoke(festivalId)

        override suspend fun packChanges(festivalId: String, fromVersion: Int): FestivalPackChanges {
            changeRequests += festivalId to fromVersion
            return packChanges.invoke(festivalId, fromVersion)
        }

        override suspend fun updatePack(festivalId: String) = updatePack.invoke(festivalId)

        override suspend fun deletePack(festivalId: String) {
            deleted += festivalId
        }

        override suspend fun checklist(festivalId: String) = checklist

        override suspend fun saveChecklist(checklist: FestivalChecklist, festivalId: String) {
            saved += checklist
        }

        override suspend fun autoDownloadOnWiFi() = autoDownloadOnWiFi

        override suspend fun setAutoDownloadOnWiFi(isOn: Boolean) {
            storedAutoDownload += isOn
        }
    }

    private fun viewModel(
        useCase: FakePackDetailUseCase = FakePackDetailUseCase(),
        mapAppClient: MapAppClient = MapAppClient { false },
        title: String = "원더리벳 2026",
    ) = PackDetailViewModel(
        festivalId = "fst_a",
        title = title,
        packDetailUseCase = useCase,
        mapAppClient = mapAppClient,
        newId = { "00000000-0000-0000-0000-000000000000" },
    )

    private fun sampleDetail(version: Int, latest: Int) = FestivalPackDetail(
        pack = FestivalReceivedPack(id = "fst_a", name = "원더리벳 2026", packVersion = version, latestPackVersion = latest),
        directions = FestivalPackDetail.Directions(lastShuttleAt = "23:40", lastTrainAt = "23:52"),
        checklist =
            listOf(
                FestivalPackDetail.ChecklistItem(id = "ticket", label = "모바일 티켓", required = true),
                FestivalPackDetail.ChecklistItem(id = "earplug", label = "귀마개"),
            ),
        rules = listOf("유리병"),
    )

    private fun loaded(version: Int, latest: Int) = PackDetailState(
        festivalId = "fst_a",
        title = "원더리벳 2026",
        load = PackDetailLoad.Loaded,
        detail = sampleDetail(version, latest),
    )

    @Test
    fun lastRideTimeUsesSeoul24Hour() {
        assertEquals("22:20", PackDetailPresentation.timeText("2026-11-22T22:20:00+09:00"))
        assertEquals("22:52", PackDetailPresentation.timeText("2026-11-22T13:52:00Z"))
        assertEquals("00:10", PackDetailPresentation.timeText("2026-11-23T00:10:00+09:00"))
        assertEquals("23:40", PackDetailPresentation.timeText("23:40"))
        assertEquals("-", PackDetailPresentation.timeText(null))
    }

    @Test
    fun appearLoadsPackChecklistAndChangesForOutdatedPack() = runTest {
        val detail = sampleDetail(version = 12, latest = 13)
        val changes = FestivalPackChanges(fromVersion = 12, toVersion = 13, sizeBytes = 524_288, changedSets = 2)
        val useCase =
            FakePackDetailUseCase(
                packDetail = { detail },
                packChanges = { _, _ -> changes },
                checklist = FestivalChecklist(checkedIds = setOf("ticket")),
                autoDownloadOnWiFi = false,
            )
        viewModel(useCase, title = "팩").test(this) {
            containerHost.onAppear()
            expectState { copy(load = PackDetailLoad.Loading) }
            expectState {
                copy(
                    detail = detail,
                    title = "원더리벳 2026",
                    load = PackDetailLoad.Loaded,
                    checklist = FestivalChecklist(checkedIds = setOf("ticket")),
                    autoDownloadOnWiFi = false,
                )
            }
            expectState { copy(changes = changes) }
            val state = containerHost.container.stateFlow.value
            assertEquals(1, state.checkedCount)
            assertEquals(listOf("ticket", "earplug"), state.checklistRows.map { it.id })
        }
        assertEquals(listOf("fst_a" to 12), useCase.changeRequests)
    }

    @Test
    fun missingPackAndFailureAreShownSeparately() = runTest {
        var attempts = 0
        val useCase =
            FakePackDetailUseCase(packDetail = {
                attempts += 1
                if (attempts == 1) throw IOException("cannot open")
                null
            })
        viewModel(useCase, title = "팩").test(this) {
            containerHost.onAppear()
            expectState { copy(load = PackDetailLoad.Loading) }
            expectState { copy(load = PackDetailLoad.Failed) }
            containerHost.onRetryTapped()
            expectState { copy(load = PackDetailLoad.Loading) }
            expectState { copy(load = PackDetailLoad.Missing) }
        }
    }

    @Test
    fun checklistEditsAreSavedAndRequiredItemsStay() = runTest {
        val useCase = FakePackDetailUseCase()
        val customId = "custom-00000000-0000-0000-0000-000000000000"
        viewModel(useCase).test(this, initialState = loaded(version = 12, latest = 12)) {
            containerHost.onItemToggled("earplug")
            expectState { copy(checklist = checklist.copy(checkedIds = setOf("earplug"))) }
            containerHost.onEditTapped()
            expectState { copy(isEditingChecklist = true) }
            containerHost.onNewItemTextChanged("  선크림 ")
            expectState { copy(newItemText = "  선크림 ") }
            containerHost.onAddItemTapped()
            expectState {
                copy(
                    checklist = checklist.copy(customItems = listOf(FestivalChecklist.CustomItem(id = customId, label = "선크림"))),
                    newItemText = "",
                )
            }
            // 필수 항목은 지우지 않는다.
            containerHost.onItemRemoved("ticket").join()
            containerHost.onItemRemoved("earplug")
            expectState { copy(checklist = checklist.copy(hiddenIds = setOf("earplug"), checkedIds = emptySet())) }
            containerHost.onItemRemoved(customId)
            expectState { copy(checklist = checklist.copy(customItems = emptyList())) }
            containerHost.onEditDoneTapped()
            expectState { copy(isEditingChecklist = false) }
            assertEquals(listOf("ticket"), containerHost.container.stateFlow.value.checklistRows.map { it.id })
        }
        assertEquals(4, useCase.saved.size)
        assertEquals(FestivalChecklist(hiddenIds = setOf("earplug")), useCase.saved.last())
    }

    @Test
    fun updateReplacesPackAndAsksProfileToReload() = runTest {
        val updated = sampleDetail(version = 13, latest = 13)
        val initial =
            loaded(version = 12, latest = 13).copy(
                changes = FestivalPackChanges(fromVersion = 12, toVersion = 13, sizeBytes = 1, changedSets = 1),
            )
        viewModel(FakePackDetailUseCase(updatePack = { updated })).test(this, initialState = initial) {
            containerHost.onUpdateTapped()
            expectState { copy(isUpdating = true) }
            expectState { copy(isUpdating = false, detail = updated, changes = null, manageMessage = "새 버전 v13을 받았어요") }
            expectSideEffect(PackDetailSideEffect.PackChanged)
        }
    }

    @Test
    fun updateTapWhileUpdatingIsIgnored() = runTest {
        val initial = loaded(version = 12, latest = 13).copy(isUpdating = true)
        viewModel().test(this, initialState = initial) {
            // 받는 중에 다시 눌러도 한 번만 받는다.
            containerHost.onUpdateTapped().join()
            containerHost.onCheckUpdateTapped().join()
        }
    }

    @Test
    fun updateFailureKeepsPackAndShowsReason() = runTest {
        val cases =
            listOf(
                FestivalPackError.RetryLater() to "지금은 받는 사람이 많아요. 잠시 후 다시 시도해 주세요.",
                FestivalPackError.Offline() to "인터넷에 연결되어 있지 않아요. 연결을 확인한 뒤 다시 시도해 주세요.",
                FestivalPackError.InvalidPack() to "업데이트를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
            )
        cases.forEach { (error, message) ->
            viewModel(FakePackDetailUseCase(updatePack = { throw error })).test(this, initialState = loaded(version = 12, latest = 12)) {
                containerHost.onCheckUpdateTapped()
                expectState { copy(isUpdating = true) }
                expectState { copy(isUpdating = false, manageMessage = message) }
            }
        }
    }

    @Test
    fun updateAndDeleteDoNotStartWhileTheOtherRuns() = runTest {
        val updating = loaded(version = 12, latest = 13).copy(isUpdating = true)
        viewModel().test(this, initialState = updating) {
            containerHost.onDeleteTapped().join()
        }
        val deleting = loaded(version = 12, latest = 13).copy(isDeleting = true)
        viewModel().test(this, initialState = deleting) {
            containerHost.onUpdateTapped().join()
            containerHost.onCheckUpdateTapped().join()
        }
    }

    @Test
    fun confirmingDeleteCancelsInFlightUpdate() = runTest {
        var updateCancelled = false
        val useCase =
            FakePackDetailUseCase(updatePack = {
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    updateCancelled = true
                    throw e
                }
            })
        val initial = loaded(version = 12, latest = 13).copy(isDeleteConfirmationPresented = true)
        viewModel(useCase).test(this, initialState = initial) {
            containerHost.onUpdateTapped()
            expectState { copy(isUpdating = true) }
            containerHost.onDeleteConfirmed()
            expectState { copy(isDeleteConfirmationPresented = false, isDeleting = true, isUpdating = false) }
            expectState { copy(isDeleting = false) }
            expectSideEffect(PackDetailSideEffect.PackDeleted)
        }
        assertEquals(listOf("fst_a"), useCase.deleted)
        assertTrue(updateCancelled)
    }

    @Test
    fun deleteRequiresConfirmation() = runTest {
        val useCase = FakePackDetailUseCase()
        viewModel(useCase).test(this, initialState = loaded(version = 12, latest = 12)) {
            containerHost.onDeleteTapped()
            expectState { copy(isDeleteConfirmationPresented = true) }
            containerHost.onDeleteDismissed()
            expectState { copy(isDeleteConfirmationPresented = false) }
            assertTrue(useCase.deleted.isEmpty())

            containerHost.onDeleteTapped()
            expectState { copy(isDeleteConfirmationPresented = true) }
            containerHost.onDeleteConfirmed()
            expectState { copy(isDeleteConfirmationPresented = false, isDeleting = true) }
            expectState { copy(isDeleting = false) }
            expectSideEffect(PackDetailSideEffect.PackDeleted)
        }
        assertEquals(listOf("fst_a"), useCase.deleted)
    }

    @Test
    fun tabBarIndexAndPageIndexShareTab() {
        assertEquals(listOf(0, 1, 2), PackDetailTab.entries.map { it.ordinal })
        assertEquals(listOf("가는 길", "준비물", "팩 관리"), PackDetailTab.entries.map { it.title })
        assertEquals(PackDetailTab.Directions, PackDetailTab.at(0))
        assertEquals(PackDetailTab.Checklist, PackDetailTab.at(1))
        assertEquals(PackDetailTab.Manage, PackDetailTab.at(2))
        assertEquals(PackDetailTab.Directions, PackDetailTab.at(3))
    }

    @Test
    fun tabTapChangesSelectedTab() = runTest {
        viewModel(title = "팩").test(this, initialState = loaded(version = 12, latest = 12)) {
            containerHost.onTabSelected(PackDetailTab.Manage)
            expectState { copy(tab = PackDetailTab.Manage) }
            containerHost.onTabSelected(PackDetailTab.Directions)
            expectState { copy(tab = PackDetailTab.Directions) }
        }
    }

    @Test
    fun swipeChangesSameSelectedTab() = runTest {
        val initial = loaded(version = 12, latest = 12).copy(tab = PackDetailTab.Checklist, isEditingChecklist = true)
        viewModel(title = "팩").test(this, initialState = initial) {
            // 준비물(1) → 팩 관리(2) → 준비물(1) → 가는 길(0)
            containerHost.onTabSelected(PackDetailTab.at(2))
            expectState { copy(tab = PackDetailTab.Manage, isEditingChecklist = false) }
            containerHost.onTabSelected(PackDetailTab.at(1))
            expectState { copy(tab = PackDetailTab.Checklist) }
            containerHost.onTabSelected(PackDetailTab.at(0))
            expectState { copy(tab = PackDetailTab.Directions) }
        }
    }

    @Test
    fun autoDownloadToggleIsStored() = runTest {
        val useCase = FakePackDetailUseCase()
        viewModel(useCase, title = "팩").test(this) {
            containerHost.onAutoDownloadToggled(false)
            expectState { copy(autoDownloadOnWiFi = false) }
        }
        assertEquals(listOf(false), useCase.storedAutoDownload)
    }

    @Test
    fun directionsChecksInstalledMapApps() = runTest {
        viewModel(mapAppClient = { it == MapApp.Naver }, title = "팩").test(this) {
            containerHost.onDirectionsAppeared()
            expectState { copy(installedMapApps = setOf(MapApp.Naver)) }
        }
    }

    /** Android 추가: 설치된 지도 앱만 앱으로 열고, 그 밖의 링크 · 확인 전 링크는 Custom Tabs로 연다(설계 쟁점 M10 · M11). */
    @Test
    fun mapLinkOpensInstalledAppOnly() = runTest {
        viewModel(mapAppClient = { it == MapApp.Naver }).test(this, initialState = loaded(version = 12, latest = 12)) {
            containerHost.onLinkTapped("https://example.com/naver", MapApp.Naver)
            expectSideEffect(PackDetailSideEffect.OpenLink("https://example.com/naver", null))
            containerHost.onDirectionsAppeared()
            expectState { copy(installedMapApps = setOf(MapApp.Naver)) }
            containerHost.onLinkTapped("https://example.com/naver", MapApp.Naver)
            expectSideEffect(PackDetailSideEffect.OpenLink("https://example.com/naver", MapApp.Naver))
            containerHost.onLinkTapped("https://example.com/kakao", MapApp.Kakao)
            expectSideEffect(PackDetailSideEffect.OpenLink("https://example.com/kakao", null))
            containerHost.onLinkTapped("https://example.com/shuttle")
            expectSideEffect(PackDetailSideEffect.OpenLink("https://example.com/shuttle", null))
        }
    }
}
