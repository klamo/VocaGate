import org.gradle.api.GradleException
import org.gradle.api.tasks.Sync

plugins {
    application
}

val vocaGateVersion = providers.gradleProperty("vocagate.version").get()
val targetPlatform = providers.gradleProperty("targetPlatform").orNull
val releaseStagingTarget = providers.gradleProperty("targetPlatform").orElse("").get()
val releaseMetadataDirectory = layout.buildDirectory.dir("generated/sources/release-metadata/java")
val generateDesktopReleaseMetadata by tasks.registering {
    inputs.property("version", vocaGateVersion)
    outputs.dir(releaseMetadataDirectory)

    doLast {
        val output = releaseMetadataDirectory.get().file(
            "com/vocagate/desktop/app/DesktopReleaseMetadata.java"
        ).asFile
        output.parentFile.mkdirs()
        output.writeText(
            """package com.vocagate.desktop.app;

public final class DesktopReleaseMetadata {
    public static final String VERSION_NAME = "$vocaGateVersion";

    private DesktopReleaseMetadata() {
    }
}
"""
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        java.srcDir(releaseMetadataDirectory)
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateDesktopReleaseMetadata)
}

dependencies {
    implementation(project(":gate-core"))
    implementation("com.github.sarxos:webcam-capture:0.3.12")
    implementation("org.bytedeco:opencv:4.9.0-1.5.10")
    val nativeClassifiers = when (targetPlatform) {
        null -> listOf("macosx-arm64", "macosx-x86_64", "windows-x86_64")
        "macos-arm64" -> listOf("macosx-arm64")
        "windows-x64" -> listOf("windows-x86_64")
        else -> throw GradleException("Unsupported targetPlatform: $targetPlatform")
    }
    nativeClassifiers.forEach { classifier ->
        runtimeOnly("org.bytedeco:opencv:4.9.0-1.5.10:$classifier")
        runtimeOnly("org.bytedeco:openblas:0.3.26-1.5.10:$classifier")
        runtimeOnly("org.bytedeco:javacpp:1.5.10:$classifier")
    }
    runtimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

application {
    mainClass.set("com.vocagate.desktop.app.DesktopCompanionApp")
}

val stageReleaseApp by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages a self-contained desktop application for the selected target platform."
    dependsOn(tasks.named("installDist"))
    into(layout.buildDirectory.dir("release-staging").map { it.dir(releaseStagingTarget) })

    from(layout.buildDirectory.dir("install/desktop/lib")) {
        into("app/lib")
    }
    from(layout.buildDirectory.dir("sidecar").map { it.dir("$releaseStagingTarget/vocagate-mediapipe") }) {
        into("app/sidecar")
    }
    from(layout.buildDirectory.dir("sidecar").map { it.dir("$releaseStagingTarget/models") }) {
        into("app/sidecar/models")
    }
    from(layout.projectDirectory.file("src/main/resources/packaging/THIRD_PARTY_NOTICES.txt")) {
        into("app")
    }

    doFirst {
        if (releaseStagingTarget !in setOf("macos-arm64", "windows-x64")) {
            throw GradleException("stageReleaseApp requires -PtargetPlatform=macos-arm64 or -PtargetPlatform=windows-x64")
        }
        val sidecar = layout.buildDirectory.file(
            "sidecar/$releaseStagingTarget/vocagate-mediapipe/" +
                    if (releaseStagingTarget == "windows-x64") "vocagate-mediapipe.exe" else "vocagate-mediapipe"
        ).get().asFile
        val model = layout.buildDirectory.file(
            "sidecar/$releaseStagingTarget/models/face_landmarker.task"
        ).get().asFile
        if (!sidecar.isFile || !model.isFile) {
            throw GradleException(
                "Prepared $releaseStagingTarget MediaPipe sidecar is missing. " +
                        "Run the matching scripts/prepare_mediapipe_sidecar helper first."
            )
        }
    }
}
