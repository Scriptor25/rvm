#include <common.h>
#include <fdt.h>
#include <paging.h>
#include <stdint.h>

void kmain(long boot_hart_id, void* fdt)
{
    char buffer[256];
    int node = -1;

    kprintf("boot_hart_id=%02x, fdt=%016p\r\n", boot_hart_id, fdt);

    kputs("Hello from kernel!\r\n");
    while (1)
    {
        kputs("> ");

        char c;
        char* bp = buffer;
        do
        {
            c = kgetc();
            kputc(c);

            *bp++ = c;
        } while (c != '\0' && c != '\r' && c != '\n');
        *bp = 0;

        kputs("\r\n");

        const char* token;
        int token_length;

        const char* line = kstrnext(buffer, &token, &token_length);
        if (token_length <= 0)
        {
            continue;
        }

        if (kstrcmpn("exit", 4, token, token_length) == 0)
        {
            kputs("Stopping kernel...\r\n");
            break;
        }

        if (kstrcmpn("hello", 5, token, token_length) == 0)
        {
            kputs("Hello world!\r\n");
            continue;
        }

        if (kstrcmpn("panic", 5, token, token_length) == 0)
        {
            *(volatile char*) ~0 = 0;
            continue;
        }

        if (kstrcmpn("paging", 6, token, token_length) == 0)
        {
            sv39_test();
            continue;
        }

        if (kstrcmpn("fdt", 3, token, token_length) == 0)
        {
            line = kstrnext(line, &token, &token_length);

            if (kstrcmpn("node", 4, token, token_length) == 0)
            {
                line = kstrnext(line, &token, &token_length);

                int nextnode = fdt_find_node(fdt, token, token_length);
                if (nextnode < 0)
                {
                    kprintf("failed to select node '%.*s'\r\n", token_length, token);
                    continue;
                }

                node = nextnode;

                const char* nname = FDT_NODE_NAME(fdt, node);

                kprintf("selected node '%s' (offset %#x)\r\n", nname, node);
                continue;
            }

            if (kstrcmpn("prop", 4, token, token_length) == 0)
            {
                if (node < 0)
                {
                    kputs("no node selected\r\n");
                    continue;
                }

                line = kstrnext(line, &token, &token_length);

                int prop = fdt_find_prop(fdt, node, token, token_length);
                if (prop < 0)
                {
                    kprintf("failed to select prop '%.*s'\r\n", token_length, token);
                    continue;
                }

                const char* nname = FDT_NODE_NAME(fdt, node);
                const char* pname = FDT_PROP_NAME(fdt, prop);

                uint32_t plen = FDT_PROP_LEN(fdt, prop);
                if (plen)
                {
                    const char* pvalue = FDT_PROP_VALUE(fdt, prop);

                    kprintf("%s.%s = [", nname, pname);
                    for (uint32_t i = 0; i < plen; ++i)
                    {
                        if (i)
                        {
                            kputs(", ");
                        }
                        kprintf("%02x", pvalue[i]);
                    }
                    kputs("] ('");
                    knputs(pvalue, plen);
                    kputs("')\r\n");
                    continue;
                }

                kprintf("%s.%s\r\n", nname, pname);
                continue;
            }
        }

        kprintf("undefined command '%.*s'\r\n", token_length, token);
    }
}
