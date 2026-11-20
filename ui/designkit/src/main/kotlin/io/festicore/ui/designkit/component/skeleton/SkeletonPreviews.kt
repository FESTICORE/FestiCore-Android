package io.festicore.ui.designkit.component.skeleton

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews

@FCPreviews
@Composable
private fun FCSkeletonViewPreview() {
    FCPreviewSurface {
        BasicText(
            "페스티벌 이름",
            modifier = Modifier.skeleton(isLoading = true, shape = FCSkeletonShape.Round(), width = 200.dp, height = 24.dp),
        )
        FCSkeletonView(FCSkeletonShape.Circle, modifier = Modifier.size(40.dp))
        FCSkeletonView(FCSkeletonShape.Rect, modifier = Modifier.size(200.dp, 24.dp))
    }
}
