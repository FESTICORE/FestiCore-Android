package io.festicore.service.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.festicore.core.logger.FestiCoreLogCategory
import io.festicore.core.logger.FestiCoreLogger

/**
 * 빌드에 들어 있는 Firebase 값으로 Firebase를 초기화한다(iOS `FirebaseConfiguration`).
 * iOS는 `GoogleService-Info.plist`가 없으면 건너뛰고, Android는 값이 하나라도 비면 건너뛴다.
 */
object FirebaseConfiguration {
    fun configure(context: Context, values: FirebaseValues) {
        val options = firebaseOptions(values)
        if (options == null) {
            FestiCoreLogger.error("Firebase configuration is unavailable in this build", FestiCoreLogCategory.APP)
            return
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context, options)
        }
    }

    internal fun firebaseOptions(values: FirebaseValues): FirebaseOptions? {
        if (!values.isComplete) return null
        return FirebaseOptions
            .Builder()
            .setApiKey(values.apiKey.trim())
            .setApplicationId(values.appId.trim())
            .setProjectId(values.projectId.trim())
            .setGcmSenderId(values.senderId.trim())
            .build()
    }
}
