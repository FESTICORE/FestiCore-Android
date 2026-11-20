package io.festicore.ui.designkit.font

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.festicore.ui.designkit.R

object FestiCoreFontFamily {
    // iOS와 같은 가변 서체 파일을 굵기 축으로 나눠 쓴다. API 26 미만은 굵기 축이 적용되지 않는다.
    val pretendard: FontFamily =
        FontFamily(
            pretendard(FontWeight.Normal),
            pretendard(FontWeight.Medium),
            pretendard(FontWeight.Bold),
        )

    val bagelFatOne: FontFamily = FontFamily(Font(R.font.bagel_fat_one_regular))

    private fun pretendard(weight: FontWeight) = Font(
        resId = R.font.pretendard_variable,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}
