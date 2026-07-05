# SSHJ / Bouncy Castle reflectively load provider classes.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**
-dontwarn org.slf4j.**

# Room entities/DAOs are referenced by generated code via reflection-free codegen, but keep names
# in case of schema export tooling run against the release artifact.
-keep class com.syncbridge.core.database.entity.** { *; }
