package io.festicore.feature.profile.notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.festicore.domain.festival.notificationsettings.NotificationSetting
import io.festicore.feature.profile.settings.SettingsRowDivider
import io.festicore.feature.profile.settings.SettingsSection
import io.festicore.ui.designkit.R
import io.festicore.ui.designkit.component.action.FCButton
import io.festicore.ui.designkit.component.action.FCButtonStyle
import io.festicore.ui.designkit.component.layout.fcScrollBottomInset
import io.festicore.ui.designkit.component.list.FCListRow
import io.festicore.ui.designkit.component.navigation.FCTopBar
import io.festicore.ui.designkit.component.navigation.FCTopBarStyle
import io.festicore.ui.designkit.component.overlay.FCBanner
import io.festicore.ui.designkit.component.toast.FCToastHost
import io.festicore.ui.designkit.component.toast.FCToastItem
import io.festicore.ui.designkit.tokens.FestiCoreSemanticColor
import io.festicore.ui.designkit.tokens.FestiCoreSpacing
import io.festicore.ui.designkit.tokens.FestiCoreTextStyle
import io.festicore.ui.designkit.typography.textStyle
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun NotificationSettingsScreen(viewModel: NotificationSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.collectAsState()
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(true) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
                viewModel.onResume()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    NotificationSettingsContent(
        state = state.copy(error = settingsError ?: state.error),
        notificationsEnabled = enabled,
        systemSettingsAvailable = android.os.Build.VERSION.SDK_INT < 33 || run {
            @Suppress("DEPRECATION")
            val permissions = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_PERMISSIONS,
            ).requestedPermissions
            permissions?.contains(android.Manifest.permission.POST_NOTIFICATIONS) == true
        },
        onBack = onBack,
        onRetry = { viewModel.onResume() },
        onToggle = { key, value -> viewModel.onToggle(key, value) },
        onOpenSystemSettings = {
            settingsError = null
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }.recoverCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
            if (opened.isFailure) settingsError = "기기 설정을 열지 못했어요. 휴대전화 설정에서 앱 알림을 확인해 주세요."
        },
    )
}

@Composable
fun NotificationSettingsContent(
    state: NotificationSettingsState,
    notificationsEnabled: Boolean,
    modifier: Modifier = Modifier,
    systemSettingsAvailable: Boolean = true,
    onBack: () -> Unit = {},
    onOpenSystemSettings: () -> Unit = {},
    onRetry: () -> Unit = {},
    onToggle: (NotificationSetting, Boolean) -> Unit = { _, _ -> },
) {
    var toast by remember(state.error, state.errorRevision) {
        mutableStateOf(state.error?.takeIf { state.values.isNotEmpty() }?.let { FCToastItem(R.drawable.ic_triangle_exclamation_16, it) })
    }
    FCToastHost(toast, { toast = it }, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(FestiCoreSemanticColor.backgroundNormal.color)) {
            FCTopBar(style = FCTopBarStyle.Back, title = "알림", onBack = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = FestiCoreSpacing.paddingP16)
                    .padding(top = FestiCoreSpacing.paddingP16).fcScrollBottomInset(),
                verticalArrangement = Arrangement.spacedBy(FestiCoreSpacing.paddingP24),
            ) {
                if (state.terminal || state.values.isEmpty()) {
                    Text(
                        state.error ?: "알림 설정을 불러오고 있어요",
                        style = FestiCoreTextStyle.baseBodyRegular.textStyle,
                        color = FestiCoreSemanticColor.labelNeutral.color,
                    )
                    if (!state.terminal && !state.loading &&
                        state.error != null
                    ) {
                        FCButton("다시 시도", style = FCButtonStyle.Secondary, onClick = onRetry)
                    }
                } else {
                    if (!notificationsEnabled &&
                        systemSettingsAvailable
                    ) {
                        FCBanner("기기 설정에서 알림이 꺼져 있어요 · 설정 열기", onClick = onOpenSystemSettings)
                    }
                    NotificationGroup(
                        "깃발 · 크루",
                        listOf(
                            "팔로우한 깃발 이동" to NotificationSetting.FlagStatus,
                            "크루원 상태" to NotificationSetting.CrewStatus,
                            "크루 모여! 요청" to NotificationSetting.CrewGather,
                        ),
                        state.values,
                        onToggle,
                    )
                    NotificationGroup(
                        "공연 · 귀가",
                        listOf(
                            "공연 10분 전" to NotificationSetting.SetReminders,
                            "귀가 알림" to NotificationSetting.Homecoming,
                        ),
                        state.values,
                        onToggle,
                    )
                    NotificationGroup("팩", listOf("팩 업데이트" to NotificationSetting.PackUpdates), state.values, onToggle)
                    if (NotificationSetting.OfficialNews in
                        state.values
                    ) {
                        NotificationGroup("축제 소식", listOf("공식 인스타그램 새 게시물" to NotificationSetting.OfficialNews), state.values, onToggle)
                    }
                    Text(
                        "이 Android 버전은 푸시 수신을 아직 지원하지 않아요. 여기서는 알림 설정값만 저장해요.",
                        style = FestiCoreTextStyle.baseFootnoteRegular.textStyle,
                        color = FestiCoreSemanticColor.labelAssistive.color,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationGroup(
    title: String,
    rows: List<Pair<String, NotificationSetting>>,
    values: Map<NotificationSetting, Boolean>,
    onToggle: (NotificationSetting, Boolean) -> Unit,
) {
    val footnote = when (title) {
        "깃발 · 크루" -> "팔로우한 깃발 이동과 크루 활동의 알림 설정이에요."
        "공연 · 귀가" -> "공연 시작 전과 귀가 시간 안내의 알림 설정이에요."
        "팩" -> "받은 팩의 시간표 변경을 알리는 설정이에요."
        else -> "고정한 축제의 공식 인스타그램 새 게시물 알림 설정이에요."
    }
    SettingsSection(title = title, footnote = footnote) {
        rows.forEachIndexed { index, (label, key) ->
            if (index > 0) SettingsRowDivider()
            FCListRow(label = label, isOn = values[key] == true, onIsOnChange = { onToggle(key, it) })
        }
    }
}
