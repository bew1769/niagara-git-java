import com.tridium.gradle.plugins.module.util.ModulePart.RuntimeProfile.*

plugins {
    id("com.tridium.niagara-module")
    id("com.tridium.convention.niagara-home-repositories")
    id("com.tridium.niagara-annotation-processors")
    // Signing: enable once a code-signing cert is installed in the Niagara
    // user home (alias often "Niagara4Modules" or your company alias).
    // scripts/sign-module.sh and sign-module.ps1 sign the jar with jarsigner
    // if this plugin is not applied.
    // id("com.tridium.niagara-signing")
}

description = "Niagara Git runtime. Versions config.bog and shared/ on the station."

moduleManifest {
    moduleName.set("niagaraGit")
    runtimeProfile.set(rt)
    vendor.set("bew1769")
}

dependencies {
    nre(":nre")
    api(":baja")
    // BSystemPlatformService.restartStation. Confirm the module-part name
    // (platform-rt) against the Niagara install if this fails to resolve.
    api(":platform-rt")

    // JGit 5.13 is the last release that runs on the Java 8 JACE VM.
    // uberjar embeds it in this module jar. The module classloader isolates it.
    // Do not upgrade to JGit 6 (Java 11) for a 4.13 JACE.
    uberjar("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    uberjar("com.googlecode.javaewah:JavaEWAH:1.1.13")
    uberjar("org.slf4j:slf4j-api:1.7.36")
    uberjar("org.slf4j:slf4j-nop:1.7.36")
}

// The engine sources live in niagara-git-core so the CLI and the tests can
// compile them without Niagara. Slot-o-matic still sees BGitService under src/.
sourceSets {
    main {
        java {
            srcDir("src")
            srcDir("../niagara-git-core/src/main/java")
        }
        resources {
            srcDir("../niagara-git-core/src/main/resources")
        }
    }
}
