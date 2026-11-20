package io.festicore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.festicore.ui.designkit.R as DesignKitR
import io.festicore.ui.designkit.theme.FestiCoreTheme
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FestiCoreTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = FestiCoreSemanticColor.backgroundNormal.color) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.marginM16, Alignment.CenterVertically),
                    ) {
                        Image(
                            painter = painterResource(DesignKitR.drawable.logo),
                            contentDescription = stringResource(R.string.app_name),
                            colorFilter = ColorFilter.tint(FestiCoreSemanticColor.labelNormal.color),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = FestiCoreTextStyle.baseHeadlineBold.textStyle,
                            color = FestiCoreSemanticColor.labelNeutral.color,
                        )
                    }
                }
            }
        }
    }
}
