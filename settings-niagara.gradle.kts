/*
 * Niagara module build. Run with:
 *   ./gradlew -c settings-niagara.gradle.kts jar
 *
 * Layout follows ci-richard-mcelhinney/nhaystack settings.gradle.kts
 * Niagara 4.13+ Gradle plugin 7.6.x.
 */

pluginManagement {
    val loaded = java.util.Properties()
    val localFile = file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { loaded.load(it) }
    }
    val gradleProps = java.util.Properties()
    val gradleFile = file("gradle.properties")
    if (gradleFile.exists()) {
        gradleFile.inputStream().use { gradleProps.load(it) }
    }
    fun prop(name: String): String? {
        val fromLocal = loaded.getProperty(name)
        if (!fromLocal.isNullOrBlank()) {
            if (System.getProperty(name) == null) {
                System.setProperty(name, fromLocal)
            }
            return fromLocal
        }
        val sys = System.getProperty(name)
        if (!sys.isNullOrBlank()) {
            return sys
        }
        val env = System.getenv(name)
        if (!env.isNullOrBlank()) {
            return env
        }
        val fromGradle = gradleProps.getProperty(name)
        if (!fromGradle.isNullOrBlank()) {
            return fromGradle
        }
        return null
    }

    val niagaraHome = prop("niagara_home")
        ?: System.getenv("NIAGARA_HOME")
        ?: System.getenv("niagara_home")
    val pluginHome = prop("gradlePluginHome")
        ?: System.getenv("GRADLE_PLUGIN_HOME")
        ?: niagaraHome?.let { "$it/etc/m2/repository" }

    if (pluginHome.isNullOrBlank()) {
        throw GradleException(
            "Set niagara_home (or NIAGARA_HOME) to a Niagara 4.13 or later install before building the module. " +
                "See README.md. Core tests do not need this: ./gradlew test"
        )
    }

    val pluginVersion = prop("gradlePluginVersion") ?: "7.6.22"
    val settingsVersion = prop("settingsPluginVersion") ?: "7.6.3"

    repositories {
        maven { url = uri(pluginHome) }
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.tridium.niagara") version pluginVersion
        id("com.tridium.vendor") version pluginVersion
        id("com.tridium.niagara-module") version pluginVersion
        id("com.tridium.niagara-signing") version pluginVersion
        id("com.tridium.convention.niagara-home-repositories") version pluginVersion
        id("com.tridium.niagara-annotation-processors") version pluginVersion
        id("com.tridium.settings.multi-project") version settingsVersion
    }
}

rootProject.name = "niagaraGit"
rootProject.buildFileName = "niagara.gradle.kts"

include("niagaraGit-rt")
project(":niagaraGit-rt").buildFileName = "niagaraGit-rt.gradle.kts"
