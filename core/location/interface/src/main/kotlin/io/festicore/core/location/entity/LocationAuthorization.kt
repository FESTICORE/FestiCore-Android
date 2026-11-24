package io.festicore.core.location.entity

/** 앱의 위치 권한 상태. 앱은 사용 중일 때만 위치를 쓴다. iOS `LocationAuthorization`. */
enum class LocationAuthorization {
    /** 아직 묻지 않았다. 필요한 순간에 한 번 요청할 수 있다. */
    NotDetermined,

    /** 사용자가 거부했다. 설정 앱에서만 바꿀 수 있다. */
    Denied,

    /** 기기 관리 · 자녀 보호 설정으로 막혀 있다. Android 권한에는 대응 상태가 없어 iOS 분기만 맞춘다. */
    Restricted,

    /** 사용 중 허용(정확 · 대략 위치 중 하나라도) */
    Authorized,
}
