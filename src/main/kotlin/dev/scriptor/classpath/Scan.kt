package dev.scriptor.classpath

fun scan(loader: ClassLoader = Thread.currentThread().contextClassLoader): List<String> {
    return ClassPathScannerFactory
        .scanners
        .flatMap(ClassPathScanner::scan)
}
