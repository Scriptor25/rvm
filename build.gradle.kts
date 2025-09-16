plugins {
    id("java")
    id("application")

    id("org.graalvm.buildtools.native") version "0.11.0"
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
    mainClass = "io.scriptor.Main"

    applicationName = "rvm"
}

graalvmNative {
    toolchainDetection = true
}

tasks.withType<JavaExec> {
    args("riscv-os/kernel.elf", "elf")
}
