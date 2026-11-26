package io.festicore.feature.bearer.register

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerPhotoUploadEvent
import io.festicore.domain.bearer.entity.BearerRegisterRequest
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.domain.festival.entity.FestivalReceivedPack
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.bearer.profile.BearerProfileState
import io.festicore.feature.bearer.profile.BearerProfileViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `BearerRegisterFeature`(Figma S14 D1 · D2 코드 없는 깃발 등록, E1 · E2 저장 완료).
 * 페스티벌은 받은 팩을 먼저, 그다음 서버의 앞으로 열리는 페스티벌을 보인다.
 * 목록 · 존 · 등록 · 사진 업로드 · 초대는 각각 하나만 돈다(iOS `cancelInFlight`). 뒤로 · 나중에 할게요는 등록 요청을 취소한다.
 *
 * @param preferredFestivalId 라이브 탭에서 연 페스티벌. 목록에 있으면 먼저 고른다.
 * @param clock '오늘'(서울)을 정하는 시계. 테스트가 바꾼다.
 */
class BearerRegisterViewModel(
    preferredFestivalId: String?,
    private val bearerUseCase: BearerUseCase,
    private val catalogUseCase: FestivalCatalogUseCase,
    private val receivedPackUseCase: FestivalReceivedPackUseCase,
    private val clock: Clock = Clock.system(SEOUL),
) : ViewModel(),
    OrbitContainerHost<BearerRegisterState, BearerRegisterState, BearerRegisterSideEffect> {
    override val container =
        orbitContainer<BearerRegisterState, BearerRegisterSideEffect>(BearerRegisterState(preferredFestivalId = preferredFestivalId))

    private val festivalsJob = AtomicReference<Job?>(null)
    private val zonesJob = AtomicReference<Job?>(null)
    private val registerJob = AtomicReference<Job?>(null)
    private val uploadJob = AtomicReference<Job?>(null)
    private val inviteJob = AtomicReference<Job?>(null)

    // MARK: - View

    fun onAppear() = intent {
        if (state.festivals.load == BearerFestivalLoad.Loaded) return@intent
        reduce { state.copy(festivals = state.festivals.copy(load = BearerFestivalLoad.Loading)) }
        loadFestivals()
    }

    fun onRetryTapped() = intent {
        reduce { state.copy(festivals = state.festivals.copy(load = BearerFestivalLoad.Loading)) }
        loadFestivals()
    }

    fun onFestivalTapped(festivalId: String) = intent {
        // 저장 중에는 등록 요청의 페스티벌이 바뀌지 않게 고르기를 막는다.
        if (state.isSaving || state.selectedFestivalId == festivalId) return@intent
        select(festivalId)
    }

    fun onShowMoreTapped() = intent {
        reduce { state.copy(festivals = state.festivals.copy(showsAllUpcoming = true)) }
    }

    fun onNameChanged(text: String) = intent {
        reduce {
            state.copy(
                form = state.form.copy(name = BearerProfileViewModel.prefix(text, BearerProfileState.NAME_MAX)),
                nameError = null,
                saveError = null,
            )
        }
    }

    fun onBioChanged(text: String) = intent {
        reduce { state.copy(form = state.form.copy(bio = BearerProfileViewModel.prefix(text, BearerProfileState.BIO_LIMIT))) }
    }

    fun onTagTapped(tag: BearerFlagTag) = intent {
        val tags = state.form.tags
        when {
            tag in tags -> reduce { state.copy(form = state.form.copy(tags = tags - tag)) }
            tags.size < BearerProfileState.TAG_LIMIT -> reduce { state.copy(form = state.form.copy(tags = tags + tag)) }
        }
    }

    fun onZoneTapped(zoneId: String) = intent {
        reduce { state.copy(form = state.form.copy(homeZoneId = if (state.form.homeZoneId == zoneId) null else zoneId)) }
    }

    fun onPlanVisibleChanged(isVisible: Boolean) = intent {
        reduce { state.copy(form = state.form.copy(planVisible = isVisible)) }
    }

    fun onPhotoPicked(data: ByteArray) = intent {
        reduce { state.copy(photoData = data, photoError = null) }
    }

    /** 고른 사진을 기기에서 읽지 못했다(URI 열기 · 읽기 실패). 이전에 고른 사진은 둔다. Android 추가(iOS는 조용히 넘어감 `BRV:44`, 프로필 `onPhotoLoadFailed`와 같음). */
    fun onPhotoLoadFailed() = intent {
        reduce { state.copy(photoError = BearerProfileViewModel.photoMessage(BearerError.Unavailable())) }
    }

    fun onSaveTapped() = intent {
        val festival = state.selectedFestival
        if (!state.canSave || festival == null) return@intent
        val change = state.change
        val pending = state.pendingRequest
        // 응답을 받지 못한 같은 등록은 같은 멱등키로 다시 보내 깃발이 두 개 생기지 않게 한다.
        val request =
            if (pending != null && pending.festivalId == festival.id && pending.change == change) {
                pending
            } else {
                bearerUseCase.makeRegisterRequest(festivalId = festival.id, change = change)
            }
        reduce { state.copy(pendingRequest = request, isSaving = true, saveError = null) }
        register(request)
    }

    fun onClaimTapped() = intent {
        val festivalId = state.selectedFestivalId ?: return@intent
        postSideEffect(BearerRegisterSideEffect.ClaimRequested(festivalId = festivalId))
    }

    fun onLaterTapped() = intent {
        registerJob.getAndSet(null)?.cancel()
        postSideEffect(BearerRegisterSideEffect.CloseRequested)
    }

    fun onBackTapped() = intent {
        registerJob.getAndSet(null)?.cancel()
        postSideEffect(BearerRegisterSideEffect.BackRequested)
    }

    /** 저장 완료의 첫 버튼(E1 팩 받기 · E2 완료) */
    fun onPrimaryDoneTapped() = intent {
        val completion = state.completion ?: return@intent
        val festivalId = completion.festival.id
        if (completion.festival.hasPack) {
            postSideEffect(BearerRegisterSideEffect.Finished(festivalId = festivalId))
        } else {
            postSideEffect(BearerRegisterSideEffect.PackRequested(festivalId = festivalId))
        }
    }

    /** 저장 완료의 둘째 버튼(E1 나중에 받을게요 · E2 공동 기수 초대하기) */
    fun onSecondaryDoneTapped() = intent {
        val completion = state.completion ?: return@intent
        if (!completion.festival.hasPack) {
            postSideEffect(BearerRegisterSideEffect.Finished(festivalId = completion.festival.id))
            return@intent
        }
        if (completion.isInviting) return@intent
        reduce { state.copy(completion = completion.copy(isInviting = true)) }
        invite(completion.claim.flagId)
    }

    /** 팝업의 확인 · 닫기(iOS `.destination(.presented(.alert(...)))`). */
    fun onAlertDismissed() = intent {
        reduce { state.copy(alert = null) }
    }

    // MARK: - Async

    private suspend fun loadFestivals() = subIntent {
        val today = LocalDate.now(clock).toString()
        val job =
            intent {
                val loaded = loadBearerFestivalOptions(receivedPackUseCase, catalogUseCase, today)
                val isOffline = loaded.isOffline
                val options = loaded.options
                if (options.received.isEmpty() && options.upcoming.isEmpty()) {
                    reduce { state.copy(festivals = state.festivals.copy(load = BearerFestivalLoad.Failed(offline = isOffline))) }
                } else {
                    festivalsLoaded(received = options.received, upcoming = options.upcoming)
                }
            }
        festivalsJob.getAndSet(job)?.cancel()
    }

    private suspend fun loadZones(festivalId: String) = subIntent {
        val job =
            intent {
                val zones =
                    try {
                        bearerUseCase.fetchFestival(festivalId = festivalId).zones
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        emptyList()
                    }
                zonesLoaded(festivalId = festivalId, zones = zones)
            }
        zonesJob.getAndSet(job)?.cancel()
    }

    private suspend fun register(request: BearerRegisterRequest) = subIntent {
        val job =
            intent {
                val claim =
                    try {
                        bearerUseCase.register(request)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: BearerError) {
                        registerFailed(error)
                        return@intent
                    } catch (_: Exception) {
                        registerFailed(BearerError.Unavailable())
                        return@intent
                    }
                registered(claim)
            }
        registerJob.getAndSet(job)?.cancel()
    }

    private suspend fun uploadPhoto(flagId: String, data: ByteArray) = subIntent {
        val job =
            intent {
                try {
                    bearerUseCase.uploadPhoto(flagId = flagId, imageData = data).collect { event ->
                        if (event is BearerPhotoUploadEvent.Completed) setPhoto(BearerRegisterPhoto.Uploaded)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    setPhoto(BearerRegisterPhoto.Failed)
                }
            }
        uploadJob.getAndSet(job)?.cancel()
    }

    private suspend fun invite(flagId: String) = subIntent {
        val job =
            intent {
                val alert =
                    try {
                        BearerRegisterAlert.Invite(code = bearerUseCase.inviteCoBearer(flagId = flagId).code)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: BearerError) {
                        BearerRegisterAlert.InviteFailed(message = BearerProfileViewModel.inviteMessage(error))
                    } catch (_: Exception) {
                        BearerRegisterAlert.InviteFailed(message = BearerProfileViewModel.inviteMessage(BearerError.Unavailable()))
                    }
                reduce { state.copy(completion = state.completion?.copy(isInviting = false), alert = alert) }
            }
        inviteJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    private suspend fun festivalsLoaded(received: List<BearerFestivalOption>, upcoming: List<BearerFestivalOption>) = subIntent {
        var selects = false
        reduce {
            val loaded = state.copy(
                festivals = state.festivals.copy(received = received, upcoming = upcoming, load = BearerFestivalLoad.Loaded),
            )
            // 고른 페스티벌이 목록에 그대로 있으면 유지하고, 없으면 라이브에서 연 페스티벌 · 첫 받은 팩 순으로 고른다.
            when {
                loaded.selectedFestival != null -> loaded

                else -> {
                    val preferred = loaded.festivals.option(loaded.preferredFestivalId) ?: received.firstOrNull()
                    selects = preferred != null
                    if (preferred == null) loaded.copy(selectedFestivalId = null) else loaded.selecting(preferred.id)
                }
            }
        }
        if (selects) loadZonesForSelection()
    }

    private suspend fun zonesLoaded(festivalId: String, zones: List<BearerZone>) = subIntent {
        if (state.selectedFestivalId != festivalId) return@subIntent
        reduce {
            val zoneId = state.form.homeZoneId
            val keepsZone = zoneId == null || zones.any { it.id == zoneId }
            state.copy(zones = zones, form = if (keepsZone) state.form else state.form.copy(homeZoneId = null))
        }
    }

    /** 테스트가 서버 응답 없이 성공을 바로 넣을 때도 쓴다(iOS `.inner(.registered)`). */
    internal fun onRegistered(claim: BearerClaim) = intent { registered(claim) }

    private suspend fun registered(claim: BearerClaim) = subIntent {
        // 완료 · 핀은 화면의 현재 선택이 아니라 실제로 보낸 요청의 페스티벌을 기준으로 한다.
        val requestedFestivalId = state.pendingRequest?.festivalId ?: state.selectedFestivalId
        val festival = state.festivals.option(requestedFestivalId)
        if (festival == null) {
            reduce { state.copy(isSaving = false, pendingRequest = null) }
            return@subIntent
        }
        val data = state.photoData
        val completion =
            BearerRegisterCompletion(
                claim = claim,
                festival = festival,
                photo = if (data != null) BearerRegisterPhoto.Uploading else BearerRegisterPhoto.None,
            )
        reduce { state.copy(isSaving = false, pendingRequest = null, completion = completion) }
        postSideEffect(BearerRegisterSideEffect.Registered(flagId = claim.flagId, festivalId = festival.id))
        if (data != null) uploadPhoto(flagId = claim.flagId, data = data)
    }

    /** 테스트가 서버 응답 없이 실패를 바로 넣을 때도 쓴다(iOS `.inner(.registerFailed)`). */
    internal fun onRegisterFailed(error: BearerError) = intent { registerFailed(error) }

    private suspend fun registerFailed(error: BearerError) = subIntent {
        reduce {
            when (error) {
                is BearerError.NameTaken ->
                    state.copy(
                        isSaving = false,
                        nameError = BearerProfileViewModel.NAME_TAKEN_MESSAGE,
                        alert = BearerRegisterAlert.NameTaken,
                        pendingRequest = null,
                    )

                is BearerError.BearerLimit ->
                    state.copy(
                        isSaving = false,
                        alert = BearerRegisterAlert.BearerLimit(festivalName = state.selectedFestival?.name),
                        pendingRequest = null,
                    )

                // 응답을 못 받았을 수 있으니 같은 멱등키를 남겨 다시 누르면 같은 등록으로 보낸다.
                is BearerError.Offline -> state.copy(isSaving = false, saveError = BearerProfileViewModel.saveMessage(error))

                else -> state.copy(isSaving = false, saveError = BearerProfileViewModel.saveMessage(error), pendingRequest = null)
            }
        }
    }

    private suspend fun setPhoto(photo: BearerRegisterPhoto) = subIntent {
        reduce { state.copy(completion = state.completion?.copy(photo = photo)) }
    }

    private suspend fun select(festivalId: String) = subIntent {
        reduce { state.selecting(festivalId) }
        loadZonesForSelection()
    }

    /** 고른 페스티벌에 받은 팩이 있으면 그 팩의 존을 읽고, 없으면 읽던 것을 멈춘다. */
    private suspend fun loadZonesForSelection() = subIntent {
        val festival = state.selectedFestival
        if (festival?.hasPack == true) {
            loadZones(festival.id)
        } else {
            zonesJob.getAndSet(null)?.cancel()
        }
    }

    /** iOS `select(_:state:)`의 상태 변경: 고르면 존을 비운다. */
    private fun BearerRegisterState.selecting(festivalId: String): BearerRegisterState =
        copy(selectedFestivalId = festivalId, zones = emptyList(), form = form.copy(homeZoneId = null))

    internal companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        data class Options(
            val received: List<BearerFestivalOption>,
            val upcoming: List<BearerFestivalOption>,
        )

        /** 받은 팩을 먼저, 끝나지 않은 서버 페스티벌을 시작 순으로 그다음에 둔다. 같은 페스티벌은 한 번만 보인다. iOS `options(packs:catalog:today:)`. */
        fun options(
            packs: List<FestivalReceivedPack>,
            catalog: List<FestivalCatalogItem>,
            today: String,
        ): Options {
            val catalogById = catalog.reversed().associateBy { it.id }
            val received =
                packs.map { pack ->
                    BearerFestivalOption(id = pack.id, name = pack.name, dateText = catalogById[pack.id]?.let(::dateText), hasPack = true)
                }
            val receivedIds = packs.map { it.id }.toSet()
            val upcoming =
                catalog
                    .filter { item ->
                        if (item.id in receivedIds) return@filter false
                        val last = day(item.lastDate) ?: day(item.firstDate) ?: return@filter false
                        last >= today
                    }.sortedBy { day(it.firstDate) ?: "" }
                    .map { item -> BearerFestivalOption(id = item.id, name = item.name, dateText = dateText(item), hasPack = false) }
            return Options(received = received, upcoming = upcoming)
        }

        /** `2026-11-20` · `2026-11-22` → `11/20-22`, 달이 다르면 `11/30-12/1`, 하루면 `11/20`. iOS `dateText(_:)`. */
        fun dateText(item: FestivalCatalogItem): String? {
            val first = monthDay(item.firstDate) ?: return null
            val last = monthDay(item.lastDate)
            if (last == null || last == first) return "${first.first}/${first.second}"
            if (last.first == first.first) return "${first.first}/${first.second}-${last.second}"
            return "${first.first}/${first.second}-${last.first}/${last.second}"
        }

        private fun day(value: String?): String? = value?.takeIf { it.length >= 10 }?.take(10)

        private fun monthDay(value: String?): Pair<Int, Int>? {
            val parts = day(value)?.split("-") ?: return null
            if (parts.size != 3) return null
            val month = parts[1].toIntOrNull() ?: return null
            val date = parts[2].toIntOrNull() ?: return null
            return month to date
        }
    }
}
