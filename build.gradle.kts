plugins {
    base
}

tasks.register("test") {
    dependsOn(":niagaragit-core:test")
    group = "verification"
    description = "Run the niagara-git core tests (no Niagara install needed)."
}
