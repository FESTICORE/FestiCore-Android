package io.festicore.service.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialStoreTest {
    private val storage = FakeSecureStorage()
    private val store = CredentialStore(storage)

    @Test
    fun `새 로그인은 새 sessionId와 revision 0으로 한 키에 저장되고 다시 읽힌다`() = runTest {
        val first = store.saveNewSession("a1", "r1")
        val second = store.saveNewSession("a2", "r2")

        assertNotEquals(first.sessionId, second.sessionId)
        assertEquals(0L, second.revision)
        assertEquals(1, storage.values.size)
        assertEquals(second, CredentialStore(storage).current())
    }

    @Test
    fun `compareAndSetTokens는 세션과 revision이 같을 때만 바꾸고 revision을 올린다`() = runTest {
        val session = store.saveNewSession("a1", "r1")

        assertFalse(store.compareAndSetTokens("other", 0, "x", "y"))
        assertFalse(store.compareAndSetTokens(session.sessionId, 1, "x", "y"))
        assertTrue(store.compareAndSetTokens(session.sessionId, 0, "a2", "r2"))

        val updated = CredentialStore(storage).current()!!
        assertEquals(session.sessionId, updated.sessionId)
        assertEquals(1L, updated.revision)
        assertEquals("a2", updated.accessToken)
        assertEquals("r2", updated.refreshToken)
    }

    @Test
    fun `clearIf는 세션과 revision이 같을 때만 지운다`() = runTest {
        val session = store.saveNewSession("a1", "r1")

        assertFalse(store.clearIf(session.sessionId, 1))
        assertEquals(session, store.current())
        assertTrue(store.clearIf(session.sessionId, 0))
        assertNull(store.current())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `clear는 세션을 없앤다`() = runTest {
        store.saveNewSession("a1", "r1")

        store.clear()

        assertNull(store.current())
        assertNull(CredentialStore(storage).current())
    }

    @Test
    fun `손상된 값은 비로그인으로 보고 지운다`() = runTest {
        storage.values["festicore.auth.session"] = """{"accessToken":"only-half"}"""

        assertNull(store.current())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `toString에 토큰을 넣지 않는다`() = runTest {
        val text = store.saveNewSession("secret-access", "secret-refresh").toString()

        assertFalse(text.contains("secret"))
    }
}
