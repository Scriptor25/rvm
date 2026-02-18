import io.scriptor.impl.HartImpl
import io.scriptor.impl.MachineImpl
import io.scriptor.isa.Registry
import io.scriptor.util.Log.format
import io.scriptor.util.Resource
import java.nio.ByteOrder
import kotlin.test.BeforeTest
import kotlin.test.Test

fun <T : Comparable<T>> requireEqual(a: T, b: T) {
    if (a == b) return
    throw IllegalArgumentException(format("requirement failed: %016x != %016x", a, b))
}

internal class TestRegistry {

    val registry = Registry()
    val machine: MachineImpl
    val hart: HartImpl

    init {
        Resource.read(true, "index.list") { stream ->
            stream
                .bufferedReader()
                .lines()
                .map { line -> line.trim { it <= ' ' } }
                .filter { line -> !line.isEmpty() }
                .filter { line -> line.endsWith(".isa") }
                .forEach { line -> Resource.read(true, line, registry::parse) }
        }

        machine = MachineImpl(registry, ByteOrder.nativeOrder(), 1, arrayOf())
        hart = machine.harts[0] as HartImpl
    }

    @Test
    fun testInstructionEncodeDecode() {

        val instruction = registry["jal"]!!

        val value = 0xBADF0
        val encoded = instruction.encode(0, "imm", value)
        val decoded = instruction.decode(encoded, "imm")

        requireEqual(decoded, value)
    }

    @BeforeTest
    fun reset() {
        machine.reset()
    }

    @Test
    fun test_lui() {
        hart.lui(5U, 0xBAADF000U)
        requireEqual(hart.gprFile.getdu(5U), 0xFFFFFFFFBAADF000UL)
    }

    @Test
    fun test_auipc() {
        hart.pc = 0x10000000UL
        hart.auipc(5U, 0xBAADF000U)
        requireEqual(hart.gprFile.getdu(5U), 0xFFFFFFFFCAADF000UL)
    }

    @Test
    fun test_jal() {
        hart.pc = 0x10000000UL
        val next = hart.jal(0x10000004UL, 5U, 0xDEAD0U)
        requireEqual(next, 0x100DEAD0UL)
        requireEqual(hart.gprFile.getdu(5U), 0x10000004UL)
    }

    @Test
    fun test_jalr() {
        hart.gprFile.put(6U, 0x10000000UL)
        val next = hart.jalr(0x10000004UL, 5U, 6U, 0xAAU)
        requireEqual(next, 0x100000AAUL)
        requireEqual(hart.gprFile.getdu(5U), 0x10000004UL)
    }

    @Test
    fun test_beq() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.beq(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_beq_fail() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x4321UL)
        val next = hart.beq(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_bne() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x4321UL)
        val next = hart.bne(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_bne_fail() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bne(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_blt() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x4321UL)
        val next = hart.blt(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_blt_fail() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.blt(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_blt_fail2() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x4321UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.blt(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_bge() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x4321UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bge(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_bge2() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bge(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_bge_fail() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x4321UL)
        val next = hart.bge(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_bltu() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x4321UL)
        val next = hart.bltu(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_bltu_fail() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x4321UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bltu(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_bltu_fail2() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bltu(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_bgeu() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x4321UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bgeu(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_bgeu2() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x1234UL)
        val next = hart.bgeu(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000008UL)
    }

    @Test
    fun test_bgeu_fail() {
        hart.pc = 0x10000000UL
        hart.gprFile.put(5U, 0x1234UL)
        hart.gprFile.put(6U, 0x4321UL)
        val next = hart.bgeu(0x10000004UL, 5U, 6U, 0x8U)
        requireEqual(next, 0x10000004UL)
    }

    @Test
    fun test_lb() {
        hart.lb(5U, 6U, 0U)
    }

    @Test
    fun test_lh() {
        hart.lh(5U, 6U, 0U)
    }

    @Test
    fun test_lw() {
        hart.lw(5U, 6U, 0U)
    }

    @Test
    fun test_lbu() {
        hart.lbu(5U, 6U, 0U)
    }

    @Test
    fun test_lhu() {
        hart.lhu(5U, 6U, 0U)
    }

    @Test
    fun test_sb() {
        hart.sb(5U, 6U, 0U)
    }

    @Test
    fun test_sh() {
        hart.sh(5U, 6U, 0U)
    }

    @Test
    fun test_sw() {
        hart.sw(5U, 6U, 0U)
    }

    @Test
    fun test_addi() {
        hart.addi(5U, 6U, 0U)
    }

    @Test
    fun test_slti() {
        hart.slti(5U, 6U, 0U)
    }

    @Test
    fun test_sltiu() {
        hart.sltiu(5U, 6U, 0U)
    }

    @Test
    fun test_xori() {
        hart.xori(5U, 6U, 0U)
    }

    @Test
    fun test_ori() {
        hart.ori(5U, 6U, 0U)
    }

    @Test
    fun test_andi() {
        hart.andi(5U, 6U, 0U)
    }

    @Test
    fun test_slli() {
        hart.slli(5U, 6U, 0U)
    }

    @Test
    fun test_srli() {
        hart.srli(5U, 6U, 0U)
    }

    @Test
    fun test_srai() {
        hart.srai(5U, 6U, 0U)
    }

    @Test
    fun test_add() {
        hart.add(5U, 6U, 7U)
    }

    @Test
    fun test_sub() {
        hart.sub(5U, 6U, 7U)
    }

    @Test
    fun test_sll() {
        hart.sll(5U, 6U, 7U)
    }

    @Test
    fun test_slt() {
        hart.slt(5U, 6U, 7U)
    }

    @Test
    fun test_sltu() {
        hart.sltu(5U, 6U, 7U)
    }

    @Test
    fun test_xor() {
        hart.xor(5U, 6U, 7U)
    }

    @Test
    fun test_srl() {
        hart.srl(5U, 6U, 7U)
    }

    @Test
    fun test_sra() {
        hart.sra(5U, 6U, 7U)
    }

    @Test
    fun test_or() {
        hart.or(5U, 6U, 7U)
    }

    @Test
    fun test_and() {
        hart.and(5U, 6U, 7U)
    }

    @Test
    fun test_fence() {
        hart.fence(5U, 6U, 0U, 0U, 0U)
    }

    @Test
    fun test_ecall() {
        hart.ecall()
    }

    @Test
    fun test_ebreak() {
        hart.ebreak(0U)
    }
}
