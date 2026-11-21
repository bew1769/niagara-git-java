pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// CI and a workstation without Niagara. This build compiles the shared engine,
// the CLI jar, and the station/workbench sources against the stubs.
//
// Workbench uses settings.niagara.gradle.kts instead. That file applies
// Tridium's plugins and discovers niagara-git-rt and niagara-git-wb.
rootProject.name = "niagara-git"

include("niagara-git-core")
include("niagara-git-cli")
include("niagara-git-stubs")
include("niagara-git-stubcompile")
