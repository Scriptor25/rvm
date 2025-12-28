.section .init

.option norvc

/* do not touch a0 and a1, contain boot hart id and pointer to fdt! */

.type start, @function
.global start
start:
	.cfi_startproc

.option push
.option norelax
	la gp, __global
.option pop

    bnez a0, trap

	/* Reset satp */
	csrw satp, zero

	/* Setup stack */
	la sp, __stack

	/* Clear the BSS section */
	la t5, __bss
	la t6, __rodata
bss_clear:
	sd zero, (t5)
	addi t5, t5, 8
	bltu t5, t6, bss_clear

	la t0, trap
	csrw mtvec, t0

	la t0, kmain
	csrw mepc, t0

	/* Jump to kernel! */
	call kmain

    /* infinite loop */
trap:
    wfi
    j trap

	.cfi_endproc

.end
