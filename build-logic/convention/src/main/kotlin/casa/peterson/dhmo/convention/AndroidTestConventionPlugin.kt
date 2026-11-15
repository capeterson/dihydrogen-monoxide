package casa.peterson.dhmo.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
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
        tasks.withType<Test>().configureEach {
            maxHeapSize = "2g"
            // Modules ship robolectric.properties before their first test; Gradle 9 would otherwise fail the task.
            failOnNoDiscoveredTests.set(false)
        }
    }
}
