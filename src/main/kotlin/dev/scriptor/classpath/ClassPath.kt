package dev.scriptor.classpath

import java.io.File

object ClassPath {

    val entries: List<File>
        get() {
            return System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map(::File)
                .filter(File::exists)
        }
}
