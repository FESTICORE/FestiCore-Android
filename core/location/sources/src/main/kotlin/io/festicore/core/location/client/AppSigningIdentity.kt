package io.festicore.core.location.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Routes API Android 앱 제한 키에 보낼 패키지 이름과 서명 인증서 SHA-1(대문자 16진, 콜론 없음). 읽지 못하면 null이다. */
internal fun appSigningIdentity(context: Context): GoogleRoutesClient.AppIdentity? = try {
    val manager = context.packageManager
    val signature =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
    signature?.let {
        val digest = MessageDigest.getInstance("SHA-1").digest(it.toByteArray())
        GoogleRoutesClient.AppIdentity(context.packageName, digest.joinToString("") { byte -> "%02X".format(byte) })
    }
} catch (_: Exception) {
    null
}
