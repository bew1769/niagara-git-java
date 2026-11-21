import com.tridium.gradle.plugins.module.util.ModulePart.RuntimeProfile.rt

plugins {
    id("com.tridium.niagara-module")
    id("com.tridium.niagara-signing")
    id("com.tridium.niagara-annotation-processors")
    id("com.tridium.convention.niagara-home-repositories")
}

description = "niagara-git station service: snapshot, commit, push, and pull a station with JGit"

moduleManifest {
    moduleName.set("niagaraGit")
    runtimeProfile.set(rt)
}

// JGit and its two runtime dependencies are folded into the module jar, the
// same way the core sources are. No other module has to carry them.
val bundled: Configuration by configurations.creating {
    isTransitive = true
}

dependencies {
    nre(":nre")
    api(":baja")
    bundled("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    compileOnly("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
}

tasks.named<JavaCompile>("compileJava") {
    // The core has no Niagara dependencies. Compile it into this jar.
    source(project.fileTree("../niagaragit-core/src/main/java") {
        include("**/*.java")
    })
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("module.palette")
    from("module-include.xml")
    from("src/niagaraGit.lexicon")
    from("../niagaragit-core/src/main/resources")
    from({
        bundled.files.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/*.EC")
        exclude("META-INF/MANIFEST.MF", "META-INF/maven/**", "module-info.class")
    }
}
