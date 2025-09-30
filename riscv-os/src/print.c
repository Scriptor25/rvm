#include <common.h>
#include <stdarg.h>
#include <stdint.h>

void kprintf(const char* format, ...)
{
    va_list ap;
    va_start(ap, format);
    vkprintf(format, ap);
    va_end(ap);
}

#define STATE_NONE      0x00
#define STATE_FLAGS     0x01
#define STATE_WIDTH     0x02
#define STATE_PRECISION 0x03
#define STATE_LENGTH    0x04
#define STATE_SPECIFIER 0x05

#define FLAG_NONE         0x00
#define FLAG_LEFT_JUSTIFY 0x01
#define FLAG_FORCE_SIGN   0x02
#define FLAG_BLANK_SPACE  0x04
#define FLAG_USE_PREFIX   0x08
#define FLAG_PAD_ZERO     0x10

#define LENGTH_NONE      0x00
#define LENGTH_BYTE      0x01
#define LENGTH_HALF      0x02
#define LENGTH_LONG      0x03
#define LENGTH_LONG_LONG 0x04

static void kprints(const char* str, int flags, int width, int precision)
{
    int left_justify = flags & FLAG_LEFT_JUSTIFY;

    int n = 0;

    int len;
    if (precision)
    {
        len = precision;
    }
    else
    {
        len = kstrlen(str);
    }

    if (!left_justify)
    {
        int rem = width - len;
        for (; n < rem; ++n)
        {
            kputc(' ');
        }
    }

    for (const char* p = str; *p && (!precision || (p - str) < precision); ++p, ++n)
    {
        kputc(*p);
    }

    for (; n < width; ++n)
    {
        kputc(' ');
    }
}

static void kprinti(uint64_t value, int signed_, uint64_t base, int uppercase, int flags, int width, int precision)
{
    int left_justify = flags & FLAG_LEFT_JUSTIFY;
    int force_sign = flags & FLAG_FORCE_SIGN;
    int blank_space = flags & FLAG_BLANK_SPACE;
    int use_prefix = flags & FLAG_USE_PREFIX;
    int pad_zero = flags & FLAG_PAD_ZERO;

    char buffer[0x100];
    int bp = 0x100;

    int sign = 0;
    if (signed_)
    {
        int64_t svalue = value;
        sign = svalue < 0;
    }

    if (sign)
    {
        value = -value;
    }

    while (((0x100 - bp) < precision) || value)
    {
        uint64_t rem = value % base;
        value /= base;
        buffer[--bp] = (rem < 10 ? ('0' + rem) : (uppercase ? ('A' + rem - 10) : ('a' + rem - 10)));
    }

    if (pad_zero)
    {
        while ((0x100 - bp) < width)
        {
            buffer[--bp] = '0';
        }
    }

    if (use_prefix)
    {
        switch (base)
        {
        case 010:
            buffer[--bp] = '0';
            break;
        case 0x10:
            buffer[--bp] = (uppercase ? 'X' : 'x');
            buffer[--bp] = '0';
            break;
        }
    }

    if (sign)
    {
        buffer[--bp] = '-';
    }
    else if (force_sign)
    {
        buffer[--bp] = '+';
    }
    else if (blank_space)
    {
        buffer[--bp] = ' ';
    }

    int n = 0;
    if (!left_justify)
    {
        int rem = width - (0x100 - bp);
        for (; n < rem; ++n)
        {
            kputc(' ');
        }
    }

    for (; n < width || bp < 0x100; ++n)
    {
        kputc(buffer[bp++]);
    }

    for (; n < width; ++n)
    {
        kputc(' ');
    }
}

