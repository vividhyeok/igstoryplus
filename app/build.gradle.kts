plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.vividhyeok.igstoryplus"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vividhyeok.igstoryplus"
        minSdk = 29
        targetSdk = 35
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.3.4-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "igstoryplus"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true; disable += "ObsoleteLintCustomCheck" }
    testOptions { unitTests.isIncludeAndroidResources = true; animationsDisabled = true }
}
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation(project(":photoeditor"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.media3:media3-transformer:1.6.1")
    implementation("androidx.media3:media3-effect:1.6.1")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
