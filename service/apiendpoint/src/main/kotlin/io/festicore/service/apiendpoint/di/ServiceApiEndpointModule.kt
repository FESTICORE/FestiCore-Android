package io.festicore.service.apiendpoint.di

import io.festicore.service.apiendpoint.auth.AuthRequest
import io.festicore.service.apiendpoint.crew.CrewRequest
import io.festicore.service.apiendpoint.festival.FestivalPackRequest
import io.festicore.service.apiendpoint.flag.FlagRequest
import io.festicore.service.apiendpoint.notice.NoticeRequest
import io.festicore.service.apiendpoint.usernotification.UserNotificationRequest
import org.koin.dsl.module

/** HttpClient는 service/auth가 인증 플러그인을 붙여 등록한다. */
val serviceApiEndpointModule = module {
    single { AuthRequest(get()) }
    single { CrewRequest(get()) }
    single { FestivalPackRequest(get()) }
    single { FlagRequest(get()) }
    single { NoticeRequest(get()) }
    single { UserNotificationRequest(get()) }
}
