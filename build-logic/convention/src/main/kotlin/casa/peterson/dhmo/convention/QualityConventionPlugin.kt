package casa.peterson.dhmo.convention

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            autoCorrect = false
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin", "src/debug/kotlin"))
        }
        tasks.withType<Detekt>().configureEach {
            jvmTarget = "17"
            reports {
                sarif.required.set(true)
                html.required.set(true)
            }
        }
        // Keep detekt's own compiler: the Kotlin Gradle plugin must not upgrade it.
        configurations.matching { it.name == "detekt" }.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
            }
        }

        extensions.configure<KtlintExtension> {
            version.set(libs.findVersion("ktlint").get().requiredVersion)
            android.set(true)
            outputToConsole.set(true)
        }
    }
}
