import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.10"
}

@Suppress("UnstableApiUsage")
android {
    namespace = "com.github.harmonicinc.clientsideadtracking"
    compileSdk = Constants.compileSdkVersion

    defaultConfig {
        minSdk = Constants.minSdkVersion

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
        debug {
            enableUnitTestCoverage = true
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
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-noverify")
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val coroutinesVersion = "1.7.3"
    val okhttpVersion = "4.12.0"

    // Use compileOnly for OMSDK AAR to exclude it from POM
    compileOnly(project(":lib:lib"))
    // Also add it as testImplementation so tests can run
    testImplementation(project(":lib:lib"))

    // 3rd party libs
    implementation("com.google.android.gms:play-services-pal:20.1.1") {}
    implementation("com.google.android.tv:tv-ads:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

    // Android / Kotlin stdlibs
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // Fix for Javadoc generation - ensure correct kotlin-reflect version
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

/*
    Publishing
 */

val publishedGroupId: String by project
val artifactName: String by project
val libraryName: String by project
val libraryDescription: String by project
val siteUrl: String by project
val gitUrl: String by project
val licenseName: String by project
val licenseUrl: String by project
val developerOrg: String by project
val developerName: String by project
val developerUrl: String by project
val releaseVersion: String by project

// Exclude OMSDK local aar (named "lib")
val excludedArtifact = setOf("lib")

val buildNumber: String? = System.getenv("BUILD_NUMBER")
val fullVersion = "$releaseVersion.$buildNumber"

mavenPublishing {
   configure(AndroidSingleVariantLibrary(
       variant = "release",
       sourcesJar = true,
       publishJavadocJar = true,
   ))

   publishToMavenCentral(
       host = com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL,
       automaticRelease = true
   )

   signAllPublications()

   coordinates(publishedGroupId, artifactName, fullVersion)

   pom {
       name.set(libraryName)
       description.set(libraryDescription)
       url.set(siteUrl)

       licenses {
           license {
               name.set(licenseName)
               url.set(licenseUrl)
           }
       }

       developers {
           developer {
               name.set(developerName)
               organization.set(developerOrg)
               url.set(developerUrl)
           }
       }

       scm {
           url.set(siteUrl)
           connection.set("scm:git:git://$gitUrl")
           developerConnection.set("scm:git:ssh://git@$gitUrl")
       }
   }
}