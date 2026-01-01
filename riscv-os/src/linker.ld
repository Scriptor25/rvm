ENTRY(_start);

SECTIONS {
	/* Include entry point at start of binary */
	.text : ALIGN(4K) {
		__text = .;
		*(.init);
		*(.text);
	}
	.bss : ALIGN(4K) {
		__bss = .;
		*(.bss);
		. += 4096;
		__stack = .;
		. += 4096;
		__global = .;
	}
	.rodata : ALIGN(4K) {
		__rodata = .;
		*(.rodata);
	}
	.data : ALIGN(4K) {
		__data = .;
		*(.data);
	}
	__wilderness = .;
}
