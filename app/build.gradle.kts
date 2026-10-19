plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.kust3r3nk0.tgwsandroid"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "io.github.kust3r3nk0.tgwsandroid"
        minSdk = 36
        targetSdk = 37
        versionCode = 15
        versionName = "0.3.9-alpha"
        testInstrumentationRunner = "io.github.kust3r3nk0.tgwsandroid.UpgradeInstrumentation"
    }
    buildFeatures { compose = true }
    sourceSets["test"].kotlin.directories.add("src/sharedTest/java")
    sourceSets["androidTest"].kotlin.directories.add("src/sharedTest/java")
    testOptions.unitTests.all { test ->
        test.systemProperty("tgws.soak.seconds", providers.gradleProperty("soakSeconds").orElse("2").get())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("androidx.core:core-ktx:1.17.0")
    // Historical DoH regressions remain test-only; no DoH provider is shipped in the APK.
    testImplementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.5.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.5.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
