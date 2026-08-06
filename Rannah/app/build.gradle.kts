import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Release signing credentials live outside the repository and are never tracked.
 * This file only names where to look for them; it holds no secret itself.
 *
 * Default: ~/.keystores/rannah/keystore.properties (mode 0600), which declares
 * storeFile, storePassword, keyAlias, keyPassword. Override the location with
 * -Prannah.keystoreProperties=… or the RANNAH_KEYSTORE_PROPERTIES env var.
 *
 * When the file is absent the release build still assembles — unsigned — so a
 * machine without the private key can verify compilation, shrinking and lint.
 */
val releaseSigningCredentials: Properties? = run {
    val configured = providers.gradleProperty("rannah.keystoreProperties").orNull
        ?: providers.environmentVariable("RANNAH_KEYSTORE_PROPERTIES").orNull
        ?: "${providers.systemProperty("user.home").get()}/.keystores/rannah/keystore.properties"
    File(configured).takeIf { it.isFile }?.let { descriptor ->
        Properties().apply { descriptor.inputStream().use(::load) }
    }
}

android {
    namespace = "com.bal.reminders"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bal.reminders"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseSigningCredentials?.let { credentials ->
            create("release") {
                storeFile = file(credentials.getProperty("storeFile"))
                storePassword = credentials.getProperty("storePassword")
                keyAlias = credentials.getProperty("keyAlias")
                keyPassword = credentials.getProperty("keyPassword")
                // v2 and v3 sign the whole APK, including the META-INF entries
                // that v1's per-entry digests cannot cover. Every device رَنّة
                // supports (minSdk 26) verifies them, so v1 would add only the
                // weaker signature and the warnings that come with it.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            // Coroutines' debug-agent metadata: only the JVM debugger reads it.
            excludes += "DebugProbesKt.bin"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    bundle {
        // The app forces the Arabic locale at runtime; never split language resources.
        language {
            enableSplit = false
        }
    }
}

val roomSchemas: Directory = layout.projectDirectory.dir("schemas")

ksp {
    arg("room.schemaLocation", roomSchemas.asFile.path)
    arg("room.generateKotlin", "true")
}

val markResources: Directory = layout.projectDirectory.dir("src/main/res")
val brandDocs: Directory = layout.projectDirectory.dir("../docs")

// The migration tests build their legacy databases from the exported schemas,
// and MarkGeometryTest reads the shipped drawables, so a change to either
// re-runs the tests that guard it.
tasks.withType<Test>().configureEach {
    systemProperty("rannah.schemaDir", roomSchemas.asFile.absolutePath)
    inputs.dir(roomSchemas).withPropertyName("roomSchemas")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // MarkGeometryTest reads the shipped drawables and brand assets directly, so
    // a hand-edited icon fails the build instead of reaching a device.
    systemProperty("rannah.resDir", markResources.asFile.absolutePath)
    systemProperty("rannah.docsDir", brandDocs.asFile.absolutePath)
    inputs.dir(markResources).withPropertyName("markResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.gson)
}
