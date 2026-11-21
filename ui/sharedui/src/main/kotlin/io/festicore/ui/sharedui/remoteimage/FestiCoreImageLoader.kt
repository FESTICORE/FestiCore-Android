package io.festicore.ui.sharedui.remoteimage

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache

/** iOS Kingfisher 기본 메모리 캐시(물리 메모리의 25%)에 맞춘 앱 메모리 비율 */
private const val MEMORY_CACHE_PERCENT = 0.25

/** Coil 기본 디스크 캐시 비율(디스크의 2%, Coil이 10MB~250MB로 자른다). iOS Kingfisher는 크기 제한 없이 7일 만료다. */
private const val DISK_CACHE_PERCENT = 0.02

private const val DISK_CACHE_DIRECTORY = "image_cache"

/**
 * [FCRemoteImage]가 쓰는 Coil 싱글톤 [ImageLoader]를 앱 시작 시 한 번 설정한다(`Application.onCreate`).
 * iOS Kingfisher 기본값처럼 응답 `cache-control`을 보지 않고 받은 이미지를 디스크에 두고 다시 쓴다(Coil 기본 `CacheStrategy`).
 * 네트워크는 `coil-network-okhttp`가 맡는다(설계 결정 D7). 이미 설정돼 있으면 바꾸지 않는다.
 */
fun installFestiCoreImageLoader(context: Context) {
    val appContext = context.applicationContext
    SingletonImageLoader.setSafe { festiCoreImageLoader(appContext) }
}

internal fun festiCoreImageLoader(context: PlatformContext): ImageLoader = ImageLoader
    .Builder(context)
    .memoryCache {
        MemoryCache
            .Builder()
            .maxSizePercent(context, MEMORY_CACHE_PERCENT)
            .build()
    }.diskCache {
        DiskCache
            .Builder()
            .directory(context.cacheDir.resolve(DISK_CACHE_DIRECTORY))
            .maxSizePercent(DISK_CACHE_PERCENT)
            .build()
    }.build()
