# Niagara API stubs (do not ship)

Source root is `src/` (package `javax.baja`, `com.tridium`). Gradle is told that explicitly. There is no `src/main/java` on purpose, so this tree is not mistaken for a module part.

These classes are compile-only stand-ins for the proprietary Niagara 4 `baja`, `bajaui`, `workbench`, and `platform` jars. They exist so GitHub Actions can compile `BGitService` and `BGitServiceView` on a machine that does not have the Niagara SDK.

They are not the real API. They are not signed, not a module, and must never be copied to a JACE or into `niagara_home/modules`. The runtime module is built in Workbench against Tridium's jars. Where a method signature is a guess, the class comment and the repository README say so.
