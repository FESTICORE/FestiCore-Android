package io.festicore.app

import android.content.Intent
import io.festicore.core.datastore.PendingDeeplinkStore
import io.festicore.core.logger.FestiCoreLogger
import io.festicore.core.logger.LogCategory
import io.festicore.core.utility.deeplink.FestiCoreDeeplink
import io.festicore.core.utility.deeplink.FestiCoreDeeplinkParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * URL 인텐트 · 푸시 data를 대기 저장소에 넣고 [received]로 알린다(iOS `AppDeeplinkBridge`).
 * 받는 쪽(`AppCoordinator`)은 홈이 준비됐을 때 [consumePending]으로 꺼낸다. 신호를 놓쳐도 대기 값이 남아 홈 진입 때 꺼낸다.
 * Android Google 로그인은 Credential Manager라 URL 콜백이 없어, 딥링크가 아닌 URL은 버린다(iOS의 OAuth 넘김 없음).
 */
class AppDeeplinkBridge(
    private val store: PendingDeeplinkStore,
    private val scope: CoroutineScope,
) {
    private val signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 받은 순서대로 하나씩 저장한다(마지막 딥링크가 남는다). */
    private val incoming = Channel<FestiCoreDeeplink>(Channel.UNLIMITED)

    val received: SharedFlow<Unit> = signal.asSharedFlow()

    init {
        scope.launch {
            for (deeplink in incoming) {
                store.save(deeplink.encoded)
                signal.emit(Unit)
                FestiCoreLogger.debug("딥링크 대기: ${deeplink::class.simpleName}", LogCategory.NAVIGATION)
            }
        }
    }

    /** `ACTION_VIEW` 인텐트의 `festicore://` URL. 딥링크면 `true`. */
    fun handleIntent(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val url = intent.dataString ?: return false
        return handleUrl(url)
    }

    fun handleUrl(url: String): Boolean {
        val deeplink = FestiCoreDeeplinkParser.parse(url) ?: return false
        broadcast(deeplink)
        return true
    }

    /** 알림 누름: data의 `deeplink`(없으면 `route` · ID)를 읽는다. */
    fun handlePushPayload(payload: Map<String, String>) {
        val deeplink = FestiCoreDeeplinkParser.parse(payload)
        if (deeplink == null) {
            FestiCoreLogger.debug("처리할 딥링크가 없는 푸시", LogCategory.NAVIGATION)
            return
        }
        broadcast(deeplink)
    }

    suspend fun consumePending(): FestiCoreDeeplink? = store.consume()?.let(FestiCoreDeeplinkParser::parse)

    private fun broadcast(deeplink: FestiCoreDeeplink) {
        incoming.trySend(deeplink)
    }
}
