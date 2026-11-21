package io.festicore.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import java.util.UUID
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.orbitmvi.orbit.compose.collectAsState
import org.orbitmvi.orbit.compose.collectSideEffect

/**
 * Home 위에 그리는 첫 진입 로그인 덮개(전체 화면). 아래 메인 탭으로 터치가 새지 않게 막는다.
 * 덮개를 다시 띄울 때마다 새 흐름으로 시작하도록 Coordinator를 덮개 표시마다 따로 만든다.
 */
@Composable
fun LaunchLoginFlow(
    start: AuthRoute,
    onResult: (LaunchLoginResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flowKey = rememberSaveable { UUID.randomUUID().toString() }
    val coordinator: AuthCoordinator = koinViewModel(key = "launchLoginFlow-$flowKey") { parametersOf(start) }
    val state by coordinator.collectAsState()

    coordinator.collectSideEffect { sideEffect ->
        when (sideEffect) {
            is AuthCoordinatorSideEffect.Finished -> onResult(sideEffect.result)
        }
    }

    Box(modifier = modifier.fillMaxSize().pointerInput(Unit) { consumeAllPointerEvents() }) {
        NavDisplay(
            backStack = state.routes,
            onBack = coordinator::goBack,
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
                            onCompleted = { coordinator.onFinished(LaunchLoginResult.Completed) },
                            onReturnToLaunchLogin = coordinator::onReturnToLaunchLogin,
                        )
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
