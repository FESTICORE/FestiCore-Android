package io.festicore.service.analytics.usecase

import io.festicore.service.analytics.event.AnalyticsButton
import io.festicore.service.analytics.event.AnalyticsEvent
import io.festicore.service.analytics.event.AnalyticsPackSyncResult
import io.festicore.service.analytics.event.AnalyticsProvider
import io.festicore.service.analytics.event.AnalyticsScreen
import io.festicore.service.analytics.event.AnalyticsTimetableAction
import io.festicore.service.analytics.event.FlagFollowData
import io.festicore.service.analytics.event.PackSyncData
import io.festicore.service.analytics.event.TimetableActionData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `AnalyticsUseCaseTests` 대응 + Mixpanel 호출 순서(가짜 sink). */
class AnalyticsUseCaseTest {
    private val base = baseSuperProperties(appVersion = "1.0.0", build = "7")
    private val client = RecordingMixpanelClient()
    private val useCase = AnalyticsUseCaseImpl({ client }, base)

    private val allEvents =
        listOf(
            AnalyticsEvent.SignUp(AnalyticsProvider.Apple) to "sign_up",
            AnalyticsEvent.ScreenView(AnalyticsScreen.Login) to "screen_view",
            AnalyticsEvent.UiAction(AnalyticsButton.FlagSort, AnalyticsScreen.FestivalFlag) to "ui_action",
            AnalyticsEvent.FlagFollow(FlagFollowData("flag", true)) to "flag_follow",
            AnalyticsEvent.TimetableAction(TimetableActionData(AnalyticsTimetableAction.SetOpen, "fst")) to "timetable_action",
            AnalyticsEvent.PackSync(PackSyncData("fst", AnalyticsPackSyncResult.Updated)) to "pack_sync",
        )

    @Test
    fun `이벤트 이름은 iOS와 같은 snake_case다`() {
        allEvents.forEach { (event, name) -> assertEquals(name, eventName(event)) }
    }

    @Test
    fun `screen_view는 referrer가 있을 때만 보낸다`() {
        assertEquals(mapOf("screen" to "festival_list"), eventProperties(AnalyticsEvent.ScreenView(AnalyticsScreen.FestivalList)))
        assertEquals(
            mapOf("screen" to "live_board", "referrer" to "festival_list"),
            eventProperties(AnalyticsEvent.ScreenView(AnalyticsScreen.LiveBoard, AnalyticsScreen.FestivalList)),
        )
    }

    @Test
    fun `이벤트 속성은 iOS와 같은 키와 값이다`() {
        assertEquals(mapOf("method" to "google"), eventProperties(AnalyticsEvent.SignUp(AnalyticsProvider.Google)))
        assertEquals(
            mapOf("action" to "profile_logout", "screen" to "profile"),
            eventProperties(AnalyticsEvent.UiAction(AnalyticsButton.ProfileLogout, AnalyticsScreen.Profile)),
        )
        assertEquals(
            mapOf("is_following" to false),
            eventProperties(AnalyticsEvent.FlagFollow(FlagFollowData("flag", false))),
        )
        assertEquals(
            mapOf("action" to "schedule_toggle"),
            eventProperties(AnalyticsEvent.TimetableAction(TimetableActionData(AnalyticsTimetableAction.ScheduleToggle, "fst"))),
        )
        assertEquals(
            mapOf("action" to "set_open"),
            eventProperties(AnalyticsEvent.TimetableAction(TimetableActionData(AnalyticsTimetableAction.SetOpen, "fst", "set"))),
        )
        assertEquals(
            mapOf("result" to "not_modified"),
            eventProperties(AnalyticsEvent.PackSync(PackSyncData("fst", AnalyticsPackSyncResult.NotModified))),
        )
    }

    @Test
    fun `화면 · 버튼 값은 겹치지 않는 snake_case다`() {
        val screens = AnalyticsScreen.entries.map { it.rawValue }
        val buttons = AnalyticsButton.entries.map { it.rawValue }
        assertEquals(screens.size, screens.toSet().size)
        assertEquals(buttons.size, buttons.toSet().size)
        assertTrue((screens + buttons).all { it == it.lowercase() && ' ' !in it })
        assertEquals(AnalyticsProvider.Google, AnalyticsProvider.fromRawValue("google"))
        assertEquals(null, AnalyticsProvider.fromRawValue("kakao"))
    }

    @Test
    fun `공통 슈퍼 프로퍼티는 플랫폼과 버전만 담는다`() {
        assertEquals(setOf("os_type", "app_version", "build"), base.keys)
        assertEquals("android", base["os_type"])
    }

    @Test
    fun `이벤트 속성에는 화면 · 버튼 값과 결과만 들어간다`() {
        val allowed = setOf("method", "screen", "referrer", "action", "is_following", "result")
        val withAll =
            allEvents.map { it.first } +
                AnalyticsEvent.ScreenView(AnalyticsScreen.LiveBoard, AnalyticsScreen.FestivalList) +
                AnalyticsEvent.TimetableAction(TimetableActionData(AnalyticsTimetableAction.SetOpen, "fst", "set"))
        withAll.forEach { assertTrue(allowed.containsAll(eventProperties(it).keys)) }
    }

    @Test
    fun `registerBaseProperties는 공통 프로퍼티를 등록한다`() {
        useCase.registerBaseProperties()

        assertEquals(listOf("registerSuperProperties" to base), client.calls)
    }

    @Test
    fun `identify는 사용자 ID를 identify 인자로만 보내고 로그인 프로퍼티를 등록한다`() {
        useCase.identify("user-1", "google")

        assertEquals(
            listOf(
                "identify" to "user-1",
                "people.set" to ("provider" to "google"),
                "registerSuperProperties" to mapOf("is_logged_in" to true, "login_provider" to "google"),
            ),
            client.calls,
        )
    }

    @Test
    fun `identify는 빈 사용자 ID를 무시하고 방법이 없으면 로그인 여부만 등록한다`() {
        useCase.identify("", "google")
        assertTrue(client.calls.isEmpty())

        useCase.identify("user-1", null)
        assertEquals(listOf("identify" to "user-1", "registerSuperProperties" to mapOf("is_logged_in" to true)), client.calls)
    }

    @Test
    fun `track은 이름과 속성을 그대로 보낸다`() {
        useCase.track(AnalyticsEvent.UiAction(AnalyticsButton.LoginGoogle, AnalyticsScreen.Login))

        assertEquals(listOf("track" to ("ui_action" to mapOf("action" to "login_google", "screen" to "login"))), client.calls)
    }

    @Test
    fun `reset은 계정을 분리하고 공통 프로퍼티만 다시 등록한다`() {
        useCase.reset()

        assertEquals(listOf("reset" to null, "registerSuperProperties" to base), client.calls)
    }

    @Test
    fun `미리보기 구현은 아무것도 하지 않는다`() {
        PreviewAnalyticsUseCase.registerBaseProperties()
        PreviewAnalyticsUseCase.identify("user", null)
        PreviewAnalyticsUseCase.track(AnalyticsEvent.ScreenView(AnalyticsScreen.Profile))
        PreviewAnalyticsUseCase.reset()
    }
}
