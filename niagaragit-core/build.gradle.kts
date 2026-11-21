plugins {
    `java-library`
}

group = "com.bew1769.niagaragit"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
    // JDK 8 javac has no --release flag; source/targetCompatibility cover it there.
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    }
}

// JGit 5.13 is the last line that runs on Java 8, which is what 4.13+ stations use.
val jgitVersion = "5.13.3.202401111512-r"

dependencies {
    api("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testRuntimeOnly("org.slf4j:slf4j-nop:1.7.36")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
