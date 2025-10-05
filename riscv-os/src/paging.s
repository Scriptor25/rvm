.text
.global __trap_handler
__trap_handler:
    la t0, __resume_address
    ld t1, 0(t0)
    csrw mepc, t1
    mret
