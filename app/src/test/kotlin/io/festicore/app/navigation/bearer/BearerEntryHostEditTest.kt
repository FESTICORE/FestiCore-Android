package io.festicore.app.navigation.bearer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.festicore.domain.bearer.bearer.BearerUseCase
import io.festicore.domain.bearer.entity.BearerBoard
import io.festicore.domain.bearer.entity.BearerFestival
import io.festicore.domain.bearer.entity.BearerFlagLifecycle
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagStatus
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerProfileChange
import io.festicore.domain.bearer.testing.FakeBearerUseCase
import io.festicore.feature.bearer.BearerRoute
import io.festicore.feature.bearer.di.bearerFeatureModule
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-mdpi")
class BearerEntryHostEditTest {
    @get:Rule val composeRule = createComposeRule()
    private val profile = BearerFlagProfile("flag", "호락호락", tags = listOf(BearerFlagTag.Slam))
    private var festivalFails = false
    private var saves = 0
    private var invites = 0
    private var closes = 0
    private var savedChange: BearerProfileChange? = null
    private var response: CompletableDeferred<BearerFlagProfile>? = null
    private var visible by mutableStateOf(true)

    @Before
    fun setUp() {
        val fake = FakeBearerUseCase(
            onFetchFestival = {
                if (festivalFails) error("팩 조회 실패")
                BearerFestival("festival", "원더리벳 2026", emptyList())
            },
            onFetchBoard = {
                BearerBoard(profile, BearerFlagStatus("flag", BearerFlagLifecycle.Scheduled, null, null, Instant.EPOCH), 0)
            },
            onUpdateProfile = { _, change ->
                savedChange = change
                saves++
                response?.await() ?: profile
            },
            onInviteCoBearer = {
                invites++
                error("예상하지 않은 초대")
            },
        )
        startKoin { modules(bearerFeatureModule, module { single<BearerUseCase> { fake } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `마이 편집 저장은 완료 화면 없이 호스트를 닫는다`() {
        show()
        composeRule.onNodeWithText("프로필 수정").assertIsDisplayed()
        composeRule.onNodeWithText("저장").performClick()
        composeRule.waitUntil { closes == 1 }
        assertEquals(1, saves)
        composeRule.onNodeWithText("깃발이 준비됐어요").assertDoesNotExist()
    }

    @Test
    fun `팩 조회 실패에도 잠긴 축제 대체 이름을 표시하고 저장한다`() {
        festivalFails = true
        show()
        val tile = composeRule.onNode(hasText("참여 페스티벌") and isSelected())
        tile.assertIsNotEnabled().assertIsSelected()
        tile.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "참여 페스티벌은 바꿀 수 없어요"))
        composeRule.onNodeWithText("참여 페스티벌은 바꿀 수 없어요. 다른 페스티벌 깃발은 마이 > '다른 페스티벌 깃발 만들기'에서 새로 만들어요.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("저장").performClick()
        composeRule.waitUntil { closes == 1 }
        assertEquals(1, saves)
    }

    @Test
    fun `계획 선택에서 돌아온 뒤 저장 중에는 새 계획이나 초대를 열지 않는다`() {
        response = CompletableDeferred()
        show()
        composeRule.onNodeWithText("오늘 계획").performScrollTo().performClick()
        composeRule.waitUntil { composeRule.onNodeWithText("오늘 갈 무대를 골라 주세요").isDisplayed() }
        composeRule.onNodeWithText("0개 선택 완료").performClick()
        composeRule.waitUntil { composeRule.onNodeWithText("저장").isDisplayed() }
        composeRule.onNodeWithText("저장").performClick()
        composeRule.waitUntil { saves == 1 }
        assertEquals(emptyList<String>(), savedChange?.plan)
        composeRule.onNodeWithText("오늘 계획").performScrollTo().performClick()
        composeRule.onNodeWithText("공동 기수 초대").performScrollTo().performClick()
        composeRule.onNodeWithText("오늘 갈 무대를 골라 주세요").assertDoesNotExist()
        assertEquals(0, invites)
        composeRule.runOnIdle { response?.complete(profile) }
        composeRule.waitUntil { closes == 1 }
        assertEquals(1, saves)
    }

    @Test
    fun `일반 등록 경로는 저장 뒤 완료 화면을 표시한다`() {
        show(editMode = false)
        composeRule.onNodeWithText("저장하기").performClick()
        composeRule.waitUntil { composeRule.onNodeWithText("깃발이 준비됐어요").isDisplayed() }
        composeRule.onNodeWithText("깃발이 준비됐어요").assertIsDisplayed()
        assertEquals(1, saves)
        assertEquals(0, closes)
    }

    private fun show(editMode: Boolean = true) {
        composeRule.setContent {
            FestiCoreTheme {
                if (visible) {
                    BearerEntryHost(
                        startRoute = BearerRoute.Profile("festival", "flag", isEditMode = editMode),
                        viewerUserId = "viewer",
                        onRegistered = {},
                        onPackRequested = {},
                        onClose = {
                            closes++
                            visible = false
                        },
                    )
                }
            }
        }
        composeRule.waitUntil {
            composeRule.onAllNodes(hasText(if (editMode) "저장" else "저장하기")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
