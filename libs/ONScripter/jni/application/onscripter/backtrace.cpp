#include <stdio.h>
#include <string.h>
#include <unwind.h>
#include <dlfcn.h>
#include <cxxabi.h>

#include "backtrace.h"

// Help from http://www.netmite.com/android/mydroid/frameworks/base/libs/utils/CallStack.cpp

struct backtrace_arg
{
    void** array;
    int cnt, size;
};

_Unwind_Reason_Code trace_func(struct _Unwind_Context *context, void* a) 
{
    struct backtrace_arg *arg = reinterpret_cast<struct backtrace_arg *>(a);
    if (arg->cnt != -1)
        arg->array[arg->cnt] = (void *) _Unwind_GetIP (context);
    if (++arg->cnt == arg->size)
        return _URC_END_OF_STACK;
    return _URC_NO_REASON;
}

unsigned int linux_gcc_demangler(const char *mangled_name, char *unmangled_name, size_t buffersize)
{
	size_t out_len = 0;
	int status = 0;
	char *demangled = abi::__cxa_demangle(mangled_name, 0, &out_len, &status);
	if (status == 0) {
		// OK
		if (out_len < buffersize) memcpy(unmangled_name, demangled, out_len);
		else out_len = 0;
		free(demangled);
	} else {
		out_len = 0;
	}
	return out_len;
}

unsigned int get_backtrace(char** out, unsigned int buffSize)
{
    const int max_frames = 100;
    const int name_max_length = 512;

    void *array[max_frames];
    struct backtrace_arg arg = {array, -1, max_frames};
    char* out_buff = *out;

    char tmp[name_max_length];
    char line[name_max_length];
    unsigned int offset;
    size_t buf_length = 0;
    Dl_info info;
    void * ip;

    _Unwind_Backtrace(trace_func, &arg);

    // Build the backtrace
    out_buff += sprintf(out_buff, "Error caused in native code");
    for (int i = 0; i < arg.cnt; i++) {
        ip = arg.array[i];

        // Get the data of this frame
        if (dladdr(ip, &info)) {
            offset = (unsigned int)ip - (unsigned int)info.dli_saddr;

            const char* fn_name = info.dli_sname;
            const char* lib_name = info.dli_fname;
            if (linux_gcc_demangler(fn_name, tmp, name_max_length) != 0)
                fn_name = tmp;
            buf_length = sprintf(line, "\n    at %s.%x[%s]+%i", lib_name, (unsigned int)ip, fn_name, offset);
        } else {
            buf_length = sprintf(line, "\n    <Unknown native code>.%x", (unsigned int)ip);
        }

        // Check if we can copy the line in the buffer; +1 for terminating character
        if (buf_length + ((unsigned int)out_buff - (unsigned int)*out) + 1 < buffSize) {
            strncpy(out_buff, line, buf_length);
            out_buff += buf_length;
        }
    }
    unsigned int length = (unsigned int)out_buff - (unsigned int)*out;
    *(*out + length) = 0;
    return length;
}
