package io.festicore.app.navigation.account

import io.festicore.app.navigation.main.AppCoordinator
import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.domain.auth.auth.AuthRepository
import io.festicore.domain.auth.entity.AuthDomainError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 마이 계정 화면에서 확인을 마친 계정 작업. iOS `AccountCoordinator.Operation`. */
enum class AccountOperation { SignOut, Withdraw }

/** 계정 작업 결과. iOS `AccountCoordinator`의 `finished` 결과와 `delegate(.sessionExpired)`를 합쳤다. */
enum class AccountOperationResult {
    /** 서버 요청이 성공했고 로컬 세션을 지웠다. 로그아웃 401도 여기다(설계 쟁점 M2). */
    Succeeded,

    /** 작업 중 로그인을 잃었다(탈퇴 401 · 그 사이 세션이 지워짐). 결과를 알 수 없어 계정을 비운다. */
    SessionLost,

    /** 그 외 실패. 세션은 그대로다. */
    Failed,
}

/**
 * iOS `AccountCoordinator.runOperation`(`AcC:155-194`). 기기 사용자 연결 해제 → 로그아웃 · 탈퇴 요청 순서로 부른다.
 * 기기 연결 해제 · 복원은 푸시 · 기기 등록(P0) 전까지 아무 일도 하지 않는다(설계 쟁점 M3). 해제가 실패해도 계정 요청은 계속한다(`ADS:30-41`).
 * 세대 · 화면 상태는 [AppCoordinator]가 정한다. 이 클래스는 작업 하나를 끝까지 실행하고 결과만 돌려준다.
 * Live Activity 종료는 Android 진행 중 알림 단계에서 진행 중 알림 취소로 잇는다.
 * 탈퇴가 성공하면 시작할 때의 사용자([currentUserId]) 기기 기록을 [removeWithdrawnUserData]로 지운다(기수 상태 대기열, iOS에 없음).
 */
class AccountCoordinator(
    private val authRepository: AuthRepository,
    private val unbindDevice: suspend () -> Unit = {},
    private val linkDevice: suspend () -> Unit = {},
    private val currentUserId: () -> String? = { null },
    private val removeWithdrawnUserData: suspend (userId: String) -> Unit = {},
) {
    /** 작업을 실행한다. 코루틴이 취소되면 다음 요청을 보내지 않고 취소를 그대로 던진다. */
    suspend fun run(operation: AccountOperation): AccountOperationResult {
        val userId = currentUserId()
        try {
            unbindDevice()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // iOS와 같이 바인딩은 다음 실행에서 복구한다. 해제 실패로 계정 요청을 막지 않는다.
            FestiCoreLogger.error("계정 작업 중 기기 연결 해제 실패: ${e::class.simpleName}", FestiCoreLogCategory.APP)
        }
        currentCoroutineContext().ensureActive()
        try {
            when (operation) {
                AccountOperation.SignOut -> authRepository.signOut()

                AccountOperation.Withdraw -> {
                    authRepository.withdraw()
                    userId?.let { removeUserData(it) }
                }
            }
            return AccountOperationResult.Succeeded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is AuthDomainError.NotAuthenticated || !isLoggedIn()) return AccountOperationResult.SessionLost
            // 인증 요청은 실패했지만 계정이 유지되면 해제했던 기기 연결을 복원한다.
            restoreDevice()
            return AccountOperationResult.Failed
        }
    }

    /**
     * 로컬 세션이 남아 있는지. iOS `isLoggedIn`은 키체인 읽기(`recoveredCredential() != nil`)이고 읽지 못하면 nil이라 로그인 없음으로 본다.
     * 같게 읽기 실패는 로그인 없음으로 본다.
     */
    private suspend fun isLoggedIn(): Boolean = try {
        authRepository.isLoggedIn()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FestiCoreLogger.error("계정 작업 실패 뒤 로그인 상태를 확인할 수 없음: ${e::class.simpleName}", FestiCoreLogCategory.AUTH)
        false
    }

    private suspend fun removeUserData(userId: String) {
        try {
            removeWithdrawnUserData(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 탈퇴는 끝났다. 남은 기록은 만료 규칙(하루)으로 사라진다.
            FestiCoreLogger.error("탈퇴한 사용자 기기 기록 삭제 실패: ${e::class.simpleName}", FestiCoreLogCategory.APP)
        }
    }

    private suspend fun restoreDevice() {
        try {
            linkDevice()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FestiCoreLogger.error("계정 기기 연결 실패: ${e::class.simpleName}", FestiCoreLogCategory.APP)
        }
    }

    companion object {
        /** iOS `AuthFlowState.Message.signOutFailed`. */
        const val SIGN_OUT_FAILED = "로그아웃에 실패했습니다. 다시 시도해 주세요."

        /** iOS `AuthFlowState.Message.withdrawFailed`. */
        const val WITHDRAW_FAILED = "회원 탈퇴에 실패했습니다. 다시 시도해 주세요."

        /** iOS `AuthFlowState.Message.sessionExpiredDuringWithdraw`. */
        const val SESSION_EXPIRED_DURING_WITHDRAW = "로그인이 만료되어 회원 탈퇴 결과를 확인할 수 없어요. 다시 로그인해 계정 상태를 확인해 주세요."
    }
}
