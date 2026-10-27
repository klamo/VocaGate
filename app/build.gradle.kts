import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

val vocaGateVersion = providers.gradleProperty("vocagate.version").get()
val vocaGateVersionCode = providers.gradleProperty("vocagate.versionCode").get().toInt()
val appProject = project
val releaseSigningFilePath = providers.gradleProperty("vocagate.releaseSigningFile").orNull
var releaseSigningConfigured = false

android {
    namespace = "com.vocagate.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vocagate.demo"
        minSdk = 23
        targetSdk = 36
        versionCode = vocaGateVersionCode
        versionName = vocaGateVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val releaseSigningConfig = signingConfigs.create("release")
    buildTypes.getByName("release").signingConfig = releaseSigningConfig

    if (releaseSigningFilePath != null) {
        val signingPropertiesFile = rootProject.file(releaseSigningFilePath)
        check(signingPropertiesFile.isFile) {
            "Release signing requires ${signingPropertiesFile.path}."
        }
        val signingProperties = Properties().apply {
            FileInputStream(signingPropertiesFile).use(::load)
        }
        fun requiredSigningProperty(name: String): String = signingProperties.getProperty(name)
            ?.takeIf(String::isNotBlank)
            ?: error("Release signing property '$name' is required in ${signingPropertiesFile.path}.")

        releaseSigningConfig.storeFile = rootProject.file(requiredSigningProperty("storeFile"))
        releaseSigningConfig.storePassword = requiredSigningProperty("storePassword")
        releaseSigningConfig.keyAlias = requiredSigningProperty("keyAlias")
        releaseSigningConfig.keyPassword = requiredSigningProperty("keyPassword")
        releaseSigningConfigured = true
    }
}

fun isReleaseArtifactTask(task: org.gradle.api.Task): Boolean {
    if (task.project != appProject) {
        return false
    }
    return task.name in setOf(
        "assembleRelease",
        "packageRelease",
        "bundleRelease",
        "signReleaseBundle",
        "signReleasePackage"
    )
}

tasks.configureEach {
    if (isReleaseArtifactTask(this)) {
        doFirst {
            check(releaseSigningConfigured) {
                "Release signing requires -Pvocagate.releaseSigningFile=<path-to-release-signing.properties>."
            }
        }
    }
}

dependencies {
    val cameraXVersion = "1.6.1"

    implementation(project(":gate-core"))
    implementation("androidx.activity:activity:1.12.4")
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("com.google.mlkit:face-detection:16.1.7")

    testImplementation("junit:junit:4.13.2")
}
