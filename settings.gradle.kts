pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SyncBridge"

include(
    ":app",
    ":core:common",
    ":core:sync",
    ":core:database",
    ":core:security",
    ":protocol:sftp",
    ":protocol:ftp",
)
