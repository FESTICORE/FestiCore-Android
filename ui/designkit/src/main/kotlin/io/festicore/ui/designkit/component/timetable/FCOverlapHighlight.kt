package io.festicore.ui.designkit.component.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private const val OVERLAP_OPACITY = 0.2f

/** 내 일정끼리 겹치는 시간 구간 띠. 셋 블록 뒤 바탕에 깐다. 크기는 부모가 정한다. */
@Composable
fun FCOverlapHighlight(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.accentPointOrange.color.copy(alpha = OVERLAP_OPACITY))
                .clearAndSetSemantics {},
    )
}
