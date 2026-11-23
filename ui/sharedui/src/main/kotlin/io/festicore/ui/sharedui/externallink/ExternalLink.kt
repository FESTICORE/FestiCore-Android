package io.festicore.ui.sharedui.externallink

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 외부 링크를 Custom Tabs로 연다(iOS `openURL`). 열 수 있는 앱이 없으면 일반 보기 요청으로 한 번 더 열고, 그래도 없으면 아무것도 하지 않는다.
 *
 * @param packageName 이 앱이 링크를 받게 하려면 패키지 이름. 그 앱이 링크를 받지 못하면 Custom Tabs로 연다.
 */
fun openExternalUrl(
    context: Context,
    url: String,
    packageName: String? = null,
) {
    val uri = Uri.parse(url)
    if (packageName != null) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(packageName))
            return
        } catch (_: ActivityNotFoundException) {
            // 앱이 이 링크를 받지 않는다. 아래에서 Custom Tabs로 연다.
        }
    }
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // 링크를 열 앱이 없다.
        }
    }
}
