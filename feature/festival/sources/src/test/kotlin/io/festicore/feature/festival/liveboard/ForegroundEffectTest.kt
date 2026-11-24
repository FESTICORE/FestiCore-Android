package io.festicore.feature.festival.liveboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 탭을 바꿔 화면이 빠져도 ViewModel이 남아 있는 동안은 포그라운드 변화를 받는다(이슈 #200). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ForegroundEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    class ProbeViewModel : ViewModel()

    @Test
    fun `화면이 빠져도 ViewModel이 남아 있으면 배경 전환을 알리고 정리되면 멈춘다`() {
        val owner = TestOwner()
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[ProbeViewModel::class.java]
        val events = mutableListOf<Boolean>()
        var isShown by mutableStateOf(true)
        composeRule.setContent {
            if (isShown) ForegroundEffect(owner, viewModel) { events += it }
        }

        // 다른 탭으로 가서 화면이 빠졌다.
        composeRule.runOnIdle { isShown = false }
        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        assertEquals(listOf(false, true), events)

        // 다시 보여도 관찰을 두 번 붙이지 않는다.
        composeRule.runOnIdle { isShown = true }
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        assertEquals(listOf(false, true, false), events)

        // 스택에서 빠져 ViewModel이 정리되면 더 받지 않는다.
        composeRule.runOnIdle { isShown = false }
        composeRule.runOnIdle {
            store.clear()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        assertEquals(listOf(false, true, false), events)
    }
}
