#include <common.h>
#include <stdint.h>
#include <uart.h>

void kputc(char c)
{
    while (!(*UART_LSR & UART_LSR_THRE))
    {
    }
    *UART_THR = c;
}

unsigned char kgetc()
{
    while (!(*UART_LSR & UART_LSR_DR))
    {
    }
    return *UART_RBR;
}

void kputs(const char* str)
{
    while (*str)
    {
        kputc(*str++);
    }
}

void knputs(const char* str, int len)
{
    for (; len > 0 && *str; --len, ++str)
    {
        kputc(*str);
    }
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
    {
    }
    return length;
}

const char* kstrnext(const char* buffer, const char** pointer, int* length)
{
    while (*buffer && *buffer <= 0x20)
    {
        buffer++;
    }

    *pointer = buffer;
    while (*buffer && *buffer > 0x20)
    {
        buffer++;
    }
    *length = (int) (buffer - *pointer);

    return buffer;
}

void* kmemset(void* buffer, int value, size_t count)
{
    if (!buffer || !count)
    {
        return buffer;
    }

    size_t chunks = count / sizeof(uint64_t);
    size_t rem = count % sizeof(uint64_t);

    uint64_t chunk = 0;
    if (value && chunks)
    {
        for (size_t i = 0; i < sizeof(uint64_t); ++i)
        {
            chunk |= (value & 0xFFULL) << (i << 3);
        }
    }

    for (size_t i = 0; i < chunks; ++i)
    {
        ((uint64_t*) buffer)[i] = chunk;
    }
    for (size_t i = 0; i < rem; ++i)
    {
        ((uint8_t*) buffer)[i] = (uint8_t) (value & 0xFF);
    }

    return buffer;
}
