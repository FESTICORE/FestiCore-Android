package io.festicore.core.location.client

import android.content.ContextWrapper
import io.festicore.core.location.entity.LocationClientError
import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingPlace
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google Routes API `computeRoutes` 호출 · 해석 · 대체 경로(사용자 결정, 설계 쟁점 F4). */
class GoogleRoutesClientTest {
    private val origin = LocationCoordinate(37.3900, 127.1200)
    private val station = LocationCoordinate(37.3836, 127.1268)
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: (String) -> String = {
            """{"routes":[{"duration":"${if ("DRIVE" in it) 240 else 610}s","distanceMeters":900}]}"""
        },
    ) = HttpClient(
        MockEngine { request ->
            requests += request
            val sent = (request.body as TextContent).text
            respond(body(sent), status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private fun routes(http: HttpClient, key: String = "test-key") = GoogleRoutesClient(http, key) {
        GoogleRoutesClient.AppIdentity("io.festiCore.co.stage", "0873D391E987982FBBD30873D391E987982FBBD3")
    }

    @Test
    fun `응답 duration은 초 문자열로 읽고 경로가 없거나 형식이 다르면 null이다`() {
        assertEquals(123.0, GoogleRoutesClient.parseDuration("""{"routes":[{"duration":"123s","distanceMeters":10}]}""")?.seconds)
        assertEquals(12.5, GoogleRoutesClient.parseDuration("""{"routes":[{"duration":"12.5s"}]}""")?.seconds)
        assertNull(GoogleRoutesClient.parseDuration("""{}"""))
        assertNull(GoogleRoutesClient.parseDuration("""{"routes":[]}"""))
        assertNull(GoogleRoutesClient.parseDuration("""{"routes":[{"duration":"5분"}]}"""))
        assertNull(GoogleRoutesClient.parseDuration("""{"routes":[{"duration":"-3s"}]}"""))
        assertNull(GoogleRoutesClient.parseDuration("깨진 응답"))
    }

    @Test
    fun `computeRoutes에 좌표 · 이동 수단 · 키 · 필드 마스크 · 앱 제한 헤더를 보낸다`() = runTest {
        val duration = routes(client()).duration(origin, station, GoogleRoutesClient.TravelMode.WALK)
        assertEquals(11, duration?.minutes)
        val request = requests.single()
        assertEquals("https://routes.googleapis.com/directions/v2:computeRoutes", request.url.toString())
        assertEquals("POST", request.method.value)
        assertEquals("test-key", request.headers["X-Goog-Api-Key"])
        assertEquals("routes.duration,routes.distanceMeters", request.headers["X-Goog-FieldMask"])
        assertEquals("io.festiCore.co.stage", request.headers["X-Android-Package"])
        assertEquals("0873D391E987982FBBD30873D391E987982FBBD3", request.headers["X-Android-Cert"])
        assertEquals(
            """{"origin":{"location":{"latLng":{"latitude":37.39,"longitude":127.12}}},""" +
                """"destination":{"location":{"latLng":{"latitude":37.3836,"longitude":127.1268}}},"travelMode":"WALK"}""",
            (request.body as TextContent).text,
        )
    }

    @Test
    fun `키가 없으면 요청하지 않는다`() = runTest {
        assertNull(routes(client(), key = "").duration(origin, station, GoogleRoutesClient.TravelMode.DRIVE))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `오류 응답 · 네트워크 실패면 null이다`() = runTest {
        assertNull(routes(client(HttpStatusCode.Forbidden)).duration(origin, station, GoogleRoutesClient.TravelMode.WALK))
        val failing = HttpClient(MockEngine { throw IOException("오프라인") })
        assertNull(routes(failing).duration(origin, station, GoogleRoutesClient.TravelMode.WALK))
    }

    @Test
    fun `지도 클라이언트는 Routes 도보 · 자동차 시간을 쓰고 실패하면 도보만 직선거리로 대체한다`() = runTest {
        val place = WalkingPlace("서현역", coordinate = station)
        val maps = IntentMapsClient(ContextWrapper(null), { null }, routes(client()))
        assertEquals(11, maps.walkingDuration(origin, place).minutes)
        assertEquals(4, maps.drivingDuration(origin, place).minutes)

        val failed = IntentMapsClient(ContextWrapper(null), { null }, routes(client(HttpStatusCode.InternalServerError)))
        assertEquals(WalkingEstimate.duration(origin, station), failed.walkingDuration(origin, place))
        assertEquals(LocationClientError.RouteUnavailable, runCatching { failed.drivingDuration(origin, place) }.exceptionOrNull())
    }
}
