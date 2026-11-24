package io.festicore.feature.bearer.claim

import androidx.lifecycle.ViewModel
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerError
import java.time.Clock
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

/**
 * iOS `BearerClaimFeature`(Figma S13 F~I). 코드 6자리를 받아 [festivalId] 축제의 기수 권한을 등록한다.
 * 등록 요청과 잠금 카운트다운은 각각 하나만 돈다(iOS `cancelInFlight`). 성공 · 닫기 때 둘 다 취소한다.
 */
class BearerClaimViewModel(
    festivalId: String,
    private val bearerUseCase: BearerUseCase,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel(),
    OrbitContainerHost<BearerClaimState, BearerClaimState, BearerClaimSideEffect> {
    override val container =
        orbitContainer<BearerClaimState, BearerClaimSideEffect>(BearerClaimState(festivalId = festivalId, now = clock.instant()))

    private val claimJob = AtomicReference<Job?>(null)
    private val lockCountdownJob = AtomicReference<Job?>(null)

    fun onCodeChanged(text: String) = intent {
        if (state.isSubmitting || state.isLocked) return@intent
        val filtered = text.uppercase().filter { it in CODE_ALPHABET }.take(BearerClaimState.CODE_LENGTH)
        val failure = state.failure.takeUnless { it is BearerClaimFailure.Invalid || it is BearerClaimFailure.Message }
        reduce { state.copy(code = filtered, failure = failure) }
    }

    fun onSubmitTapped() = intent {
        val next = state.copy(now = clock.instant())
        if (!next.canSubmit) {
            reduce { next }
            return@intent
        }
        reduce { next.copy(isSubmitting = true, failure = null) }
        claim(next.festivalId, next.code)
    }

    fun onBackTapped() = intent {
        cancelRunning()
        reduce { state.copy(isSubmitting = false) }
        postSideEffect(BearerClaimSideEffect.CloseRequested)
    }

    private suspend fun claim(festivalId: String, code: String) = subIntent {
        val job =
            intent {
                val claim =
                    try {
                        bearerUseCase.claim(festivalId = festivalId, code = code)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: BearerError) {
                        claimFailed(error)
                        return@intent
                    } catch (_: Exception) {
                        claimFailed(BearerError.Unavailable())
                        return@intent
                    }
                claimSucceeded(claim)
            }
        claimJob.getAndSet(job)?.cancel()
    }

    private suspend fun startLockCountdown() = subIntent {
        val job =
            intent {
                while (true) {
                    delay(1.seconds)
                    lockTicked()
                }
            }
        lockCountdownJob.getAndSet(job)?.cancel()
    }

    /** 테스트가 서버 응답 없이 실패를 바로 넣을 때도 쓴다(iOS `.inner(.claimFailed)`). */
    internal fun onClaimFailed(error: BearerError) = intent { claimFailed(error) }

    private suspend fun claimSucceeded(claim: BearerClaim) = subIntent {
        // 지금 도는 요청은 이 작업 자신이라 취소하지 않고 비우기만 한다.
        claimJob.set(null)
        lockCountdownJob.getAndSet(null)?.cancel()
        reduce { state.copy(isSubmitting = false, consecutiveFailures = 0) }
        postSideEffect(BearerClaimSideEffect.Claimed(festivalId = state.festivalId, claim = claim))
    }

    private suspend fun claimFailed(error: BearerError) = subIntent {
        when (error) {
            is BearerError.InvalidCode -> {
                val failures = state.consecutiveFailures + 1
                val fallback = maxOf(0, MAX_ATTEMPTS - failures)
                reduce {
                    state.copy(
                        isSubmitting = false,
                        consecutiveFailures = failures,
                        failure = BearerClaimFailure.Invalid(remainingAttempts = error.remainingAttempts ?: fallback),
                    )
                }
            }

            is BearerError.CodeLocked -> {
                val now = clock.instant()
                reduce {
                    state.copy(
                        isSubmitting = false,
                        now = now,
                        lockedUntil = now.plusSeconds(error.retryAfter.toLong()),
                        code = "",
                        failure = BearerClaimFailure.Locked,
                    )
                }
                startLockCountdown()
            }

            else -> reduce { state.copy(isSubmitting = false, failure = BearerClaimFailure.Message(message(error))) }
        }
    }

    private suspend fun lockTicked() = subIntent {
        reduce { state.copy(now = clock.instant()) }
        if (state.isLocked) return@subIntent
        reduce { state.copy(lockedUntil = null, consecutiveFailures = 0, failure = null) }
        lockCountdownJob.getAndSet(null)?.cancel()
    }

    private fun cancelRunning() {
        claimJob.getAndSet(null)?.cancel()
        lockCountdownJob.getAndSet(null)?.cancel()
    }

    internal companion object {
        /** 서버 코드에 쓰는 글자(I · O · 0 · 1 제외) */
        const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** 서버가 남은 횟수를 주지 않을 때 쓰는 10분 안 최대 시도 횟수 */
        const val MAX_ATTEMPTS = 3

        fun message(error: BearerError): String = when (error) {
            is BearerError.InvalidRequest -> "코드 6자리를 다시 확인해 주세요"
            is BearerError.BearerLimit -> "이 페스티벌에서 이미 다른 깃발을 맡고 있어요"
            is BearerError.CoBearerFull -> "이 깃발의 공동 기수 자리가 다 찼어요"
            is BearerError.NotAuthenticated -> "로그인한 뒤에 입력해 주세요"
            is BearerError.TermsRequired -> "새 약관에 동의한 뒤에 입력해 주세요"
            is BearerError.NotFound -> "아직 공개되지 않은 페스티벌이에요"
            is BearerError.Offline -> "인터넷에 연결되지 않았어요. 연결 후 다시 눌러 주세요"
            else -> "코드를 확인하지 못했어요. 잠시 후 다시 시도해 주세요"
        }
    }
}
