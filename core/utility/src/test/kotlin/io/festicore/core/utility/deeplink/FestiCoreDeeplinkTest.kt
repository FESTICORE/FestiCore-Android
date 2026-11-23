package io.festicore.core.utility.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `FestiCoreDeeplinkTests` 6건을 같은 사례로 옮긴다. */
class FestiCoreDeeplinkTest {
    @Test
    fun `위젯 URL은 목록과 축제 라이브 보드만 열고 OAuth URL은 받지 않는다`() {
        assertEquals(FestiCoreDeeplink.FestivalList, parse("festicore://festival"))
        assertEquals(FestiCoreDeeplink.Festival("fst_a"), parse("festicore://festival/fst_a"))
        assertNull(parse("festicore://festival/"))
        assertNull(parse("festicore://festival/a%2Fb"))
        assertNull(parse("festicore://festival/fst?other=1"))
        assertNull(parse("com.googleusercontent.apps.sample:/oauth"))
    }

    @Test
    fun `깃발 공유 링크는 서버 ID 형식의 깃발 하나만 연다`() {
        assertEquals(FestiCoreDeeplink.Flag("flg_1"), parse("festicore://flag/flg_1"))
        assertEquals(FestiCoreDeeplink.Flag("flg-A_9"), parse("FESTICORE://FLAG/flg-A_9"))
        assertNull(parse("festicore://flag"))
        assertNull(parse("festicore://flag/"))
        assertNull(parse("festicore://flag/a.b"))
        assertNull(parse("festicore://flag/%ED%98%B8"))
        assertNull(parse("festicore://flag/flg_1/extra"))
        assertNull(parse("festicore://flag/flg_1?ref=share"))
        assertNull(parse("festicore://unknown/flg_1"))
    }

    @Test
    fun `공연 알림 링크는 축제와 공연을 함께 연다`() {
        assertEquals(FestiCoreDeeplink.Set(festivalId = "fst_a", setId = "set_1"), parse("festicore://festival/fst_a/set/set_1"))
        assertNull(parse("festicore://festival/fst_a/stage/set_1"))
        assertNull(parse("festicore://festival/fst_a/set"))
    }

    @Test
    fun `크루 초대 링크는 초대 코드 형식 6자리만 열고 대문자로 맞춘다`() {
        assertEquals(FestiCoreDeeplink.CrewInvite("K7P2QX"), parse("festicore://crew/K7P2QX"))
        assertEquals(FestiCoreDeeplink.CrewInvite("K7P2QX"), parse("festicore://crew/k7p2qx"))
        assertNull(parse("festicore://crew"))
        assertNull(parse("festicore://crew/K7P2Q"))
        assertNull(parse("festicore://crew/K7P2Q0"))
        assertNull(parse("festicore://crew/K7P2QX/extra"))
    }

    @Test
    fun `대기 저장 문자열은 같은 딥링크로 되돌아온다`() {
        val links =
            listOf(
                FestiCoreDeeplink.FestivalList,
                FestiCoreDeeplink.Festival("fst_a"),
                FestiCoreDeeplink.Flag("flg_1"),
                FestiCoreDeeplink.Set(festivalId = "fst_a", setId = "set_1"),
                FestiCoreDeeplink.CrewInvite("K7P2QX"),
            )
        for (link in links) assertEquals(link, FestiCoreDeeplinkParser.parse(link.encoded))
    }

    @Test
    fun `푸시는 deeplink를 먼저 읽고 없으면 route와 ID로 찾는다`() {
        assertEquals(
            FestiCoreDeeplink.Set(festivalId = "fst_a", setId = "set_1"),
            FestiCoreDeeplinkParser.parse(
                mapOf("deeplink" to "festicore://festival/fst_a/set/set_1", "route" to "/festivals/fst_b/live"),
            ),
        )
        assertEquals(FestiCoreDeeplink.Festival("fst_b"), FestiCoreDeeplinkParser.parse(mapOf("route" to "/festivals/fst_b/checklist")))
        assertEquals(FestiCoreDeeplink.Flag("flg_2"), FestiCoreDeeplinkParser.parse(mapOf("route" to "/flags/flg_2")))
        assertEquals(
            FestiCoreDeeplink.Festival("fst_c"),
            FestiCoreDeeplinkParser.parse(mapOf("route" to "/crews/crw_1", "festivalId" to "fst_c")),
        )
        assertNull(FestiCoreDeeplinkParser.parse(mapOf("route" to "/")))
        assertNull(FestiCoreDeeplinkParser.parse(emptyMap()))
    }

    private fun parse(value: String): FestiCoreDeeplink? = FestiCoreDeeplinkParser.parse(value)
}
