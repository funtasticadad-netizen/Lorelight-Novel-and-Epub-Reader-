plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

tasks.register("unzipFonts") {
    doLast {
        val fontsZip = file("../fonts.zip")
        if (fontsZip.exists()) {
            copy {
                from(zipTree(fontsZip))
                into(file("src/main/res/font"))
            }
        }
        val fontDir = file("src/main/res/font")
        if (fontDir.exists()) {
            fontDir.listFiles()?.filter { it.name.endsWith(".zip") }?.forEach { subZip ->
                copy {
                    from(zipTree(subZip))
                    into(file("src/main/res/font/${subZip.nameWithoutExtension}"))
                }
                subZip.delete()
            }
        }
    }
}

android {
    namespace = "com.example.lorelight"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aistudio.lorelight.fkmqz"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.8")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.webkit:webkit:1.11.0")
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
