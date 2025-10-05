#include <common.h>
#include <paging.h>
#include <stddef.h>
#include <stdint.h>

volatile void* __resume_address = 0;
void __trap_handler(void);

static uint8_t __attribute__((aligned(PAGE_SIZE))) table0[PAGE_SIZE];
static uint8_t __attribute__((aligned(PAGE_SIZE))) table1[PAGE_SIZE];
static uint8_t __attribute__((aligned(PAGE_SIZE))) table2[PAGE_SIZE];

void sv39_build(uint64_t vaddr, uint64_t paddr)
{
    uint64_t vpn0 = (vaddr >> (PAGE_SHIFT + 0x00)) & 0x1FF;
    uint64_t vpn1 = (vaddr >> (PAGE_SHIFT + 0x09)) & 0x1FF;
    uint64_t vpn2 = (vaddr >> (PAGE_SHIFT + 0x12)) & 0x1FF;

    for (size_t i = 0; i < PAGE_SIZE; ++i)
    {
        table0[i] = 0;
        table1[i] = 0;
        table2[i] = 0;
    }

    {
        uint64_t ppn = PPN((intptr_t) table1);
        uint64_t pte = PTE(ppn, PTE_V);
        ((uint64_t*) table0)[vpn2] = pte;
    }

    {
        uint64_t ppn = PPN((intptr_t) table2);
        uint64_t pte = PTE(ppn, PTE_V);
        ((uint64_t*) table1)[vpn1] = pte;
    }

    {
        uint64_t ppn = PPN(paddr);
        uint64_t pte = PTE(ppn, PTE_V | PTE_R | PTE_W | PTE_X);
        ((uint64_t*) table2)[vpn0] = pte;
    }
}

__attribute__((noreturn)) static void sentry(void)
{
    volatile uint64_t* vptr = (volatile uint64_t*) TEST_VADDR;

    uint64_t magic = 0xDEADBEEFCAFEBABEULL;
    *vptr = magic;

    uint64_t test = *vptr;
    (void) test;

    asm volatile("ecall");

    for (;;)
        asm volatile("wfi");
}

void sv39_test(void)
{
    uint64_t ppn;
    uint64_t satp;
    uint64_t mstatus;
    uint64_t mcause;

    sv39_build(TEST_VADDR, TEST_PADDR);

    CSR_WRITE(mtvec, __trap_handler);
    __resume_address = &&resume;

    ppn = PPN((intptr_t) table0);
    satp = (SATP_MODE_SV39 << SATP_MODE_SHIFT) | ppn;

    kprintf("ppn=%x, satp=%x\r\n", ppn, satp);

    CSR_WRITE(satp, satp);
    asm volatile("sfence.vma" ::: "memory");

    CSR_WRITE(mepc, &sentry);

    CSR_READ(mstatus, mstatus);
    mstatus &= ~MSTATUS_MPP_MASK;
    mstatus |= MSTATUS_MPP_S;
    CSR_WRITE(mstatus, mstatus);

    asm volatile("mret" ::: "memory");

resume:

    CSR_READ(mcause, mcause);

    (void) mcause;
    return;
}
