package io.festicore.service.auth

import io.festicore.service.auth.store.CredentialStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionIdentityTest {
    @Test fun `failed first disk read never emits a logout identity`() = runTest {
        val disk = object : io.festicore.core.storage.secure.SecureStorage {
            override suspend fun read(key: String): String? = error("temporarily unavailable")
            override suspend fun write(key: String, value: String) = Unit
            override suspend fun remove(key: String) = Unit
        }
        val seen = mutableListOf<String?>()
        val failure = runCatching {
            AuthSessionImpl(CredentialStore(disk)).sessionIdentities().collect { seen += it }
        }
        assertEquals(true, failure.isFailure)
        assertEquals(emptyList<String?>(), seen)
    }

    @Test fun `observer receives persisted identity then store expiry without UI`() = runTest {
        val disk = FakeSecureStorage()
        val old = CredentialStore(disk).saveNewSession("access", "refresh").sessionId
        val store = CredentialStore(disk)
        val session = AuthSessionImpl(store)
        val seen = mutableListOf<String?>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.sessionIdentities().collect { seen += it }
        }
        assertEquals(listOf(old), seen)
        store.clearIfSession(old)
        assertEquals(listOf(old, null), seen)
        job.cancel()
    }
}
