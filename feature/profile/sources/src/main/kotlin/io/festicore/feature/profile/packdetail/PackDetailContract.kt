package io.festicore.feature.profile.packdetail

import io.festicore.core.location.client.MapApp
import io.festicore.domain.festival.entity.FestivalChecklist
import io.festicore.domain.festival.entity.FestivalPackChanges
import io.festicore.domain.festival.entity.FestivalPackDetail

/**
 * 받은 팩 상세 화면 상태. iOS `PackDetailFeature.State`.
 *
 * @param title 상단 바 제목. 처음에는 넘겨받은 축제 이름이고, 팩을 읽으면 팩 이름으로 바꾼다.
 * @param changes 새 버전이 있을 때 서버가 알려 준 변경 요약.
 * @param manageMessage 팩 관리 탭 아래에 보이는 결과 문구.
 * @param installedMapApps 설치된 지도 앱. 확인 전(null)에는 앱 이름으로 두고, 없는 앱은 "웹 ↗" 버튼으로 바꾼다(Figma S11 G).
 * @param isDeleteConfirmationPresented 팩 삭제 확인 팝업이 떠 있는지. iOS `destination = .alert(.deletePack)`.
 */
data class PackDetailState(
    val festivalId: String,
    val title: String,
    val tab: PackDetailTab = PackDetailTab.Directions,
    val load: PackDetailLoad = PackDetailLoad.Idle,
    val detail: FestivalPackDetail? = null,
    val checklist: FestivalChecklist = FestivalChecklist(),
    val isEditingChecklist: Boolean = false,
    val newItemText: String = "",
    val changes: FestivalPackChanges? = null,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val manageMessage: String? = null,
    val autoDownloadOnWiFi: Boolean = true,
    val installedMapApps: Set<MapApp>? = null,
    val isDeleteConfirmationPresented: Boolean = false,
) {
    /** 지운 팩 항목을 빼고(필수는 항상 남긴다) 내가 추가한 항목을 뒤에 붙인다. iOS `checklistRows`. */
    val checklistRows: List<PackChecklistRow>
        get() {
            val packItems =
                detail?.checklist.orEmpty()
                    .filter { it.required || it.id !in checklist.hiddenIds }
                    .map { PackChecklistRow(it.id, it.label, isRequired = it.required, isChecked = it.id in checklist.checkedIds) }
            val customItems =
                checklist.customItems.map {
                    PackChecklistRow(it.id, it.label, isRequired = false, isChecked = it.id in checklist.checkedIds)
                }
            return packItems + customItems
        }

    val checkedCount: Int get() = checklistRows.count { it.isChecked }
}

/** iOS `PackDetailFeature.Tab`. 순서가 탭 바 칸 번호 · 본문 페이지 번호다. */
enum class PackDetailTab(val title: String) {
    Directions("가는 길"),
    Checklist("준비물"),
    Manage("팩 관리"),
    ;

    companion object {
        /** 탭 바 칸 · 본문 페이지 번호를 탭으로 바꾼다. 범위를 벗어나면 가는 길로 둔다. iOS `PackDetailView.tab(at:)`. */
        fun at(index: Int): PackDetailTab = entries.getOrNull(index) ?: Directions
    }
}

/** iOS `PackDetailFeature.Load`. `Idle`은 처음 나타나기 전이고 화면은 `Loading`과 같게 그린다. */
enum class PackDetailLoad {
    Idle,
    Loading,
    Loaded,

    /** 기기에 받은 팩이 없다. */
    Missing,
    Failed,
}

/** 준비물 목록의 한 줄. iOS `PackDetailFeature.ChecklistRow`. */
data class PackChecklistRow(
    val id: String,
    val label: String,
    val isRequired: Boolean,
    val isChecked: Boolean,
)

/** iOS `PackDetailDelegate` + 외부 링크 열기(iOS `openURL`). */
sealed interface PackDetailSideEffect {
    data object BackRequested : PackDetailSideEffect

    /** 팩을 새로 받았거나 확인해 마이 화면 목록을 다시 읽어야 한다. */
    data object PackChanged : PackDetailSideEffect

    /** 팩을 지워 상세 화면을 닫아야 한다. */
    data object PackDeleted : PackDetailSideEffect

    /** 외부 링크를 연다. [mapApp]이 있으면 설치된 그 지도 앱이 받게 하고, 없으면 Custom Tabs로 연다(설계 쟁점 M10 · M11). */
    data class OpenLink(
        val url: String,
        val mapApp: MapApp? = null,
    ) : PackDetailSideEffect
}
