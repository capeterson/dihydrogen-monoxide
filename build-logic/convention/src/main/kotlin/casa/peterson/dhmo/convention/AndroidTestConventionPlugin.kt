package casa.peterson.dhmo.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

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
        // Robolectric's Android framework jar comes from Gradle's dependency cache, not from a download at test time.
        val androidAll = configurations.create("robolectricAndroidAll") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isTransitive = false
        }
        dependencies { add(androidAll.name, libs.library("robolectric.android.all")) }
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
            jvmArgumentProviders.add(RobolectricOfflineArguments(androidAll))
            // Robolectric needs Java 21+ to load the API 37 framework. Tests run on the project's JDK (25, the same as
            // the Gradle daemon), stated here so a daemon started some other way cannot pick an older one.
            javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(JDK_VERSION)) })
            // Modules ship robolectric.properties before their first test; Gradle 9 would otherwise fail the task.
            failOnNoDiscoveredTests.set(false)
        }
    }
}

/**
 * Points Robolectric at the cached framework jar and forbids it from reaching for the network. Gradle keeps each
 * artifact alone in its own directory under the original file name, which is the layout `robolectric.dependency.dir`
 * expects. The jar is a task input, so a different jar re-runs the tests.
 */
internal class RobolectricOfflineArguments(@get:InputFiles @get:PathSensitive(PathSensitivity.NONE) val androidAll: FileCollection) :
    CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Drobolectric.offline=true", "-Drobolectric.dependency.dir=${androidAll.singleFile.parent}")
}

private const val DEFAULT_TEST_HEAP = "1g"
private const val DEFAULT_TEST_PARALLELISM = 2
