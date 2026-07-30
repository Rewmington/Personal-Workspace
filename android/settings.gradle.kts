import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        // Compose Multiplatform 专用仓库！
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        google()
        mavenCentral()
    }
}
rootProject.name = "PersonalWorkstation"
include(":app")