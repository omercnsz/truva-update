plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

import java.util.Properties
import java.io.FileInputStream

group = "com.truva"
version = "19.9.0"

android {
    namespace = "com.truva"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.truva"
        minSdk = 24
        targetSdk = 34
        versionCode = 47
        versionName = "19.9.0"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val propFile = rootProject.file("local.properties")
                if (propFile.exists()) load(FileInputStream(propFile))
            }
            storeFile = file("truva-key.jks")
            storePassword = localProps.getProperty("TRUVA_STORE_PASSWORD", "")
            keyAlias = localProps.getProperty("TRUVA_KEY_ALIAS", "truva")
            keyPassword = localProps.getProperty("TRUVA_KEY_PASSWORD", "")
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Android bytecode için 17 kullanılır; JDK 25 ile derleme için gradle.properties'te org.gradle.java.home= path verebilirsin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // C++ Native Hook Modülü (libtruva_hook.so)
    // CMake dosyası hazır olduğunda aşağıdaki bloğu aktif et:
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }
    // ndkVersion = "26.1.10909125"

    packaging {
        jniLibs {
            // Xray AAR ve gelecekteki native hook .so dosyalarının çakışmasını önle
            pickFirsts += listOf("**/libgojni.so", "**/libtruva_hook.so")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // include xray gomobile aar if present (avoid failure when file missing)
    val xrayAar = file("libs/xray.aar")
    if (xrayAar.exists()) {
        implementation(files(xrayAar))
    }

    // Xposed API stubs (LSPatch hook modülü derlenmesi için)
    // compileOnly: Sadece derleme zamanı, APK'ya dahil edilmez
    compileOnly(files("libs/xposed-api-82.jar"))

    // Room (SQLite yerine modern kalıcı depolama katmanı)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended")

    // JSON (SmartRouter Xray config build)
    implementation("com.google.code.gson:gson:2.10.1")

    // Play Services Location (GPS mock provider fallback)
    implementation("com.google.android.gms:play-services-location:21.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

// buildLoaderDex task removed as part of Truva Core architectural cleanup.
