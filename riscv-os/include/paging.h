#pragma once

#include <stdint.h>

#define PAGE_SHIFT 12
#define PAGE_SIZE  (1ULL << PAGE_SHIFT)
#define PTE_PER_PT 512ULL
#define PTE_SIZE   8ULL
#define PAGE_MASK  (~(PAGE_SIZE - 1ULL))

#define SATP_MODE_SV39  8ULL
#define SATP_MODE_SHIFT 60

#define MSTATUS_MPP_MASK (3ULL << 11)
#define MSTATUS_MPP_S    (1ULL << 11)

#define MSTATUS_MPRV_MASK (1ULL << 17)

#define TEST_VADDR 0xFFFFFFFF81000000ULL
#define TEST_PADDR 0x0000000081000000ULL

#define PTE_V (1ULL << 0)
#define PTE_R (1ULL << 1)
#define PTE_W (1ULL << 2)
#define PTE_X (1ULL << 3)
#define PTE_U (1ULL << 4)
#define PTE_G (1ULL << 5)
#define PTE_A (1ULL << 6)
#define PTE_D (1ULL << 7)

#define CSR_READ(X, V)  asm volatile("csrr %0, " #X : "=r"(V))
#define CSR_WRITE(X, V) asm volatile("csrw " #X ", %0" ::"r"(V))

#define PTE(PPN, FLAGS) ((PPN) << 10 | (FLAGS))
#define PPN(PADDR)      ((PADDR) >> PAGE_SHIFT)

#define PTEX_PPN(PTE) (((PTE) >> 10) & 0xFFFFFFFFFFF)
#define PPNX(PPN)     ((PPN) << PAGE_SHIFT)

#define ALIGN_LO(X, A) ((X) & ~((A) - 1))
#define ALIGN_HI(X, A) (((X) + ((A) - 1)) & ~((A) - 1))

void sv39_map_page(uint64_t* root, uint64_t vaddr, uint64_t paddr, uint64_t flags);
uint64_t* sv39_build(void);
void sv39_test(void);
