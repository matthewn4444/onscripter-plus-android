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
#include "../../events/SDL_keyboard_c.h"
#include "../../events/SDL_mouse_c.h"

#include "SDL_androidvideo.h"
#include "SDL_scancode.h"
#include "SDL_compat.h"


static SDLKey keymap[KEYCODE_LAST+1];

/* JNI-C++ wrapper stuff */

#ifndef SDL_JAVA_PACKAGE_PATH
#error You have to define SDL_JAVA_PACKAGE_PATH to your package path with dots replaced with underscores, for example "com_example_SanAngeles"
#endif
#define JAVA_EXPORT_NAME2(name,package) Java_##package##_##name
#define JAVA_EXPORT_NAME1(name,package) JAVA_EXPORT_NAME2(name,package)
#define JAVA_EXPORT_NAME(name) JAVA_EXPORT_NAME1(name,SDL_JAVA_PACKAGE_PATH)



enum MOUSE_ACTION { MOUSE_DOWN = 0, MOUSE_UP=1, MOUSE_MOVE=2 };

JNIEXPORT void JNICALL 
JAVA_EXPORT_NAME(DemoGLSurfaceView_nativeMouse) ( JNIEnv*  env, jobject  thiz, jint x, jint y, jint action )
{
	if( action == MOUSE_DOWN || action == MOUSE_UP )
	{
		SDL_SendMouseMotion(0, x, y);
		SDL_SendMouseButton( (action == MOUSE_DOWN) ? SDL_PRESSED : SDL_RELEASED, 1 );
	}
	if( action == MOUSE_MOVE )
		SDL_SendMouseMotion(0, x, y);
}

static SDL_scancode TranslateKey(int scancode)
{
	if ( scancode >= SDL_arraysize(keymap) )
		scancode = KEYCODE_UNKNOWN;
	return keymap[scancode];
}


JNIEXPORT void JNICALL 
JAVA_EXPORT_NAME(DemoGLSurfaceView_nativeKey) ( JNIEnv*  env, jobject thiz, jint key, jint action )
{
	//if( ! processAndroidTrackballKeyDelays(key, action) )
    if (action == 2){
        SDL_Event event;
        event.type = SDL_QUIT;
        SDL_PushEvent( &event );
    }
    if (action == 3){
        SDL_Event event;
        event.type = SDL_ACTIVEEVENT;
        event.active.gain = 1;
        event.active.state = SDL_APPACTIVE;
        SDL_PushEvent( &event );
    }
	int posted = SDL_SendKeyboardKey( action ? SDL_PRESSED : SDL_RELEASED, TranslateKey(key) );
	//__android_log_print(ANDROID_LOG_INFO, "libSDL", "SDL_SendKeyboardKey state %d code %d posted %d, SDL_PollEvent %d", (int)action, TranslateKey(key), posted, ret);
}


