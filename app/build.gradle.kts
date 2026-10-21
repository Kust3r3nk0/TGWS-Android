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
        versionCode = 22
        versionName = "0.6.0"
        testInstrumentationRunner = "io.github.kust3r3nk0.tgwsandroid.UpgradeInstrumentation"
    }
    // Release credentials come only from the local/CI environment, never from Git.
    val releaseStore = providers.environmentVariable("TGWS_RELEASE_STORE_FILE").orNull
    if (releaseStore != null) signingConfigs.create("production") {
        storeFile = file(releaseStore)
        storePassword = providers.environmentVariable("TGWS_RELEASE_STORE_PASSWORD").orNull
        keyAlias = providers.environmentVariable("TGWS_RELEASE_KEY_ALIAS").orNull
        keyPassword = providers.environmentVariable("TGWS_RELEASE_KEY_PASSWORD").orNull
    }
    buildTypes {
        release {
            isDebuggable = false
            // No debug signing fallback. Without credentials this produces unsigned candidate only.
            signingConfig = signingConfigs.findByName("production")
        }
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

abstract class GenerateUpdateFixtures : DefaultTask() {
    @get:InputFile abstract val script: RegularFileProperty
    @get:InputFile abstract val keyStore: RegularFileProperty
    @get:Internal abstract val sdkDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:javax.inject.Inject abstract val execOperations: org.gradle.process.ExecOperations

    @TaskAction fun generate() {
        execOperations.exec {
            commandLine("python3", script.get().asFile.absolutePath, "--sdk", sdkDirectory.get().asFile.absolutePath,
                "--keystore", keyStore.get().asFile.absolutePath, "--output", outputDirectory.get().asFile.absolutePath)
        }
    }
}
val generateUpdateFixtures by tasks.registering(GenerateUpdateFixtures::class) {
    dependsOn("assembleDebug")
    script.set(rootProject.layout.projectDirectory.file(".github/scripts/create_update_fixtures.py"))
    keyStore.set(android.signingConfigs.getByName("debug").storeFile!!)
    sdkDirectory.set(androidComponents.sdkComponents.sdkDirectory)
    outputDirectory.set(layout.buildDirectory.dir("generated/update-fixtures"))
}
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.deviceTests.values.forEach { test ->
        test.sources.assets?.addGeneratedSourceDirectory(generateUpdateFixtures, GenerateUpdateFixtures::outputDirectory)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.19.1")
    // Full APK cryptographic verification, including proof-of-rotation capabilities.
    implementation("com.android.tools.build:apksig:9.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    // Historical DoH regressions remain test-only; no DoH provider is shipped in the APK.
    testImplementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.5.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.5.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // Compose otherwise selects Espresso 3.5.0, whose hidden InputManager API
    // was removed on API 37. Use the current stable AndroidX Test integration.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
}
