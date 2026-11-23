package io.festicore.ui.designkit.component.navigation

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val Height = 52.dp
private val LargeHeight = 60.dp
private val TouchSize = 44.dp
private val IconSize = 24.dp
private val BackIconSize = 20.dp
private val SmallIconSize = 16.dp
private val LogoWidth = 108.dp
private val LogoHeight = 24.dp
private val BearerHeight = 32.dp
private val SearchHeight = 40.dp
private val BadgeSize = 6.dp
private val BadgeInset = 10.dp
private val BadgeCountMinSize = 16.dp
private val BadgeCountHorizontalPadding = 3.dp
private val BadgeCountOffset = 4.dp

/** iOS `FCTopBar.Style`. `search(text:)`의 `Binding<String>`은 [Search.text] + [Search.onTextChange]로 받는다. */
sealed interface FCTopBarStyle {
    data object Logo : FCTopBarStyle

    data object Back : FCTopBarStyle

    data object Large : FCTopBarStyle

    data class Search(
        val text: String,
        val onTextChange: (String) -> Unit,
    ) : FCTopBarStyle

    data object LogoBearer : FCTopBarStyle

    data object BackOnImage : FCTopBarStyle
}

/**
 * 화면 상단 바. 배경은 위 상태 표시줄 영역까지 칠한다.
 *
 * @param backgroundColor 배경색(iOS `backgroundColor(_:)`). `null`이면 스타일 기본 배경.
 * @param trailingIcon 오른쪽 아이콘(iOS `trailingAction(icon:accessibilityLabel:action:)`의 `icon`). `null`이면 스타일 기본(로고 계열은 알림, 나머지는 검색).
 * @param trailingAccessibilityLabel 오른쪽 아이콘 접근성 라벨. `null`이면 스타일 기본("알림" · "검색").
 * @param shareUrl 공유 링크(iOS `shareLink(_:title:)`). 있으면 오른쪽 버튼이 시스템 공유 시트를 연다.
 * @param shareTitle 공유 미리보기 제목(iOS `shareLink(_:title:)`의 `title`).
 * @param showsBadge 오른쪽 아이콘에 점 배지(iOS `showsBadge(_:)`). [badgeCount]가 `null`일 때만 그린다.
 * @param badgeCount 오른쪽 아이콘의 숫자 배지(iOS `badgeCount(_:)`). 음수는 0으로 보고 0이면 그리지 않는다. 99 초과는 "99+".
 * @param bearerTitle 기수 버튼 글자(iOS `onBearer(title:_:)`의 `title`).
 * @param searchPlaceholder 검색창 안내 글자(iOS `searchPlaceholder(_:)`).
 * @param onBack 뒤로 버튼 동작(iOS `onBack(_:)`). `null`이면 버튼을 그리지 않는다.
 * @param onTrailingAction 오른쪽 버튼 동작(iOS `trailingAction`). `null`이고 [shareUrl]도 없으면 버튼을 그리지 않는다. 검색 스타일에서는 그리지 않는다.
 * @param onBearer 기수 버튼 동작(iOS `onBearer(title:_:)`). logoBearer 스타일에서만 그린다.
 * @param onCancel 검색 취소 버튼 동작(iOS `onCancel(_:)`). 검색 스타일에서만 그린다.
 * @param onSearchSubmit 키보드 검색 키 동작(iOS `onSubmit`). 검색 스타일에서만 쓴다. 누르면 이 동작 뒤에 iOS 제출처럼 입력 초점을 놓아 키보드를 닫는다.
 */
