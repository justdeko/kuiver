plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.knit)
}

// Compile the README code blocks that knit generates into build/knit
private val knitDir = layout.buildDirectory.dir("knit")

knit {
    files = fileTree(project.rootDir) { include("README.md") }
}

tasks.named("knit") {
    val outputDir = knitDir.get().asFile
    inputs.files(rootProject.file("README.md"), rootProject.file("knit.properties"))
    outputs.dir(outputDir)
    // Drop examples removed from the README
    doFirst { outputDir.deleteRecursively() }
}

tasks.named("knitCheck") {
    enabled = false
}

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            kotlin.srcDir(tasks.named("knit"))
            dependencies {
                implementation(project(":core"))
                implementation(libs.runtime)
                implementation(libs.foundation)
                implementation(libs.ui)
                implementation(libs.material3)
            }
        }
    }
}
