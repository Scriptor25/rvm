package dev.scriptor.classpath

import java.io.File

class DirectoryScanner(private val root: File) : ClassPathScanner {

    override fun scan(): Sequence<String> {
        if (!root.exists()) {
            return emptySequence()
        }

        return root
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path }
    }
}
