package io.festicore.ui.designkit.component.navigation

internal object FCSelection {
    fun isSelected(
        index: Int,
        selection: Int,
        count: Int,
    ): Boolean = selection in 0 until count && index == selection
}
