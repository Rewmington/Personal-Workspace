buildscript {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev/")
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.compose:compose-gradle-plugin:1.7.0")
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}