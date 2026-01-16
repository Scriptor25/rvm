import io.scriptor.isa.Instruction
import io.scriptor.isa.Operand
import io.scriptor.isa.Segment
import io.scriptor.util.Log.format
import kotlin.test.Test

internal class TestRegistry {

    @Test
    fun testInstructionEncodeDecode() {

        val operands: MutableMap<String, Operand> = HashMap()
        run {
            val segments: MutableList<Segment> = ArrayList()
            segments.add(Segment(11, 7, 0))
            operands["rd"] = Operand("rd", segments, HashSet())
        }
        run {
            // [31<<20|30:21<<1|20<<11|19:12<<12]
            val segments: MutableList<Segment> = ArrayList()
            segments.add(Segment(19, 12, 12))
            segments.add(Segment(20, 20, 11))
            segments.add(Segment(30, 21, 1))
            segments.add(Segment(31, 31, 20))
            operands["imm"] = Operand("imm", segments, HashSet())
        }
        val instruction = Instruction("jal", 4U, 0b1111111, 0b1101111, 0U, operands)

        val value = 0xBADF0
        val encoded = instruction.encode(0, "imm", value)
        val decoded = instruction.decode(encoded, "imm")
        require(decoded == value) {
            format(
                "requirement failed, encoded=%08x, decoded=%08x, value=%08x",
                encoded,
                decoded,
                value
            )
        }
    }
}
