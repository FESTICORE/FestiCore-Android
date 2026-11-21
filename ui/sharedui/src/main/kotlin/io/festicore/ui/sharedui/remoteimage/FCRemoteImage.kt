package io.festicore.ui.sharedui.remoteimage

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

private val DefaultIconSize = 24.dp

/** iOS `KFImage.fade(duration: 0.2)` */
private const val FADE_MILLIS = 200

internal const val FC_REMOTE_IMAGE_LOADING_TAG = "FCRemoteImage.loading"
internal const val FC_REMOTE_IMAGE_PLACEHOLDER_TAG = "FCRemoteImage.placeholder"
internal const val FC_REMOTE_IMAGE_CONTENT_TAG = "FCRemoteImage.content"

/**
 * 서버 이미지를 캐시해 표시한다(iOS `FestiCoreSharedUI` `FCRemoteImage`).
 * 주소가 없거나 불러오지 못하면 Figma `image=empty`처럼 `fillStrong` 바탕에 (지정했다면) 가운데 아이콘을 둔 기본 모양을 그린다.
 * 불러오는 동안은 `fillNormal` 바탕 위에 [FCSkeletonView] `Rect`를 그린다(iOS Kingfisher `placeholder`). 넘친 부분은 자른다.
 * 이미지 로더는 Coil 싱글톤이다([installFestiCoreImageLoader]).
 *
 * @param url 이미지 주소. 바뀌면 상태를 처음부터 다시 시작한다(iOS `.id(url)`).
 * @param contentScale 영역 채우기(`Crop`, iOS `.fill`, 기본) 또는 전체 보이기(`Fit`, iOS `.fit`). iOS `content(_:)`.
 * @param placeholderIcon 없음 · 실패 때 가운데 아이콘(iOS `placeholderIcon(_:size:)`). `null`이면 바탕만 그린다.
 * @param placeholderIconSize 아이콘 크기(iOS 기본 24).
 */
@Composable
fun FCRemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    @DrawableRes placeholderIcon: Int? = null,
    placeholderIconSize: Dp = DefaultIconSize,
) {
    val boxModifier = modifier.clipToBounds()
    if (url == null) {
        Placeholder(placeholderIcon, placeholderIconSize, boxModifier)
        return
    }
    key(url) {
        RemoteContent(url, contentScale, placeholderIcon, placeholderIconSize, boxModifier)
    }
}

@Composable
private fun RemoteContent(
    url: String,
    contentScale: ContentScale,
    @DrawableRes placeholderIcon: Int?,
    placeholderIconSize: Dp,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(FADE_MILLIS).build() }
    val painter = rememberAsyncImagePainter(request, contentScale = contentScale)
    val state by painter.state.collectAsState()
    Box(modifier = modifier) {
        when (state) {
            is AsyncImagePainter.State.Error -> {
                Placeholder(placeholderIcon, placeholderIconSize, Modifier.fillMaxSize())
            }

            is AsyncImagePainter.State.Success -> Unit

            AsyncImagePainter.State.Empty, is AsyncImagePainter.State.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(FestiCoreSemanticColor.fillNormal.color)
                            .testTag(FC_REMOTE_IMAGE_LOADING_TAG),
                ) {
                    FCSkeletonView(FCSkeletonShape.Rect, Modifier.fillMaxSize())
                }
            }
        }
        if (state !is AsyncImagePainter.State.Error) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().testTag(FC_REMOTE_IMAGE_CONTENT_TAG),
                contentScale = contentScale,
            )
        }
    }
}

/** Figma Thumbnail · Festival Image의 `image=empty`: `fillStrong` 바탕 + `labelAssistive` 아이콘 */
@Composable
private fun Placeholder(
    @DrawableRes icon: Int?,
    iconSize: Dp,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .background(FestiCoreSemanticColor.fillStrong.color)
                .testTag(FC_REMOTE_IMAGE_PLACEHOLDER_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
            )
        }
    }
}
