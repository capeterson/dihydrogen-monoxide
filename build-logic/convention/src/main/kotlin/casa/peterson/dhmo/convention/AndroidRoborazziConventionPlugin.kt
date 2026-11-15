package casa.peterson.dhmo.convention

import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")

        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }

        tasks.withType<Test>().configureEach {
            systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }

        dependencies {
            add("testImplementation", libs.library("roborazzi"))
            add("testImplementation", libs.library("roborazzi.compose"))
            add("testImplementation", libs.library("roborazzi.junit.rule"))
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("androidx.compose.ui.test.junit4"))
            add("testImplementation", libs.library("androidx.test.espresso.core"))
        }
    }
}
