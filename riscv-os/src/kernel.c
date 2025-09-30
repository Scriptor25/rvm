#include <common.h>
#include <fdt.h>
#include <stddef.h>
#include <stdint.h>

void kmain(void* fdt)
{
    char buffer[256], c, *bp;
    const char *line, *token;
    int tokenlen;

    int node = -1, prop = -1, nextnode, nextprop;

    kputs("Hello from kernel!\r\n");
    while (1)
    {
        kputs("> ");

        bp = buffer;
        do
        {
            c = kgetc();
            kputc(c);

            (*bp++) = c;
        } while (!(c == '\0' || c == '\r' || c == '\n'));
        *bp = 0;

        line = kstrnext(buffer, &token, &tokenlen);

        if (kstrcmpn("exit", 4, token, tokenlen) == 0)
        {
            kputs("Stopping kernel...\r\n");
            break;
        }
        else if (kstrcmpn("hello", 5, token, tokenlen) == 0)
        {
            kputs("Hello world!\r\n");
        }
        else if (kstrcmpn("panic", 5, token, tokenlen) == 0)
        {
            *((volatile char*) ~0) = 0;
        }
        else if (kstrcmpn("fdt", 3, token, tokenlen) == 0)
        {
            line = kstrnext(line, &token, &tokenlen);

            if (kstrcmpn("node", 4, token, tokenlen) == 0)
            {
                line = kstrnext(line, &token, &tokenlen);

                nextnode = fdt_find_node(fdt, token, tokenlen);
                if (nextnode < 0)
                {
                    kprintf("failed to select node '%.*s'\r\n", tokenlen, token);
                }
                else
                {
                    node = nextnode;

                    kprintf("selected node '%.*s' (offset %#x)\r\n", tokenlen, token, node);
                }
            }
            else if (kstrcmpn("prop", 4, token, tokenlen) == 0)
            {
                if (node < 0)
                {
                    kputs("no node selected\r\n");
                }
                else
                {
                    line = kstrnext(line, &token, &tokenlen);

                    nextprop = fdt_find_prop(fdt, node, token, tokenlen);
                    if (nextprop < 0)
                    {
                        kprintf("failed to select prop '%.*s'\r\n", tokenlen, token);
                    }
                    else
                    {
                        prop = nextprop;

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
                            kputs("]\r\n");
                        }
                        else
                        {
                            kprintf("%s.%s\r\n", nname, pname);
                        }
                    }
                }
            }
            else
            {
                kprintf("undefined command 'fdt %.*s'\r\n", tokenlen, token);
            }
        }
        else
        {
            kprintf("undefined command '%.*s'\r\n", tokenlen, token);
        }
    }

    return;
}
