package io.festicore.ui.designkit.component.button

import androidx.compose.runtime.Composable
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews

@FCPreviews
@Composable
private fun FCPrimaryButtonPreview() {
    FCPreviewSurface {
        FCPrimaryButton("확인") {}
        FCPrimaryButton("확인", isEnabled = false) {}
    }
}
