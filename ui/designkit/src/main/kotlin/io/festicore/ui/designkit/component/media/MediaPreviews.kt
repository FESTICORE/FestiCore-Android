package io.festicore.ui.designkit.component.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

@FCPreviews
@Composable
private fun FCAvatarPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCAvatarTint.entries.forEach { FCAvatar("페", tint = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCAvatarSize.entries.forEach { FCAvatar("Roy", size = it, tint = FCAvatarTint.Lime) }
        }
    }
}

@FCPreviews
@Composable
private fun FCThumbnailPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCThumbnail(null)
            FCThumbnail(null, side = 96.dp)
            FCThumbnail(null, placeholderIcon = R.drawable.ic_music_20, side = 40.dp, cornerRadius = 8.dp)
        }
    }
}
