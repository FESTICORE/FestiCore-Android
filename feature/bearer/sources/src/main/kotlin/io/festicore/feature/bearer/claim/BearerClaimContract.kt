package io.festicore.feature.bearer.claim

import io.festicore.domain.bearer.entity.BearerClaim
import java.time.Duration
import java.time.Instant

/**
 * iOS `BearerClaimFeature.State`.
 *
 * @param consecutiveFailures 이 화면에서 연달아 틀린 횟수. 서버가 남은 횟수를 주지 않을 때 쓴다.
 * @param lockedUntil 코드 입력 잠금이 풀리는 시각. 잠기지 않았으면 null이다.
 * @param now 잠금 남은 시간을 셀 기준 시각. 보낼 때 · 잠길 때 · 1초마다 갱신한다.
 */
data class BearerClaimState(
    val festivalId: String,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val failure: BearerClaimFailure? = null,
    val consecutiveFailures: Int = 0,
    val lockedUntil: Instant? = null,
    val now: Instant = Instant.EPOCH,
) {
    val isLocked: Boolean get() = lockedUntil?.isAfter(now) == true

    val canSubmit: Boolean get() = code.length == CODE_LENGTH && !isSubmitting && !isLocked

    /** 잠금이 풀리기까지 남은 초(올림). */
    val lockRemainingSeconds: Int
        get() {
            val until = lockedUntil ?: return 0
            val millis = Duration.between(now, until).toMillis()
            return if (millis <= 0) 0 else ((millis + 999) / 1000).toInt()
        }

    companion object {
        const val CODE_LENGTH = 6
    }
}

/** iOS `BearerClaimFeature.Failure`. */
sealed interface BearerClaimFailure {
    data class Invalid(val remainingAttempts: Int) : BearerClaimFailure

    data object Locked : BearerClaimFailure

    data class Message(val text: String) : BearerClaimFailure
}

/** iOS `BearerClaimDelegate`. */
sealed interface BearerClaimSideEffect {
    /** 코드가 맞아 기수가 됐다. 부모는 깃발 프로필 화면을 연다. */
    data class Claimed(
        val festivalId: String,
        val claim: BearerClaim,
    ) : BearerClaimSideEffect

    data object CloseRequested : BearerClaimSideEffect
}
