ENTRY(start);

. = 0x80000000;

SECTIONS {
	/* Include entry point at start of binary */
	.text : ALIGN(4K) {
		PROVIDE(__text = .);
		*(.init);
		*(.text);
	}
	.bss : ALIGN(4K) {
		PROVIDE(__bss = .);
		*(.bss);
		. += 4096;
		PROVIDE(__stack = .);
		. += 4096;
		PROVIDE(__global = .);
	}
	.rodata : ALIGN(4K) {
		PROVIDE(__rodata = .);
		*(.rodata);
	}
	.data : ALIGN(4K) {
		PROVIDE(__data = .);
		*(.data);
	}
	PROVIDE(__wilderness = .);
}
