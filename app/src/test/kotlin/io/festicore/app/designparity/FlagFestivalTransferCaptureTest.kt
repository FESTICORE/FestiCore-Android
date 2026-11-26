package io.festicore.app.designparity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.festival.entity.FestivalFlagDetail
import io.festicore.feature.bearer.register.BearerFestivalList
import io.festicore.feature.bearer.register.BearerFestivalLoad
import io.festicore.feature.bearer.transfer.BearerFestivalTransferCallbacks
import io.festicore.feature.bearer.transfer.BearerFestivalTransferContent
import io.festicore.feature.bearer.transfer.BearerFestivalTransferState
import io.festicore.feature.bearer.transfer.BearerTransferEligibility
import io.festicore.feature.festival.flagdetail.FlagDetailContent
import io.festicore.feature.festival.flagdetail.FlagDetailContext
import io.festicore.feature.festival.flagdetail.FlagDetailState
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * 깃발 상세 기수 메뉴 → 축제 이동 화면 → 확인 팝업(Android 먼저, 백엔드 #409). 새 모양 없이 S07 G 메뉴 시트 · S14 D 페스티벌 고르기 ·
 * FCDialog를 쓰는지 화면으로 확인하고, 라이트 · 다크 캡처를 `build/reports/design-parity/`에 남긴다(PR 첨부용, 픽셀 일치 판정 아님).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class FlagFestivalTransferCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-21T12:00:00Z")

    private val detail =
        FestivalFlagDetail(
            id = "flg_1",
            name = "호락호락",
            thumbnailUrl = null,
            photoUrl = null,
            tags = listOf("slam"),
            bio = "처음 와도 괜찮아요",
            followerCount = 128,
            isPlanVisible = true,
            planSetIds = emptyList(),
            homeZoneId = null,
            status = FestivalFlagDetail.Status.Scheduled,
            zoneId = null,
            activity = null,
            statusUpdatedAt = now,
            isFollowing = false,
            isBearer = true,
            isHidden = false,
        )

    private fun detailState(isMenuPresented: Boolean, isBearer: Boolean = true) = FlagDetailState(
        flagId = "flg_1",
        context = FlagDetailContext(hasSession = true),
        now = now,
        content = FlagDetailContent.Loaded(detail.copy(isBearer = isBearer)),
        lastLoadedAt = now,
        canTransferFestival = isBearer,
        isMenuPresented = isMenuPresented,
    )

    private val busan = BearerFestivalOption(id = "fst_busan", name = "부산국제록페스티벌 2026", dateText = "11/30-12/1", hasPack = true)
    private val seoul = BearerFestivalOption(id = "fst_seoul", name = "서울 파크 뮤직 페스티벌", dateText = "12/19-20", hasPack = false)

    private fun transferState(eligibility: BearerTransferEligibility = BearerTransferEligibility.Eligible, isConfirming: Boolean = false) =
        BearerFestivalTransferState(
            flagId = "flg_1",
            fromFestivalId = "fst_wonder",
            eligibility = eligibility,
            festivals = BearerFestivalList(load = BearerFestivalLoad.Loaded, received = listOf(busan), upcoming = listOf(seoul)),
            selectedFestivalId = "fst_busan",
            isConfirming = isConfirming,
        )

    @Test
    @Config(qualifiers = "w393dp-h852dp-notnight-xxhdpi")
    fun bearerMenuLight() = bearerMenu("light")

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-xxhdpi")
    fun bearerMenuDark() = bearerMenu("dark")

    private fun bearerMenu(theme: String) {
        val taps = mutableListOf<String>()
        composeRule.setContent {
            FestiCoreTheme {
                FlagDetailContent(
                    state = detailState(isMenuPresented = true),
                    onMenuTransferTapped = { taps += "transfer" },
                    onMenuShareTapped = { taps += "share" },
                )
            }
        }
        composeRule.onNodeWithText("깃발 관리").assertExists()
        composeRule.onNodeWithText("공유하기").assertExists()
        composeRule.onNodeWithText("옮기면 계획·주로 있는 존·현장 상태가 초기화돼요.").assertExists()
        capture("flag-detail-menu-$theme")
        composeRule.onNodeWithText("다른 페스티벌로 옮기기").performClick()
        assertEquals(listOf("transfer"), taps)
    }

    /** 기수가 아니면 상단 바는 공유 그대로이고 메뉴가 없다. */
    @Test
    @Config(qualifiers = "w393dp-h852dp-notnight-xxhdpi")
    fun nonBearerKeepsShare() {
        composeRule.setContent { FestiCoreTheme { FlagDetailContent(state = detailState(isMenuPresented = false, isBearer = false)) } }
        composeRule.onNodeWithContentDescription("공유").assertExists()
        composeRule.onNodeWithContentDescription("깃발 관리").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-notnight-xxhdpi")
    fun transferLight() = transfer("light")

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-xxhdpi")
    fun transferDark() = transfer("dark")

    private fun transfer(theme: String) {
        var transferTaps = 0
        composeRule.setContent {
            FestiCoreTheme {
                BearerFestivalTransferContent(
                    state = transferState(),
                    callbacks = BearerFestivalTransferCallbacks(onTransferTapped = { transferTaps++ }),
                )
            }
        }
        composeRule.onNodeWithText("어느 페스티벌로 옮길까요?").assertExists()
        composeRule.onNodeWithText("내가 받은 팩").assertExists()
        composeRule.onNodeWithText("옮기기").assertIsEnabled()
        capture("festival-transfer-$theme")
        composeRule.onNodeWithText("옮기기").performClick()
        assertEquals(1, transferTaps)
    }

    @Test
    @Config(qualifiers = "w393dp-h852dp-notnight-xxhdpi")
    fun confirmLight() = confirm("light")

    @Test
    @Config(qualifiers = "w393dp-h852dp-night-xxhdpi")
    fun confirmDark() = confirm("dark")

    private fun confirm(theme: String) {
        var confirms = 0
        composeRule.setContent {
            FestiCoreTheme {
                BearerFestivalTransferContent(
                    state = transferState(isConfirming = true),
                    callbacks = BearerFestivalTransferCallbacks(onConfirmTapped = { confirms++ }),
                )
            }
        }
        composeRule.onNodeWithText("이 페스티벌로 옮길까요?").assertExists()
        composeRule.onNodeWithText("부산국제록페스티벌 2026 · 11/30-12/1\n계획·주로 있는 존·현장 상태가 초기화돼요.").assertExists()
        capture("festival-transfer-confirm-$theme")
        // 팝업의 '옮기기'(화면 아래 버튼과 같은 글자)는 나중에 그린 팝업 창에 있다.
        val buttons = composeRule.onAllNodesWithText("옮기기")
        buttons[buttons.fetchSemanticsNodes().size - 1].performClick()
        assertEquals(1, confirms)
    }

    /** 공동 기수가 있으면 버튼을 끄고 이유를 보인다. */
    @Test
    @Config(qualifiers = "w393dp-h852dp-notnight-xxhdpi")
    fun coBearersBlock() {
        composeRule.setContent {
            FestiCoreTheme { BearerFestivalTransferContent(state = transferState(BearerTransferEligibility.HasCoBearers)) }
        }
        composeRule.onNodeWithText("공동 기수가 있는 깃발은 옮길 수 없어요. 기수 보드에서 공동 기수를 먼저 해제해 주세요.").assertExists()
        composeRule.onNodeWithText("옮기기").assertIsNotEnabled()
        capture("festival-transfer-cobearers-light")
    }

    /** 화면과 떠 있는 시트 · 팝업을 한 장의 PNG로 남긴다. */
    private fun capture(name: String) {
        composeRule.waitForIdle()
        val dir = File("build/reports/design-parity").apply { mkdirs() }
        val screen = composeRule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        // 시트 · 팝업은 따로 뜨는 창이라 compose 캡처가 화면 창을 다시 찍는다. 창의 뷰를 직접 그려 화면 위에 겹친다.
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
        val bitmap =
            if (dialog == null) {
                screen
            } else {
                val decor = dialog.window?.decorView ?: error("팝업 창이 없다")
                val out = screen.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(out)
                // Robolectric은 팝업 창의 가운데 정렬(gravity)을 적용하지 않아, 기기처럼 화면 가운데에 놓는다. 아래 시트는 창이 화면 크기라 그대로다.
                canvas.translate((out.width - decor.width) / 2f, (out.height - decor.height) / 2f)
                decor.draw(canvas)
                out
            }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
