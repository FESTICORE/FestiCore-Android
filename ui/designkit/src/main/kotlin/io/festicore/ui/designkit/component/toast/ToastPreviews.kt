package io.festicore.ui.designkit.component.toast

import androidx.compose.runtime.Composable
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews

@FCPreviews
@Composable
private fun FCToastPreview() {
    FCPreviewSurface {
        FCToast(FCToastItem(icon = R.drawable.ic_check, message = "내 일정에 추가됐어요"))
        FCToast(FCToastItem(icon = R.drawable.ic_circle_info_16, message = "예매처 정보가 없습니다"))
    }
}
