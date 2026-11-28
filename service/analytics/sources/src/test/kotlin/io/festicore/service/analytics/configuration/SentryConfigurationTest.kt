package io.festicore.service.analytics.configuration

import android.content.Context
import io.festicore.core.logger.FestiCoreLogSink
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroidOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SentryConfigurationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val validDsn = "https://publickey@example.com/123"

    @After
    fun tearDown() {
        Sentry.close()
        SentryConfiguration.resetForTesting()
    }

    @Test
    fun `유효한 설정과 활성 SDK를 함께 요구하고 같은 sink를 재사용한다`() {
        val sinks = mutableListOf<FestiCoreLogSink>()
        repeat(2) {
            SentryConfiguration.configure(
                context, validDsn, true, true, "1", 1,
                sdkEnabled = { true }, registerSink = { sinks += it }, initialize = { _, _ -> },
            )
        }
        assertTrue(SentryConfiguration.isEnabled { true })
        assertFalse(SentryConfiguration.isEnabled { false })
        assertSame(sinks[0], sinks[1])
        SentryConfiguration.configure(
            context, "", true, true, "1", 1,
            sdkEnabled = { true }, registerSink = { sinks += it }, initialize = { _, _ -> },
        )
        assertFalse(SentryConfiguration.isEnabled { true })
        assertEquals(2, sinks.size)
    }

    @Test
    fun `초기화 실패 또는 비활성 SDK는 sink를 등록하지 않는다`() {
        val sinks = mutableListOf<FestiCoreLogSink>()
        SentryConfiguration.configure(
            context, validDsn, true, true, "1", 1,
            sdkEnabled = { false }, registerSink = { sinks += it }, initialize = { _, _ -> },
        )
        assertFalse(SentryConfiguration.isEnabled { true })
        runCatching {
            SentryConfiguration.configure(
                context, validDsn, true, true, "1", 1,
                sdkEnabled = { true }, registerSink = { sinks += it }, initialize = { _, _ -> error("test init failure") },
            )
        }
        assertFalse(SentryConfiguration.isEnabled { true })
        assertTrue(sinks.isEmpty())
    }

    @Test
    fun `DSN은 HTTPS와 공개 키 호스트 숫자 프로젝트를 요구한다`() {
        assertEquals(validDsn, SentryConfiguration.dsn(" $validDsn "))
        invalidDsns.forEach { assertNull(SentryConfiguration.dsn(it)) }
    }

    @Test
    fun `누락되거나 잘못된 DSN이면 SDK를 시작하지 않는다`() {
        var initialized = false
        invalidDsns.forEach { raw ->
            SentryConfiguration.configure(context, raw, true, true, "1.0", 1, sdkEnabled = { false }, registerSink = {}) { _, _ ->
                initialized =
                    true
            }
        }
        assertFalse(initialized)
    }

    @Test
    fun `디버그와 Stage는 stage 운영 릴리스만 production이다`() {
        assertEquals("stage", SentryConfiguration.environment(isDebug = true, isStage = true))
        assertEquals("stage", SentryConfiguration.environment(isDebug = true, isStage = false))
        assertEquals("stage", SentryConfiguration.environment(isDebug = false, isStage = true))
        assertEquals("production", SentryConfiguration.environment(isDebug = false, isStage = false))
    }

    @Test
    fun `실제 초기화 콜백은 개인정보와 화면 수집 없이 iOS 성능 비율을 설정한다`() {
        val options = SentryAndroidOptions()
        SentryConfiguration.configure(context, validDsn, false, false, "1.2.3", 42, sdkEnabled = {
            false
        }, registerSink = {}) { appContext, configureOptions ->
            assertSame(context, appContext)
            configureOptions(options)
        }
        assertEquals(validDsn, options.dsn)
        assertEquals("production", options.environment)
        assertEquals("festicore-android@1.2.3+42", options.release)
        assertFalse(options.isDebug)
        assertFalse(options.isSendDefaultPii)
        assertEquals(0.2, options.tracesSampleRate!!, 0.0)
        assertFalse(options.isAttachScreenshot)
        assertFalse(options.isAttachViewHierarchy)
        assertEquals(0.0, options.sessionReplay.sessionSampleRate!!, 0.0)
        assertEquals(0.0, options.sessionReplay.onErrorSampleRate!!, 0.0)
    }

    @Test
    fun `철회 상태에서는 오류 전송 콜백이 이벤트를 버린다`() {
        FestiCoreAnalyticsConfiguration.configure(context, "")
        val options = SentryAndroidOptions()
        SentryConfiguration.configure(context, validDsn, true, true, "1.0", 1, sdkEnabled = {
            false
        }, registerSink = {}) { _, setup -> setup(options) }
        assertNull(options.beforeSend!!.execute(io.sentry.SentryEvent(), io.sentry.Hint()))
        assertEquals(io.sentry.SentryLevel.INFO, options.diagnosticLevel)
        assertFalse(options.isEnableUserInteractionBreadcrumbs)
        assertFalse(options.isEnableSystemEventBreadcrumbs)
    }

    private val invalidDsns = listOf(
        "",
        " \n ",
        "http://publickey@example.com/123",
        "https://example.com/123",
        "https://@example.com/123",
        "https://publickey:secret@example.com/123",
        "https://publickey@/123",
        "https://publickey@example.com/",
        "https://publickey@example.com/project",
        "https://publickey@example.com/123?query=1",
        "https://publickey@example.com/123#fragment",
        "https://publickey@example.com:443/123",
        "not a URI",
    )
}
