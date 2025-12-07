import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.justdeko"
version = project.findProperty("publish.version") as String? ?: "1.0.0-SNAPSHOT"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        publishLibraryVariants("release")
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

// Debug: Check environment variables
println("=== Signing Configuration Debug ===")
val envSigningKey = System.getenv("SIGNING_KEY")
val envSigningKeyId = System.getenv("SIGNING_KEY_ID")  // NEW: Key ID needed!
val envSigningPassword = System.getenv("SIGNING_PASSWORD")
val envMavenUser = System.getenv("CENTRAL_PORTAL_USERNAME")
val envMavenPass = System.getenv("CENTRAL_PORTAL_PASSWORD")

println("ENV SIGNING_KEY present: ${envSigningKey != null}")
println("ENV SIGNING_KEY length: ${envSigningKey?.length ?: 0}")
println("ENV SIGNING_KEY starts correctly: ${envSigningKey?.startsWith("-----BEGIN PGP") ?: false}")
println("ENV SIGNING_KEY_ID present: ${envSigningKeyId != null}")
println("ENV SIGNING_PASSWORD present: ${envSigningPassword != null}")
println("ENV MAVEN credentials present: ${envMavenUser != null && envMavenPass != null}")

// Map environment variables to project properties for Vanniktech plugin
// Requires ALL THREE signing properties: key, keyId, and password
if (envSigningKey != null && envSigningPassword != null) {
    println("Setting signing properties...")
    ext["signingInMemoryKey"] = envSigningKey
    ext["signingInMemoryKeyPassword"] = envSigningPassword

    // Key ID: Extract from GPG key or use environment variable
    // If not provided, try to extract from key (last 8 chars of key ID line)
    if (envSigningKeyId != null) {
        ext["signingInMemoryKeyId"] = envSigningKeyId
        println("Using provided key ID: $envSigningKeyId")
    } else {
        println("WARNING: SIGNING_KEY_ID not set - signing may fail")
        println("To get your key ID, run: gpg --list-secret-keys --keyid-format=long")
    }
}
if (envMavenUser != null) ext["mavenCentralUsername"] = envMavenUser
if (envMavenPass != null) ext["mavenCentralPassword"] = envMavenPass

// Verify properties are accessible
afterEvaluate {
    println("=== After Evaluate Check ===")
    println("ext.signingInMemoryKey present: ${ext.has("signingInMemoryKey")}")
    println("ext.signingInMemoryKeyPassword present: ${ext.has("signingInMemoryKeyPassword")}")
    println("ext.mavenCentralUsername present: ${ext.has("mavenCentralUsername")}")
    println("ext.mavenCentralPassword present: ${ext.has("mavenCentralPassword")}")
    println("===================================")
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("io.github.justdeko", "kuiver", version.toString())

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
