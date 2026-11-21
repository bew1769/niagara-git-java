/*
 * Workbench settings. Copy over settings.gradle.kts (and build.niagara.gradle.kts
 * over build.gradle.kts) on a machine that has NIAGARA_HOME set.
 *
 * Plugin versions come from gradle.properties (gradlePluginVersion,
 * settingsPluginVersion). Replace them with the versions in any
 * settings.gradle.kts Workbench generated for this Niagara install.
 * Tridium's public 4.13 code-samples use 7.3.40 / 7.3.0; a later 4.13 build
 * uses a different fourth number.
 */

import com.tridium.gradle.plugins.settings.LocalSettingsExtension
import com.tridium.gradle.plugins.settings.MultiProjectExtension

pluginManagement {
    val niagaraHome: Provider<String> = providers.gradleProperty("niagara_home").orElse(
        providers.systemProperty("niagara_home").orElse(
            providers.environmentVariable("NIAGARA_HOME").orElse(
                providers.environmentVariable("niagara_home")
            )
        )
    )
    val gradlePluginHome: String = providers.gradleProperty("gradlePluginHome").orElse(
        providers.environmentVariable("GRADLE_PLUGIN_HOME").orElse(
            niagaraHome.map { "$it/etc/m2/repository" }
        )
    ).orNull ?: throw InvalidUserDataException(
        "Set niagara_home or NIAGARA_HOME before using settings.niagara.gradle.kts"
    )
    val gradlePluginRepoUrl = "file:///${gradlePluginHome.replace('\\', '/')}"
    val gradlePluginVersion = providers.gradleProperty("gradlePluginVersion").getOrElse("7.3.40")
    val settingsPluginVersion = providers.gradleProperty("settingsPluginVersion").getOrElse("7.3.0")

    repositories {
        maven(url = gradlePluginRepoUrl)
        gradlePluginPortal()
    }
    plugins {
        id("com.tridium.settings.multi-project") version settingsPluginVersion
        id("com.tridium.settings.local-settings-convention") version settingsPluginVersion
        id("com.tridium.niagara") version gradlePluginVersion
        id("com.tridium.vendor") version gradlePluginVersion
        id("com.tridium.niagara-module") version gradlePluginVersion
        id("com.tridium.niagara-signing") version gradlePluginVersion
        id("com.tridium.convention.niagara-home-repositories") version gradlePluginVersion
        id("com.tridium.niagara-annotation-processors") version gradlePluginVersion
    }
}

plugins {
    id("com.tridium.settings.multi-project")
    id("com.tridium.settings.local-settings-convention")
}

configure<LocalSettingsExtension> {
    loadLocalSettings()
}

configure<MultiProjectExtension> {
    // Only the module parts. The CLI and the stubs stay on the plain Gradle build.
    findProjects("niagara-git-rt")
    findProjects("niagara-git-wb")
}

rootProject.name = "niagaraGit"