@Composable
fun FCTopBar(
    style: FCTopBarStyle,
    modifier: Modifier = Modifier,
    title: String = "",
    backgroundColor: FestiCoreSemanticColor? = null,
    @DrawableRes trailingIcon: Int? = null,
    trailingAccessibilityLabel: String? = null,
    shareUrl: String? = null,
    shareTitle: String = "",
    showsBadge: Boolean = false,
    badgeCount: Int? = null,
    bearerTitle: String = "기수",
    searchPlaceholder: String = "",
    onBack: (() -> Unit)? = null,
    onTrailingAction: (() -> Unit)? = null,
    onBearer: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onSearchSubmit: (() -> Unit)? = null,
) {
    val isOnImage = style == FCTopBarStyle.BackOnImage
    val keepsTitleCentered = style == FCTopBarStyle.Back || isOnImage
    val iconColor = if (isOnImage) FestiCoreSemanticColor.staticWhite else FestiCoreSemanticColor.labelNeutral
    val isLogoStyle = style == FCTopBarStyle.Logo || style == FCTopBarStyle.LogoBearer
    val showsTrailing = style !is FCTopBarStyle.Search && (onTrailingAction != null || shareUrl != null)
    val trailing: @Composable () -> Unit = {
        TrailingButton(
            showsTrailing = showsTrailing,
            keepsTitleCentered = keepsTitleCentered,
            iconColor = iconColor,
            icon = trailingIcon ?: if (isLogoStyle) R.drawable.ic_bell else R.drawable.ic_magnifying_glass,
            accessibilityLabel = trailingAccessibilityLabel ?: if (isLogoStyle) "알림" else "검색",
            shareUrl = shareUrl,
            shareTitle = shareTitle,
            showsBadge = showsBadge,
            badgeCount = badgeCount?.coerceAtLeast(0),
            onTrailingAction = onTrailingAction,
        )
    }
    val height = if (style == FCTopBarStyle.Large || style is FCTopBarStyle.Search) LargeHeight else Height

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .topBarBackground(style, backgroundColor)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(height)
                .padding(contentPadding(style)),
        horizontalArrangement = Arrangement.spacedBy(contentSpacing(style)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (style) {
            FCTopBarStyle.Logo -> {
                Logo()
                Spacer(modifier = Modifier.weight(1f))
                trailing()
            }

            FCTopBarStyle.Back, FCTopBarStyle.BackOnImage -> {
                if (onBack != null) {
                    IconButton(
                        icon = R.drawable.ic_chevron_left,
                        iconSize = BackIconSize,
                        color = iconColor,
                        accessibilityLabel = "뒤로",
                        onClick = onBack,
                    )
                } else {
                    Spacer(modifier = Modifier.size(TouchSize))
                }
                Title(
                    title = title,
                    style = FestiCoreTextStyle.baseHeadlineBold,
                    color = if (isOnImage) FestiCoreSemanticColor.staticWhite else FestiCoreSemanticColor.labelNormal,
                    textAlign = TextAlign.Center,
                )
                trailing()
            }

            FCTopBarStyle.Large -> {
                Title(
                    title = title,
                    style = FestiCoreTextStyle.baseTitle1Bold,
                    color = FestiCoreSemanticColor.labelNormal,
                    textAlign = TextAlign.Start,
                )
                trailing()
            }

            is FCTopBarStyle.Search -> {
                SearchField(
                    text = style.text,
                    onTextChange = style.onTextChange,
                    placeholder = searchPlaceholder,
                    onSubmit = onSearchSubmit,
                    modifier = Modifier.weight(1f),
                )
                if (onCancel != null) {
                    BasicText(
                        text = "취소",
                        modifier = Modifier.clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onCancel),
                        style = FestiCoreTextStyle.baseCalloutMedium.textStyleWithoutLineBox.copy(
                            color = FestiCoreSemanticColor.labelNormal.color,
                        ),
                    )
                }
            }

            FCTopBarStyle.LogoBearer -> {
                Logo()
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBearer != null) BearerButton(title = bearerTitle, onClick = onBearer)
                    trailing()
                }
            }
        }
    }
}

private fun contentPadding(style: FCTopBarStyle) = when (style) {
    FCTopBarStyle.Back, FCTopBarStyle.BackOnImage -> PaddingValues(horizontal = FestiCoreSpacing.paddingP8)
    is FCTopBarStyle.Search -> PaddingValues(horizontal = FestiCoreSpacing.paddingP16)
    else -> PaddingValues(start = FestiCoreSpacing.paddingP20, end = FestiCoreSpacing.paddingP8)
}

private fun contentSpacing(style: FCTopBarStyle): Dp = when (style) {
    FCTopBarStyle.Back, FCTopBarStyle.BackOnImage, FCTopBarStyle.Large -> FestiCoreSpacing.paddingP16
    is FCTopBarStyle.Search -> FestiCoreSpacing.paddingP12
    else -> 0.dp
}

/** iOS `.ultraThinMaterial` 흐림은 Compose에서 뒤 내용을 흐리게 할 수 없어 같은 색 덧칠만 둔다. */
@Composable
private fun Modifier.topBarBackground(style: FCTopBarStyle, backgroundColor: FestiCoreSemanticColor?): Modifier = when {
    backgroundColor != null -> background(backgroundColor.color)

    style == FCTopBarStyle.BackOnImage ->
        background(FestiCoreSemanticColor.materialGlass.dark).background(FestiCoreSemanticColor.staticalphaBlack20.color)

    style == FCTopBarStyle.Large || style is FCTopBarStyle.Search -> background(FestiCoreSemanticColor.backgroundNormal.color)

    else -> background(FestiCoreSemanticColor.materialGlass.color)
}

@Composable
private fun Logo() {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "FESTICORE",
        modifier = Modifier.size(LogoWidth, LogoHeight),
        colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
    )
}

