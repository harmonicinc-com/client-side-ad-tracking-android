plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

@Suppress("UnstableApiUsage")
android {
    namespace = "com.gtihub.harmonicinc.clientsideadtracking"
    compileSdk = Constants.compileSdkVersion

    defaultConfig {
        minSdk = Constants.minSdkVersion
        targetSdk = Constants.targetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        //OMSDK Config
        buildConfigField("String", "PARTNER_NAME", "\"com.harmonicinc.omsdkdemo\"")
        buildConfigField("String", "VENDOR_KEY", "\"harmonic\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests.all {
            it.jvmArgs("-noverify")
        }
    }
}

dependencies {
    val coroutines_version = "1.7.1"

    implementation(project(":lib:lib"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("com.google.android.gms:play-services-pal:20.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutines_version")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.google.android.tv:tv-ads:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")

    // Workaround Volley NoClassDefFoundError
    testImplementation("org.apache.httpcomponents:httpclient:4.5.14")

    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
}