JNIEXPORT void JNICALL 
JAVA_EXPORT_NAME(AccelerometerReader_nativeAccelerometer) ( JNIEnv*  env, jobject  thiz, jfloat accX, jfloat accY, jfloat accZ )
{
	// TODO: use accelerometer as joystick, make this configurable
	// Currenly it's used as cursor + Home/End keys
	static const float dx = 1.0, dy = 1.0, dz = 1.0;

	static float midX = 0, midY = 0, midZ = 0;
	static int pressLeft = 0, pressRight = 0, pressUp = 0, pressDown = 0, pressR = 0, pressL = 0;
	
	if( accX < midX - dx )
	{
		if( !pressLeft )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: press left, acc %f mid %f d %f", accX, midX, dx);
			pressLeft = 1;
			SDL_SendKeyboardKey( SDL_PRESSED, SDL_SCANCODE_LEFT );
		}
	}
	else
	{
		if( pressLeft )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: release left, acc %f mid %f d %f", accX, midX, dx);
			pressLeft = 0;
			SDL_SendKeyboardKey( SDL_RELEASED, SDL_SCANCODE_LEFT );
		}
	}
	if( accX < midX - dx*2 )
		midX = accX + dx*2;

	if( accX > midX + dx )
	{
		if( !pressRight )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: press right, acc %f mid %f d %f", accX, midX, dx);
			pressRight = 1;
			SDL_SendKeyboardKey( SDL_PRESSED, SDL_SCANCODE_RIGHT );
		}
	}
	else
	{
		if( pressRight )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: release right, acc %f mid %f d %f", accX, midX, dx);
			pressRight = 0;
			SDL_SendKeyboardKey( SDL_RELEASED, SDL_SCANCODE_RIGHT );
		}
	}
	if( accX > midX + dx*2 )
		midX = accX - dx*2;

	if( accY < midY + dy )
	{
		if( !pressUp )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: press up, acc %f mid %f d %f", accY, midY, dy);
			pressUp = 1;
			SDL_SendKeyboardKey( SDL_PRESSED, SDL_SCANCODE_UP );
		}
	}
	else
	{
		if( pressUp )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: release up, acc %f mid %f d %f", accY, midY, dy);
			pressUp = 0;
			SDL_SendKeyboardKey( SDL_RELEASED, SDL_SCANCODE_UP );
		}
	}
	if( accY < midY + dy*2 )
		midY = accY - dy*2;

	if( accY > midY - dy )
	{
		if( !pressDown )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: press down, acc %f mid %f d %f", accY, midY, dy);
			pressDown = 1;
			SDL_SendKeyboardKey( SDL_PRESSED, SDL_SCANCODE_DOWN );
		}
	}
	else
	{
		if( pressDown )
		{
			//__android_log_print(ANDROID_LOG_INFO, "libSDL", "Accelerometer: release down, acc %f mid %f d %f", accY, midY, dy);
			pressDown = 0;
			SDL_SendKeyboardKey( SDL_RELEASED, SDL_SCANCODE_DOWN );
		}
	}
	if( accY > midY - dy*2 )
		midY = accY + dy*2;

}


