subprojects {
    repositories {
        mavenCentral()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Niagara 4.13 on a JACE-8000/9000 runs Java 8. 4.13 did not move the
        // station JVM to 11 (that happens in later releases). --release 8 keeps
        // the bytecode legal for that VM while compiling on a current JDK.
        options.release.set(8)
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
