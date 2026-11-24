package io.festicore.feature.bearer.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.bearer.entity.BearerSet
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant

// 프리뷰 전용 가짜 값.
private val PreviewSets =
    listOf(
        BearerSet(
            id = "set_1",
            artist = "아티스트 A",
            stageName = "스테이지 A",
            startsAt = Instant.parse("2026-08-07T08:00:00Z"),
            endsAt = Instant.parse("2026-08-07T09:00:00Z"),
        ),
        BearerSet(
            id = "set_2",
            artist = "아티스트 B",
            stageName = null,
            startsAt = Instant.parse("2026-08-07T10:00:00Z"),
            endsAt = Instant.parse("2026-08-07T11:00:00Z"),
        ),
        BearerSet(
            id = "set_3",
            artist = "아티스트 C",
            stageName = "스테이지 A",
            startsAt = Instant.parse("2026-08-08T08:00:00Z"),
            endsAt = Instant.parse("2026-08-08T09:00:00Z"),
        ),
    )

@Preview(showBackground = true)
@Composable
private fun BearerPlanPickPreview() {
    FestiCoreTheme { BearerPlanPickContent(state = BearerPlanPickState.initial(sets = PreviewSets, plan = listOf("set_1"))) }
}

@Preview(showBackground = true)
@Composable
private fun BearerPlanPickLimitPreview() {
    FestiCoreTheme {
        BearerPlanPickContent(
            state = BearerPlanPickState.initial(sets = PreviewSets, plan = listOf("set_1")).copy(limitMessage = "오늘 계획은 30개까지 고를 수 있어요."),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerPlanPickEmptyPreview() {
    FestiCoreTheme { BearerPlanPickContent(state = BearerPlanPickState.initial(sets = emptyList(), plan = emptyList())) }
}
