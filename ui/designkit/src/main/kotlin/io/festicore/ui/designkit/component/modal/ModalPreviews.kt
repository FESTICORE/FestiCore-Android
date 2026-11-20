package io.festicore.ui.designkit.component.modal

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews

// 시트는 별도 창에 그려져 미리보기에서 대화형 모드로 확인한다.
@FCPreviews
@Composable
private fun FCSheetPreview() {
    FCPreviewSurface {
        BasicText("배경")
        FCSheet(isPresented = true, onDismissRequest = {}) {
            BasicText("시트 내용")
        }
    }
}
