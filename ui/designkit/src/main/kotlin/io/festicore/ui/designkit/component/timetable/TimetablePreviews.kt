package io.festicore.ui.designkit.component.timetable

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

@FCPreviews
@Composable
private fun FCNowLinePreview() {
    FCPreviewSurface {
        FCNowLine(time = "18:30")
    }
}

@FCPreviews
@Composable
private fun FCOverlapHighlightPreview() {
    FCPreviewSurface {
        FCOverlapHighlight(modifier = Modifier.size(width = 120.dp, height = 80.dp))
    }
}

@FCPreviews
@Composable
private fun FCSetBlockPreview() {
    FCPreviewSurface {
        FCSetBlock(
            artist = "아티스트",
            time = "18:00 - 19:00",
            modifier = Modifier.width(120.dp).height(fcSetBlockHeight(minutes = 60)),
        )
        FCSetBlock(
            artist = "아티스트",
            time = "18:00 - 19:00",
            modifier = Modifier.width(120.dp).height(fcSetBlockHeight(minutes = 60)),
            state = FCSetBlockState.Mine,
        )
        FCSetBlock(
            artist = "아티스트",
            time = "18:30 - 19:30",
            modifier = Modifier.width(120.dp).height(fcSetBlockHeight(minutes = 60)),
            state = FCSetBlockState.Changed(oldTime = "18:00", change = "30분 지연"),
        )
        FCSetBlock(
            artist = "아티스트",
            time = "18:00 - 18:20",
            modifier = Modifier.width(120.dp).height(fcSetBlockHeight(minutes = 20)),
            state = FCSetBlockState.Overlap(),
        )
    }
}

@FCPreviews
@Composable
private fun FCStageHeaderPreview() {
    FCPreviewSurface {
        FCStageHeader(name = "메인 스테이지")
        FCStageHeader(name = "서브 스테이지", dotColor = FestiCoreSemanticColor.accentPointOrange)
    }
}
