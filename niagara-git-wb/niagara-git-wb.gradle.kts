import com.tridium.gradle.plugins.module.util.ModulePart.RuntimeProfile.*

plugins {
    id("com.tridium.niagara-module")
    id("com.tridium.convention.niagara-home-repositories")
    id("com.tridium.niagara-annotation-processors")
    // id("com.tridium.niagara-signing")
}

description = "Niagara Git Workbench view."

moduleManifest {
    moduleName.set("niagaraGit")
    runtimeProfile.set(wb)
    vendor.set("bew1769")
}

dependencies {
    nre(":nre")
    api(":baja")
    // BWbComponentView is in workbench-wb. Widgets are in bajaui-wb.
    // Confirm both part names if Gradle cannot resolve them.
    api(":bajaui-wb")
    api(":workbench-wb")
    api(project(":niagara-git-rt"))
}
