.text
.global __trap_handler
__trap_handler:
    la t0, __resume_address
    ld t1, 0(t0)
    csrw mepc, t1
    csrr t0, mstatus
    li t1, 3
    sll t1, t1, 11
    or t0, t0, t1
    csrw mstatus, t0
    mret

.global save_registers
save_registers:
    sd sp,    8(a0)
    sd gp,   16(a0)
    sd tp,   24(a0)
    sd s0,   32(a0)
    sd s1,   40(a0)
    sd s2,   48(a0)
    sd s3,   56(a0)
    sd s4,   64(a0)
    sd s5,   72(a0)
    sd s6,   80(a0)
    sd s7,   88(a0)
    sd s8,   96(a0)
    sd s9,  104(a0)
    sd s10, 112(a0)
    sd s11, 120(a0)
    ret

.global restore_registers
restore_registers:
    ld sp,    8(a0)
    ld gp,   16(a0)
    ld tp,   24(a0)
    ld s0,   32(a0)
    ld s1,   40(a0)
    ld s2,   48(a0)
    ld s3,   56(a0)
    ld s4,   64(a0)
    ld s5,   72(a0)
    ld s6,   80(a0)
    ld s7,   88(a0)
    ld s8,   96(a0)
    ld s9,  104(a0)
    ld s10, 112(a0)
    ld s11, 120(a0)
    ret
