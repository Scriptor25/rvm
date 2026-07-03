package io.scriptor.fdt

interface Constant {
    companion object {
        const val FDT_BEGIN_NODE = 0x00000001U
        const val FDT_END_NODE = 0x00000002U
        const val FDT_PROP = 0x00000003U
        const val FDT_NOP = 0x00000004U
        const val FDT_END = 0x00000009U
    }
}
