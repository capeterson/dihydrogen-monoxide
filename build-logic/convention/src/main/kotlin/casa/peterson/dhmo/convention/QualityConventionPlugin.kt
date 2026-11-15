package casa.peterson.dhmo.convention

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig.set(true)
            parallel.set(true)
            autoCorrect.set(false)
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            source.setFrom(files("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin", "src/debug/kotlin"))
        }
        tasks.withType<Detekt>().configureEach {
            jvmTarget.set("17")
            reports {
                sarif.required.set(true)
                html.required.set(true)
            }
        }
        extensions.configure<KtlintExtension> {
            version.set(libs.findVersion("ktlint").get().requiredVersion)
            android.set(true)
            outputToConsole.set(true)
        }
    }
}
