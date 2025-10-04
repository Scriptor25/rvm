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

    implementation("com.carrotsearch:hppc:0.10.0")
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

    binaries {
        named("main") {
            resources {
                includedPatterns = listOf(".*")
            }
        }
    }
}

val generateResourceDescriptor by tasks.registering {
    val source = layout.buildDirectory.dir("resources/main")
    val index = source.map { it.file("index.txt") }

    outputs.file(index)
    dependsOn(tasks.named("processResources"))

    doLast {
        val directory = source.get().asFile
        val file = index.get().asFile

        if (!directory.exists()) {
            directory.mkdirs()
        }

        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            directory.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val path = directory.toPath().relativize(file.toPath()).toString()
                    if (path != "index.txt") {
                        writer.write(path.replace(File.separatorChar, '/'))
                        writer.newLine()
                    }
                }
        }
    }
}

tasks.named("jar") {
    dependsOn(generateResourceDescriptor)
}

tasks.named("nativeCompile") {
    dependsOn(generateResourceDescriptor)
}
