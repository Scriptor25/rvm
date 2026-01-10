package io.scriptor.fdt

interface Constant {
    companion object {
        const val FDT_BEGIN_NODE = 0x00000001u
        const val FDT_END_NODE = 0x00000002u
        const val FDT_PROP = 0x00000003u
        const val FDT_NOP = 0x00000004u
        const val FDT_END = 0x00000009u
    }
}
