apply(from = "gradle/versions.gradle.kts")

plugins {
    java
    checkstyle
    id("com.diffplug.spotless") version "7.0.2"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "com.diffplug.spotless")

    checkstyle {
        toolVersion = "10.17.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    }

    spotless {
        java {
            googleJavaFormat()
            removeUnusedImports()
        }
    }

    tasks.withType<Checkstyle>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}