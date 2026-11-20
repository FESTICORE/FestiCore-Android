package io.festicore.ui.designkit.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

/** 컴포넌트 미리보기를 라이트 · 다크로 함께 그린다. */
@Preview(name = "라이트", showBackground = true)
@Preview(name = "다크", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
internal annotation class FCPreviews

@Composable
internal fun FCPreviewSurface(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .background(FestiCoreSemanticColor.backgroundNormal.color)
                .padding(FestiCoreSpacing.paddingP20),
        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12),
    ) {
        content()
    }
}
