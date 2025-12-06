import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("maven-publish")
    id("signing")
}

group = "io.github.justdeko"
version = project.findProperty("publish.version") as String? ?: "1.0.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
        mavenPublication {
            artifactId = "kuiver-android"
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Kuiver"
            isStatic = true
        }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.dk.kuiver.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("Kuiver")
                description.set("A Compose Multiplatform library for visualizing and interacting with directed graphs")
                url.set("https://github.com/justdeko/kuiver")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("dk")
                        name.set("Denis Koljada")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/justdeko/kuiver.git")
                    developerConnection.set("scm:git:ssh://github.com/justdeko/kuiver.git")
                    url.set("https://github.com/justdeko/kuiver")
                }
            }
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "centralPortal"
            val isSnapshot = version.toString().endsWith("-SNAPSHOT")
            url = if (isSnapshot) {
                // Snapshots go to the snapshots repository
                uri("https://central.sonatype.com/repository/maven-snapshots/")
            } else {
                // Releases go to the publisher upload API
                uri("https://central.sonatype.com/api/v1/publisher/upload?publishingType=USER_MANAGED")
            }
            credentials {
                username = System.getenv("CENTRAL_PORTAL_USERNAME") ?: project.findProperty("centralPortalUsername") as String? ?: ""
                password = System.getenv("CENTRAL_PORTAL_PASSWORD") ?: project.findProperty("centralPortalPassword") as String? ?: ""
            }
        }
    }
}

signing {
    // Use in-memory ASCII-armored keys from environment variables
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

// Configure artifact IDs after publications are created
afterEvaluate {
    publishing.publications.withType<MavenPublication> {
        artifactId = when (name) {
            "kotlinMultiplatform" -> "kuiver"
            "androidRelease" -> "kuiver-android"
            else -> "kuiver-${name.lowercase()}"
        }
    }
}
