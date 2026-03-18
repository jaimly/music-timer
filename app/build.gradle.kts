plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.ganquan.musictimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.ganquan.musictimer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.1.5"

//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all { variant ->
        variant.outputs.all { output ->
            // todo: 未生效，待研究
//            if (output.outputFile?.name?.endsWith(".apk") == true) {
//                val file = File(output.outputFile.parent + "音乐定时器-v${variant.versionName}.apk")
//                output.outputFile.renameTo(file)
//            }
//            output.outputFile.name = "音乐定时器-v${variant.versionName}.apk"

            return@all true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media)
    implementation(libs.gson)
    implementation(libs.material)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}