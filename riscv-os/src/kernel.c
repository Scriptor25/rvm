#include <common.h>
#include <fdt.h>
#include <stdint.h>

void kmain(void* fdt)
{
    char buffer[256];
    auto node = -1;

    kputs("Hello from kernel!\r\n");
    while (1)
    {
        kputs("> ");

        char c;
        auto bp = buffer;
        do
        {
            c = kgetc();
            kputc(c);

            *bp++ = c;
        } while (!(c == '\0' || c == '\r' || c == '\n'));
        *bp = 0;

        const char* token;
        int token_length;

        auto line = kstrnext(buffer, &token, &token_length);
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

        if (kstrcmpn("fdt", 3, token, token_length) == 0)
        {
            line = kstrnext(line, &token, &token_length);

            if (kstrcmpn("node", 4, token, token_length) == 0)
            {
                line = kstrnext(line, &token, &token_length);

                auto const nextnode = fdt_find_node(fdt, token, token_length);
                if (nextnode < 0)
                {
                    kprintf("failed to select node '%.*s'\r\n", token_length, token);
                    continue;
                }

                node = nextnode;

                kprintf("selected node '%.*s' (offset %#x)\r\n", token_length, token, node);
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

                auto const prop = fdt_find_prop(fdt, node, token, token_length);
                if (prop < 0)
                {
                    kprintf("failed to select prop '%.*s'\r\n", token_length, token);
                    continue;
                }

                auto const nname = FDT_NODE_NAME(fdt, node);
                auto const pname = FDT_PROP_NAME(fdt, prop);

                auto const plen = FDT_PROP_LEN(fdt, prop);
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
                    kputs("]\r\n");
                    continue;
                }

                kprintf("%s.%s\r\n", nname, pname);
                continue;
            }
        }

        kprintf("undefined command '%.*s'\r\n", token_length, token);
    }
}
