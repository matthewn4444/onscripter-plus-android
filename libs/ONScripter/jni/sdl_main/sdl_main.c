#ifdef ANDROID

#include <unistd.h>
#include <jni.h>
#include <android/log.h>
#include "SDL_thread.h"
#include "SDL_main.h"

/* JNI-C wrapper stuff */

#ifdef __cplusplus
#define C_LINKAGE "C"
#else
#define C_LINKAGE
#endif


#ifndef SDL_JAVA_PACKAGE_PATH
#error You have to define SDL_JAVA_PACKAGE_PATH to your package path with dots replaced with underscores, for example "com_example_SanAngeles"
#endif
#define JAVA_EXPORT_NAME2(name,package) Java_##package##_##name
#define JAVA_EXPORT_NAME1(name,package) JAVA_EXPORT_NAME2(name,package)
#define JAVA_EXPORT_NAME(name) JAVA_EXPORT_NAME1(name,SDL_JAVA_PACKAGE_PATH)

extern C_LINKAGE void
JAVA_EXPORT_NAME(DemoRenderer_nativeInit) ( JNIEnv*  env, jobject thiz, jstring currentDirectoryPath_j, jobjectArray arg )
{
	int argc = 1 + (*env)->GetArrayLength(env, arg);
	char **argv = malloc(sizeof(char*)*argc);
	argv[0] = "sdl";
	int i;
	for (i=0 ; i<argc-1 ; i++){
		jstring str = (jstring)(*env)->GetObjectArrayElement(env, arg, i);
		argv[i+1] = (char*)(*env)->GetStringUTFChars(env, str, 0);
	}

	// Set current directory
	const char *currentDirectoryPath = (*env)->GetStringUTFChars(env, currentDirectoryPath_j, 0);
	chdir(currentDirectoryPath);
	(*env)->ReleaseStringUTFChars(env, currentDirectoryPath_j, currentDirectoryPath);

	SDL_main( argc, argv );
};

#undef JAVA_EXPORT_NAME
#undef JAVA_EXPORT_NAME1
#undef JAVA_EXPORT_NAME2
#undef C_LINKAGE

#endif
