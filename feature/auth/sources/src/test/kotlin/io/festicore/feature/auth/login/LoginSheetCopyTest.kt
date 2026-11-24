package io.festicore.feature.auth.login

import io.festicore.feature.auth.LoginContext
import io.festicore.ui.designkit.R as DesignKitR
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `LoginFeatureTests`의 문맥 전환(`contextChangedUpdatesSheetCopyAndFlowMessage` · `s01LoginContextSwitches…`)과 `LoginModalView.copy` 문구. */
class LoginSheetCopyTest {
    @Test
    fun `문맥마다 iOS와 같은 제목과 본문을 고른다`() {
        val follow = LoginSheetCopy("깃발을 팔로우 하려면 로그인이 필요해요", "내 취향의 깃발을 팔로우하고,\n함께 페스티벌을 즐길 사람들과 크루를 만들어봐요.")
        val expected =
            mapOf(
                LoginContext.Profile to LoginSheetCopy("계정 정보를 보려면 로그인이 필요해요", "로그인 후 프로필을 확인할 수 있어요."),
                LoginContext.Flag to follow,
                LoginContext.Follow to follow,
                LoginContext.Crew to
                    LoginSheetCopy(
                        "크루와 함께 보려면 로그인이 필요해요",
                        "크루원끼리 지금 어느 존에 있는지 서로 볼 수 있어요.\n공연과 타임테이블은 로그인 없이 그대로 쓸 수 있어요.",
                    ),
                LoginContext.CrewJoin("페스티 크루") to
                    LoginSheetCopy(
                        "크루에 들어가려면 로그인이 필요해요",
                        "로그인하면 바로 페스티 크루에 들어가요.\n크루원끼리만 서로의 존과 상태를 볼 수 있어요.",
                    ),
                LoginContext.Bookmark to LoginSheetCopy("축제를 저장하려면 로그인이 필요해요", "로그인 후 선택한 축제를 저장할 수 있어요."),
                LoginContext.Report to LoginSheetCopy("깃발을 신고하려면 로그인이 필요해요", "로그인 후 문제가 있는 깃발을 신고할 수 있어요."),
            )
        for ((context, copy) in expected) assertEquals(context.toString(), copy, context.sheetCopy)
    }

    @Test
    fun `크루 참여 문맥에 이름이 없으면 크루라고 쓴다`() {
        assertEquals(
            "로그인하면 바로 크루에 들어가요.\n크루원끼리만 서로의 존과 상태를 볼 수 있어요.",
            LoginContext.CrewJoin(crewName = null).sheetCopy.message,
        )
    }

    @Test
    fun `크루 문맥만 사람 아이콘이고 나머지는 깃발 아이콘이다`() {
        assertEquals(DesignKitR.drawable.ic_users_24, LoginContext.Crew.markIcon)
        val others =
            listOf(
                LoginContext.Profile,
                LoginContext.Flag,
                LoginContext.Follow,
                LoginContext.CrewJoin("페스티 크루"),
                LoginContext.Bookmark,
                LoginContext.Report,
            )
        for (context in others) assertEquals(context.toString(), DesignKitR.drawable.ic_flag, context.markIcon)
    }
}
