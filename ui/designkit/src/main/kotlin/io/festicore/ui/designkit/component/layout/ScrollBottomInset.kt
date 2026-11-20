package io.festicore.ui.designkit.component.layout

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.festicore.ui.designkit.tokens.FestiCoreSpacing

/** 스크롤 내용 끝에 하단 여백을 둔다. `verticalScroll` 안쪽 내용에 붙이고, Lazy 목록은 `contentPadding`으로 같은 값을 준다. */
fun Modifier.fcScrollBottomInset(): Modifier = padding(bottom = FestiCoreSpacing.paddingP20)
