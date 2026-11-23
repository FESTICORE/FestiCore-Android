package io.festicore.ui.sharedui.toast

import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.toast.FCToastItem

/** FestiCore 화면에서 쓰는 토스트 문구(iOS `FCToast+FestiCore.swift`). 화면에서 쓰는 것부터 옮긴다. */
object FCToastItems {
    fun scheduleAdded(message: String = "내 일정에 추가됐어요"): FCToastItem = FCToastItem(icon = R.drawable.ic_check, message = message)

    /** 깃발 팔로우 완료. 화면이 아이콘 색을 `staticLime`으로 준다. */
    fun followed(name: String): FCToastItem = FCToastItem(icon = R.drawable.ic_flag_16, message = "${name}님을 팔로우했습니다")
}
