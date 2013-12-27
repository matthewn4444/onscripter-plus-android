/*
    SDL - Simple DirectMedia Layer
    Copyright (C) 1997-2009 Sam Lantinga

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Sam Lantinga
    slouken@libsdl.org
*/

#include <jni.h>
#include <android/log.h>
#include <GLES/gl.h>
#include <GLES/glext.h>
#include <sys/time.h>
#include <time.h>
#include <stdint.h>
#include <math.h>
#include <string.h> // for memset()

#include "SDL_config.h"

#include "SDL_video.h"
#include "SDL_mouse.h"
#include "SDL_mutex.h"
#include "SDL_thread.h"
#include "../SDL_sysvideo.h"
#include "../SDL_pixels_c.h"
#include "../../events/SDL_events_c.h"

#include "SDL_androidvideo.h"


/* Initialization/Query functions */
static int ANDROID_VideoInit(_THIS);
static int ANDROID_GetDisplayBounds(_THIS, SDL_VideoDisplay * display, SDL_Rect * rect);
static void ANDROID_GetDisplayModes(_THIS, SDL_VideoDisplay * display);
static int ANDROID_SetDisplayMode(_THIS, SDL_VideoDisplay * display, SDL_DisplayMode * mode);
static void ANDROID_VideoQuit(_THIS);

static void ANDROID_GL_SwapBuffers(_THIS, SDL_Window * window);
// Stubs
static SDL_GLContext ANDROID_GL_CreateContext(_THIS, SDL_Window * window);
static int ANDROID_GL_MakeCurrent (_THIS, SDL_Window * window, SDL_GLContext context);
static void ANDROID_GL_DeleteContext (_THIS, SDL_GLContext context);


// The device screen dimensions to draw on
static int sWindowWidth  = 320;
static int sWindowHeight = 480;

// Extremely wicked JNI environment to call Java functions from C code
static JNIEnv* JavaEnv = NULL;
static jclass JavaRendererClass = NULL;
static jobject JavaRenderer = NULL;
static jmethodID JavaSwapBuffers = NULL;

static void SdlGlRenderInit();

/* ANDROID driver bootstrap functions */

static int ANDROID_Available(void)
{
	return 1;
}

static void ANDROID_DeleteDevice(SDL_VideoDevice *device)
{
	SDL_free(device);
}

static SDL_VideoDevice *ANDROID_CreateDevice(int devindex)
{
	SDL_VideoDevice *device;

	/* Initialize all variables that we clean on shutdown */
	device = (SDL_VideoDevice *)SDL_malloc(sizeof(SDL_VideoDevice));
	if ( device ) {
		SDL_memset(device, 0, sizeof (*device));
	}
	if ( (device == NULL) ) {
		SDL_OutOfMemory();
		if ( device ) {
			SDL_free(device);
		}
		return(0);
	}

	/* Set the function pointers */
	device->VideoInit = ANDROID_VideoInit;
	device->GetDisplayBounds = ANDROID_GetDisplayBounds;
	device->GetDisplayModes = ANDROID_GetDisplayModes;
	device->SetDisplayMode = ANDROID_SetDisplayMode;
	device->PumpEvents = ANDROID_PumpEvents;
	device->VideoQuit = ANDROID_VideoQuit;
	device->free = ANDROID_DeleteDevice;
	
	device->GL_SwapWindow = ANDROID_GL_SwapBuffers;
	device->GL_CreateContext = ANDROID_GL_CreateContext;
	device->GL_MakeCurrent = ANDROID_GL_MakeCurrent;
	device->GL_DeleteContext = ANDROID_GL_DeleteContext;

	return device;
}

VideoBootStrap ANDROID_bootstrap = {
	"android", "SDL Android video driver",
	ANDROID_Available, ANDROID_CreateDevice
};


int ANDROID_VideoInit(_THIS)
{
	SDL_VideoDisplay display;
	SDL_DisplayMode mode;

	mode.w = sWindowWidth;
	mode.h = sWindowHeight;
	mode.refresh_rate = 0;
	// modified in 2012.04.14 by Ogapee
	//mode.format = SDL_PIXELFORMAT_RGB565;
	mode.format = SDL_PIXELFORMAT_ABGR8888;
	mode.driverdata = NULL;

	SDL_zero(display);
	display.desktop_mode = mode;
	display.current_mode = mode;
	display.driverdata = NULL;
	SDL_AddVideoDisplay(&display);

	return 1;
}


