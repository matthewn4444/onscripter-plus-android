#ifndef __ONSCRIPTER_LOG_H__
#define __ONSCRIPTER_LOG_H__

#define ONSCRIPTER_LOG_TAG "ONScripter"

#if defined(ANDROID)
#include <android/log.h>

#define logv(...) __android_log_print(ANDROID_LOG_VERBOSE, ONSCRIPTER_LOG_TAG, __VA_ARGS__)
#define logw(stderr, ...) __android_log_print(ANDROID_LOG_WARN, ONSCRIPTER_LOG_TAG, __VA_ARGS__)
#define logi(...) __android_log_print(ANDROID_LOG_INFO, ONSCRIPTER_LOG_TAG, __VA_ARGS__)
#define loge(stderr, ...) logError(__VA_ARGS__)
#define logee(stderr, ...) logErrorWithExtra(__VA_ARGS__)
#else
#define logv(...) printf(__VA_ARGS__)
#define logw(stderr, ...) fprintf(stderr, __VA_ARGS__)
#define logi(...) printf(__VA_ARGS__)
#define loge(stderr, ...) fprintf(stderr, __VA_ARGS__)
#define logee(stderr, currentline, ...) fprintf(stderr, __VA_ARGS__)
#endif

#endif // __ONSCRIPTER_LOG_H__
