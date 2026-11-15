package casa.peterson.dhmo.convention

import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            // The Room plugin also registers this directory as test and androidTest assets.
            schemaDirectory("$projectDir/schemas")
        }
        extensions.configure<KspExtension> {
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("implementation", libs.library("androidx.room.runtime"))
            add("implementation", libs.library("androidx.room.ktx"))
            add("implementation", libs.library("androidx.room.paging"))
            add("ksp", libs.library("androidx.room.compiler"))
            add("testImplementation", libs.library("androidx.room.testing"))
            add("androidTestImplementation", libs.library("androidx.room.testing"))
        }
    }
}
