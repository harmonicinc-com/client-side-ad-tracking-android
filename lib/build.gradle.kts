plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    signing
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

    implementation(project(":lib:lib"))

    // 3rd party libs
    implementation("com.google.android.gms:play-services-pal:20.1.1") {}
    implementation("com.github.bumptech.glide:glide:4.16.0")
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
val releaseVersion: String by project

// Exclude OMSDK local aar (named "lib")
val excludedArtifact = setOf("lib")

// Credentials
val ossrhUsername: String? = System.getenv("OSSRH_USERNAME")
val ossrhPassword: String? = System.getenv("OSSRH_PASSWORD")
ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")

val buildNumber: String? = System.getenv("BUILD_NUMBER")

signing {
    sign(publishing.publications)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = publishedGroupId
            artifactId = artifactName
            version = "$releaseVersion.$buildNumber"
            artifact("$buildDir/outputs/aar/${project.name}-release.aar")

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
                    }
                }
                organization {
                    name.set(developerOrg)
                }
                scm {
                    connection.set(gitUrl)
                    developerConnection.set(gitUrl)
                    url.set(siteUrl)
                }
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    val configurationNames = arrayOf("implementation", "api")
                    configurationNames.forEach { configurationName ->
                        configurations[configurationName].allDependencies.forEach {
                            // Exclude OMSDK AAR in POM as it is private
                            if (it.group != null && it.name !in excludedArtifact) {
                                val dependencyNode = dependenciesNode.appendNode("dependency")
                                dependencyNode.appendNode("groupId", it.group)
                                dependencyNode.appendNode("artifactId", it.name)
                                dependencyNode.appendNode("version", it.version)
                            }
                        }
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                username = System.getenv("OSSRH_DEPLOY_USERNAME")
                password = System.getenv("OSSRH_DEPLOY_PASSWORD")
            }
        }
    }
}