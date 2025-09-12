plugins {
    id("java")
    id("application")
}

group = "io.scriptor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24

    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

application {
    applicationName = "rvm"
    mainClass = "io.scriptor.Main"
}
