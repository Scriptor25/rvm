#include <stdint.h>
#include <stddef.h>

#define UART_RX  ((volatile unsigned char*) 0x10000000)
#define UART_LSR ((volatile unsigned char*) 0x10000005)

#define UART_DR_BIT 0x01

void putc(char c)
{
    *UART_RX = c;
    return;
}

unsigned char getc()
{
    while (!(*UART_LSR & UART_DR_BIT));
    return *UART_RX;
}

void puts(const char* str)
{
	while (*str)
	    putc(*str++);
	return;
}

int strcmp(const char* a, const char* b)
{
    int i = 0;
    for (; a[i] && b[i] && a[i] == b[i]; ++i);
    if (a[i] != b[i])
        return a[i] - b[i];
    return 0;
}

int strlen(const char* str)
{
    int length = 0;
    for (; *str; ++str, ++length);
    return length;
}

char* trim(char* buffer, int length)
{
    int begin = 0, end = length;

    for (; begin < length; ++begin)
        if (!buffer[begin] || buffer[begin] > 0x20)
            break;

    for (; end > 0; --end)
        if (buffer[end - 1] > 0x20)
            break;

    buffer[end] = 0;
    return &buffer[begin];
}

void kmain(void)
{
    char buffer[256], c, *pointer;

	puts("Hello from kernel!\r\n");
	while (1)
	{
        puts("> ");

        pointer = buffer;
        *pointer = 0;

        c = getc();
        while (!(c == '\0' || c == '\r' || c == '\n'))
        {
            if (c >= 0x20)
            {
                (*pointer++) = c;
            }
            putc(c);
            c = getc();
        }
        *pointer = 0;

        puts("\r\n");

        char* command = trim(buffer, 256);

        if (strcmp(command, "exit") == 0)
        {
            puts("Stopping kernel...\r\n");
            break;
        }
        else if (strcmp(command, "hello") == 0)
        {
            puts("Hello world!\r\n");
        }
        else if (strlen(command))
        {
            puts("undefined command '");
            puts(command);
            puts("'\r\n");
        }
    }

	return;
}
