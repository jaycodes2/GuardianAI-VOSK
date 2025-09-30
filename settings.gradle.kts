pluginManagement {
    repositories {
        // Correctly includes all plugins from Google's repository
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Correctly includes all dependencies from Google's repository
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GuardianAI"
include(":app")
include(":core")
include(":text-redaction")
include(":audio-redaction")
include(":image-redaction")
include(":ner")
