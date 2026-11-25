package io.festicore.feature.auth.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.festicore.feature.auth.AuthCoordinator
import io.festicore.feature.auth.AuthCoordinatorSideEffect
import io.festicore.feature.auth.AuthRoute
import io.festicore.feature.auth.AuthTransitions
import io.festicore.feature.auth.LaunchLoginResult
import io.festicore.feature.auth.signup.LegalDocument
import io.festicore.feature.auth.signup.SignUpScreen
import io.festicore.feature.web.WebPage
import java.util.UUID
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Home 위에 그리는 첫 진입 로그인 덮개(전체 화면). 아래 메인 탭으로 터치가 새지 않게 막는다.
 * 덮개를 다시 띄울 때마다 새 흐름으로 시작하도록 Coordinator를 덮개 표시마다 따로 만든다.
 * [onTopChanged]로 맨 위 화면을 상위에 알린다. 상위는 가입 화면이 보이는지로 재확인 · 세션 만료 처리를 정한다.
 * 약관 상세는 가입 정보 위에 쌓인 화면이라 알리지 않는다(iOS에서도 가입 정보 표시 중이다).
 * [legalDocument]는 약관 상세 화면이다. feature끼리 sources를 보지 않아 app이 `feature/web` 화면을 넘긴다.
 * 스택 push · pop은 [AuthTransitions]로 오른쪽에서 밀어 넣고 오른쪽으로 뺀다(iOS `.move(edge: .trailing)`, 0.3초 easeInOut).
 * 첫 진입 흐름의 가입 정보는 로그인 화면 위에 쌓여 있어 시스템 뒤로 · predictive back을 `NavDisplay`가 pop으로 받는다.
 * [signUpProfile]은 재확인 가입 덮개의 최신 프로필이다. 스택의 route는 그대로 두고(초안 유지) 같은 사용자 · 세션의 가입 화면에 반영한다.
 */
@Composable
fun LaunchLoginFlow(
    start: AuthRoute,
    onResult: (LaunchLoginResult) -> Unit,
    onTopChanged: (AuthRoute) -> Unit,
    legalDocument: @Composable (page: WebPage, onBackToRoot: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    signUpProfile: AuthRoute.SignUp? = null,
) {
    val flowKey = rememberSaveable { UUID.randomUUID().toString() }
    val coordinator: AuthCoordinator = koinViewModel(key = "launchLoginFlow-$flowKey") { parametersOf(start) }
    val state by coordinator.collectAsState()
    val top = state.routes.lastOrNull { it !is AuthRoute.LegalDocument } as? AuthRoute

    LaunchedEffect(top) { top?.let(onTopChanged) }

    coordinator.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is AuthCoordinatorSideEffect.Finished -> onResult(sideEffect.result)
        }
    }

    Box(modifier = modifier.fillMaxSize().pointerInput(Unit) { consumeAllPointerEvents() }) {
        NavDisplay(
            backStack = state.routes,
            onBack = coordinator::goBack,
            transitionSpec = { AuthTransitions.push() },
            popTransitionSpec = { AuthTransitions.pop() },
            predictivePopTransitionSpec = { AuthTransitions.pop() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<AuthRoute.LaunchLogin> {
                        LaunchLoginScreen(
                            onResult = coordinator::onFinished,
                            onSignUpRequired = coordinator::onSignUpRequired,
                        )
                    }
                    entry<AuthRoute.SignUp> { route ->
                        SignUpScreen(
                            route = route,
                            onCompleted = { profile ->
                                coordinator.onFinished(
                                    LaunchLoginResult.Completed(
                                        userId = route.userId,
                                        sessionId = route.sessionId,
                                        profile = profile,
                                        didCompleteSignUp = route.isNewUser,
                                    ),
                                )
                            },
                            onClosed = coordinator::onSignUpClosed,
                            onLegalDocumentRequested = coordinator::onLegalDocumentRequested,
                            // 재확인 가입 덮개는 스택의 첫 화면이라 NavDisplay가 뒤로를 받지 않는다. 가입 화면이 직접 닫기를 알린다.
                            handlesSystemBack = start is AuthRoute.SignUp,
                            profile =
                                signUpProfile?.takeIf { it.userId == route.userId && it.sessionId == route.sessionId } ?: route,
                        )
                    }
                    entry<AuthRoute.LegalDocument> { route ->
                        legalDocument(route.page, coordinator::goBack)
                    }
                },
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.consumeAllPointerEvents() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
