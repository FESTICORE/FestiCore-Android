package io.festicore.feature.profile.appearance

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.ui.designkit.theme.FestiCoreTheme

@Preview(showBackground = true)
@Composable
private fun AppearanceSettingsPreview() {
    FestiCoreTheme { AppearanceSettingsContent(state = AppearanceSettingsState(mode = AppearanceMode.System)) }
}
