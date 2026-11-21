plugins {
    `java-library`
}

dependencies {
    // Last JGit line that runs on Java 8. EDL (Eclipse Distribution License).
    api("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("org.slf4j:slf4j-api:1.7.36")
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
