package io.festicore.ui.designkit.component.skeleton

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.festicore.ui.designkit.tokens.FestiCoreRadius

/** 스켈레톤이 그릴 모양. iOS처럼 그대로 `Shape`로도 쓸 수 있다. */
sealed class FCSkeletonShape : Shape {
    data object Rect : FCSkeletonShape() {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline = RectangleShape.createOutline(size, layoutDirection, density)
    }

    data class Round(val cornerRadius: Dp = FestiCoreRadius.r12) : FCSkeletonShape() {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline = RoundedCornerShape(cornerRadius).createOutline(size, layoutDirection, density)
    }

    data object Circle : FCSkeletonShape() {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline = CircleShape.createOutline(size, layoutDirection, density)
    }
}
