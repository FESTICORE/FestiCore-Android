package io.festicore.ui.designkit.component.divider

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews

@FCPreviews
@Composable
private fun FCDividerPreview() {
    FCPreviewSurface {
        FCDivider()
        FCDivider(thickness = 4.dp)
    }
}