@Composable
private fun RowScope.Title(
    title: String,
    style: FestiCoreTextStyle,
    color: FestiCoreSemanticColor,
    textAlign: TextAlign,
) {
    BasicText(
        text = title,
        modifier = Modifier.weight(1f).semantics { heading() },
        style = style.textStyleWithoutLineBox.copy(color = color.color, textAlign = textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun IconButton(
    @DrawableRes icon: Int,
    iconSize: Dp,
    color: FestiCoreSemanticColor,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badge: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .size(TouchSize)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            colorFilter = ColorFilter.tint(color.color),
        )
        badge()
    }
}

@Composable
private fun TrailingButton(
    showsTrailing: Boolean,
    keepsTitleCentered: Boolean,
    iconColor: FestiCoreSemanticColor,
    @DrawableRes icon: Int,
    accessibilityLabel: String,
    shareUrl: String?,
    shareTitle: String,
    showsBadge: Boolean,
    badgeCount: Int?,
    onTrailingAction: (() -> Unit)?,
) {
    when {
        showsTrailing && shareUrl != null -> {
            val context = LocalContext.current
            IconButton(
                icon = R.drawable.ic_share_nodes,
                iconSize = IconSize,
                color = iconColor,
                accessibilityLabel = "공유",
                onClick = {
                    val send =
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shareUrl)
                            .putExtra(Intent.EXTRA_TITLE, shareTitle)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                },
            )
        }

        showsTrailing && onTrailingAction != null -> {
            val badgeValue =
                when {
                    badgeCount != null -> if (badgeCount > 0) "읽지 않은 알림 ${badgeCount}개" else ""
                    showsBadge -> "새 소식 있음"
                    else -> ""
                }
            IconButton(
                icon = icon,
                iconSize = IconSize,
                color = iconColor,
                accessibilityLabel = accessibilityLabel,
                modifier = Modifier.semantics { stateDescription = badgeValue },
                onClick = onTrailingAction,
            ) {
                if (badgeCount != null && badgeCount > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = -BadgeCountOffset, y = BadgeCountOffset)
                                .defaultMinSize(BadgeCountMinSize, BadgeCountMinSize)
                                .background(FestiCoreSemanticColor.statusNegative.color, CircleShape)
                                .padding(horizontal = BadgeCountHorizontalPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = if (badgeCount > 99) "99+" else "$badgeCount",
                            style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(
                                color = FestiCoreSemanticColor.staticWhite.color,
                            ),
                        )
                    }
                } else if (badgeCount == null && showsBadge) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = -BadgeInset, y = BadgeInset)
                                .size(BadgeSize)
                                .background(FestiCoreSemanticColor.statusNegative.color, CircleShape),
                    )
                }
            }
        }

        keepsTitleCentered -> Spacer(modifier = Modifier.size(TouchSize))
    }
}

@Composable
private fun BearerButton(title: String, onClick: () -> Unit) {
    val color = FestiCoreSemanticColor.accentForegroundPurple.color
    Row(
        modifier =
            Modifier
                .height(BearerHeight)
                .background(FestiCoreSemanticColor.accentBackgroundPurple.color, CircleShape)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = FestiCoreSpacing.paddingP12),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_flag_16),
            contentDescription = null,
            modifier = Modifier.size(SmallIconSize),
            colorFilter = ColorFilter.tint(color),
        )
        BasicText(text = title, style = FestiCoreTextStyle.baseFootnoteBold.textStyleWithoutLineBox.copy(color = color))
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    onSubmit: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val assistive = FestiCoreSemanticColor.labelAssistive.color
    Row(
        modifier =
            modifier
                .height(SearchHeight)
                .background(FestiCoreSemanticColor.fillNormal.color, RoundedCornerShape(FestiCoreRadius.r12))
                .padding(horizontal = FestiCoreSpacing.paddingP12),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_magnifying_glass_16),
            contentDescription = null,
            modifier = Modifier.size(SmallIconSize),
            colorFilter = ColorFilter.tint(assistive),
        )
        val textStyle = FestiCoreTextStyle.baseCalloutRegular.textStyleWithoutLineBox
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            textStyle = textStyle.copy(color = FestiCoreSemanticColor.labelNormal.color),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(onSearch = {
                    onSubmit?.invoke()
                    focusManager.clearFocus()
                }),
            cursorBrush = SolidColor(FestiCoreSemanticColor.labelNormal.color),
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) BasicText(text = placeholder, style = textStyle.copy(color = assistive), maxLines = 1)
                    innerTextField()
                }
            },
        )
        if (text.isNotEmpty()) {
            Image(
                painter = painterResource(R.drawable.ic_circle_xmark),
                contentDescription = "검색어 지우기",
                modifier =
                    Modifier
                        .size(SmallIconSize)
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { onTextChange("") },
                colorFilter = ColorFilter.tint(assistive),
            )
        }
    }
}
