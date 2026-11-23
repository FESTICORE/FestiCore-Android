package io.festicore.core.storage

import android.app.Application
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveBoardPreferenceStoreTest {
    private val stores = TestDataStores("live_board_test")

    @After
    fun tearDown() = runTest { stores.close() }

    @Test
    fun `고른 축제는 다시 열어도 남는다`() = runTest {
        val store = DataStoreLiveBoardPreferenceStore(stores.open())
        assertNull(store.selectedFestivalId())

        store.saveSelectedFestivalId("fst_a")
        stores.close()

        assertEquals("fst_a", DataStoreLiveBoardPreferenceStore(stores.open()).selectedFestivalId())
    }

    @Test
    fun `팔로우 안내 숨김은 축제마다 따로 남고 빈 ID는 저장하지 않는다`() = runTest {
        val store = DataStoreLiveBoardPreferenceStore(stores.open())
        store.hideFollowedFlagGuide("fst_a")
        store.hideFollowedFlagGuide("")

        assertTrue(store.isFollowedFlagGuideHidden("fst_a"))
        assertFalse(store.isFollowedFlagGuideHidden("fst_b"))
        assertFalse(store.isFollowedFlagGuideHidden(""))
    }
}
