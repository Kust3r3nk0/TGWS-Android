// AGP 9.x has built-in Kotlin. The Compose compiler plugin is pinned separately.
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
