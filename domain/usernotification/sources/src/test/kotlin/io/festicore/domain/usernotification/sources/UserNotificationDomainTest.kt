package io.festicore.domain.usernotification.sources

import io.festicore.core.datastore.SecureStorage
import io.festicore.core.network.BaseResponse
import io.festicore.core.network.FestiCoreJson
import io.festicore.core.network.di.coreNetworkModule
import io.festicore.domain.usernotification.UserNotificationBadgeUseCase
import io.festicore.domain.usernotification.UserNotificationError
import io.festicore.domain.usernotification.UserNotificationPage
import io.festicore.domain.usernotification.UserNotificationRepository
import io.festicore.domain.usernotification.UserNotificationUseCase
import io.festicore.domain.usernotification.sources.di.domainUserNotificationModule
import io.festicore.service.api.UserNotificationItemDto
import io.festicore.service.api.UserNotificationListDto
import io.festicore.service.api.di.serviceApiModule
import io.festicore.service.auth.di.serviceAuthModule
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** iOS `UserNotificationDomainTests` 3건(같은 이름). 응답 본문은 iOS DTO 형태의 가짜 값이다. */
class UserNotificationDomainTest {
    private fun decode(json: String) = FestiCoreJson.decodeFromString(BaseResponse.serializer(UserNotificationListDto.serializer()), json)

    @Test
    fun mapsNullableReadDateAndOpaqueCursor() {
        val response =
            decode(
                """{"code":"SUCCESS","message":"OK","data":{"items":[
                {"id":"ntf_0123456789abcdef0123456789abcdef","type":"festival.start",
                 "festivalId":"fst_2026","title":"시작","body":"곧 시작합니다",
                 "createdAt":"2026-11-04T00:00:00.000Z","readAt":null,"route":"/festivals/fst_2026/live"}
                ],"nextCursor":"opaque|cursor"}}""",
            )
        val page = response.data?.toDomain() ?: return fail("data가 없다")
        assertEquals("opaque|cursor", page.nextCursor)
        assertEquals(1, page.items.size)
        assertEquals("fst_2026", page.items[0].festivalId)
        assertNull(page.items[0].readAt)
        assertEquals("/festivals/fst_2026/live", page.items[0].route)
    }

    @Test
    fun malformedDatesAndRequiredFieldsFail() {
        val item =
            UserNotificationItemDto(
                id = "ntf_0123456789abcdef0123456789abcdef",
                type = "festival.start",
                festivalId = null,
                title = "시작",
                body = "본문",
                createdAt = "invalid",
                readAt = null,
                route = null,
            )
        assertInvalid { item.toDomain() }
        assertInvalid { item.copy(createdAt = "2026-11-04T00:00:00.000Z", readAt = "invalid").toDomain() }
        try {
            decode(
                """{"code":"SUCCESS","message":"OK","data":{"items":[
                {"id":"ntf_0123456789abcdef0123456789abcdef","type":"festival.start",
                 "festivalId":null,"title":null,"body":"본문",
                 "createdAt":"2026-11-04T00:00:00.000Z","readAt":null,"route":null}
                ],"nextCursor":null}}""",
            )
            fail("제목이 없으면 해석에 실패해야 한다")
        } catch (_: SerializationException) {
        }
    }

    @Test
    fun useCaseForwardsRepositoryAndLiveDependenciesResolve() = runTest {
        val expected = UserNotificationPage(items = emptyList(), nextCursor = "next")
        val repository =
            object : UserNotificationRepository {
                override suspend fun notifications(before: String?) = expected

                override suspend fun unreadCount() = 0

                override suspend fun markRead(ids: List<String>) = Unit

                override suspend fun delete(ids: List<String>) = Unit
            }
        assertEquals(expected, UserNotificationUseCaseImpl(repository).notifications(null))

        val koin =
            koinApplication {
                modules(
                    coreNetworkModule,
                    serviceApiModule,
                    serviceAuthModule("https://example.test/"),
                    module { single<SecureStorage> { FakeSecureStorage() } },
                    domainUserNotificationModule,
                )
            }.koin
        assertTrue(koin.get<UserNotificationRepository>() is UserNotificationRepositoryImpl)
        assertTrue(koin.get<UserNotificationUseCase>() is UserNotificationUseCaseImpl)
        // 보드 · 알림함이 같은 배지를 본다.
        assertTrue(koin.get<UserNotificationBadgeUseCase>() === koin.get<UserNotificationBadgeUseCase>())
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("InvalidResponse를 기대했다")
        } catch (_: UserNotificationError.InvalidResponse) {
        }
    }
}
