package io.festicore.ui.designkit.component.label

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.festicore.ui.designkit.component.FCPreviewSurface
import io.festicore.ui.designkit.component.FCPreviews
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

@FCPreviews
@Composable
private fun FCBadgePreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            FCBadge(count = 3)
            FCBadge(count = 12)
        }
    }
}

@FCPreviews
@Composable
private fun FCBearerFooterPreview() {
    FCPreviewSurface {
        FCBearerFooter()
        FCBearerFooter(queuedCount = 1)
    }
}

@FCPreviews
@Composable
private fun FCChecklistProgressPreview() {
    FCPreviewSurface {
        FCChecklistProgress(checked = 3, total = 7)
        FCChecklistProgress(checked = 7, total = 7)
    }
}

@FCPreviews
@Composable
private fun FCLiveBadgePreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            FCLiveBadgeKind.entries.forEach { FCLiveBadge(it) }
        }
    }
}

@FCPreviews
@Composable
private fun FCLivePulsePreview() {
    FCPreviewSurface {
        FCLivePulse()
    }
}

@FCPreviews
@Composable
private fun FCOfflineBadgePreview() {
    FCPreviewSurface {
        FCOfflineBadge("오프라인 · 3분 전 정보")
    }
}

@FCPreviews
@Composable
private fun FCPackDownloadProgressPreview() {
    FCPreviewSurface {
        FCPackDownloadProgress("팩 받는 중 · 62%", progress = 0.62)
        FCPackDownloadProgress("팩 받는 중")
    }
}

@FCPreviews
@Composable
private fun FCSourceLinePreview() {
    FCPreviewSurface {
        FCSourceLine("출처: 주최측 공지 · 확인 10/16 14:00")
        FCSourceLine("출처: 주최측 공지 · 확인 10/16 14:00", isInset = false)
    }
}

@FCPreviews
@Composable
private fun FCStatusChipPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP12)) {
            FCStatusChipStatus.entries.forEach { FCStatusChip(it) }
        }
    }
}

@FCPreviews
@Composable
private fun FCTagPreview() {
    FCPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
            FCTagTone.entries.forEach { tone ->
                if (tone == FCTagTone.Inverse) {
                    FCTag(
                        "슬램",
                        modifier =
                            Modifier
                                .background(FestiCoreSemanticColor.staticalphaBlack80.color)
                                .padding(FestiCoreSpacing.paddingP4),
                        tone = tone,
                    )
                } else {
                    FCTag("슬램", tone = tone)
                }
            }
        }
    }
}

@FCPreviews
@Composable
private fun FCZoneChipPreview() {
    FCPreviewSurface {
        FCZoneChip("리벳 앞 왼쪽") {}
    }
}
