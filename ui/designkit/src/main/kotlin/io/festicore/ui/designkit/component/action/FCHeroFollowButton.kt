package io.festicore.ui.designkit.component.action

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.tokens.FestiCoreRadius
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreState
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyleWithoutLineBox

private val Height = 40.dp
private val IconSize = 16.dp
private val BorderWidth = 1.dp

@Composable
fun FCHeroFollowButton(
    isFollowing: Boolean,
    modifier: Modifier = Modifier,
    followTitle: String = "팔로우",
    followingTitle: String = "팔로잉",
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(FestiCoreRadius.r8)
    val foreground = if (isFollowing) FestiCoreSemanticColor.staticWhite.color else FestiCoreSemanticColor.staticInk.color
    Row(
        modifier =
            modifier
                .semantics { selected = isFollowing }
                .height(Height)
                .clip(shape)
                .background(if (isFollowing) FestiCoreSemanticColor.staticalphaWhite20.color else FestiCoreSemanticColor.staticWhite.color)
                .then(if (isFollowing) Modifier.border(BorderWidth, FestiCoreSemanticColor.staticalphaWhite40.color, shape) else Modifier)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = FestiCoreSpacing.paddingP16),
        horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFollowing) {
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                colorFilter = ColorFilter.tint(foreground),
            )
        }
        BasicText(
            text = if (isFollowing) followingTitle else followTitle,
            style = FestiCoreTextStyle.baseSubheadlineBold.textStyleWithoutLineBox.copy(color = foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
