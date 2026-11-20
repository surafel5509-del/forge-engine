plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine-core"))
    implementation(project(":editor-core"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

/** Renders off-screen preview images of the demo project (build/screenshots). */
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    description = "Renders scene and play-mode previews to build/screenshots"
    mainClass.set("com.forge.desktop.ScreenshotTool")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(File(buildDir, "screenshots").absolutePath)
}
