package io.festicore.core.location.client

import io.festicore.core.location.entity.LocationCoordinate
import io.festicore.core.location.entity.WalkingDuration
import io.festicore.core.network.FestiCoreJson
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Google Routes API v2 `computeRoutes`(REST, SDK 없음)로 소요 시간을 구한다(사용자 결정, 설계 R6 쟁점 F4).
 * 근거(공식 문서 developers.google.com/maps/documentation/routes):
 * - `POST https://routes.googleapis.com/directions/v2:computeRoutes`, 본문 `origin`/`destination.location.latLng` · `travelMode`(WALK · DRIVE)
 * - 헤더 `X-Goog-Api-Key`, 응답 필드 마스크 `X-Goog-FieldMask: routes.duration,routes.distanceMeters`(필수)
 * - Android 앱 제한 키는 `X-Android-Package` · `X-Android-Cert`(SHA-1 지문 16진 40자, docs.cloud.google.com/docs/authentication/api-keys)
 * - 응답 `duration`은 초 단위 Duration 문자열(`"123s"`)
 * 현재 위치 좌표가 Google로 전송된다. 좌표 · 응답은 로그 · 분석에 넣지 않는다.
 * [apiKey]가 비었거나 호출 · 해석이 실패하면 null이고, 호출자가 대체 경로를 쓴다.
 */
internal class GoogleRoutesClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val appIdentity: () -> AppIdentity?,
) {
    /** API 제한 키 검사용 앱 정보. 서명 지문을 읽지 못하면 헤더 없이 보낸다(제한 없는 키면 그대로 동작). */
    data class AppIdentity(
        val packageName: String,
        val certSha1: String,
    )

    enum class TravelMode { WALK, DRIVE }

    suspend fun duration(
        origin: LocationCoordinate,
        destination: LocationCoordinate,
        mode: TravelMode,
    ): WalkingDuration? {
        if (apiKey.isBlank() || !origin.isValid || !destination.isValid) return null
        val body = FestiCoreJson.encodeToString(
            ComputeRoutesRequest.serializer(),
            ComputeRoutesRequest(waypoint(origin), waypoint(destination), mode.name),
        )
        return try {
            val response =
                httpClient.post(ENDPOINT) {
                    headers {
                        append("X-Goog-Api-Key", apiKey)
                        append("X-Goog-FieldMask", FIELD_MASK)
                        appIdentity()?.let {
                            append("X-Android-Package", it.packageName)
                            append("X-Android-Cert", it.certSha1)
                        }
                    }
                    setBody(TextContent(body, ContentType.Application.Json))
                }
            if (!response.status.isSuccess()) return null
            parseDuration(response.bodyAsText())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val FIELD_MASK = "routes.duration,routes.distanceMeters"

        /** 첫 경로의 `duration`(`"123s"` · `"12.5s"`). 경로가 없거나 형식이 다르면 null이다. */
        fun parseDuration(json: String): WalkingDuration? {
            val response = runCatching { FestiCoreJson.decodeFromString(ComputeRoutesResponse.serializer(), json) }.getOrNull()
            val text = response?.routes?.firstOrNull()?.duration ?: return null
            val seconds = text.removeSuffix("s").takeIf { text.endsWith("s") }?.toDoubleOrNull() ?: return null
            return seconds.takeIf { it.isFinite() && it >= 0 }?.let(::WalkingDuration)
        }

        private fun waypoint(coordinate: LocationCoordinate) = Waypoint(Location(LatLng(coordinate.latitude, coordinate.longitude)))
    }

    @Serializable
    private data class ComputeRoutesRequest(
        val origin: Waypoint,
        val destination: Waypoint,
        val travelMode: String,
    )

    @Serializable
    private data class Waypoint(val location: Location)

    @Serializable
    private data class Location(@SerialName("latLng") val latLng: LatLng)

    @Serializable
    private data class LatLng(
        val latitude: Double,
        val longitude: Double,
    )

    @Serializable
    private data class ComputeRoutesResponse(val routes: List<Route>? = null)

    @Serializable
    private data class Route(
        val duration: String? = null,
        val distanceMeters: Int? = null,
    )
}
