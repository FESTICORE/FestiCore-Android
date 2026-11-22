package io.festicore.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

object AndroidConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    const val JVM_TOOLCHAIN = 21
    const val APPLICATION_ID = "io.festiCore.co"
    const val VERSION_NAME = "1.0.0"
}

/** Java 예약어인 interface는 contract로 바꾼다. */
fun Project.androidNamespace(): String =
    "io.festicore." + path.removePrefix(":").split(':').joinToString(".") { if (it == "interface") "contract" else it }
