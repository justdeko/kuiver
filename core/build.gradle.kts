import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

group = "io.github.justdeko"
version = project.findProperty("publish.version") as String? ?: "1.0.0-SNAPSHOT"

kotlin {
    android {
        namespace = "com.dk.kuiver.library"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
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

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.ui.test)
            // needed to render in ui tests
            implementation(compose.desktop.currentOs)
        }
    }
}

private val benchmarkFilter = "com.dk.kuiver.renderer.LayoutTransitionBenchmark"
private val benchmarkReport = layout.buildDirectory.file("reports/benchmarks/layout-transition.txt")

// Timings are meaningless on a shared CI runner, so keep them out of the regular test run
tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching(benchmarkFilter)
}

tasks.register<Test>("benchmark") {
    group = "verification"
    description = "Renderer frame cost report, see docs/benchmarks/layout-transition.md"

    val testCompilation = kotlin.jvm().compilations.getByName("test")
    testClassesDirs = testCompilation.output.classesDirs
    classpath = testCompilation.output.allOutputs + testCompilation.runtimeDependencyFiles

    val reportFile = benchmarkReport.get().asFile
    filter.includeTestsMatching(benchmarkFilter)
    systemProperty("kuiver.benchmark.report", reportFile.absolutePath)
    testLogging.showStandardStreams = true
    outputs.upToDateWhen { false }

    doFirst { reportFile.delete() }
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
