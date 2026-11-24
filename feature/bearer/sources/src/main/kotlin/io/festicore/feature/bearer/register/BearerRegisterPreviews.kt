package io.festicore.feature.bearer.register

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.festicore.domain.bearer.entity.BearerClaim
import io.festicore.domain.bearer.entity.BearerFestivalOption
import io.festicore.domain.bearer.entity.BearerFlagProfile
import io.festicore.domain.bearer.entity.BearerFlagTag
import io.festicore.domain.bearer.entity.BearerZone
import io.festicore.ui.designkit.theme.FestiCoreTheme

// 프리뷰 전용 가짜 값.
private val PreviewReceived = BearerFestivalOption(id = "fst_a", name = "페스티벌 미리보기 A", dateText = "11/20-22", hasPack = true)
private val PreviewUpcoming =
    listOf(
        BearerFestivalOption(id = "fst_b", name = "페스티벌 미리보기 B", dateText = "11/30-12/1", hasPack = false),
        BearerFestivalOption(id = "fst_c", name = "페스티벌 미리보기 C", dateText = "12/19-20", hasPack = false),
        BearerFestivalOption(id = "fst_d", name = "페스티벌 미리보기 D", dateText = "1/10", hasPack = false),
        BearerFestivalOption(id = "fst_e", name = "페스티벌 미리보기 E", dateText = null, hasPack = false),
    )
private val PreviewZones = listOf(BearerZone(id = "zone_a", name = "미리보기 존 A"), BearerZone(id = "zone_b", name = "미리보기 존 B"))
private val PreviewClaim =
    BearerClaim(
        flagId = "flg_preview",
        profile = BearerFlagProfile(id = "flg_preview", name = "미리보기 깃발", tags = listOf(BearerFlagTag.Newbie), homeZoneId = "zone_a"),
        isNew = true,
        coBearerIds = emptyList(),
    )

private fun loadedState(withPack: Boolean): BearerRegisterState = BearerRegisterState(
    festivals =
        BearerFestivalList(
            load = BearerFestivalLoad.Loaded,
            received = if (withPack) listOf(PreviewReceived) else emptyList(),
            upcoming = PreviewUpcoming,
        ),
    selectedFestivalId = if (withPack) PreviewReceived.id else PreviewUpcoming.first().id,
    zones = if (withPack) PreviewZones else emptyList(),
)

@Preview(showBackground = true)
@Composable
private fun BearerRegisterUpcomingPreview() {
    FestiCoreTheme { BearerRegisterContent(state = loadedState(withPack = false)) }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun BearerRegisterReceivedPreview() {
    FestiCoreTheme {
        BearerRegisterContent(
            state =
                loadedState(withPack = true).copy(
                    form = BearerRegisterForm(name = "미리보기 깃발", tags = listOf(BearerFlagTag.Slam), homeZoneId = "zone_a"),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerRegisterLoadingPreview() {
    FestiCoreTheme { BearerRegisterContent(state = BearerRegisterState()) }
}

@Preview(showBackground = true)
@Composable
private fun BearerRegisterFailedPreview() {
    FestiCoreTheme {
        BearerRegisterContent(state = BearerRegisterState(festivals = BearerFestivalList(load = BearerFestivalLoad.Failed(offline = true))))
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerRegisterDoneWithoutPackPreview() {
    FestiCoreTheme {
        BearerRegisterContent(
            state = loadedState(
                withPack = false,
            ).copy(completion = BearerRegisterCompletion(claim = PreviewClaim, festival = PreviewUpcoming.first())),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerRegisterDoneWithPackPreview() {
    FestiCoreTheme {
        BearerRegisterContent(
            state =
                loadedState(withPack = true).copy(
                    completion =
                        BearerRegisterCompletion(claim = PreviewClaim, festival = PreviewReceived, photo = BearerRegisterPhoto.Failed),
                ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BearerRoleChoicePreview() {
    FestiCoreTheme { BearerRoleChoiceContent(state = BearerRoleChoiceState()) }
}

@Preview(showBackground = true)
@Composable
private fun BearerRoleChoiceSelectedPreview() {
    FestiCoreTheme { BearerRoleChoiceContent(state = BearerRoleChoiceState(role = BearerRole.Bearer)) }
}
