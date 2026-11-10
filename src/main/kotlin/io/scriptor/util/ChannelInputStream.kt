package io.scriptor.util

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ChannelInputStream : ExtendedInputStream {

    private val stream: FileInputStream
    private val channel: FileChannel

    constructor(name: String) : super() {
        this.stream = FileInputStream(name)
        this.channel = stream.channel
    }

    constructor(file: File) : super() {
        this.stream = FileInputStream(file)
        this.channel = stream.channel
    }

    constructor(fd: FileDescriptor) : super() {
        this.stream = FileInputStream(fd)
        this.channel = stream.channel
    }

    override fun read(): Int {
        return stream.read()
    }

    override fun read(b: ByteArray): Int {
        return stream.read(b)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return stream.read(b, off, len)
    }

    override fun readAllBytes(): ByteArray {
        return stream.readAllBytes()
    }

    override fun readNBytes(len: Int): ByteArray {
        return stream.readNBytes(len)
    }

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
        return stream.readNBytes(b, off, len)
    }

    override fun skip(n: Long): Long {
        return stream.skip(n)
    }

    override fun skipNBytes(n: Long) {
        stream.skipNBytes(n)
    }

    override fun available(): Int {
        return stream.available()
    }

    override fun mark(readlimit: Int) {
        stream.mark(readlimit)
    }

    override fun reset() {
        stream.reset()
    }

    override fun markSupported(): Boolean {
        return stream.markSupported()
    }

    override fun transferTo(out: OutputStream): Long {
        return stream.transferTo(out)
    }

    override fun tell(): Long {
        return channel.position()
    }

    override fun seek(pos: Long) {
        channel.position(pos)
    }

    override fun size(): Long {
        return channel.size()
    }

    override fun read(buffer: ByteBuffer) {
        channel.read(buffer)
    }

    override fun close() {
        stream.close()
    }
}
