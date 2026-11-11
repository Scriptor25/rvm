package dev.scriptor.classpath

object ClassPathScannerFactory {

    val scanners: List<ClassPathScanner>
        get() = ClassPath.entries.mapNotNull {
            when {
                it.isDirectory -> DirectoryScanner(it)
                it.isFile && it.extension == "jar" -> JarScanner(it)
                else -> null
            }
        }
}
