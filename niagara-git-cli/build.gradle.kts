plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":niagara-git-core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.shadowJar {
    archiveFileName.set("niagara-git.jar")
    manifest {
        attributes["Main-Class"] = "com.bew1769.niagaragit.cli.Main"
    }
    mergeServiceFiles()
    // The CLI is one jar. Relocation keeps JGit off the plain org.eclipse package
    // when this jar is launched with other tools. The station module uses
    // Tridium's uberjar configuration instead; a Niagara module classloader is
    // already isolated. See README.
    relocate("org.eclipse.jgit", "com.bew1769.niagaragit.shaded.jgit")
    relocate("com.googlecode.javaewah", "com.bew1769.niagaragit.shaded.ewah")
    relocate("org.slf4j", "com.bew1769.niagaragit.shaded.slf4j")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
