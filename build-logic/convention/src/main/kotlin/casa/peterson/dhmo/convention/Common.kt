package casa.peterson.dhmo.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/**
 * The JDK that runs Gradle and the unit tests (gradle/gradle-daemon-jvm.properties pins the daemon to it). Bytecode
 * still targets 17: Android (minSdk 28) has no Java 21+ library APIs, and the JVM modules are consumed by Android ones.
 */
internal const val JDK_VERSION = 25
internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 28
internal const val TARGET_SDK = 37
internal const val BUILD_TOOLS = "37.0.0"
internal const val BASE_NAMESPACE = "casa.peterson.dhmo"

/** Kotlin compiler options shared by every module, Android or JVM. */
internal fun Project.configureKotlinCompilerOptions(extraOptIns: List<String> = emptyList()) {
    val warningsAsErrors = providers.gradleProperty("dhmo.warningsAsErrors").map(String::toBoolean).orElse(true)
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            extraOptIns.forEach { freeCompilerArgs.add("-opt-in=$it") }
        }
    }
}
