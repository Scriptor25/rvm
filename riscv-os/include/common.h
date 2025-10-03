#pragma once

#include <stdarg.h>

#define UART_TX     ((volatile unsigned char*) 0x10000000)
#define UART_RX     ((volatile const unsigned char*) 0x10000004)
#define UART_STATUS ((volatile const unsigned char*) 0x10000008)

#define UART_TX_READY_BIT 0x1
#define UART_RX_READY_BIT 0x2

void kputc(char c);
void kputs(const char* str);

void kprintf(const char* format, ...);
void vkprintf(const char* format, va_list ap);

unsigned char kgetc();

int kstrcmp(const char* lhs, const char* rhs);
int kstrcmpn(const char* lhs, int lhs_len, const char* rhs, int rhs_len);
int kstrlen(const char* str);

const char* kstrnext(const char* buffer, const char** pointer, int* length);
