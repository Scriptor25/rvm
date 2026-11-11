package dev.scriptor.classpath

import java.io.File
import java.util.zip.ZipFile

class JarScanner(private val jar: File) : ClassPathScanner {

    override fun scan(): Sequence<String> {
        return sequence {
            ZipFile(jar).use { zip ->
                val entries = zip.entries()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()

                    yield(entry.name)
                }
            }
        }
    }
}
