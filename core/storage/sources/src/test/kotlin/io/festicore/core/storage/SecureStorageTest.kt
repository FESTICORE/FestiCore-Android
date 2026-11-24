package io.festicore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.festicore.core.storage.secure.DataStoreSecureStorage
import io.festicore.core.storage.secure.SecureStorageException
import io.festicore.core.storage.secure.ValueCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SecureStorageTest {
    /** IV 대신 고정 머리 바이트를 붙이고 XOR로 뒤집는다. [failDecrypt] · [failEncrypt]로 실패를 흉내 낸다. */
    private class FakeCipher : ValueCipher {
        var failDecrypt = false
        var failEncrypt = false

        override fun encrypt(plain: ByteArray): ByteArray {
            if (failEncrypt) throw IllegalStateException("키 생성 실패")
            return HEADER + plain.map { (it.toInt() xor 0x5A).toByte() }
        }

        override fun decrypt(data: ByteArray): ByteArray {
            if (failDecrypt || !data.copyOfRange(0, HEADER.size).contentEquals(HEADER)) throw IllegalStateException("복호화 실패")
            return data.copyOfRange(HEADER.size, data.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }

        private companion object {
            val HEADER = byteArrayOf(1, 2, 3)
        }
    }

    private val stores = TestDataStores("secure_test")
    private val cipher = FakeCipher()

    @After
    fun tearDown() = runTest { stores.close() }

    private suspend fun DataStore<Preferences>.raw(key: String) = data.first()[stringPreferencesKey(key)]

    @Test
    fun `쓴 값을 그대로 읽고 저장 값은 평문이 아니다`() = runTest {
        val dataStore = stores.open()
        val storage = DataStoreSecureStorage(dataStore, cipher)

        storage.write("ACCESS_TOKEN", "토큰-값")

        assertEquals("토큰-값", storage.read("ACCESS_TOKEN"))
        assertNotEquals("토큰-값", dataStore.raw("ACCESS_TOKEN"))
        assertNull(storage.read("REFRESH_TOKEN"))
    }

    @Test
    fun `remove하면 null`() = runTest {
        val storage = DataStoreSecureStorage(stores.open(), cipher)
        storage.write("ACCESS_TOKEN", "값")

        storage.remove("ACCESS_TOKEN")

        assertNull(storage.read("ACCESS_TOKEN"))
    }

    @Test
    fun `복호화에 실패하면 null을 돌려주고 값을 지운다`() = runTest {
        val dataStore = stores.open()
        val storage = DataStoreSecureStorage(dataStore, cipher)
        storage.write("ACCESS_TOKEN", "값")
        cipher.failDecrypt = true

        assertNull(storage.read("ACCESS_TOKEN"))
        assertNull(dataStore.raw("ACCESS_TOKEN"))
    }

    @Test
    fun `Base64가 아닌 저장 값도 null을 돌려주고 지운다`() = runTest {
        val dataStore = stores.open()
        dataStore.edit { it[stringPreferencesKey("ACCESS_TOKEN")] = "%%%" }
        val storage = DataStoreSecureStorage(dataStore, cipher)

        assertNull(storage.read("ACCESS_TOKEN"))
        assertNull(dataStore.raw("ACCESS_TOKEN"))
    }

    @Test
    fun `암호화에 실패하면 SecureStorageException을 던지고 저장하지 않는다`() = runTest {
        val dataStore = stores.open()
        val storage = DataStoreSecureStorage(dataStore, cipher)
        cipher.failEncrypt = true

        try {
            storage.write("ACCESS_TOKEN", "값")
            fail("예외가 나지 않았습니다")
        } catch (e: SecureStorageException) {
            assertTrue(e.cause is IllegalStateException)
        }
        assertNull(dataStore.raw("ACCESS_TOKEN"))
    }
}
