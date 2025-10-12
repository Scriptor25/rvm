#pragma once

#include <stdarg.h>
#include <stddef.h>

void kputc(char c);
void kputs(const char* str);
void knputs(const char* str, int len);

void kprintf(const char* format, ...);
void vkprintf(const char* format, va_list ap);

unsigned char kgetc();

int kstrcmp(const char* lhs, const char* rhs);
int kstrcmpn(const char* lhs, int lhs_len, const char* rhs, int rhs_len);
int kstrlen(const char* str);

const char* kstrnext(const char* buffer, const char** pointer, int* length);

void* kmemset(void* buffer, int value, size_t count);
