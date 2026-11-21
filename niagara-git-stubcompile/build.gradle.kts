plugins {
    java
}

dependencies {
    implementation(project(":niagara-git-core"))
    compileOnly(project(":niagara-git-stubs"))
}

// Prove the station and Workbench sources compile without the Niagara SDK.
// This project produces no jar.
sourceSets.main.get().java.setSrcDirs(listOf(
    "../niagara-git-rt/src",
    "../niagara-git-wb/src"
))

tasks.jar {
    enabled = false
}
