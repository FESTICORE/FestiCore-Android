package io.festicore.ui.sharedui.sheet

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor

@Preview(name = "FCReportSheet")
@Composable
private fun ReportSheetPreview() {
    var selection by remember { mutableStateOf<Int?>(null) }
    FCReportSheet(
        selection = selection,
        options = listOf("가짜 깃발이에요", "부적절한 사진이나 소개예요", "기타"),
        onSelect = { selection = it },
        onSubmit = {},
        modifier = Modifier.background(FestiCoreSemanticColor.backgroundElevated.color),
    )
}
