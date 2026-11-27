package io.festicore.feature.profile.packdetail

import androidx.lifecycle.ViewModel
import io.festicore.core.location.client.MapApp
import io.festicore.core.location.client.MapAppClient
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail
import io.festicore.domain.festival.entity.FestivalPackError
import io.festicore.domain.festival.packdetail.FestivalPackDetailUseCase
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * 받은 팩 상세. iOS `PackDetailFeature`. 진입 경로와 관계없이 기기에 받은 팩만 읽고, 새 버전은 사용자가 팩 관리 탭에서 받는다(설계 검토 P2).
 *
 * @param newId 내 준비물 항목 id에 붙일 값(iOS `uuid()`). 테스트가 고정한다.
 */
class PackDetailViewModel(
    festivalId: String,
    title: String,
    private val packDetailUseCase: FestivalPackDetailUseCase,
    private val mapAppClient: MapAppClient,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val tracksChecklistWrites: Boolean = false,
) : ViewModel(),
    OrbitContainerHost<PackDetailState, PackDetailState, PackDetailSideEffect> {
    override val container =
        orbitContainer<PackDetailState, PackDetailSideEffect>(PackDetailState(festivalId = festivalId, title = title))

    private val checklistSaveLock = Mutex()

    /** 진행 중 업데이트. 삭제를 확인하면 멈춘다(iOS `CancelID.update`). */
    private val updateJob = AtomicReference<Job?>(null)

    // view

    /** 처음 보일 때만 읽는다. 이미 상세가 있거나 읽는 중이면 무시한다. */
    fun onAppear() = intent {
        if (state.detail != null || state.load == PackDetailLoad.Loading) return@intent
        reduce { state.copy(load = PackDetailLoad.Loading) }
        load()
    }

    /** 가는 길의 지도 버튼이 보일 때 지도 앱 설치 여부를 다시 확인한다. */
    fun onDirectionsAppeared() = intent { checkMapApps() }

    fun onBackTapped() = intent { postSideEffect(PackDetailSideEffect.BackRequested) }

    /** 탭 바를 누르거나 본문을 넘겼다. 준비물 탭을 벗어나면 편집을 끝낸다. */
    fun onTabSelected(tab: PackDetailTab) = intent {
        reduce { state.copy(tab = tab, isEditingChecklist = if (tab != PackDetailTab.Checklist) false else state.isEditingChecklist) }
    }

    /** 가는 길 링크 · 지도 버튼. 지도 앱이 설치돼 있으면 그 앱이 받게 한다. */
    fun onLinkTapped(url: String, mapApp: MapApp? = null) = intent {
        val installedApp = mapApp?.takeIf { state.installedMapApps?.contains(it) == true }
        postSideEffect(PackDetailSideEffect.OpenLink(url, installedApp))
    }

    fun onRetryTapped() = intent {
        if (state.load != PackDetailLoad.Failed) return@intent
        reduce { state.copy(load = PackDetailLoad.Loading) }
        load()
    }

    private val queuedChecklistWrites = java.util.concurrent.atomic.AtomicInteger()
    val hasPendingChecklistWrites: Boolean
        get() = queuedChecklistWrites.get() > 0 || container.stateFlow.value.pendingChecklistWrites > 0

    private fun trackedChecklistIntent(block: () -> kotlinx.coroutines.Job): kotlinx.coroutines.Job {
        if (tracksChecklistWrites) queuedChecklistWrites.incrementAndGet()
        return block().also { job ->
            if (tracksChecklistWrites) job.invokeOnCompletion { queuedChecklistWrites.decrementAndGet() }
        }
    }

    fun onItemToggled(id: String) = trackedChecklistIntent {
        intent {
            val checked = state.checklist.checkedIds
            reduce { state.copy(checklist = state.checklist.copy(checkedIds = if (id in checked) checked - id else checked + id)) }
            saveChecklist()
        }
    }

    fun onEditTapped() = intent { reduce { state.copy(isEditingChecklist = true) } }

    fun onEditDoneTapped() = intent { reduce { state.copy(isEditingChecklist = false, newItemText = "") } }

    /** 운영팀 필수 항목은 지우지 않는다. 내 항목은 삭제하고 팩 항목은 숨긴다. */
    fun onItemRemoved(id: String) = trackedChecklistIntent {
        intent {
            if (state.detail?.checklist?.firstOrNull { it.id == id }?.required == true) return@intent
            val checklist = state.checklist
            val removed =
                if (checklist.customItems.any { it.id == id }) {
                    checklist.copy(customItems = checklist.customItems.filterNot { it.id == id })
                } else {
                    checklist.copy(hiddenIds = checklist.hiddenIds + id)
                }
            reduce { state.copy(checklist = removed.copy(checkedIds = removed.checkedIds - id)) }
            saveChecklist()
        }
    }

    /** 내 항목 입력. iOS `$store.newItemText`. */
    fun onNewItemTextChanged(value: String) = intent { reduce { state.copy(newItemText = value) } }

    fun onAddItemTapped() = trackedChecklistIntent {
        intent {
            val label = state.newItemText.trim()
            if (label.isEmpty()) return@intent
            val item = FestivalChecklist.CustomItem(id = "custom-${newId().lowercase()}", label = label)
            reduce { state.copy(checklist = state.checklist.copy(customItems = state.checklist.customItems + item), newItemText = "") }
            saveChecklist()
        }
    }

    fun onUpdateTapped() = startUpdate()

    fun onCheckUpdateTapped() = startUpdate()

    fun onAutoDownloadToggled(isOn: Boolean) = intent {
        reduce { state.copy(autoDownloadOnWiFi = isOn) }
        ignoringFailure { packDetailUseCase.setAutoDownloadOnWiFi(isOn) }
    }

    fun onDeleteTapped() = intent {
        if (state.isDeleting || state.isUpdating) return@intent
        reduce { state.copy(isDeleteConfirmationPresented = true) }
    }

    // scope (삭제 확인 팝업)

    /** 확인 팝업의 "삭제". 팝업이 떠 있는 동안 시작한 업데이트가 지운 팩을 다시 받지 않게 멈춘다. */
    fun onDeleteConfirmed() = intent {
        if (!state.isDeleteConfirmationPresented) return@intent
        if (state.isDeleting) {
            reduce { state.copy(isDeleteConfirmationPresented = false) }
            return@intent
        }
        reduce { state.copy(isDeleteConfirmationPresented = false, isDeleting = true, isUpdating = false) }
        updateJob.getAndSet(null)?.cancel()
        delete()
    }

    /** 확인 팝업의 취소 · 딤 · 뒤로. */
    fun onDeleteDismissed() = intent {
        if (state.isDeleteConfirmationPresented) reduce { state.copy(isDeleteConfirmationPresented = false) }
    }

    private fun startUpdate() = intent {
        if (state.isUpdating || state.isDeleting || state.detail == null) return@intent
        reduce { state.copy(isUpdating = true, manageMessage = null) }
        updateJob.getAndSet(intent { update() })?.cancel()
    }

    // async

    private suspend fun load() = subIntent {
        val festivalId = state.festivalId
        val loaded =
            try {
                Triple(
                    packDetailUseCase.packDetail(festivalId),
                    packDetailUseCase.checklist(festivalId),
                    packDetailUseCase.autoDownloadOnWiFi(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reduce { state.copy(load = PackDetailLoad.Failed) }
                return@subIntent
            }
        onLoaded(loaded.first, loaded.second, loaded.third)
    }

    private suspend fun checkMapApps() = subIntent {
        val installed = MapApp.entries.filter { mapAppClient.isInstalled(it) }.toSet()
        reduce { state.copy(installedMapApps = installed) }
    }

    /** 변경 요약을 받지 못해도 업데이트 받기는 할 수 있다. */
    private suspend fun fetchChangesIfNeeded() = subIntent {
        val pack = state.detail?.pack ?: return@subIntent
        if (!pack.hasUpdate) return@subIntent
        val changes =
            try {
                packDetailUseCase.packChanges(state.festivalId, pack.packVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@subIntent
            }
        onChangesLoaded(changes)
    }

    private suspend fun update() = subIntent {
        val detail =
            try {
                packDetailUseCase.updatePack(state.festivalId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: FestivalPackError.RetryLater) {
                onUpdateFailed(RETRY_LATER_MESSAGE)
                return@subIntent
            } catch (_: FestivalPackError.Offline) {
                onUpdateFailed(OFFLINE_MESSAGE)
                return@subIntent
            } catch (_: Exception) {
                onUpdateFailed(UPDATE_FAILED_MESSAGE)
                return@subIntent
            }
        onUpdateFinished(detail)
    }

    private suspend fun delete() = subIntent {
        try {
            packDetailUseCase.deletePack(state.festivalId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            reduce { state.copy(isDeleting = false, manageMessage = DELETE_FAILED_MESSAGE) }
            return@subIntent
        }
        reduce { state.copy(isDeleting = false) }
        postSideEffect(PackDetailSideEffect.PackDeleted)
    }

    private suspend fun saveChecklist() = subIntent {
        // 저장을 한 줄로 세우고 잠금을 얻은 뒤의 최신 준비물을 쓴다. 빠른 연속 변경에서 오래된 저장이 나중에 덮어쓰지 않게 한다.
        if (tracksChecklistWrites) reduce { state.copy(pendingChecklistWrites = state.pendingChecklistWrites + 1) }
        try {
            checklistSaveLock.withLock {
                ignoringFailure { packDetailUseCase.saveChecklist(state.checklist, state.festivalId) }
            }
        } finally {
            if (tracksChecklistWrites) reduce { state.copy(pendingChecklistWrites = state.pendingChecklistWrites - 1) }
        }
    }

    /** iOS는 저장 실패를 알리지 않는다(저장 함수가 던지지 않음). 화면 값은 그대로 둔다. */
    private suspend fun ignoringFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 다음 저장 때 다시 쓴다.
        }
    }

    // inner

    private suspend fun onLoaded(
        detail: FestivalPackDetail?,
        checklist: FestivalChecklist,
        autoDownloadOnWiFi: Boolean,
    ) = subIntent {
        if (detail == null) {
            reduce { state.copy(checklist = checklist, autoDownloadOnWiFi = autoDownloadOnWiFi, load = PackDetailLoad.Missing) }
            return@subIntent
        }
        reduce {
            state.copy(
                checklist = checklist,
                autoDownloadOnWiFi = autoDownloadOnWiFi,
                detail = detail,
                title = detail.pack.name,
                load = PackDetailLoad.Loaded,
            )
        }
        fetchChangesIfNeeded()
    }

    /** 받은 버전과 요약의 시작 버전이 같을 때만 반영한다. */
    private suspend fun onChangesLoaded(changes: FestivalPackChanges) = subIntent {
        if (changes.fromVersion != state.detail?.pack?.packVersion) return@subIntent
        reduce { state.copy(changes = changes) }
    }

    private suspend fun onUpdateFinished(detail: FestivalPackDetail?) = subIntent {
        if (detail == null) {
            reduce { state.copy(isUpdating = false, load = PackDetailLoad.Missing) }
            postSideEffect(PackDetailSideEffect.PackChanged)
            return@subIntent
        }
        val updated = detail.pack.packVersion != state.detail?.pack?.packVersion
        reduce {
            state.copy(
                isUpdating = false,
                detail = detail,
                changes = null,
                manageMessage = if (updated) "새 버전 v${detail.pack.packVersion}을 받았어요" else "최신 버전이에요",
            )
        }
        postSideEffect(PackDetailSideEffect.PackChanged)
        fetchChangesIfNeeded()
    }

    private suspend fun onUpdateFailed(message: String) = subIntent {
        reduce { state.copy(isUpdating = false, manageMessage = message) }
    }

    companion object {
        const val RETRY_LATER_MESSAGE = "지금은 받는 사람이 많아요. 잠시 후 다시 시도해 주세요."
        const val OFFLINE_MESSAGE = "인터넷에 연결되어 있지 않아요. 연결을 확인한 뒤 다시 시도해 주세요."
        const val UPDATE_FAILED_MESSAGE = "업데이트를 확인하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val DELETE_FAILED_MESSAGE = "팩을 삭제하지 못했어요. 다시 시도해 주세요."
    }
}
