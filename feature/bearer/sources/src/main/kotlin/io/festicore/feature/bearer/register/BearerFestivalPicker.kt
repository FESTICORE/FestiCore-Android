package io.festicore.feature.bearer.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.festicore.domain.festival.catalog.FestivalCatalogUseCase
import io.festicore.domain.festival.receivedpack.FestivalReceivedPackUseCase
import io.festicore.feature.bearer.profile.BearerZoneButton
import io.festicore.feature.bearer.profile.Helper
import io.festicore.feature.bearer.profile.SectionLabel
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonSize
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.skeleton.FCSkeletonShape
import io.festicore.ui.designkit.component.skeleton.FCSkeletonView
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import kotlin.coroutines.cancellation.CancellationException

// iOS `BearerRegisterView.Metric`: 라벨 · 내용 · 안내 사이 12(`BRV:470-475`), 페스티벌 스켈레톤 높이 44(`BRV:161`)
private val GroupSpacing = 12.dp
private val FestivalSkeletonHeight = 44.dp

/** 받은 팩 · 서버 페스티벌 목록. 서버에 연결하지 못하면 기기에 저장된 목록을 쓰고 [isOffline]을 켠다. */
internal data class BearerFestivalLoadResult(
    val options: BearerRegisterViewModel.Companion.Options,
    val isOffline: Boolean,
)

/** 깃발 등록(S14 D)과 축제 이동이 같은 순서 · 같은 실패 처리로 페스티벌 후보를 읽는다. [today]는 `yyyy-MM-dd`. */
internal suspend fun loadBearerFestivalOptions(
    receivedPackUseCase: FestivalReceivedPackUseCase,
    catalogUseCase: FestivalCatalogUseCase,
    today: String,
): BearerFestivalLoadResult {
    val packs =
        try {
            receivedPackUseCase.receivedPacks()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
    var isOffline = false
    val catalog =
        try {
            catalogUseCase.refreshFestivals()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 연결되지 않았으면 기기에 저장된 목록으로 고르게 한다.
            isOffline = true
            try {
                catalogUseCase.cachedFestivals()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                emptyList()
            }
        }
    return BearerFestivalLoadResult(BearerRegisterViewModel.options(packs = packs, catalog = catalog, today = today), isOffline)
}

/**
 * 페스티벌 고르기(Figma S14 D '어느 페스티벌의 깃발인가요?'). 깃발 등록과 축제 이동이 같은 모양을 쓴다.
 *
 * @param failedMessage 목록을 받지 못했을 때(오프라인이 아닐 때) 안내
 * @param helper 목록 아래 안내. 받은 팩이 있으면 true를 받는다.
 */
@Composable
internal fun BearerFestivalPicker(
    title: String,
    festivals: BearerFestivalList,
    selectedFestivalId: String?,
    failedMessage: String,
    helper: (hasReceived: Boolean) -> String,
    onFestivalTapped: (String) -> Unit,
    onRetryTapped: () -> Unit,
    onShowMoreTapped: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupSpacing)) {
        SectionLabel(title = title, count = null)
        when (val load = festivals.load) {
            BearerFestivalLoad.Loading ->
                repeat(BearerRegisterState.UPCOMING_PREVIEW_COUNT) {
                    FCSkeletonView(FCSkeletonShape.Round(), Modifier.fillMaxWidth().height(FestivalSkeletonHeight))
                }

            is BearerFestivalLoad.Failed ->
                Column(verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP8)) {
                    Helper(if (load.offline) "인터넷에 연결되지 않아 페스티벌을 불러오지 못했어요." else failedMessage)
                    FCButton(
                        title = "다시 시도",
                        style = FCButtonStyle.Secondary,
                        size = FCButtonSize.Medium,
                        onClick = onRetryTapped,
                    )
                }

            BearerFestivalLoad.Loaded -> FestivalRows(festivals, selectedFestivalId, helper, onFestivalTapped, onShowMoreTapped)
        }
    }
}

@Composable
private fun ColumnScope.FestivalRows(
    festivals: BearerFestivalList,
    selectedFestivalId: String?,
    helper: (hasReceived: Boolean) -> String,
    onFestivalTapped: (String) -> Unit,
    onShowMoreTapped: () -> Unit,
) {
    val hasReceived = festivals.received.isNotEmpty()
    if (hasReceived) {
        Helper("내가 받은 팩")
        festivals.received.forEach { option ->
            BearerZoneButton(name = option.title, isSelected = selectedFestivalId == option.id, onClick = { onFestivalTapped(option.id) })
        }
        if (festivals.visibleUpcoming.isNotEmpty()) {
            Helper("다른 페스티벌")
        }
    }
    festivals.visibleUpcoming.forEach { option ->
        BearerZoneButton(name = option.title, isSelected = selectedFestivalId == option.id, onClick = { onFestivalTapped(option.id) })
    }
    if (festivals.canShowMore) {
        FCButton(
            title = "다른 페스티벌 찾기",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = FCButtonStyle.Ghost,
            onClick = onShowMoreTapped,
        )
    }
    Helper(helper(hasReceived))
}