void ANDROID_GetDisplayModes(_THIS, SDL_VideoDisplay * display)
{
	SDL_DisplayMode mode;
	mode.w = sWindowWidth;
	mode.h = sWindowHeight;
	mode.refresh_rate = 0;
	// modified in 2012.04.14 by Ogapee
	//mode.format = SDL_PIXELFORMAT_RGB565;
	mode.format = SDL_PIXELFORMAT_ABGR8888;
	mode.driverdata = NULL;
	SDL_AddDisplayMode(display, &mode);

	/*
	struct compatModes_t { int x, int y } compatModes[] =
	{ {800, 600}, {640, 480}, {320, 240}, {320, 200} };
	
	for(int i = 0; i < sizeof(compatModes) / sizeof(compatModes[0]); i++)
		if( sWindowWidth >= compatModes[i].x && sWindowHeight >= compatModes[i].y )
		{
			mode.w = compatModes[i].x;
			mode.h = compatModes[i].y;
			SDL_AddDisplayMode(display, &mode);
		}
	*/
}

int ANDROID_GetDisplayBounds(_THIS, SDL_VideoDisplay * display, SDL_Rect * rect)
{
	rect->w = sWindowWidth;
	rect->h = sWindowHeight;
	return 1;
};

int ANDROID_SetDisplayMode(_THIS, SDL_VideoDisplay * display, SDL_DisplayMode * mode)
{
	return 1;
};


/* Note:  If we are terminated, this could be called in the middle of
   another SDL video routine -- notably UpdateRects.
*/
void ANDROID_VideoQuit(_THIS)
{
}

void ANDROID_PumpEvents(_THIS)
{
}

static inline int CallJavaSwapBuffers()
{
	return (*JavaEnv)->CallIntMethod( JavaEnv, JavaRenderer, JavaSwapBuffers );
}

void ANDROID_GL_SwapBuffers(_THIS, SDL_Window * window)
{
	CallJavaSwapBuffers();
};

SDL_GLContext ANDROID_GL_CreateContext(_THIS, SDL_Window * window)
{
	return (SDL_GLContext)1;
};
int ANDROID_GL_MakeCurrent (_THIS, SDL_Window * window, SDL_GLContext context)
{
	return 1;
};
void ANDROID_GL_DeleteContext (_THIS, SDL_GLContext context)
{
};

/* JNI-C++ wrapper stuff */

#ifndef SDL_JAVA_PACKAGE_PATH
#error You have to define SDL_JAVA_PACKAGE_PATH to your package path with dots replaced with underscores, for example "com_example_SanAngeles"
#endif
#define JAVA_EXPORT_NAME2(name,package) Java_##package##_##name
#define JAVA_EXPORT_NAME1(name,package) JAVA_EXPORT_NAME2(name,package)
#define JAVA_EXPORT_NAME(name) JAVA_EXPORT_NAME1(name,SDL_JAVA_PACKAGE_PATH)

JNIEXPORT void JNICALL 
JAVA_EXPORT_NAME(DemoRenderer_nativeResize) ( JNIEnv*  env, jobject  thiz, jint w, jint h )
{
    sWindowWidth  = w;
    sWindowHeight = h;
    __android_log_print(ANDROID_LOG_INFO, "libSDL", "Physical screen resolution is %dx%d", w, h);
}

/* Call to finalize the graphics state */
JNIEXPORT void JNICALL 
JAVA_EXPORT_NAME(DemoRenderer_nativeDone) ( JNIEnv*  env, jobject  thiz )
{
	__android_log_print(ANDROID_LOG_INFO, "libSDL", "quitting...");
	SDL_SendQuit();
	__android_log_print(ANDROID_LOG_INFO, "libSDL", "quit OK");
}

JNIEXPORT void JNICALL 
JAVA_EXPORT_NAME(DemoRenderer_nativeInitJavaCallbacks) ( JNIEnv*  env, jobject thiz )
{
	JavaEnv = env;
	JavaRenderer = thiz;
	
	JavaRendererClass = (*JavaEnv)->GetObjectClass(JavaEnv, thiz);
	JavaSwapBuffers = (*JavaEnv)->GetMethodID(JavaEnv, JavaRendererClass, "swapBuffers", "()I");
	
	ANDROID_InitOSKeymap();
	
}
