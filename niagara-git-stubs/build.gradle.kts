plugins {
    `java-library`
}

// Sources sit in src/ (not src/main/java) so this tree is obviously not a
// Niagara module part. The package roots are javax.baja and com.tridium.
sourceSets.main.get().java.setSrcDirs(listOf("src"))

// Compile-only stand-ins. The jar name says so. CI never publishes it,
// and the CLI shadow jar must not contain these classes.
tasks.jar {
    archiveBaseName.set("niagara-stubs-NOT-FOR-JACE")
}
