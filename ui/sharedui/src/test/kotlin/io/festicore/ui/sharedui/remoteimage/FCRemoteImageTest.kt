package io.festicore.ui.sharedui.remoteimage

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.test.FakeImageLoaderEngine
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * iOS `FCRemoteImage.swift`의 상태 분기: 주소 없음 → 기본 모양, 불러오는 중 → `fillNormal` + 스켈레톤,
 * 성공 → 이미지, 실패 → 기본 모양(이미지는 그리지 않음).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FCRemoteImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `주소가 없으면 기본 모양만 그리고 불러오지 않는다`() {
        install(FakeImageLoaderEngine.Builder().default(ColorImage()).build())

        show(url = null)

        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_PLACEHOLDER_TAG).assertExists()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_LOADING_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    fun `불러오는 동안은 로딩 바탕을 그린다`() {
        install(FakeImageLoaderEngine.Builder().default(Interceptor { awaitCancellation() }).build())

        show(url = URL)

        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_LOADING_TAG).assertExists()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_PLACEHOLDER_TAG).assertDoesNotExist()
    }

    @Test
    fun `성공하면 이미지만 그린다`() {
        install(FakeImageLoaderEngine.Builder().intercept(URL, ColorImage()).build())

        show(url = URL)

        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_CONTENT_TAG).assertExists()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_LOADING_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_PLACEHOLDER_TAG).assertDoesNotExist()
    }

    @Test
    fun `실패하면 기본 모양을 그리고 이미지는 그리지 않는다`() {
        install(
            FakeImageLoaderEngine
                .Builder()
                .default(Interceptor { chain -> ErrorResult(null, chain.request, IOException("400")) })
                .build(),
        )

        show(url = URL)

        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_PLACEHOLDER_TAG).assertExists()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_CONTENT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(FC_REMOTE_IMAGE_LOADING_TAG).assertDoesNotExist()
    }

    private fun install(engine: FakeImageLoaderEngine) {
        val context = RuntimeEnvironment.getApplication()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
    }

    private fun show(url: String?) {
        composeRule.setContent {
            FCRemoteImage(url = url, modifier = Modifier.size(SIZE))
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val URL = "https://example.com/poster.webp"
        val SIZE = 40.dp
    }
}
