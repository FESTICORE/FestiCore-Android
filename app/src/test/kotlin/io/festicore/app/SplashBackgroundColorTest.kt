package io.festicore.app

import androidx.compose.ui.graphics.toArgb
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 시스템 스플래시 배경 색 리소스가 생성 토큰 staticInk와 같은지 본다(토큰이 바뀌면 여기서 실패한다).
 * Robolectric으로 앱을 띄우면 `FestiCoreApplication`이 Koin을 한 번 더 시작하므로, 리소스 파일을 직접 읽는 JVM 테스트로 둔다.
 */
class SplashBackgroundColorTest {
    @Test
    fun `시스템 스플래시 배경은 staticInk와 같다`() {
        val xml = File("src/main/res/values/colors.xml").readText()
        val hex =
            Regex("""<color name="splash_background">#([0-9A-Fa-f]{6})</color>""").find(xml)?.groupValues?.get(1)
                ?: error("colors.xml에 splash_background가 없다")
        val resource = (0xFF000000 or hex.toLong(16)).toInt()
        // 시스템 스플래시는 다크 모드를 따로 두지 않으므로 light · dark 모두 같아야 한다.
        assertEquals(FestiCoreSemanticColor.staticInk.light.toArgb(), resource)
        assertEquals(FestiCoreSemanticColor.staticInk.dark.toArgb(), resource)
    }
}
