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
    mainClass = "io.scriptor.Main"

    applicationName = "rvm"
    applicationDefaultJvmArgs = listOf("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"))
}

tasks.withType<JavaExec> {
    args("riscv-os/kernel.elf", "elf")
    jvmArgs("--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED")
}
