plugins {
    id("com.tridium.niagara")
    id("com.tridium.vendor")
    id("com.tridium.niagara-signing")
    id("com.tridium.convention.niagara-home-repositories")
}

vendor {
    defaultVendor("bew1769")
    defaultModuleVersion("0.1.0")
}

subprojects {
    repositories {
        mavenCentral()
    }
}
