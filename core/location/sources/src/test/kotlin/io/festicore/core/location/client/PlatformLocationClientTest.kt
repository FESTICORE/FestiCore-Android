package io.festicore.core.location.client

import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import io.festicore.core.location.entity.LocationAuthorization
import io.festicore.core.storage.shared.LocationPermissionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** 위치 권한 요청(점검 R6-LOC-001 화면이 보이는 중 등록 · Activity 재생성, R6-LOC-003 영구 거부 기록). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformLocationClientTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakePermissionStore()
    private lateinit var controller: ActivityController<ComponentActivity>
    private var current: ComponentActivity? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        current = controller.get()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun client() = PlatformLocationClient(controller.get().application, { current }, store)

    private fun answer(activity: ComponentActivity, granted: Boolean) {
        val requestCode = shadowOf(activity).lastRequestedPermission?.requestCode ?: error("요청 없음")
        answer(activity, requestCode, granted)
    }

    @Suppress("DEPRECATION")
    private fun answer(
        activity: ComponentActivity,
        requestCode: Int,
        granted: Boolean,
    ) {
        val result = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        if (granted) shadowOf(activity.application).grantPermissions(*PlatformLocationClient.PERMISSIONS)
        activity.onRequestPermissionsResult(requestCode, PlatformLocationClient.PERMISSIONS, intArrayOf(result, result))
    }

    @Test
    fun `화면이 보이는 중에도 권한을 요청하고 허용하면 허용이다`() = runTest(dispatcher) {
        val client = client()
        assertEquals(LocationAuthorization.NotDetermined, client.authorization())
        val request = async { client.requestWhenInUseAuthorization() }
        val requested = shadowOf(controller.get()).lastRequestedPermission
        assertEquals(PlatformLocationClient.PERMISSIONS.toList(), requested?.requestedPermissions?.toList())
        answer(controller.get(), granted = true)
        assertEquals(LocationAuthorization.Authorized, request.await())
        assertFalse(store.denied)
    }

    @Test
    fun `동시에 여러 번 요청해도 대화상자는 한 번만 띄운다`() = runTest(dispatcher) {
        val client = client()
        val first = async { client.requestWhenInUseAuthorization() }
        val firstRequest = shadowOf(controller.get()).lastRequestedPermission
        val requestCode = firstRequest?.requestCode
        val second = async { client.requestWhenInUseAuthorization() }
        assertSame(firstRequest, shadowOf(controller.get()).lastRequestedPermission)
        answer(controller.get(), requestCode ?: error("요청 없음"), granted = true)
        assertEquals(LocationAuthorization.Authorized, first.await())
        assertEquals(LocationAuthorization.Authorized, second.await())
    }

    @Test
    fun `거부하면 기록해 영구 거부처럼 시스템이 설명을 권하지 않아도 다시 묻지 않는다`() = runTest(dispatcher) {
        val client = client()
        val request = async { client.requestWhenInUseAuthorization() }
        answer(controller.get(), granted = false)
        assertEquals(LocationAuthorization.Denied, request.await())
        assertTrue(store.denied)

        val answered = shadowOf(controller.get()).lastRequestedPermission
        assertEquals(LocationAuthorization.Denied, client.authorization())
        assertEquals(LocationAuthorization.Denied, client.requestWhenInUseAuthorization())
        assertSame(answered, shadowOf(controller.get()).lastRequestedPermission)
    }

    @Test
    fun `나중에 설정에서 허용하면 거부 기록보다 허용이 앞선다`() = runTest(dispatcher) {
        store.denied = true
        shadowOf(controller.get().application).grantPermissions(*PlatformLocationClient.PERMISSIONS)
        assertEquals(LocationAuthorization.Authorized, client().authorization())
    }

    @Test
    fun `대화상자가 떠 있는 동안 Activity가 다시 만들어져도 결과를 받는다`() = runTest(dispatcher) {
        val client = client()
        val request = async { client.requestWhenInUseAuthorization() }
        val requestCode = shadowOf(controller.get()).lastRequestedPermission?.requestCode ?: error("요청 없음")
        controller.recreate()
        current = controller.get()
        // 다시 보인 화면이 같은 요청을 이어 받는다(새 등록부에 같은 키로 붙는다).
        val resumed = async { client.requestWhenInUseAuthorization() }
        answer(controller.get(), requestCode, granted = true)
        assertEquals(LocationAuthorization.Authorized, request.await())
        assertEquals(LocationAuthorization.Authorized, resumed.await())
    }

    private class FakePermissionStore : LocationPermissionStore {
        var denied = false

        override suspend fun isDenied(): Boolean = denied

        override suspend fun setDenied(denied: Boolean) {
            this.denied = denied
        }
    }
}
