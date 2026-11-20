package io.festicore.ui.designkit.component.popup

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.component.action.FCButtonStyle

@FCPreviews
@Composable
private fun FCCheckOverlayPreview() {
    FCPreviewSurface {
        FCCheckOverlay(
            FCCheckOverlayItem(title = "리벳 스테이지 중앙으로 바꿨어요", message = "팔로워에게 알렸어요"),
            modifier = Modifier.height(400.dp),
        )
    }
}

// Dialog 창은 미리보기에 그려지지 않아 같은 카드를 직접 그린다.
@FCPreviews
@Composable
private fun FCDialogPreview() {
    val item = FCDialogItem(title = "이 안내를 숨길까요?", message = "마이 > 설정에서 다시 켤 수 있어요.", onConfirm = {})
    FCPreviewSurface {
        FCDialogCard(item, onDismiss = {})
        FCDialogCard(item, onDismiss = {}, textAlign = FCDialogTextAlign.Center, confirmStyle = FCButtonStyle.Inverse)
    }
}