void ANDROID_InitOSKeymap()
{
  int i;

  SDLKey defaultKeymap[SDL_NUM_SCANCODES];
  SDL_GetDefaultKeymap(defaultKeymap);
  SDL_SetKeymap(0, defaultKeymap, SDL_NUM_SCANCODES);

  // TODO: keys are mapped rather randomly

  for (i=0; i<SDL_arraysize(keymap); ++i)
    keymap[i] = SDL_SCANCODE_UNKNOWN;

  keymap[KEYCODE_UNKNOWN] = SDL_SCANCODE_UNKNOWN;

  keymap[KEYCODE_BACK] = SDL_SCANCODE_ESCAPE;

  // HTC Evo has only two keys - Menu and Back, and all games require Enter. (Also Volume Up/Down, but they are hard to reach) 
  // TODO: make this configurable
  keymap[KEYCODE_MENU] = SDL_SCANCODE_RETURN; // SDL_SCANCODE_LALT;

  keymap[KEYCODE_CALL] = SDL_SCANCODE_LCTRL;
  keymap[KEYCODE_ENDCALL] = SDL_SCANCODE_LSHIFT;
  keymap[KEYCODE_CAMERA] = SDL_SCANCODE_RSHIFT;
  keymap[KEYCODE_POWER] = SDL_SCANCODE_RALT;

  keymap[KEYCODE_0] = SDL_SCANCODE_0;
  keymap[KEYCODE_1] = SDL_SCANCODE_1;
  keymap[KEYCODE_2] = SDL_SCANCODE_2;
  keymap[KEYCODE_3] = SDL_SCANCODE_3;
  keymap[KEYCODE_4] = SDL_SCANCODE_4;
  keymap[KEYCODE_5] = SDL_SCANCODE_5;
  keymap[KEYCODE_6] = SDL_SCANCODE_6;
  keymap[KEYCODE_7] = SDL_SCANCODE_7;
  keymap[KEYCODE_8] = SDL_SCANCODE_8;
  keymap[KEYCODE_9] = SDL_SCANCODE_9;
  keymap[KEYCODE_STAR] = SDL_SCANCODE_KP_DIVIDE;
  keymap[KEYCODE_POUND] = SDL_SCANCODE_KP_MULTIPLY;

  keymap[KEYCODE_DPAD_UP] = SDL_SCANCODE_UP;
  keymap[KEYCODE_DPAD_DOWN] = SDL_SCANCODE_DOWN;
  keymap[KEYCODE_DPAD_LEFT] = SDL_SCANCODE_LEFT;
  keymap[KEYCODE_DPAD_RIGHT] = SDL_SCANCODE_RIGHT;
  keymap[KEYCODE_DPAD_CENTER] = SDL_SCANCODE_RETURN;

  keymap[KEYCODE_SOFT_LEFT] = SDL_SCANCODE_KP_4;
  keymap[KEYCODE_SOFT_RIGHT] = SDL_SCANCODE_KP_6;
  keymap[KEYCODE_ENTER] = SDL_SCANCODE_KP_ENTER;

  keymap[KEYCODE_VOLUME_UP] = SDL_SCANCODE_PAGEUP;
  keymap[KEYCODE_VOLUME_DOWN] = SDL_SCANCODE_PAGEDOWN;
  keymap[KEYCODE_SEARCH] = SDL_SCANCODE_END;
  keymap[KEYCODE_HOME] = SDL_SCANCODE_HOME;

  keymap[KEYCODE_CLEAR] = SDL_SCANCODE_BACKSPACE;
  keymap[KEYCODE_A] = SDL_SCANCODE_A;
  keymap[KEYCODE_B] = SDL_SCANCODE_B;
  keymap[KEYCODE_C] = SDL_SCANCODE_C;
  keymap[KEYCODE_D] = SDL_SCANCODE_D;
  keymap[KEYCODE_E] = SDL_SCANCODE_E;
  keymap[KEYCODE_F] = SDL_SCANCODE_F;
  keymap[KEYCODE_G] = SDL_SCANCODE_G;
  keymap[KEYCODE_H] = SDL_SCANCODE_H;
  keymap[KEYCODE_I] = SDL_SCANCODE_I;
  keymap[KEYCODE_J] = SDL_SCANCODE_J;
  keymap[KEYCODE_K] = SDL_SCANCODE_K;
  keymap[KEYCODE_L] = SDL_SCANCODE_L;
  keymap[KEYCODE_M] = SDL_SCANCODE_M;
  keymap[KEYCODE_N] = SDL_SCANCODE_N;
  keymap[KEYCODE_O] = SDL_SCANCODE_O;
  keymap[KEYCODE_P] = SDL_SCANCODE_P;
  keymap[KEYCODE_Q] = SDL_SCANCODE_Q;
  keymap[KEYCODE_R] = SDL_SCANCODE_R;
  keymap[KEYCODE_S] = SDL_SCANCODE_S;
  keymap[KEYCODE_T] = SDL_SCANCODE_T;
  keymap[KEYCODE_U] = SDL_SCANCODE_U;
  keymap[KEYCODE_V] = SDL_SCANCODE_V;
  keymap[KEYCODE_W] = SDL_SCANCODE_W;
  keymap[KEYCODE_X] = SDL_SCANCODE_X;
  keymap[KEYCODE_Y] = SDL_SCANCODE_Y;
  keymap[KEYCODE_Z] = SDL_SCANCODE_Z;
  keymap[KEYCODE_COMMA] = SDL_SCANCODE_COMMA;
  keymap[KEYCODE_PERIOD] = SDL_SCANCODE_PERIOD;
  keymap[KEYCODE_TAB] = SDL_SCANCODE_TAB;
  keymap[KEYCODE_SPACE] = SDL_SCANCODE_SPACE;
  keymap[KEYCODE_DEL] = SDL_SCANCODE_DELETE;
  keymap[KEYCODE_GRAVE] = SDL_SCANCODE_GRAVE;
  keymap[KEYCODE_MINUS] = SDL_SCANCODE_KP_MINUS;
  keymap[KEYCODE_PLUS] = SDL_SCANCODE_KP_PLUS;
  keymap[KEYCODE_EQUALS] = SDL_SCANCODE_EQUALS;
  keymap[KEYCODE_LEFT_BRACKET] = SDL_SCANCODE_LEFTBRACKET;
  keymap[KEYCODE_RIGHT_BRACKET] = SDL_SCANCODE_RIGHTBRACKET;
  keymap[KEYCODE_BACKSLASH] = SDL_SCANCODE_BACKSLASH;
  keymap[KEYCODE_SEMICOLON] = SDL_SCANCODE_SEMICOLON;
  keymap[KEYCODE_APOSTROPHE] = SDL_SCANCODE_APOSTROPHE;
  keymap[KEYCODE_SLASH] = SDL_SCANCODE_SLASH;
  keymap[KEYCODE_AT] = SDL_SCANCODE_KP_AT;

  keymap[KEYCODE_MEDIA_PLAY_PAUSE] = SDL_SCANCODE_AUDIOPLAY;
  keymap[KEYCODE_MEDIA_STOP] = SDL_SCANCODE_AUDIOSTOP;
  keymap[KEYCODE_MEDIA_NEXT] = SDL_SCANCODE_AUDIONEXT;
  keymap[KEYCODE_MEDIA_PREVIOUS] = SDL_SCANCODE_AUDIOPREV;
  keymap[KEYCODE_MEDIA_REWIND] = SDL_SCANCODE_KP_1;
  keymap[KEYCODE_MEDIA_FAST_FORWARD] = SDL_SCANCODE_KP_3;
  keymap[KEYCODE_MUTE] = SDL_SCANCODE_MUTE;

  keymap[KEYCODE_SYM] = SDL_SCANCODE_LGUI;
  keymap[KEYCODE_NUM] = SDL_SCANCODE_NUMLOCKCLEAR;

  keymap[KEYCODE_ALT_LEFT] = SDL_SCANCODE_AC_BACK;
  keymap[KEYCODE_ALT_RIGHT] = SDL_SCANCODE_AC_FORWARD;
  keymap[KEYCODE_SHIFT_LEFT] = SDL_SCANCODE_VOLUMEUP;
  keymap[KEYCODE_SHIFT_RIGHT] = SDL_SCANCODE_VOLUMEDOWN;

  keymap[KEYCODE_EXPLORER] = SDL_SCANCODE_WWW;
  keymap[KEYCODE_ENVELOPE] = SDL_SCANCODE_MAIL;

  keymap[KEYCODE_HEADSETHOOK] = SDL_SCANCODE_AC_SEARCH;
  keymap[KEYCODE_FOCUS] = SDL_SCANCODE_AC_REFRESH;
  keymap[KEYCODE_NOTIFICATION] = SDL_SCANCODE_AC_BOOKMARKS;

}

