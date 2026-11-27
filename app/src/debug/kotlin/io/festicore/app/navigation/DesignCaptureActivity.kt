@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.festicore.app.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.tooling.ComposableInvoker
import io.festicore.ui.designkit.theme.FestiCoreTheme

/** 기존 무인자 Preview를 실제 기기의 앱 테마·edge-to-edge로 렌더한다. release에는 포함되지 않는다. */
class DesignCaptureActivity : ComponentActivity() {
    @OptIn(InternalComposeApi::class, ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preview = intent.getStringExtra("preview") ?: return finish()
        val className = preview.substringBeforeLast('.')
        val methodName = preview.substringAfterLast('.')
        // 디버그 도구도 앱의 순수 UI 프리뷰로 호출 범위를 한정한다.
        if (!className.startsWith("io.festicore.feature.") || !methodName.endsWith("Preview")) return finish()
        enableEdgeToEdge()
        setContent {
            FestiCoreTheme {
                ComposableInvoker.invokeComposable(className, methodName, currentComposer)
            }
        }
    }
}
