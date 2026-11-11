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

        // Jitsi Maven repository
        maven {
            url = uri(
                "https://github.com/jitsi/jitsi-maven-repository/raw/master/releases"
            )
        }

        // JitPack - required for uCrop
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "Arshith Connect Attendance"

include(":app")