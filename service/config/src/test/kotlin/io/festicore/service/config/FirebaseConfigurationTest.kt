package io.festicore.service.config

import android.content.Context
import com.google.firebase.FirebaseApp
import io.festicore.core.logger.FestiCoreLogEntry
import io.festicore.core.logger.FestiCoreLogLevel
import io.festicore.core.logger.FestiCoreLogger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FirebaseConfigurationTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val entries = CopyOnWriteArrayList<FestiCoreLogEntry>()
    private var recording = true

    @Before
    fun setUp() {
        FestiCoreLogger.addSink { if (recording) entries += it }
    }

    @After
    fun tearDown() {
        recording = false
        FirebaseApp.getApps(context).forEach { it.delete() }
    }

    @Test
    fun `값이 없으면 초기화하지 않고 로그를 한 줄 남긴다`() {
        FirebaseConfiguration.configure(context, FirebaseValues("", "", "", ""))

        assertTrue(FirebaseApp.getApps(context).isEmpty())
        assertEquals(1, entries.size)
        assertEquals(FestiCoreLogLevel.ERROR, entries.single().level)
        assertEquals("Firebase configuration is unavailable in this build", entries.single().message)
    }

    @Test
    fun `값이 하나라도 비거나 공백이면 초기화하지 않는다`() {
        FirebaseConfiguration.configure(context, FirebaseValues("key", "1:123:android:abc", "  ", "123"))

        assertTrue(FirebaseApp.getApps(context).isEmpty())
        assertNull(FirebaseConfiguration.firebaseOptions(FirebaseValues("key", "", "project", "123")))
        assertEquals(1, entries.size)
    }

    @Test
    fun `값이 모두 있으면 옵션을 구성해 초기화한다`() {
        val values = FirebaseValues("key", "1:123:android:abc", "project", "123")

        val options = checkNotNull(FirebaseConfiguration.firebaseOptions(values))
        assertEquals("key", options.apiKey)
        assertEquals("1:123:android:abc", options.applicationId)
        assertEquals("project", options.projectId)
        assertEquals("123", options.gcmSenderId)

        FirebaseConfiguration.configure(context, values)

        assertEquals("project", FirebaseApp.getInstance().options.projectId)
        assertTrue(entries.isEmpty())
    }
}
