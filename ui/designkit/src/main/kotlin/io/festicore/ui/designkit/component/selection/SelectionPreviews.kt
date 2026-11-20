package io.festicore.ui.designkit.component.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.dp
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.component.label.FCTag
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

@FCPreviews
@Composable
private fun FCSelectionControlsPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP16)) {
            FCCheckCircle(isChecked = true)
            FCCheckCircle(isChecked = false)
            var isOn by remember { mutableStateOf(true) }
            FCToggle(isOn = isOn, onIsOnChange = { isOn = it }, accessibilityLabel = "알림")
            FCToggle(isOn = false, onIsOnChange = {}, accessibilityLabel = "잠금화면 표시")
        }
        FCFieldLabel("성향 태그", counter = "2 / 3")
        FCFlowLayout {
            listOf("떼창", "슬램", "펜스", "응원봉", "사진").forEach { FCTag(it) }
        }
        FCRecentKeyword("펜타포트", onSelect = {}, onDelete = {})
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCChoiceTile("리벳 앞", icon = R.drawable.ic_location_dot_16, modifier = Modifier.weight(1f), isSelected = true) {}
            FCChoiceTile("원더 뒤", icon = R.drawable.ic_location_dot_16, modifier = Modifier.weight(1f)) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCChoiceTile(
                "떼창",
                icon = R.drawable.ic_music_24,
                modifier = Modifier.weight(1f).height(96.dp),
                size = FCChoiceTileSize.Activity,
                isSelected = true,
            ) {}
            FCChoiceTile("깃발", icon = R.drawable.ic_flag, modifier = Modifier.weight(1f).height(96.dp), size = FCChoiceTileSize.Activity) {}
        }
    }
}

@FCPreviews
@Composable
private fun FCSelectionRowsPreview() {
    FCPreviewSurface {
        FCConsentAll(isChecked = true) {}
        FCConsentRow("서비스 이용약관 동의", isChecked = true, tag = "필수", onDetail = {}) {}
        FCConsentRow("마케팅 정보 수신 동의", isChecked = false, tag = "선택", sub = "이벤트 · 혜택 알림을 받아요") {}
        FCChecklistRow("보조배터리", isChecked = false) {}
        FCChecklistRow("신분증", isChecked = true, isRequired = true) {}
        FCChecklistEditRow("선크림", onDelete = {})
        FCChecklistEditRow("티켓")
        FCAddItemRow(text = "선크림", onTextChange = {}) {}
        FCSetSelectRow(artist = "coldrain", stage = "WONDER STAGE", start = "16:40", end = "17:30", isSelected = false) {}
        FCSetSelectRow(
            artist = "실리카겔",
            stage = "MAIN STAGE",
            start = "17:00",
            end = "18:00",
            isSelected = true,
            overlapText = "coldrain과 30분 겹쳐요",
        ) {}
    }
}

@FCPreviews
@Composable
private fun FCSelectionInputsPreview() {
    val photo = ColorPainter(FestiCoreSemanticColor.accentBackgroundLime.color)
    FCPreviewSurface {
        var query by remember { mutableStateOf("펜타") }
        FCSearchField(text = query, onTextChange = { query = it })
        FCSearchField(text = "", onTextChange = {})
        var nickname by remember { mutableStateOf("페스티") }
        FCTextField(text = nickname, onTextChange = { nickname = it }, placeholder = "닉네임", label = "닉네임", maxLength = 10, helper = "2~10자")
        FCTextField(text = "!!", onTextChange = {}, placeholder = "닉네임", label = "닉네임", error = "특수문자는 쓸 수 없어요")
        FCPhotoPicker(FCPhotoPickerState.Empty) {}
        FCPhotoPicker(FCPhotoPickerState.Uploading(image = photo, progress = 0.64)) {}
        FCPhotoPicker(FCPhotoPickerState.Filled(photo)) {}
    }
}
