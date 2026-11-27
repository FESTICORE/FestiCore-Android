package io.festicore.feature.festival.festivallist

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.festicore.domain.festival.entity.FestivalCatalogItem
import io.festicore.ui.designkit.theme.FestiCoreTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FestivalSearchDesignParityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `아티스트 검색을 지원하는 빈 결과는 시안의 안내를 표시한다`() {
        showEmptySearch(supportsArtists = true)
        composeRule.onNodeWithText("페스티벌 이름, 아티스트, 장소로 다시 찾아보세요").assertExists()
    }

    @Test
    fun `아티스트 검색 미지원 목록은 장소 안내를 유지한다`() {
        showEmptySearch(supportsArtists = false)
        composeRule.onNodeWithText("페스티벌 이름이나 장소로 다시 찾아보세요").assertExists()
    }

    private fun showEmptySearch(supportsArtists: Boolean) {
        composeRule.setContent {
            FestiCoreTheme {
                FestivalListContent(
                    state = FestivalListState(
                        viewState = FestivalListViewState.Loaded,
                        hasFinishedFirstCatalogRefresh = true,
                        isSearching = true,
                        query = "없는 검색어",
                        catalog = listOf(
                            FestivalCatalogItem(
                                id = "festival",
                                name = "페스티벌",
                                packVersion = 1,
                                artists = if (supportsArtists) emptyList() else null,
                            ),
                        ),
                    ),
                    now = Instant.EPOCH,
                )
            }
        }
    }
}
