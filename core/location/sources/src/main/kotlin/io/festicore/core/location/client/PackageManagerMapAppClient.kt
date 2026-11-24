package io.festicore.core.location.client

import android.content.Context
import android.content.pm.PackageManager

/** `PackageManager`로 설치 여부를 본다. iOS `UIKitMapAppClient`(`UIApplication.canOpenURL`). 패키지가 `<queries>`에 없으면 늘 false다. */
internal class PackageManagerMapAppClient(private val context: Context) : MapAppClient {
    override suspend fun isInstalled(app: MapApp): Boolean = try {
        context.packageManager.getPackageInfo(app.packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
