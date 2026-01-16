plugins {
    kotlin("jvm")

    application
}

group = "io.scriptor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "io.scriptor.MainKt"
    applicationName = "rvm"
}

val generateResourceDescriptor by tasks.registering {
    val source = layout.buildDirectory.dir("resources/main")
    val index = source.map { it.file("index.list") }

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
                .forEach {
                    val path = directory.toPath().relativize(it.toPath()).toString()
                    if (path != "index.list") {
                        writer.write(path.replace(File.separatorChar, '/'))
                        writer.newLine()
                    }
                }
        }
    }
}

tasks.named("compileJava") {
    dependsOn(generateResourceDescriptor)
}
