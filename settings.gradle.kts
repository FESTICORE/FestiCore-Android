pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FestiCore"

// 주의: listFiles · isFile은 이 스크립트에서 직접 부른다. walkTopDown 안에서 부르면 Gradle이 추적하지 못해 모듈을 추가 · 삭제해도 configuration cache가 예전 목록을 쓴다.
val skippedDirs = setOf("build", "src", "gradle")

fun findModules(dir: File): List<File> {
    val children = dir.listFiles()?.filter { it.isDirectory && it.name !in skippedDirs && !it.name.startsWith(".") }.orEmpty()
    val self = if (File(dir, "build.gradle.kts").isFile) listOf(dir) else emptyList()
    return self + children.flatMap { findModules(it) }
}

listOf("app", "feature", "domain", "service", "core", "ui")
    .map { File(rootDir, it) }
    .filter { it.isDirectory }
    .flatMap { findModules(it) }
    .map { ":" + it.relativeTo(rootDir).invariantSeparatorsPath.replace('/', ':') }
    .sorted()
    .forEach { include(it) }
