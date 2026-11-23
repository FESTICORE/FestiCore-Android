# 의존성 주입

원칙: **구현을 가진 모듈이 자기 구현을 직접 등록한다.** 앱이나 조립 모듈에 등록 목록을 길게 두지 않는다.
모듈을 추가하거나 지울 때 다른 모듈의 등록 코드를 고치지 않아도 되게 하기 위해서다.

예: `domain/auth/sources/.../di/AuthDomainModule.kt`가 이 모듈이 소유한 구현을 스스로 등록한다.


## Koin

iOS `@Dependency` · `DependencyKey` 등록에 대응한다.

### 각 모듈이 자기 Koin 모듈을 가진다

모듈마다 `di/{이름}Module.kt`에 자기 정의만 둔다.

```kotlin
// feature/auth/sources/src/main/kotlin/.../di/AuthFeatureModule.kt
val authFeatureModule = module {
    viewModelOf(::LoginViewModel)
}
```

```kotlin
// domain/auth/sources/src/main/kotlin/.../di/AuthDomainModule.kt
val authDomainModule = module {
    singleOf(::AuthRepositoryImpl) bind AuthRepository::class
    factoryOf(::LoginUseCaseImpl) bind LoginUseCase::class
}
```

### assembly 모듈은 목록만, app은 목록을 합친다

```kotlin
// app/.../di/AppModules.kt
val appModules: List<Module> =
    coreModules +                                   // core/coreassembly
        serviceModules(baseUrl = BuildConfig.BASE_URL) + // service/serviceassembly
        listOf(domainAuthModule(...), domainFestivalModule, domainUserNotificationModule) +
        featureModules(legalBaseUrl = BuildConfig.LEGAL_BASE_URL) + // feature/featureassembly
        appModule                                    // 루트 Coordinator · 딥링크 브리지
```

- `core/coreassembly` · `service/serviceassembly` · `feature/featureassembly`(iOS `CoreAssembly` · `ServiceAssembly` · `FeatureAssembly`)는 각 계층 모듈이 공개한 Koin 모듈(`val xxxModule`)을 나열만 한다. 정의(`single` · `factory` · `viewModel` · `module { }`)를 두지 않는다(check-boundaries가 막는다). domain 목록은 iOS처럼 조립 모듈 없이 app이 나열한다.
- `interface` · `sources`로 나뉜 service · core 모듈(`service/{pack,device,auth}` · `core/network`)의 Koin 모듈은 `sources`의 `di/`에 두고 인터페이스 타입으로 등록한다(`single<AuthSession> { AuthSessionImpl(get()) }`, `single { SessionExpiryEventsImpl() } bind SessionExpiryEvents::class`, `single<DeviceInstallationService> { DeviceInstallationServiceImpl(get()) }`). assembly만 `sources`를 의존한다.
- 새 core · service · feature Koin 모듈은 해당 assembly 목록에 추가한다. app은 모듈 안의 정의를 알지 못한다.
- app에 `single { }`, `factory { }` 정의를 직접 쓰지 않는다. 정의는 항상 그 클래스가 있는 모듈에 둔다.
- 테스트에서는 `koin-test`의 `verify()` 또는 생성자 주입으로 가짜 구현을 넣는다.

## 금지

- 앱 시작 코드에 구현체를 하나씩 등록하는 긴 목록 금지.
- 다른 모듈의 구현체를 대신 등록하는 코드 금지.
- 서비스 로케이터 직접 호출(`KoinPlatform.getKoin().get()`, 전역 싱글턴) 금지. 생성자 주입을 쓴다.
- domain `interface` · feature `interface`에서 Koin import 금지.
