import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing is read from keystore.properties (git-ignored) or from environment variables in CI.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.talkto.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.talkto.app"
        minSdk = 30 // Android 11: MANAGE_EXTERNAL_STORAGE, StorageVolume.directory
        targetSdk = 36
        // Every CI build gets a higher code, so each bundle can go to the Play Store as an update.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = build ?: 1
        versionName = "1.1." + (build ?: 0)
        // Where "Обратна връзка" sends its e-mail: the TALKTO_FEEDBACK_EMAIL secret in CI, empty means "pick an app".
        val feedback = (System.getenv("TALKTO_FEEDBACK_EMAIL") ?: providers.gradleProperty("talkto.feedbackEmail").orNull ?: "").replace("\"", "")
        buildConfigField("String", "FEEDBACK_EMAIL", "\"$feedback\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Two versions of the same app:
    // - full: everything, including the file manager and app control (All files access, all apps, accessibility);
    //   installed directly as an APK, and it keeps the old id so phones that have it keep their ZnaiKo;
    // - play: for Google Play, whose policy allows those three permissions only to file managers, launchers and
    //   accessibility tools. The pet, games, tasks, lessons, voice and Claude are the same.
    flavorDimensions += "store"
    productFlavors {
        create("full") {
            dimension = "store"
            applicationId = "com.talkto.app"
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
        create("play") {
            dimension = "store"
            applicationId = "znaiKo.app"
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = signingValue("storeFile", "TALKTO_KEYSTORE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "TALKTO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TALKTO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "TALKTO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time + newer java.nio APIs on every supported API level.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "/META-INF/*.kotlin_module",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // MANAGE_EXTERNAL_STORAGE and QUERY_ALL_PACKAGES are intentional for this app (sideloaded, not Play Store).
        disable += setOf("ScopedStorage", "QueryAllPackagesPermission")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Room (adaptive memory)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Face landmarks for live portrait
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.image.labeling)

    // Privileged app control (optional at runtime)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // Full version only: Play warns about SDKs that bypass hidden API limits. Called by name in PrivilegedShell.
    "fullImplementation"(libs.hiddenapibypass)

    // Unit tests (JVM, Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)

    // Instrumented tests (device / emulator)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
