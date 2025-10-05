#include <common.h>

void kputc(char c)
{
    while (!(*UART_STATUS & UART_TX_READY_BIT))
        ;
    *UART_TX = c;
}

unsigned char kgetc()
{
    while (!(*UART_STATUS & UART_RX_READY_BIT))
        ;
    return *UART_RX;
}

void kputs(const char* str)
{
    while (*str)
        kputc(*str++);
}

int kstrcmp(const char* lhs, const char* rhs)
{
    for (; *lhs || *rhs; ++lhs, ++rhs)
    {
        if (*lhs != *rhs)
        {
            return *lhs - *rhs;
        }
    }
    return 0;
}

int kstrcmpn(const char* lhs, int lhs_len, const char* rhs, int rhs_len)
{
    if (lhs_len != rhs_len)
    {
        return lhs_len - rhs_len;
    }

    for (int n = 0; n < lhs_len; ++n)
    {
        if (lhs[n] != rhs[n])
        {
            return lhs[n] - rhs[n];
        }
    }
    return 0;
}

int kstrlen(const char* str)
{
    int length = 0;
    for (; *str; ++str, ++length)
        ;
    return length;
}

const char* kstrnext(const char* buffer, const char** pointer, int* length)
{
    while (*buffer && *buffer <= 0x20)
        buffer++;

    *pointer = buffer;
    while (*buffer && *buffer > 0x20)
        buffer++;
    *length = (int) (buffer - *pointer);

    return buffer;
}
