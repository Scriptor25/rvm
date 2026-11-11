package dev.scriptor.classpath

interface ClassPathScanner {

    fun scan(): Sequence<String>
}
