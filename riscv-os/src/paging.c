#include <common.h>
#include <paging.h>
#include <stddef.h>
#include <stdint.h>

#define TEXT       ((uintptr_t) &__text)
#define BSS        ((uintptr_t) &__bss)
#define RODATA     ((uintptr_t) &__rodata)
#define DATA       ((uintptr_t) &__data)
#define WILDERNESS ((uintptr_t) &__wilderness)

extern int __text;
extern int __bss;
extern int __rodata;
extern int __data;
extern int __wilderness;

volatile void* __resume_address = 0;
void __trap_handler(void);

static uint8_t* next_page;

void sv39_map_page(uint64_t* root, uint64_t vaddr, uint64_t paddr, uint64_t flags)
{
    uint64_t vpn0, vpn1, vpn2, ppn, pte;
    uint64_t *pg0, *pg1, *pg2;

    vpn0 = (vaddr >> (PAGE_SHIFT + 0x00)) & 0x1FF;
    vpn1 = (vaddr >> (PAGE_SHIFT + 0x09)) & 0x1FF;
    vpn2 = (vaddr >> (PAGE_SHIFT + 0x12)) & 0x1FF;

    pg0 = root;

    if (pg0[vpn2] & PTE_V)
    {
        pte = pg0[vpn2];
        ppn = PTEX_PPN(pte);
        pg1 = (uint64_t*) (uintptr_t) PPNX(ppn);
    }
    else
    {
        pg1 = (uint64_t*) next_page;
        next_page += PAGE_SIZE;

        kmemset(pg1, 0, PAGE_SIZE);

        ppn = PPN((uintptr_t) pg1);
        pte = PTE(ppn, PTE_V);
        pg0[vpn2] = pte;
    }

    if (pg1[vpn1] & PTE_V)
    {
        pte = pg1[vpn1];
        ppn = PTEX_PPN(pte);
        pg2 = (uint64_t*) (uintptr_t) PPNX(ppn);
    }
    else
    {
        pg2 = (uint64_t*) next_page;
        next_page += PAGE_SIZE;

        kmemset(pg2, 0, PAGE_SIZE);

        ppn = PPN((uintptr_t) pg2);
        pte = PTE(ppn, PTE_V);
        pg1[vpn1] = pte;
    }

    if (pg2[vpn0] & PTE_V)
    {
        kprintf("re-mapping aready mapped page at vaddr=%x, pte=%x\r\n", vaddr, pg2[vpn0]);
    }

    ppn = PPN(paddr);
    pte = PTE(ppn, flags | PTE_V);
    pg2[vpn0] = pte;
}

__attribute__((noreturn)) static void s_entry(void)
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

uint64_t* sv39_build(void)
{
    next_page = (uint8_t*) ALIGN_HI(WILDERNESS, PAGE_SIZE);

    uint64_t* root = (uint64_t*) next_page;
    next_page += PAGE_SIZE;

    kmemset(root, 0, PAGE_SIZE);

    for (uintptr_t x = TEXT; x < BSS; x += PAGE_SIZE)
    {
        sv39_map_page(root, x, x, PTE_X);
    }
    for (uintptr_t x = BSS; x < RODATA; x += PAGE_SIZE)
    {
        sv39_map_page(root, x, x, PTE_R | PTE_W);
    }
    for (uintptr_t x = RODATA; x < DATA; x += PAGE_SIZE)
    {
        sv39_map_page(root, x, x, PTE_R);
    }
    for (uintptr_t x = DATA; x < WILDERNESS; x += PAGE_SIZE)
    {
        sv39_map_page(root, x, x, PTE_R | PTE_W);
    }

    sv39_map_page(root, TEST_VADDR, TEST_PADDR, PTE_R | PTE_W);

    return root;
}

static uint64_t registers[16];

void save_registers(void* context);
void restore_registers(void* context);

void sv39_test(void)
{
    uint64_t ppn;
    uint64_t satp;
    uint64_t mstatus;
    uint64_t mcause;

    uint64_t* root = sv39_build();

    CSR_WRITE(mtvec, __trap_handler);
    __resume_address = &&resume;

    ppn = PPN((uintptr_t) root);
    satp = (SATP_MODE_SV39 << SATP_MODE_SHIFT) | ppn;

    kprintf("ppn=%llx, satp=%llx\r\n", ppn, satp);

    CSR_WRITE(satp, satp);
    asm volatile("sfence.vma" ::: "memory");

    uint64_t entry = (uintptr_t) s_entry;
    CSR_WRITE(mepc, entry);

    CSR_WRITE(pmpaddr0, 0xFFFFFFFFFFFFF000 >> 2);
    CSR_WRITE(pmpcfg0, 0b00001111);

    CSR_READ(mstatus, mstatus);
    mstatus &= ~MSTATUS_MPP_MASK;
    mstatus |= MSTATUS_MPP_S;
    CSR_WRITE(mstatus, mstatus);

    save_registers(registers);

    asm volatile("mret" ::: "memory");

resume:

    restore_registers(registers);

    CSR_READ(mcause, mcause);

    kprintf("returned from supervisor mode; cause=%x\r\n", mcause);
    return;
}
