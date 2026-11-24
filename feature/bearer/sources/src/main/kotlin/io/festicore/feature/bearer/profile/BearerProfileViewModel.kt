package io.festicore.feature.bearer.profile

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerError
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerPhotoUploadEvent
import io.festicore.domain.bearer.entity.BearerProfileChange
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `BearerProfileFeature`(Figma S10 A~E 깃발 프로필). 프로필을 고치고 사진을 올리고 공동 기수를 초대한다.
 * 읽기 · 사진 업로드 · 저장 · 초대는 각각 하나만 돈다(iOS `cancelInFlight`). 뒤로 가면 업로드를 취소한다.
 *
 * @param profile 코드 등록 응답의 프로필. 없으면 서버에서 읽는다.
 */
class BearerProfileViewModel(
    festivalId: String,
    flagId: String,
    profile: BearerFlagProfile?,
    isNewBearer: Boolean,
    private val bearerUseCase: BearerUseCase,
) : ViewModel(),
    OrbitContainerHost<BearerProfileState, BearerProfileState, BearerProfileSideEffect> {
    override val container =
        orbitContainer<BearerProfileState, BearerProfileSideEffect>(
            BearerProfileState.initial(festivalId = festivalId, flagId = flagId, profile = profile, isNewBearer = isNewBearer),
        )

    private val loadJob = AtomicReference<Job?>(null)
    private val uploadJob = AtomicReference<Job?>(null)
    private val saveJob = AtomicReference<Job?>(null)
    private val inviteJob = AtomicReference<Job?>(null)

    // MARK: - View

    fun onAppear() = intent {
        reduce { state.copy(loadFailed = false) }
        load()
    }

    fun onRetryTapped() = onAppear()

    fun onNameChanged(text: String) = intent {
        reduce { state.copy(name = prefix(text, BearerProfileState.NAME_MAX), nameError = null, saveError = null) }
    }

    fun onBioChanged(text: String) = intent {
        reduce { state.copy(bio = prefix(text, BearerProfileState.BIO_LIMIT)) }
    }

    fun onTagTapped(tag: BearerFlagTag) = intent {
        val tags = state.tags
        when {
            tag in tags -> reduce { state.copy(tags = tags - tag) }
            tags.size < BearerProfileState.TAG_LIMIT -> reduce { state.copy(tags = tags + tag) }
        }
    }

    fun onZoneTapped(zoneId: String) = intent {
        reduce { state.copy(homeZoneId = if (state.homeZoneId == zoneId) null else zoneId) }
    }

    fun onPlanVisibleChanged(isVisible: Boolean) = intent {
        reduce { state.copy(planVisible = isVisible) }
    }

    fun onPlanTapped() = intent {
        postSideEffect(BearerProfileSideEffect.PlanRequested(festivalId = state.festivalId))
    }

    /** 오늘 계획 고르기에서 고른 공연(시작 순) */
    fun onPlanChanged(setIds: List<String>) = intent {
        reduce { state.copy(plan = setIds, isPlanEdited = true) }
    }

    fun onPhotoPicked(data: ByteArray) = intent {
        if (state.isSaving) return@intent
        reduce { state.copy(photoUpload = BearerPhotoUpload.Uploading(data = data, progress = 0.0)) }
        uploadPhoto(data)
    }

    /** 고른 사진을 기기에서 읽지 못했다(URI 열기 · 읽기 실패). 저장 중이면 무시한다. Android 추가(iOS는 조용히 넘어감 `BPV:41`). */
    fun onPhotoLoadFailed() = intent {
        if (state.isSaving) return@intent
        uploadJob.getAndSet(null)?.cancel()
        reduce { state.copy(photoUpload = BearerPhotoUpload.Failed(photoMessage(BearerError.Unavailable()))) }
    }

    fun onPhotoCancelTapped() = intent {
        if (!state.isUploadingPhoto) return@intent
        uploadJob.getAndSet(null)?.cancel()
        reduce { state.copy(photoUpload = BearerPhotoUpload.Idle) }
    }

    fun onInviteTapped() = intent {
        if (state.isInviting) return@intent
        reduce { state.copy(isInviting = true) }
        invite()
    }

    fun onSaveTapped() = intent {
        if (!state.canSave) return@intent
        reduce { state.copy(isSaving = true, saveError = null) }
        save(state.change)
    }

    fun onDoneTapped() = intent {
        postSideEffect(BearerProfileSideEffect.Finished(flagId = state.flagId))
    }

    fun onBackTapped() = intent {
        uploadJob.getAndSet(null)?.cancel()
        postSideEffect(BearerProfileSideEffect.CloseRequested)
    }

    /** 팝업의 확인 · 닫기(iOS `.destination(.presented(.alert(...)))`). */
    fun onAlertDismissed() = intent {
        reduce { state.copy(alert = null) }
    }

    // MARK: - Async

    private suspend fun load() = subIntent {
        val festivalId = state.festivalId
        val flagId = state.flagId
        val needsProfile = state.isLoading
        val job =
            intent {
                coroutineScope {
                    val festival = async { fetchFestivalOrNull(festivalId) }
                    val board =
                        try {
                            bearerUseCase.fetchBoard(flagId = flagId)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            // 프로필을 이미 받았으면 공동 기수 수만 못 채운 채로 입력을 계속한다.
                            if (needsProfile) {
                                festival.await()
                                reduce { state.copy(loadFailed = true) }
                                return@coroutineScope
                            }
                            null
                        }
                    loaded(board, festival.await())
                }
            }
        loadJob.getAndSet(job)?.cancel()
    }

    private suspend fun fetchFestivalOrNull(festivalId: String): BearerFestival? = try {
        bearerUseCase.fetchFestival(festivalId = festivalId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun uploadPhoto(data: ByteArray) = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                try {
                    bearerUseCase.uploadPhoto(flagId = flagId, imageData = data).collect { event ->
                        when (event) {
                            is BearerPhotoUploadEvent.Progress -> photoProgress(event.fraction)
                            is BearerPhotoUploadEvent.Completed -> photoUploaded(event.photo.thumbnailUrl ?: event.photo.photoUrl)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: BearerError) {
                    photoFailed(error)
                } catch (_: Exception) {
                    photoFailed(BearerError.Unavailable())
                }
            }
        uploadJob.getAndSet(job)?.cancel()
    }

    private suspend fun save(change: BearerProfileChange) = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                val profile =
                    try {
                        bearerUseCase.updateProfile(flagId = flagId, change = change)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: BearerError) {
                        saveFailed(error)
                        return@intent
                    } catch (_: Exception) {
                        saveFailed(BearerError.Unavailable())
                        return@intent
                    }
                reduce { state.copy(isSaving = false, isPlanEdited = false, savedProfile = profile) }
            }
        saveJob.getAndSet(job)?.cancel()
    }

    private suspend fun invite() = subIntent {
        val flagId = state.flagId
        val job =
            intent {
                val alert =
                    try {
                        BearerProfileAlert.Invite(code = bearerUseCase.inviteCoBearer(flagId = flagId).code)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: BearerError) {
                        BearerProfileAlert.InviteFailed(message = inviteMessage(error))
                    } catch (_: Exception) {
                        BearerProfileAlert.InviteFailed(message = inviteMessage(BearerError.Unavailable()))
                    }
                reduce { state.copy(isInviting = false, alert = alert) }
            }
        inviteJob.getAndSet(job)?.cancel()
    }

    // MARK: - Inner

    private suspend fun loaded(board: BearerBoard?, festival: BearerFestival?) = subIntent {
        reduce {
            var next = state
            if (festival != null) {
                next = next.copy(festivalName = festival.name, zones = festival.zones, sets = festival.sets)
            }
            if (board != null) {
                if (next.isLoading) next = next.applying(board.profile)
                next =
                    next.copy(
                        photoUrl = board.profile.thumbnailUrl ?: board.profile.photoUrl,
                        bearerCount = maxOf(1, board.coBearers.size),
                    )
            }
            next.copy(isLoading = false)
        }
    }

    private suspend fun photoProgress(fraction: Double) = subIntent {
        val upload = state.photoUpload as? BearerPhotoUpload.Uploading ?: return@subIntent
        reduce { state.copy(photoUpload = upload.copy(progress = fraction)) }
    }

    private suspend fun photoUploaded(url: String?) = subIntent {
        val upload = state.photoUpload as? BearerPhotoUpload.Uploading ?: return@subIntent
        reduce { state.copy(uploadedPhotoData = upload.data, photoUrl = url, photoUpload = BearerPhotoUpload.Idle) }
    }

    /** 테스트가 서버 응답 없이 실패를 바로 넣을 때도 쓴다(iOS `.inner(.photoFailed)`). */
    internal fun onPhotoFailed(error: BearerError) = intent { photoFailed(error) }

    private suspend fun photoFailed(error: BearerError) = subIntent {
        if (!state.isUploadingPhoto) return@subIntent
        reduce { state.copy(photoUpload = BearerPhotoUpload.Failed(photoMessage(error))) }
    }

    /** 테스트가 서버 응답 없이 실패를 바로 넣을 때도 쓴다(iOS `.inner(.saveFailed)`). */
    internal fun onSaveFailed(error: BearerError) = intent { saveFailed(error) }

    private suspend fun saveFailed(error: BearerError) = subIntent {
        if (error is BearerError.NameTaken) {
            reduce { state.copy(isSaving = false, nameError = NAME_TAKEN_MESSAGE) }
        } else {
            reduce { state.copy(isSaving = false, saveError = saveMessage(error)) }
        }
    }

    internal companion object {
        const val NAME_TAKEN_MESSAGE = "이미 있는 깃발 이름이에요. 다른 이름을 지어 주세요."

        /** 서버처럼 코드포인트 수로 길이를 자른다. 이모지 하나가 글자 여러 개로 세어질 수 있다. iOS `prefix(_:scalars:)`. */
        fun prefix(text: String, limit: Int): String {
            if (text.codePointCount(0, text.length) <= limit) return text
            return text.substring(0, text.offsetByCodePoints(0, limit))
        }

        fun photoMessage(error: BearerError): String = when (error) {
            is BearerError.PhotoTooSmall -> "256×256보다 큰 사진을 골라 주세요"
            is BearerError.PhotoTooLarge -> "사진이 너무 커요. 다른 사진을 골라 주세요"
            is BearerError.UnsupportedPhoto, is BearerError.InvalidRequest -> "이 사진은 올릴 수 없어요. JPEG·PNG·HEIC 사진을 골라 주세요"
            is BearerError.Offline -> "인터넷에 연결되지 않아 사진을 올리지 못했어요"
            else -> "사진을 올리지 못했어요. 다시 골라 주세요"
        }

        fun saveMessage(error: BearerError): String = when (error) {
            is BearerError.Offline -> "인터넷에 연결되지 않았어요. 연결 후 다시 눌러 주세요"
            is BearerError.NotBearer -> "이 깃발의 기수가 아니에요"
            is BearerError.ZoneNotFound -> "고른 존을 찾을 수 없어요. 다른 존을 골라 주세요"
            is BearerError.TermsRequired -> "새 약관에 동의한 뒤에 저장해 주세요"
            is BearerError.NotAuthenticated -> "로그인한 뒤에 저장해 주세요"
            else -> "저장하지 못했어요. 잠시 후 다시 시도해 주세요"
        }

        fun inviteMessage(error: BearerError): String = when (error) {
            is BearerError.NotOwner -> "처음 깃발을 만든 기수만 초대할 수 있어요."
            is BearerError.CoBearerFull -> "공동 기수는 깃발마다 3명까지예요."
            is BearerError.Offline -> "인터넷에 연결되지 않았어요."
            else -> "초대 코드를 만들지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}
