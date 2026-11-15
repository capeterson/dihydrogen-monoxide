package casa.peterson.dhmo.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/** Applied by the application and library plugins, not directly. */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        dependencies {
            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("kotlinx.coroutines.test"))
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("androidx.test.core"))
            add("testImplementation", libs.library("androidx.test.ext.junit"))
            add("androidTestImplementation", libs.library("androidx.test.runner"))
            add("androidTestImplementation", libs.library("androidx.test.ext.junit"))
            add("androidTestImplementation", libs.library("truth"))
        }
        val toolchains = extensions.getByType<JavaToolchainService>()
        // Machine-local knobs, set in ~/.gradle/gradle.properties: heap per test JVM and how many test JVMs may run at once.
        val testHeap = providers.gradleProperty("dhmo.test.maxHeap").getOrElse(DEFAULT_TEST_HEAP)
        val testParallelism = providers.gradleProperty("dhmo.test.parallelism").map(String::toInt).getOrElse(DEFAULT_TEST_PARALLELISM)
        val testLimiter = gradle.sharedServices.registerIfAbsent("testConcurrency", TestConcurrencyService::class.java) {
            maxParallelUsages.set(testParallelism)
        }
        tasks.withType<Test>().configureEach {
            maxHeapSize = testHeap
            usesService(testLimiter)
            // Run Robolectric on Java 25; the application still targets Java 17 bytecode.
            javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(ROBOLECTRIC_JAVA_VERSION)) })
            // Modules ship robolectric.properties before their first test; Gradle 9 would otherwise fail the task.
            failOnNoDiscoveredTests.set(false)
        }
    }
}

private const val ROBOLECTRIC_JAVA_VERSION = 25
private const val DEFAULT_TEST_HEAP = "1g"
private const val DEFAULT_TEST_PARALLELISM = 2
