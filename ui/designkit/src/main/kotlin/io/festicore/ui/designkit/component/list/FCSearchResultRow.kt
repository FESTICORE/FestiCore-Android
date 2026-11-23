package io.festicore.ui.designkit.component.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.media.FCAvatar
import io.festicore.ui.designkit.component.media.FCAvatarSize
import io.festicore.ui.designkit.component.media.FCThumbnail
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox
import java.text.Normalizer

private val LeadingSize = 48.dp
private val IconSize = 16.dp

/** 검색 결과 배지 색(iOS `FCSearchResultRow.BadgeStyle`). 카드 상태 색과 맞춘다. */
enum class FCSearchResultRowBadgeStyle {
    Day,
    Closing,
    Upcoming,
}

/** 검색 결과 종류(iOS `FCSearchResultRow.Kind`). */
sealed interface FCSearchResultRowKind {
    data class Festival(
        val image: Painter?,
        val dDay: String?,
    ) : FCSearchResultRowKind

    data class Artist(val initial: String) : FCSearchResultRowKind
}

/**
 * 검색 결과 한 줄. festival은 썸네일과 D-day, artist는 이니셜과 화살표를 표시한다.
 *
 * @param highlightedQuery 제목에서 검색어와 일치하는 부분을 강조한다(iOS `highlightedQuery(_:)`).
 * @param badgeStyle D-day 배지 색(iOS `badgeStyle(_:)`).
 * @param thumbnail festival 썸네일 자리에 넣을 원격 이미지 같은 내용(iOS `thumbnail(_:)`). 48 정사각형 r12로 자른다.
 */
@Composable
fun FCSearchResultRow(
    kind: FCSearchResultRowKind,
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    highlightedQuery: String = "",
    badgeStyle: FCSearchResultRowBadgeStyle = FCSearchResultRowBadgeStyle.Day,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val highlightColor = FestiCoreSemanticColor.secondaryNormal.color
    val highlightedTitle =
        buildAnnotatedString {
            var start = 0
            fcSearchHighlightRanges(title, highlightedQuery).forEach { range ->
                append(title.substring(start, range.first))
                withStyle(SpanStyle(color = highlightColor)) { append(title.substring(range.first, range.last + 1)) }
                start = range.last + 1
            }
            append(title.substring(start))
        }
    Row(
        modifier =
            modifier
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .padding(vertical = FestiCoreSpacing.paddingP12, horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (kind) {
            is FCSearchResultRowKind.Festival ->
                if (thumbnail != null) {
                    Box(
                        modifier =
                            Modifier
                                .clearAndSetSemantics {}
                                .size(LeadingSize)
                                .clip(RoundedCornerShape(FestiCoreRadius.r12)),
                    ) {
                        thumbnail()
                    }
                } else {
                    FCThumbnail(kind.image, placeholderIcon = R.drawable.ic_music_20, side = LeadingSize)
                }

            is FCSearchResultRowKind.Artist -> FCAvatar(kind.initial, size = FCAvatarSize.Large)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        ) {
            BasicText(
                text = highlightedTitle,
                style = FestiCoreTextStyle.baseCalloutBold.textStyleWithoutLineBox.copy(color = FestiCoreSemanticColor.labelNormal.color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = meta,
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelNeutral.color,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (kind) {
            is FCSearchResultRowKind.Festival ->
                if (kind.dDay != null) {
                    val (background, foreground) = badgeStyle.colors()
                    BasicText(
                        text = kind.dDay,
                        modifier =
                            Modifier
                                .background(background.color, RoundedCornerShape(FestiCoreRadius.r4))
                                .padding(vertical = FestiCoreSpacing.paddingP4, horizontal = FestiCoreSpacing.marginM6),
                        style = FestiCoreTextStyle.baseCaption2Bold.textStyleWithoutLineBox.copy(color = foreground.color),
                        maxLines = 1,
                    )
                }

            is FCSearchResultRowKind.Artist ->
                Image(
                    painter = painterResource(R.drawable.ic_chevron_right_16),
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelAssistive.color),
                )
        }
    }
}

private fun FCSearchResultRowBadgeStyle.colors(): Pair<FestiCoreSemanticColor, FestiCoreSemanticColor> = when (this) {
    FCSearchResultRowBadgeStyle.Day -> FestiCoreSemanticColor.inverseBackground to FestiCoreSemanticColor.inverseLabel
    FCSearchResultRowBadgeStyle.Closing -> FestiCoreSemanticColor.accentPointRed to FestiCoreSemanticColor.staticWhite
    FCSearchResultRowBadgeStyle.Upcoming -> FestiCoreSemanticColor.fillStrong to FestiCoreSemanticColor.labelNormal
}

/**
 * [title]에서 [query]와 겹치지 않게 일치하는 구간을 앞에서부터 원문 인덱스 범위로 돌려준다.
 * iOS `range(of:options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive])`처럼 대소문자 · 라틴 발음 기호 · 전각을 무시한다.
 * 분해형(NFD) 결합 문자는 접힌 문자열에서 버리고, 접힌 글자마다 원문 구간을 기록해 결합 문자를 앞 기본 문자 범위에 포함한다.
 */
internal fun fcSearchHighlightRanges(title: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val foldedTitle = title.fcFoldForSearch()
    val foldedQuery = query.fcFoldForSearch().text
    if (foldedQuery.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (true) {
        val index = foldedTitle.text.indexOf(foldedQuery, startIndex = from)
        if (index < 0) break
        val last = index + foldedQuery.length - 1
        ranges += foldedTitle.starts[index] until foldedTitle.ends[last]
        from = last + 1
    }
    return ranges
}

/** 접힌 문자열과, 접힌 UTF-16 단위마다 대응하는 원문 구간(시작 · 끝 제외) */
private class FoldedText(
    val text: String,
    val starts: IntArray,
    val ends: IntArray,
)

private fun String.fcFoldForSearch(): FoldedText {
    val text = StringBuilder()
    val starts = mutableListOf<Int>()
    val ends = mutableListOf<Int>()
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        val next = index + Character.charCount(codePoint)
        val folded = fcFoldCodePoint(codePoint)
        if (folded.isEmpty()) {
            // 결합 문자는 앞 기본 문자의 원문 구간을 넓힌다.
            if (ends.isNotEmpty()) ends[ends.lastIndex] = next
        } else {
            text.append(folded)
            repeat(folded.length) {
                starts += index
                ends += next
            }
        }
        index = next
    }
    return FoldedText(text.toString(), starts.toIntArray(), ends.toIntArray())
}

private fun fcFoldCodePoint(codePoint: Int): String {
    if (Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()) return ""
    var folded = codePoint
    if (folded in '！'.code..'～'.code) folded -= FULL_WIDTH_OFFSET
    if (folded == '　'.code) folded = ' '.code
    var text = String(Character.toChars(folded))
    // 한글은 NFD로 자모가 갈라지지 않게 라틴 범위만 발음 기호를 푼다.
    if (folded in 'À'.code..'ɏ'.code) {
        text = Normalizer.normalize(text, Normalizer.Form.NFD).filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
    }
    return text.lowercase()
}

private const val FULL_WIDTH_OFFSET = 0xFEE0