static int AndroidTrackballKeyDelays[4] = {0,0,0,0};

// Key = -1 if we want to send KeyUp events from main loop
int processAndroidTrackballKeyDelays( int key, int action )
{
	return 0;
	
	//TODO: fix that code
	
	// Send Directional Pad Up events with a delay, so app wil lthink we're holding the key a bit
	static const int KeysMapping[4] = {KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT};
	int idx, idx2;
	
	if( key < 0 )
	{
		for( idx = 0; idx < 4; idx ++ )
		{
			if( AndroidTrackballKeyDelays[idx] > 0 )
			{
				AndroidTrackballKeyDelays[idx] --;
				if( AndroidTrackballKeyDelays[idx] == 0 )
					SDL_SendKeyboardKey( SDL_RELEASED, TranslateKey(idx) );
			}
		}
	}
	else
	{
		idx = -1;
		// Too lazy to do switch or function
		if( key == KEYCODE_DPAD_UP )
			idx = 0;
		else if( key == KEYCODE_DPAD_DOWN )
			idx = 1;
		else if( key == KEYCODE_DPAD_LEFT )
			idx = 2;
		else if( key == KEYCODE_DPAD_RIGHT )
			idx = 3;
		if( idx >= 0 )
		{
			if( action && AndroidTrackballKeyDelays[idx] == 0 )
			{
				// User pressed key for the first time
				idx2 = (idx + 2) % 4; // Opposite key for current key - if it's still pressing, release it
				if( AndroidTrackballKeyDelays[idx2] > 0 )
				{
					AndroidTrackballKeyDelays[idx2] = 0;
					SDL_SendKeyboardKey( SDL_RELEASED, TranslateKey(idx2) );
				}
				SDL_SendKeyboardKey( SDL_PRESSED, TranslateKey(key) );
			}
			else if( !action && AndroidTrackballKeyDelays[idx] == 0 )
			{
				// User released key - make a delay, do not send release event
				AndroidTrackballKeyDelays[idx] = SDL_TRACKBALL_KEYUP_DELAY;
			}
			else if( action && AndroidTrackballKeyDelays[idx] > 0 )
			{
				// User pressed key another time - add some more time for key to be pressed
				AndroidTrackballKeyDelays[idx] += SDL_TRACKBALL_KEYUP_DELAY;
				if( AndroidTrackballKeyDelays[idx] < SDL_TRACKBALL_KEYUP_DELAY * 4 )
					AndroidTrackballKeyDelays[idx] = SDL_TRACKBALL_KEYUP_DELAY * 4;
			}
			return 1;
		}
	}
	return 0;
}