void vkprintf(const char* format, va_list ap)
{
    const char* fp = format;

    int state = STATE_NONE;

    int flags, width, precision, length;

    while (*fp)
    {
        switch (state)
        {
        case STATE_NONE:
            if (*fp == '%')
            {
                fp++;
                state = STATE_FLAGS;
                flags = FLAG_NONE;
                break;
            }
            kputc(*fp++);
            break;

        case STATE_FLAGS:
            switch (*fp)
            {
            case '-':
                fp++;
                flags |= FLAG_LEFT_JUSTIFY;
                break;
            case '+':
                fp++;
                flags |= FLAG_FORCE_SIGN;
                break;
            case ' ':
                fp++;
                flags |= FLAG_BLANK_SPACE;
                break;
            case '#':
                fp++;
                flags |= FLAG_USE_PREFIX;
                break;
            case '0':
                fp++;
                flags |= FLAG_PAD_ZERO;
                break;
            default:
                state = STATE_WIDTH;
                width = 0;
                break;
            }
            break;

        case STATE_WIDTH:
            if ('0' <= *fp && *fp <= '9')
            {
                width = width * 10 + *fp++ - '0';
                break;
            }

            if (*fp == '*')
            {
                fp++;
                width = va_arg(ap, int);
            }

            precision = 0;

            if (*fp == '.')
            {
                fp++;
                state = STATE_PRECISION;
                break;
            }

            state = STATE_LENGTH;
            length = LENGTH_NONE;
            break;

        case STATE_PRECISION:
            if ('0' <= *fp && *fp <= '9')
            {
                precision = precision * 10 + *fp++ - '0';
                break;
            }

            if (*fp == '*')
            {
                fp++;
                precision = va_arg(ap, int);
            }

            state = STATE_LENGTH;
            length = LENGTH_NONE;
            break;

        case STATE_LENGTH:
            switch (length)
            {
            case LENGTH_NONE:
                switch (*fp)
                {
                case 'h':
                    fp++;
                    length = LENGTH_HALF;
                    break;
                case 'l':
                    fp++;
                    length = LENGTH_LONG;
                    break;
                default:
                    state = STATE_SPECIFIER;
                    break;
                }
                break;
            case LENGTH_HALF:
                switch (*fp)
                {
                case 'h':
                    fp++;
                    length = LENGTH_BYTE;
                    break;
                default:
                    state = STATE_SPECIFIER;
                    break;
                }
                break;
            case LENGTH_LONG:
                switch (*fp)
                {
                case 'l':
                    fp++;
                    length = LENGTH_LONG_LONG;
                    break;
                default:
                    state = STATE_SPECIFIER;
                    break;
                }
                break;
            default:
                state = STATE_SPECIFIER;
                break;
            }
            break;

        case STATE_SPECIFIER:
            switch (*fp)
            {
            case 'd':
            case 'i':
            {
                uint64_t value;
                switch (length)
                {
                case LENGTH_LONG:
                    value = va_arg(ap, long int);
                    break;
                case LENGTH_LONG_LONG:
                    value = va_arg(ap, long long int);
                    break;
                default:
                    value = va_arg(ap, int);
                    break;
                }
                fp++;
                kprinti(value, 1, 10, 0, flags, width, precision);
                break;
            }
            case 'u':
            {
                uint64_t value;
                switch (length)
                {
                case LENGTH_LONG:
                    value = va_arg(ap, unsigned long int);
                    break;
                case LENGTH_LONG_LONG:
                    value = va_arg(ap, unsigned long long int);
                    break;
                default:
                    value = va_arg(ap, unsigned int);
                    break;
                }
                fp++;
                kprinti(value, 0, 10, 0, flags, width, precision);
                break;
            }
            case 'o':
            {
                uint64_t value;
                switch (length)
                {
                case LENGTH_LONG:
                    value = va_arg(ap, unsigned long int);
                    break;
                case LENGTH_LONG_LONG:
                    value = va_arg(ap, unsigned long long int);
                    break;
                default:
                    value = va_arg(ap, unsigned int);
                    break;
                }
                fp++;
                kprinti(value, 0, 010, 0, flags, width, precision);
                break;
            }
            case 'x':
            {
                uint64_t value;
                switch (length)
                {
                case LENGTH_LONG:
                    value = va_arg(ap, unsigned long int);
                    break;
                case LENGTH_LONG_LONG:
                    value = va_arg(ap, unsigned long long int);
                    break;
                default:
                    value = va_arg(ap, unsigned int);
                    break;
                }
                fp++;
                kprinti(value, 0, 0x10, 0, flags, width, precision);
                break;
            }
            case 'X':
            {
                uint64_t value;
                switch (length)
                {
                case LENGTH_LONG:
                    value = va_arg(ap, unsigned long int);
                    break;
                case LENGTH_LONG_LONG:
                    value = va_arg(ap, unsigned long long int);
                    break;
                default:
                    value = va_arg(ap, unsigned int);
                    break;
                }
                fp++;
                kprinti(value, 0, 0x10, 1, flags, width, precision);
                break;
            }
            case 'c':
            {
                fp++;
                kputc(va_arg(ap, int));
                break;
            }
            case 's':
            {
                fp++;
                kprints(va_arg(ap, const char*), flags, width, precision);
                break;
            }
            case 'p':
            {
                fp++;
                kprinti((uint64_t) (uintptr_t) va_arg(ap, void*), 0, 0x10, 0, flags, width, precision);
                break;
            }
            default:
            {
                kputc(*fp++);
                break;
            }
            }
            state = STATE_NONE;
            break;
        }
    }
}
