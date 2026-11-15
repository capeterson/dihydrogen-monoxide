import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Also pins the Kotlin Gradle plugin version used by AGP's built-in Kotlin.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
}

// ---------------------------------------------------------------------------------------------
// checkModuleGraph (TECHNICAL_PLAN §3.2): fails when a module declares a project dependency that
// is not on its allowlist. Test configurations may additionally use :core:testing.
// ---------------------------------------------------------------------------------------------

val featureSet = setOf(":core:data", ":core:domain", ":core:model", ":core:common", ":core:designsystem")

val allowedProjectDependencies: Map<String, Set<String>> =
    mapOf(
        ":app" to
            setOf(
                ":core:common", ":core:model", ":core:domain", ":core:backup", ":core:database", ":core:datastore",
                ":core:data", ":core:designsystem", ":core:network", ":core:reminders", ":core:weather",
                ":feature:onboarding", ":feature:today", ":feature:history", ":feature:insights", ":feature:profile",
                ":feature:reminders", ":feature:settings", ":sync:healthconnect", ":sync:drive", ":widget",
            ),
        ":core:common" to emptySet(),
        ":core:model" to emptySet(),
        ":core:domain" to setOf(":core:model"),
        ":core:backup" to setOf(":core:model"),
        ":core:database" to setOf(":core:model"),
        ":core:datastore" to setOf(":core:model", ":core:common"),
        ":core:data" to
            setOf(":core:model", ":core:common", ":core:domain", ":core:backup", ":core:database", ":core:datastore"),
        ":core:designsystem" to setOf(":core:model"),
        ":core:network" to setOf(":core:common"),
        ":core:reminders" to setOf(":core:data", ":core:domain", ":core:model", ":core:common"),
        ":core:weather" to setOf(":core:data", ":core:network", ":core:domain", ":core:model", ":core:common"),
        ":core:testing" to setOf(":core:data", ":core:model", ":core:domain", ":core:common", ":core:designsystem"),
        ":feature:onboarding" to featureSet,
        ":feature:today" to featureSet,
        ":feature:history" to featureSet,
        ":feature:insights" to featureSet,
        ":feature:profile" to featureSet,
        ":feature:reminders" to featureSet,
        ":feature:settings" to featureSet + ":core:backup",
        ":sync:healthconnect" to
            setOf(":core:data", ":core:database", ":core:domain", ":core:model", ":core:common", ":core:designsystem"),
        ":sync:drive" to
            setOf(":core:data", ":core:backup", ":core:network", ":core:model", ":core:common", ":core:designsystem"),
        ":widget" to setOf(":core:data", ":core:domain", ":core:model", ":core:common", ":core:designsystem"),
    )

abstract class CheckModuleGraphTask : DefaultTask() {
    /** "project|configuration|dependency" triples, collected at configuration time. */
    @get:Input
    abstract val declaredEdges: ListProperty<String>

    @get:Input
    abstract val allowed: MapProperty<String, Set<String>>

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        val knownModules = allowed.get().keys
        declaredEdges.get().forEach { edge ->
            val (from, configuration, to) = edge.split("|")
            if (from == to) return@forEach // test variants depend on their own module
            val isTestConfiguration = configuration.contains("test", ignoreCase = true)
            val permitted =
                allowed.get()[from].orEmpty() + if (isTestConfiguration) setOf(":core:testing") else emptySet()
            if (from !in knownModules) {
                violations += "$from is not in the module graph allowlist"
            } else if (to !in permitted) {
                violations += "$from -> $to (configuration '$configuration') is not an allowed edge"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Module graph violations:\n" + violations.distinct().joinToString("\n") { "  - $it" })
        }
        logger.lifecycle("Module graph OK: ${declaredEdges.get().size} declared project dependencies checked.")
    }
}

tasks.register<CheckModuleGraphTask>("checkModuleGraph") {
    group = "verification"
    description = "Fails when a module has a project dependency that is not allowed by TECHNICAL_PLAN §3.2."
    allowed.set(allowedProjectDependencies)
    declaredEdges.set(
        provider {
            subprojects
                .filter { it.path in allowedProjectDependencies.keys || it.childProjects.isEmpty() }
                .flatMap { module ->
                    module.configurations.flatMap { configuration ->
                        configuration.dependencies.withType<ProjectDependency>().map {
                            "${module.path}|${configuration.name}|${it.path}"
                        }
                    }
                }
        },
    )
}

// The included build-logic project is formatted with the same ktlint rules.
tasks.register("ktlintCheck") {
    group = "verification"
    description = "Runs ktlint on the build-logic included build; module tasks of the same name run per project."
    dependsOn(gradle.includedBuild("build-logic").task(":convention:ktlintCheck"))
}
