package io.festicore.feature.bearer.board

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.festicore.domain.bearer.entity.BearerActivity
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.selection.FCChoiceTile
import io.festicore.ui.designkit.component.selection.FCChoiceTileSize
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

// iOS `BearerZonePickerView.Metric`(`BZV:100-103`) · `BearerActivityPickerView.Metric`(`BAV:86-88`)
private val ZoneTileHeight = 44.dp
private val ZoneGridMaxHeight = 420.dp
private val ActivityTileHeight = 96.dp

// iOS `GridItem(.flexible())` 기본 열 간격(시스템 기본 8pt, `BZV:69` · `BAV:29`)
private val ColumnSpacing = FestiCoreSpacing.marginM8

private const val COLUMNS = 2

/** Figma S09 B 존 고르기 시트(iOS `BearerZonePickerView`). 시트가 손잡이 아래 16을 두므로 위 여백은 넣지 않는다. */
@Composable
internal fun BearerZonePicker(
    zones: List<BearerZone>,
    selectedZoneId: String?,
    modifier: Modifier = Modifier,
    onZoneTapped: (String) -> Unit = {},
) {
    PickerColumn(modifier) {
        PickerHeader(title = "지금 어느 존에 있나요?", subtitle = "누르면 장소 정보가 바로 바뀌고 팔로워에게 알림이 가요")
        if (zones.isEmpty()) {
            BasicText(
                text = "받아 둔 페스티벌 정보에 존이 없어요. 라이브 보드에서 정보를 받은 뒤 다시 열어 주세요.",
                style = FestiCoreTextStyle.baseFootnoteRegular.textStyleWithoutLineBox.copy(
                    color = FestiCoreSemanticColor.labelAssistive.color,
                ),
            )
        } else {
            // 시트는 내용 높이를 따르고, 존이 많으면 최대 높이 안에서 스크롤한다.
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxWidth().height(zoneGridHeight(zones.size)),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12),
                horizontalArrangement = Arrangement.spacedBy(ColumnSpacing),
            ) {
                items(zones, key = { it.id }) { zone ->
                    FCChoiceTile(
                        title = zone.name,
                        icon = R.drawable.ic_location_dot_16,
                        isSelected = zone.id == selectedZoneId,
                        onClick = { onZoneTapped(zone.id) },
                    )
                }
            }
        }
    }
}

/** Figma S09 C 활동 고르기 시트(iOS `BearerActivityPickerView`). */
@Composable
internal fun BearerActivityPicker(
    selected: BearerActivity?,
    modifier: Modifier = Modifier,
    onActivityTapped: (BearerActivity) -> Unit = {},
) {
    PickerColumn(modifier) {
        PickerHeader(title = "지금 뭐 하고 있나요?", subtitle = "누르면 바로 바뀌어요")
        Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM12)) {
            BearerActivity.entries.chunked(COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ColumnSpacing)) {
                    row.forEach { activity ->
                        FCChoiceTile(
                            title = activity.title,
                            icon = activity.icon,
                            modifier = Modifier.weight(1f).height(ActivityTileHeight),
                            size = FCChoiceTileSize.Activity,
                            isSelected = activity == selected,
                            onClick = { onActivityTapped(activity) },
                        )
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PickerColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = FestiCoreSpacing.paddingP16)
                .padding(bottom = FestiCoreSpacing.paddingP16),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16),
    ) { content() }
}

@Composable
private fun PickerHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM4)) {
        BasicText(
            text = title,
            style = FestiCoreTextStyle.baseTitle2Bold.textStyle.copy(color = FestiCoreSemanticColor.labelStrong.color),
        )
        BasicText(
            text = subtitle,
            style = FestiCoreTextStyle.baseFootnoteRegular.textStyle.copy(color = FestiCoreSemanticColor.labelAssistive.color),
        )
    }
}

/** 행 수만큼의 높이, 최대 420(`BZV:87-98`) */
private fun zoneGridHeight(count: Int): Dp {
    val rows = (count + 1) / COLUMNS
    val gaps = (rows - 1).coerceAtLeast(0)
    return (ZoneTileHeight * rows + FestiCoreSpacing.marginM12 * gaps).coerceAtMost(ZoneGridMaxHeight)
}

/** 활동 아이콘(`BAV:77-84`) */
private val BearerActivity.icon: Int
    @DrawableRes get() =
        when (this) {
            BearerActivity.Slam -> R.drawable.ic_users_24
            BearerActivity.Sing -> R.drawable.ic_music_24
            BearerActivity.Train -> R.drawable.ic_route_24
            BearerActivity.Calm -> R.drawable.ic_user_24
        }
