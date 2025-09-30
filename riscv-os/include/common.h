#pragma once

#include <stdarg.h>

#define UART_RX  ((volatile unsigned char*) 0x10000000)
#define UART_LSR ((volatile unsigned char*) 0x10000005)

#define UART_DR_BIT 0x01

void kputc(char c);
void kputs(const char* str);

void kprintf(const char* format, ...);
void vkprintf(const char* format, va_list ap);

unsigned char kgetc();

int kstrcmp(const char* lhs, const char* rhs);
int kstrcmpn(const char* lhs, int lhs_len, const char* rhs, int rhs_len);
int kstrlen(const char* str);

const char* kstrnext(const char* buffer, const char** pointer, int* length);
