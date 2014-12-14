#ifndef __BACKTRACE_H__
#define __BACKTRACE_H__

unsigned int get_backtrace(char** out, unsigned int buffSize);

void print_backtrace(const char* label = NULL);

#endif // __BACKTRACE_H__
