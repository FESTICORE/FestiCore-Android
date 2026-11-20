package io.festicore.ui.designkit.component.action

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

@FCPreviews
@Composable
private fun FCButtonPreview() {
    FCPreviewSurface {
        FCButtonStyle.entries.forEach { FCButton(it.name, style = it, isFullWidth = true) {} }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCButton("중간", size = FCButtonSize.Medium) {}
            FCButton("비활성", isEnabled = false) {}
        }
    }
}

@FCPreviews
@Composable
private fun FCBigButtonPreview() {
    FCPreviewSurface {
        Row(
            modifier = Modifier.height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8),
        ) {
            FCBigButton("관객", sub = "공연을 즐겨요", icon = R.drawable.ic_user_24, modifier = Modifier.weight(1f)) {}
            FCBigButton("기수", sub = "깃발을 들어요", icon = R.drawable.ic_flag, modifier = Modifier.weight(1f), isSelected = true) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCBigButton("음악", sub = "", icon = R.drawable.ic_music_20, modifier = Modifier.weight(1f), size = FCBigButtonSize.Small) {}
            FCBigButton(
                "티켓",
                sub = "",
                icon = R.drawable.ic_ticket_24,
                modifier = Modifier.weight(1f),
                isSelected = true,
                size = FCBigButtonSize.Small,
            ) {}
        }
    }
}

@FCPreviews
@Composable
private fun FCChipAndPillPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCChip("전체", isSelected = true) {}
            FCChip("락") {}
        }
        FCBackPill("관객 화면으로") {}
        FCSortButton("활동 중 순") {}
        FCLoginButton(FCLoginButtonProvider.Google) {}
    }
}

@FCPreviews
@Composable
private fun FCHeroFollowButtonPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCHeroFollowButton(isFollowing = false) {}
            FCHeroFollowButton(isFollowing = true) {}
        }
    }
}

@FCPreviews
@Composable
private fun FCMenuPreview() {
    FCPreviewSurface {
        FCMenuItem("활동 중 순", isSelected = true)
        FCSortMenu(selection = 1, onSelectionChange = {}, items = listOf("최신 순", "활동 중 순", "이름 순"))
    }
}

@FCPreviews
@Composable
private fun FCBearerPreview() {
    FCPreviewSurface {
        FCBearerPrompt {}
        FCBearerCTA(FCBearerCTAState.Start) {}
        FCBearerCTA(FCBearerCTAState.Restart) {}
    }
}
