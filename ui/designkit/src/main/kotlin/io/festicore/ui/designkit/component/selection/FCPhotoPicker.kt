package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

private val PhotoSize = 88.dp
private val IconSize = 24.dp
private val BorderWidth = 1.dp
private val ProgressWidth = 52.dp
private val ProgressHeight = 4.dp

/** 사진 선택 상태(iOS `FCPhotoPicker.State`). 사진은 화면이 `PickVisualMedia` 결과를 불러와 [Painter]로 넘긴다. */
sealed interface FCPhotoPickerState {
    data object Empty : FCPhotoPickerState

    /** [progress]는 0..1이며 범위를 벗어나면 잘라 그린다. */
    data class Uploading(
        val image: Painter,
        val progress: Double,
    ) : FCPhotoPickerState

    data class Filled(val image: Painter) : FCPhotoPickerState
}

/**
 * 깃발 사진 1장 고르기. 업로드 중에는 진행률과 취소 버튼을 표시한다. 실제 선택은 화면이 [onClick]에서 연다.
 *
 * @param title 제목(iOS `title(_:)`).
 * @param description 설명 문구(iOS `description(_:)`). `null`이면 상태별 기본 문구를 쓴다.
 * @param buttonTitle 버튼 글자(iOS `buttonTitle(_:)`). `null`이면 상태별 기본 글자를 쓴다.
 */
@Composable
fun FCPhotoPicker(
    state: FCPhotoPickerState,
    modifier: Modifier = Modifier,
    title: String = "깃발 사진",
    description: String? = null,
    buttonTitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoPickerPhoto(state)
        // Figma Photo Picker: 제목 줄 24 · 설명 줄 18, 설명과 버튼 사이 12(간격 4 + 여백 8)로 높이 98
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            // 제목은 한 줄이다. 행간 24.004sp의 픽셀 올림이 설명 줄과 겹쳐 Figma 98을 넘지 않게 iOS 줄 박스 높이로 고정한다.
            val titleStyle = FestiCoreTextStyle.baseHeadlineBold.textStyle
            BasicText(
                text = title,
                modifier = Modifier.height(with(LocalDensity.current) { titleStyle.lineHeight.toDp() }),
                style = titleStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = description ?: state.defaultDescription,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
            )
            FCButton(
                title = buttonTitle ?: state.defaultButtonTitle,
                modifier = Modifier.padding(top = FestiCoreSpacing.paddingP8),
                style = FCButtonStyle.Secondary,
                size = FCButtonSize.Medium,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun PhotoPickerPhoto(state: FCPhotoPickerState) {
    val shape = RoundedCornerShape(FestiCoreRadius.r16)
    when (state) {
        FCPhotoPickerState.Empty ->
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {}
                        .size(PhotoSize)
                        .background(FestiCoreSemanticColor.fillNormal.color, shape)
                        .border(BorderWidth, FestiCoreSemanticColor.lineNormal.color, shape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
            }

        is FCPhotoPickerState.Uploading -> {
            val percent = (state.progress.coerceIn(0.0, 1.0) * 100).toInt()
            Box(
                modifier =
                    Modifier
                        .clearAndSetSemantics {
                            contentDescription = "업로드 중"
                            stateDescription = "${percent}퍼센트"
                        }.size(PhotoSize)
                        .clip(shape),
                contentAlignment = Alignment.Center,
            ) {
                PhotoPickerThumbnail(state.image)
                Box(modifier = Modifier.fillMaxSize().background(FestiCoreSemanticColor.staticalphaBlack60.color))
                Column(
                    verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BasicText(
                        text = "$percent%",
                        style =
                            FestiCoreTextStyle.baseSubheadlineBold.textStyle.copy(
                                color = FestiCoreSemanticColor.staticalphaWhite80.color,
                                fontFeatureSettings = TABULAR_NUMBERS,
                            ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(ProgressWidth, ProgressHeight)
                                .background(FestiCoreSemanticColor.staticalphaWhite20.color, CircleShape),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(ProgressWidth * state.progress.coerceIn(0.0, 1.0).toFloat(), ProgressHeight)
                                    .background(FestiCoreSemanticColor.staticLime.color, CircleShape),
                        )
                    }
                }
            }
        }

        is FCPhotoPickerState.Filled ->
            Box(modifier = Modifier.clearAndSetSemantics {}.size(PhotoSize).clip(shape)) {
                PhotoPickerThumbnail(state.image)
            }
    }
}

@Composable
private fun PhotoPickerThumbnail(image: Painter) {
    Image(
        painter = image,
        contentDescription = null,
        modifier = Modifier.size(PhotoSize),
        contentScale = ContentScale.Crop,
    )
}

private val FCPhotoPickerState.defaultDescription: String
    get() =
        when (this) {
            FCPhotoPickerState.Empty -> "깃발이 잘 보이는 사진 1장 · 정사각형으로 잘려요"
            is FCPhotoPickerState.Uploading -> "사진을 올리고 있어요. 잠시만요"
            is FCPhotoPickerState.Filled -> "깃발 목록과 상세 화면에 보여요"
        }

private val FCPhotoPickerState.defaultButtonTitle: String
    get() =
        when (this) {
            FCPhotoPickerState.Empty -> "사진 고르기"
            is FCPhotoPickerState.Uploading -> "취소"
            is FCPhotoPickerState.Filled -> "바꾸기"
        }
