#include <common.h>
#include <fdt.h>
#include <stdint.h>

uint32_t htobe32(const void* p)
{
    const uint8_t* x = p;
    return (uint32_t) x[0] << 0x18 | (uint32_t) x[1] << 0x10 | (uint32_t) x[2] << 0x08 | (uint32_t) x[3];
}

void fdt_walk(void* fdt)
{
    uint32_t magic = FDT_HEADER_MAGIC(fdt);
    if (magic != 0xd00dfeed)
    {
        kprintf("fdt invalid magic %#08x != 0xd00dfeed\r\n", magic);
        return;
    }

    for (int offset = FDT_HEADER_OFF_DT_STRUCT(fdt);; offset = ALIGN32(offset))
    {
        uint32_t token = FDT_TOKEN(fdt, offset);
        switch (token)
        {
        case FDT_TOKEN_BEGIN_NODE:
        {
            const char* name = FDT_NODE_NAME(fdt, offset);

            kprintf("fdt begin node '%s' -->\r\n", name);

            offset += sizeof(uint32_t);
            offset += kstrlen(name) + 1;
            break;
        }

        case FDT_TOKEN_END_NODE:
            kputs("<-- fdt end node\r\n");
            offset += sizeof(uint32_t);
            break;

        case FDT_TOKEN_PROP:
        {
            uint32_t plen = FDT_PROP_LEN(fdt, offset);
            const char* pname = FDT_PROP_NAME(fdt, offset);
            const uint8_t* pvalue = FDT_PROP_VALUE(fdt, offset);

            if (plen)
            {
                kprintf("fdt prop:\r\n"
                        " - len = %#08x\r\n"
                        " - name = %s\r\n"
                        " - value = [",
                        plen,
                        pname);

                for (uint32_t i = 0; i < plen; ++i)
                {
                    if (i)
                    {
                        kputs(", ");
                    }
                    kprintf("%02x", pvalue[i]);
                }
                kputs("]\r\n");
            }
            else
            {
                kprintf("fdt prop:\r\n"
                        " - name = %s\r\n",
                        pname);
            }

            offset += sizeof(uint32_t);
            offset += sizeof(uint32_t);
            offset += sizeof(uint32_t);
            offset += (int) plen;
            break;
        }

        case FDT_TOKEN_NOP:
            kputs("fdt nop\r\n");
            offset += sizeof(uint32_t);
            break;

        case FDT_TOKEN_END:
            kputs("fdt end\r\n");
            return;

        default:
            kprintf("fdt unkown token %#08x\r\n", token);
            offset += sizeof(uint32_t);
            break;
        }
    }
}

static int fdt_find_subnode(void* fdt, int root, const char* name, int namelen)
{
    for (int offset = root;; offset = ALIGN32(offset))
    {
        uint32_t token = FDT_TOKEN(fdt, offset);
        switch (token)
        {
        case FDT_TOKEN_BEGIN_NODE:
        {
            const char* nname = FDT_NODE_NAME(fdt, offset);
            int nnamelen = kstrlen(nname);

            if (kstrcmpn(nname, nnamelen, name, namelen) == 0)
            {
                return offset;
            }

            offset += sizeof(uint32_t);
            offset += nnamelen + 1;
            break;
        }

        case FDT_TOKEN_PROP:
        {
            uint32_t plen = FDT_PROP_LEN(fdt, offset);

            offset += sizeof(uint32_t);
            offset += sizeof(uint32_t);
            offset += sizeof(uint32_t);
            offset += (int) plen;
            break;
        }

        case FDT_TOKEN_END_NODE:
        case FDT_TOKEN_NOP:
            offset += sizeof(uint32_t);
            break;

        default:
            return -1;
        }
    }
}

int fdt_find_node(void* fdt, const char* path, int pathlen)
{
    if (*path != '/')
    {
        return -1;
    }

    int offset = FDT_HEADER_OFF_DT_STRUCT(fdt);
    const char* p = path;

    while (offset >= 0 && p - path < pathlen && *p++ == '/')
    {
        const char* end = p;
        for (; end - path < pathlen && *end != '/'; ++end)
            ;

        int namelen = end - p;
        offset = fdt_find_subnode(fdt, offset, p, namelen);

        p = end;
    }

    return offset;
}

int fdt_find_prop(void* fdt, int node, const char* name, int namelen)
{
    for (int offset = node;; offset = ALIGN32(offset))
    {
        uint32_t token = FDT_TOKEN(fdt, offset);
        switch (token)
        {
        case FDT_TOKEN_BEGIN_NODE:
        {
            const char* nname = FDT_NODE_NAME(fdt, offset);

            offset += sizeof(uint32_t);
            offset += kstrlen(nname) + 1;
            break;
        }

        case FDT_TOKEN_PROP:
        {
            const char* pname = FDT_PROP_NAME(fdt, offset);
            int pnamelen = kstrlen(pname);

            if (kstrcmpn(pname, pnamelen, name, namelen) == 0)
            {
                return offset;
            }

            uint32_t plen = FDT_PROP_LEN(fdt, offset);

            offset += sizeof(uint32_t);
            offset += sizeof(uint32_t);
            offset += sizeof(uint32_t);
            offset += (int) plen;
            break;
        }

        case FDT_TOKEN_END_NODE:
        case FDT_TOKEN_NOP:
            offset += sizeof(uint32_t);
            break;

        default:
            return -1;
        }
    }
}
