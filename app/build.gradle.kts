import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePath = System.getenv("KEYSTORE_PATH")
val requireSigning = System.getenv("REQUIRE_SIGNING") == "true"

if (requireSigning) {
    val missing = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
        .filter { System.getenv(it).isNullOrBlank() }
    if (missing.isNotEmpty() || keystorePath.isNullOrBlank() || !file(keystorePath).isFile) {
        throw GradleException(
            "Signed release requires KEYSTORE_PATH (PKCS12 file), KEYSTORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD."
        )
    }
}

android {
    namespace = "app.cartube"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "app.cartube"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0"
    }

    signingConfigs {
        if (!keystorePath.isNullOrBlank() && file(keystorePath).isFile) {
            create("release") {
                storeType = "PKCS12"
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!keystorePath.isNullOrBlank() && file(keystorePath).isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

base {
    archivesName.set("car-tube")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.media:media:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
