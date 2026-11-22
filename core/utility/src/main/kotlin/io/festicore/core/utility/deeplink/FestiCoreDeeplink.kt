package io.festicore.core.utility.deeplink

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

/** 앱이 여는 화면 경로. URL 인텐트 · 푸시 data · 알림함 항목이 모두 이 값으로 모인다(iOS `FestiCoreDeeplink`). */
sealed interface FestiCoreDeeplink {
    /** 축제 목록(S00). */
    data object FestivalList : FestiCoreDeeplink

    /** 축제 라이브 보드(S01). */
    data class Festival(val id: String) : FestiCoreDeeplink

    /** 깃발 상세(S05). */
    data class Flag(val id: String) : FestiCoreDeeplink

    /** 축제 타임테이블의 공연 상세(S03). 공연 10분 전 알림이 연다. */
    data class Set(
        val festivalId: String,
        val setId: String,
    ) : FestiCoreDeeplink

    /** 크루 초대(`festicore://crew/<초대 코드>`). 크루 참여 확인(S08)을 연다. */
    data class CrewInvite(val code: String) : FestiCoreDeeplink

    /** 대기 저장용 URL 문자열. [FestiCoreDeeplinkParser.parse]로 되돌린다. */
    val encoded: String
        get() =
            when (this) {
                FestivalList -> "$SCHEME://festival"
                is Festival -> "$SCHEME://festival/${escape(id)}"
                is Flag -> "$SCHEME://flag/${escape(id)}"
                is Set -> "$SCHEME://festival/${escape(festivalId)}/set/${escape(setId)}"
                is CrewInvite -> "$SCHEME://crew/${escape(code)}"
            }

    companion object {
        const val SCHEME = "festicore"
    }
}

private fun escape(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

/** iOS `FestiCoreDeeplinkParser`와 같은 규칙. 형식이 맞지 않으면 `null`이다. */
object FestiCoreDeeplinkParser {
    /** 푸시 data에서 딥링크를 읽는다. `deeplink` URL이 우선이고, 없으면 `route` · `flagId` · `festivalId` 순으로 찾는다. */
    fun parse(pushPayload: Map<String, String>): FestiCoreDeeplink? {
        pushPayload["deeplink"]?.let(::parse)?.let { return it }
        pushPayload["route"]?.let(::parseRoute)?.let { return it }
        validId(pushPayload["flagId"])?.let { return FestiCoreDeeplink.Flag(it) }
        validId(pushPayload["festivalId"])?.let { return FestiCoreDeeplink.Festival(it) }
        return null
    }

    /**
     * `festicore://festival`, `festicore://festival/<id>`, `festicore://festival/<id>/set/<setId>`, `festicore://flag/<id>`,
     * `festicore://crew/<초대 코드>`. 사용자 · 비밀번호 · 포트 · query · fragment가 있으면 받지 않는다.
     */
    fun parse(urlString: String): FestiCoreDeeplink? {
        val uri =
            try {
                URI(urlString)
            } catch (_: URISyntaxException) {
                return null
            }
        if (!uri.scheme.equals(FestiCoreDeeplink.SCHEME, ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) return null
        val host = uri.host?.lowercase() ?: return null
        val segments = pathSegments(uri.rawPath.orEmpty()) ?: return null
        return route(resource = host, segments = segments)
    }

    /** 서버 알림의 `route`(`/festivals/<id>/live`, `/flags/<id>` 등). `/crews/<id>`처럼 축제를 모르는 경로는 처리하지 않는다. */
    fun parseRoute(route: String): FestiCoreDeeplink? {
        if (!route.startsWith("/")) return null
        val segments = pathSegments(route) ?: return null
        val resource = segments.firstOrNull() ?: return null
        val rest = segments.drop(1)
        return when (resource) {
            "festivals" -> rest.firstOrNull()?.let { FestiCoreDeeplink.Festival(it) } ?: FestiCoreDeeplink.FestivalList
            "flags" -> rest.firstOrNull()?.let { FestiCoreDeeplink.Flag(it) }
            else -> null
        }
    }

    private fun route(resource: String, segments: List<String>): FestiCoreDeeplink? = when (resource) {
        "festival" -> festivalRoute(segments)
        "flag" -> if (segments.size == 1) FestiCoreDeeplink.Flag(segments[0]) else null
        "crew" -> if (segments.size == 1) inviteCode(segments[0])?.let { FestiCoreDeeplink.CrewInvite(it) } else null
        else -> null
    }

    private fun festivalRoute(segments: List<String>): FestiCoreDeeplink? = when {
        segments.isEmpty() -> FestiCoreDeeplink.FestivalList
        segments.size == 1 -> FestiCoreDeeplink.Festival(segments[0])
        segments.size == 3 && segments[1] == "set" -> FestiCoreDeeplink.Set(festivalId = segments[0], setId = segments[2])
        else -> null
    }

    /** 초대 코드는 헷갈리는 글자(I · O · 0 · 1)를 뺀 영숫자 6자리다. 대문자로 맞춘다. */
    private fun inviteCode(value: String): String? {
        val code = value.uppercase()
        return code.takeIf { it.length == 6 && it.all { char -> char in INVITE_CODE_CHARACTERS } }
    }

    /** 빈 경로는 [], 각 조각은 퍼센트 디코딩 후 서버 ID 형식(ASCII 영숫자 · `-` · `_`)만 받는다. */
    private fun pathSegments(path: String): List<String>? {
        if (path.isEmpty()) return emptyList()
        if (!path.startsWith("/")) return null
        return path.substring(1).split("/").map { raw -> validId(decode(raw)) ?: return null }
    }

    private fun decode(raw: String): String? {
        // URLDecoder는 `+`를 공백으로 바꾼다. 경로 조각에서 `+`는 그대로 두어 ID 검사에서 걸러지게 한다.
        if ('+' in raw) return raw
        return try {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun validId(value: String?): String? =
        value?.takeIf { it.isNotEmpty() && it.all { char -> char.isAsciiLetterOrDigit() || char == '-' || char == '_' } }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private const val INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
}
