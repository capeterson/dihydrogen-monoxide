package casa.peterson.dhmo.convention

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/** Settings shared by the application and library plugins. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    with(extension) {
        compileSdk = COMPILE_SDK
        buildToolsVersion = BUILD_TOOLS

        defaultConfig.minSdk = MIN_SDK
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        with(lint) {
            warningsAsErrors = true
            abortOnError = true
            disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
        }

        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.managedDevices.localDevices.create("pixel6Api34").apply {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp-atd"
        }
    }
    configureKotlinCompilerOptions()
}
