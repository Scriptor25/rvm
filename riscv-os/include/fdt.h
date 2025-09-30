#pragma once

#include <stdint.h>

#define FDT_HEADER_magic             0x00
#define FDT_HEADER_totalsize         0x04
#define FDT_HEADER_off_dt_struct     0x08
#define FDT_HEADER_off_dt_strings    0x0C
#define FDT_HEADER_off_mem_rsvmap    0x10
#define FDT_HEADER_version           0x14
#define FDT_HEADER_last_comp_version 0x18
#define FDT_HEADER_boot_cpuid_phys   0x1C
#define FDT_HEADER_size_dt_strings   0x20
#define FDT_HEADER_size_dt_struct    0x24

#define FDT_HEADER(FDT, FLD) htobe32((char*) (FDT) + FDT_HEADER_##FLD)

#define FDT_HEADER_MAGIC(FDT)             FDT_HEADER((FDT), magic)
#define FDT_HEADER_TOTALSIZE(FDT)         FDT_HEADER((FDT), totalsize)
#define FDT_HEADER_OFF_DT_STRUCT(FDT)     FDT_HEADER((FDT), off_dt_struct)
#define FDT_HEADER_OFF_DT_STRINGS(FDT)    FDT_HEADER((FDT), off_dt_strings)
#define FDT_HEADER_OFF_MEM_RSVMAP(FDT)    FDT_HEADER((FDT), off_mem_rsvmap)
#define FDT_HEADER_VERSION(FDT)           FDT_HEADER((FDT), version)
#define FDT_HEADER_LAST_COMP_VERSION(FDT) FDT_HEADER((FDT), last_comp_version)
#define FDT_HEADER_BOOT_CPUID_PHYS(FDT)   FDT_HEADER((FDT), boot_cpuid_phys)
#define FDT_HEADER_SIZE_DT_STRINGS(FDT)   FDT_HEADER((FDT), size_dt_strings)
#define FDT_HEADER_SIZE_DT_STRUCT(FDT)    FDT_HEADER((FDT), size_dt_struct)

#define FDT_NODE_NAME(FDT, OFF) ((const char*) ((char*) (FDT) + (OFF) + 0x04))

#define FDT_PROP_LEN(FDT, OFF) htobe32((char*) (FDT) + (OFF) + 0x04)
#define FDT_PROP_NAME(FDT, OFF)                                                                              \
    ((const char*) ((char*) (FDT) + FDT_HEADER_OFF_DT_STRINGS(FDT) + htobe32((char*) (FDT) + (OFF) + 0x08)))
#define FDT_PROP_VALUE(FDT, OFF) ((void*) ((char*) (FDT) + (OFF) + 0x0C))

#define FDT_TOKEN(FDT, OFF) htobe32((char*) (FDT) + (OFF))

#define FDT_TOKEN_BEGIN_NODE ((uint32_t) 0x00000001)
#define FDT_TOKEN_END_NODE   ((uint32_t) 0x00000002)
#define FDT_TOKEN_PROP       ((uint32_t) 0x00000003)
#define FDT_TOKEN_NOP        ((uint32_t) 0x00000004)
#define FDT_TOKEN_END        ((uint32_t) 0x00000009)

static inline uint32_t htobe32(void* p)
{
    uint8_t* x = p;
    return (uint32_t) x[0] << 0x18 | (uint32_t) x[1] << 0x10 | (uint32_t) x[2] << 0x08 | (uint32_t) x[3];
}

void fdt_walk(void* fdt);

int fdt_find_node(void* fdt, const char* path, int pathlen);
int fdt_find_prop(void* fdt, int node, const char* name, int namelen);
