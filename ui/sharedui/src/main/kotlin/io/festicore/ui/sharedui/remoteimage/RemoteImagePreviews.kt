package io.festicore.ui.sharedui.remoteimage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

/** 프리뷰 가짜 값: 실제 서버를 부르지 않는다. */
private const val PREVIEW_URL = "https://example.com/poster.webp"

/** 프리뷰에서 불러오기 실패를 흉내 낸다. */
private object FailingPreviewHandler : AsyncImagePreviewHandler {
    override suspend fun handle(imageLoader: ImageLoader, request: ImageRequest): AsyncImagePainter.State =
        AsyncImagePainter.State.Error(null, ErrorResult(null, request, IllegalStateException("preview")))
}

@Composable
private fun PreviewRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.padding(FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
    ) { content() }
}

@Preview(name = "FCRemoteImage 없음 · 아이콘", showBackground = true)
@Composable
private fun FCRemoteImageEmptyPreview() {
    PreviewRow {
        FCRemoteImage(url = null, modifier = Modifier.size(96.dp), placeholderIcon = R.drawable.ic_music_24, placeholderIconSize = 36.dp)
        FCRemoteImage(url = null, modifier = Modifier.size(96.dp))
    }
}

@Preview(name = "FCRemoteImage 성공 · 실패", showBackground = true)
@Composable
private fun FCRemoteImageLoadedPreview() {
    val poster = ColorImage(FestiCoreSemanticColor.accentPointOrange.color.toArgb())
    PreviewRow {
        CompositionLocalProvider(LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { poster }) {
            FCRemoteImage(
                url = PREVIEW_URL,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(FestiCoreRadius.r8)),
            )
        }
        CompositionLocalProvider(LocalAsyncImagePreviewHandler provides FailingPreviewHandler) {
            FCRemoteImage(
                url = PREVIEW_URL,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(FestiCoreRadius.r8)),
                placeholderIcon = R.drawable.ic_music_24,
                placeholderIconSize = 36.dp,
            )
        }
    }
}
