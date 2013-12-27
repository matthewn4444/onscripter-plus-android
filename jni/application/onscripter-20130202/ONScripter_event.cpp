/* -*- C++ -*-
 * 
 *  ONScripter_event.cpp - Event handler of ONScripter
 *
 *  Copyright (c) 2001-2013 Ogapee. All rights reserved.
 *
 *  ogapee@aqua.dti2.ne.jp
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

#include "ONScripter.h"
#if defined(LINUX)
#include <sys/types.h>
#include <sys/wait.h>
#endif

#define ONS_TIMER_EVENT   (SDL_USEREVENT)
#define ONS_MUSIC_EVENT   (SDL_USEREVENT+1)
#define ONS_CDAUDIO_EVENT (SDL_USEREVENT+2)
#define ONS_MIDI_EVENT    (SDL_USEREVENT+3)
#define ONS_CHUNK_EVENT   (SDL_USEREVENT+4)
#define ONS_BREAK_EVENT   (SDL_USEREVENT+5)
#define ONS_BGMFADE_EVENT (SDL_USEREVENT+6)

// This sets up the fade event flag for use in bgm fadeout and fadein.
#define BGM_FADEOUT 0
#define BGM_FADEIN  1

#define EDIT_MODE_PREFIX "[EDIT MODE]  "
#define EDIT_SELECT_STRING "MP3 vol (m)  SE vol (s)  Voice vol (v)  Numeric variable (n)"

static SDL_TimerID timer_id = NULL;
SDL_TimerID timer_cdaudio_id = NULL;
SDL_TimerID timer_bgmfade_id = NULL;

bool ext_music_play_once_flag = false;

/* **************************************** *
 * Callback functions
 * **************************************** */
extern "C" void musicFinishCallback()
{
    SDL_Event event;
    event.type = ONS_MUSIC_EVENT;
    SDL_PushEvent(&event);
}

extern "C" Uint32 SDLCALL timerCallback( Uint32 interval, void *param )
{
    SDL_RemoveTimer( timer_id );
    timer_id = NULL;

	SDL_Event event;
	event.type = ONS_TIMER_EVENT;
	SDL_PushEvent( &event );

    return 0;
}

extern "C" Uint32 cdaudioCallback( Uint32 interval, void *param )
{
    SDL_RemoveTimer( timer_cdaudio_id );
    timer_cdaudio_id = NULL;

    SDL_Event event;
    event.type = ONS_CDAUDIO_EVENT;
    SDL_PushEvent( &event );

    return interval;
}

extern "C" Uint32 SDLCALL bgmfadeCallback( Uint32 interval, void *param )
{
    SDL_Event event;
    event.type = ONS_BGMFADE_EVENT;
    event.user.code = (param == NULL) ? BGM_FADEOUT : BGM_FADEIN;
    SDL_PushEvent( &event );

    return interval;
}

/* **************************************** *
 * OS Dependent Input Translation
 * **************************************** */

SDLKey transKey(SDLKey key)
{
#if defined(IPODLINUX)
 	switch(key){
      case SDLK_m:      key = SDLK_UP;      break; /* Menu                   */
      case SDLK_d:      key = SDLK_DOWN;    break; /* Play/Pause             */
      case SDLK_f:      key = SDLK_RIGHT;   break; /* Fast forward           */
      case SDLK_w:      key = SDLK_LEFT;    break; /* Rewind                 */
      case SDLK_RETURN: key = SDLK_RETURN;  break; /* Action                 */
      case SDLK_h:      key = SDLK_ESCAPE;  break; /* Hold                   */
      case SDLK_r:      key = SDLK_UNKNOWN; break; /* Wheel clockwise        */
      case SDLK_l:      key = SDLK_UNKNOWN; break; /* Wheel counterclockwise */
      default: break;
    }
#endif
#if defined(WINCE)
    switch(key){
      case SDLK_UP:     key = SDLK_UP;      break; /* vkUP    */
      case SDLK_DOWN:   key = SDLK_DOWN;    break; /* vkDOWN  */
      case SDLK_LEFT:   key = SDLK_LCTRL;   break; /* vkLEFT  */
      case SDLK_RIGHT:  key = SDLK_RETURN;  break; /* vkRIGHT */
      case SDLK_KP0:    key = SDLK_q;       break; /* vkStart */
      case SDLK_KP1:    key = SDLK_RETURN;  break; /* vkA     */
      case SDLK_KP2:    key = SDLK_SPACE;   break; /* vkB     */
      case SDLK_KP3:    key = SDLK_ESCAPE;  break; /* vkC     */
      default: break;
    }
#endif
    return key;
}

SDLKey transJoystickButton(Uint8 button)
{
#if defined(PSP)    
    SDLKey button_map[] = { SDLK_ESCAPE, /* TRIANGLE */
                            SDLK_RETURN, /* CIRCLE   */
                            SDLK_SPACE,  /* CROSS    */
                            SDLK_RCTRL,  /* SQUARE   */
                            SDLK_o,      /* LTRIGGER */
                            SDLK_s,      /* RTRIGGER */
                            SDLK_DOWN,   /* DOWN     */
                            SDLK_LEFT,   /* LEFT     */
                            SDLK_UP,     /* UP       */
                            SDLK_RIGHT,  /* RIGHT    */
                            SDLK_0,      /* SELECT   */
                            SDLK_a,      /* START    */
                            SDLK_UNKNOWN,/* HOME     */ /* kernel mode only */
                            SDLK_UNKNOWN,/* HOLD     */};
    return button_map[button];
#elif defined(__PS3__)    
    SDLKey button_map[] = {
        SDLK_0,      /* SELECT   */
        SDLK_UNKNOWN,/* L3       */
        SDLK_UNKNOWN,/* R3       */
        SDLK_a,      /* START    */
        SDLK_UP,     /* UP       */
        SDLK_RIGHT,  /* RIGHT    */
        SDLK_DOWN,   /* DOWN     */
        SDLK_LEFT,   /* LEFT     */
        SDLK_SPACE,  /* L2       */
        SDLK_RETURN, /* R2       */
        SDLK_o,      /* L1       */
        SDLK_s,      /* R1       */
        SDLK_ESCAPE, /* TRIANGLE */
        SDLK_RETURN, /* CIRCLE   */
        SDLK_SPACE,  /* CROSS    */
        SDLK_RCTRL,  /* SQUARE   */
        SDLK_UNKNOWN,/* PS       */
        SDLK_UNKNOWN,
        SDLK_UNKNOWN,
    };
    return button_map[button];
#elif defined(GP2X)
    SDLKey button_map[] = {
        SDLK_UP,      /* UP        */
        SDLK_UNKNOWN, /* UPLEFT    */
        SDLK_LEFT,    /* LEFT      */
        SDLK_UNKNOWN, /* DOWNLEFT  */
        SDLK_DOWN,    /* DOWN      */
        SDLK_UNKNOWN, /* DOWNRIGHT */
        SDLK_RIGHT,   /* RIGHT     */
        SDLK_UNKNOWN, /* UPRIGHT   */
        SDLK_a,       /* START     */
        SDLK_0,       /* SELECT    */
        SDLK_o,       /* L         */
        SDLK_s,       /* R         */
        SDLK_RCTRL,   /* A         */
        SDLK_RETURN,  /* B         */
        SDLK_SPACE,   /* X         */
        SDLK_ESCAPE,  /* Y         */
        SDLK_UNKNOWN, /* VOLUP     */
        SDLK_UNKNOWN, /* VOLDOWN   */
        SDLK_UNKNOWN, /* STICK     */
    };
    return button_map[button];
#endif
    return SDLK_UNKNOWN;
}

SDL_KeyboardEvent transJoystickAxis(SDL_JoyAxisEvent &jaxis)
{
    static int old_axis=-1;

    SDL_KeyboardEvent event;

    SDLKey axis_map[] = {SDLK_LEFT,  /* AL-LEFT  */
                         SDLK_RIGHT, /* AL-RIGHT */
                         SDLK_UP,    /* AL-UP    */
                         SDLK_DOWN   /* AL-DOWN  */};

    int axis = -1;
    /* rerofumi: Jan.15.2007 */
    /* ps3's pad has 0x1b axis (with analog button) */
    if (jaxis.axis < 2){
        axis = ((3200 > jaxis.value) && (jaxis.value > -3200) ? -1 :
                (jaxis.axis * 2 + (jaxis.value>0 ? 1 : 0) ));
    }

    if (axis != old_axis){
        if (axis == -1){
            event.type = SDL_KEYUP;
            event.keysym.sym = axis_map[old_axis];
        }
        else{
            event.type = SDL_KEYDOWN;
            event.keysym.sym = axis_map[axis];
        }
        old_axis = axis;
    }
    else{
        event.keysym.sym = SDLK_UNKNOWN;
    }
    
    return event;
}

void ONScripter::flushEventSub( SDL_Event &event )
{
    if ( event.type == ONS_MUSIC_EVENT ){
        if ( music_play_loop_flag ||
             (cd_play_loop_flag && !cdaudio_flag ) ){
            stopBGM( true );
            if (music_file_name)
                playSound(music_file_name, SOUND_MUSIC, true);
            else
                playCDAudio();
        }
        else{
            stopBGM( false );
        }
    }
    else if ((event.type == ONS_BGMFADE_EVENT) &&
             (event.user.code == BGM_FADEOUT)){
        Uint32 cur_fade_duration = mp3fadeout_duration_internal;
        if (skip_mode & (SKIP_NORMAL | SKIP_TO_EOP) ||
            ctrl_pressed_status) {
            cur_fade_duration = 0;
            Mix_VolumeMusic( 0 );
        }
        Uint32 tmp = SDL_GetTicks() - mp3fade_start;
        if ( tmp < cur_fade_duration ) {
            tmp = cur_fade_duration - tmp;
            tmp *= music_volume;
            tmp /= cur_fade_duration;

            Mix_VolumeMusic( tmp * MIX_MAX_VOLUME / 100 );
        } else {
            char *ext = NULL;
            if (music_file_name) ext = strrchr(music_file_name, '.');
            if (ext && (strcmp(ext+1, "OGG") && strcmp(ext+1, "ogg"))){
                // set break event to return to script processing when playing music other than ogg
                SDL_Event event;
                event.type = ONS_BREAK_EVENT;
                SDL_PushEvent( &event );
            }

            stopBGM( false );
        }
    }
    else if ((event.type == ONS_BGMFADE_EVENT) &&
             (event.user.code == BGM_FADEIN)){
        Uint32 cur_fade_duration = mp3fadein_duration_internal;
        if (skip_mode & (SKIP_NORMAL | SKIP_TO_EOP) ||
            ctrl_pressed_status) {
            cur_fade_duration = 0;
            Mix_VolumeMusic( music_volume * MIX_MAX_VOLUME / 100 );
        }
        Uint32 tmp = SDL_GetTicks() - mp3fade_start;
        if ( tmp < cur_fade_duration ) {
            tmp *= music_volume;
            tmp /= cur_fade_duration;

            Mix_VolumeMusic( tmp * MIX_MAX_VOLUME / 100 );
        } else {
            if (timer_bgmfade_id) SDL_RemoveTimer( timer_bgmfade_id );
            timer_bgmfade_id = NULL;
            mp3fadeout_duration_internal = 0;

            char *ext = NULL;
            if (music_file_name) ext = strrchr(music_file_name, '.');
            if (ext && (strcmp(ext+1, "OGG") && strcmp(ext+1, "ogg"))){
                // set break event to return to script processing when playing music other than ogg
                SDL_Event event;
                event.type = ONS_BREAK_EVENT;
                SDL_PushEvent( &event );
            }
        }
    }
    else if ( event.type == ONS_CDAUDIO_EVENT ){
        if ( cd_play_loop_flag ){
            stopBGM( true );
            playCDAudio();
        }
        else{
            stopBGM( false );
        }
    }
    else if ( event.type == ONS_MIDI_EVENT ){
        ext_music_play_once_flag = !midi_play_loop_flag;
        Mix_FreeMusic( midi_info );
        playMIDI(midi_play_loop_flag);
    }
    else if ( event.type == ONS_CHUNK_EVENT ){ // for processing btntim2 and automode correctly
        if ( wave_sample[event.user.code] ){
            Mix_FreeChunk( wave_sample[event.user.code] );
            wave_sample[event.user.code] = NULL;
            if (event.user.code == MIX_LOOPBGM_CHANNEL0 && 
                loop_bgm_name[1] &&
                wave_sample[MIX_LOOPBGM_CHANNEL1])
                Mix_PlayChannel(MIX_LOOPBGM_CHANNEL1, 
                                wave_sample[MIX_LOOPBGM_CHANNEL1], -1);
        }
    }
}

void ONScripter::flushEvent()
{
    SDL_Event event;
    while( SDL_PollEvent( &event ) )
        flushEventSub( event );
}

void ONScripter::removeEvent(int type)
{
    SDL_Event event;
#if SDL_VERSION_ATLEAST(1, 3, 0)
    while(SDL_PeepEvents( &event, 1, SDL_GETEVENT, type, type) > 0);
#else
    while(SDL_PeepEvents( &event, 1, SDL_GETEVENT, SDL_EVENTMASK(type) ) > 0 );
#endif
}

void ONScripter::removeBGMFadeEvent()
{
    removeEvent(ONS_BGMFADE_EVENT);
}

void ONScripter::waitEventSub(int count)
{
    remaining_time = count;
    timerEvent();

    runEventLoop();
    removeEvent( ONS_BREAK_EVENT );
}

bool ONScripter::waitEvent( int count )
{
    while(1){
        waitEventSub( count );
        if ( system_menu_mode == SYSTEM_NULL ) break;
        int ret = executeSystemCall();
        if      (ret == 1) return true;
        else if (ret == 2) return false;
    }

    return false;
}

void midiCallback( int sig )
{
#if defined(LINUX)
    int status;
    wait( &status );
#endif
    if ( !ext_music_play_once_flag ){
        SDL_Event event;
        event.type = ONS_MIDI_EVENT;
        SDL_PushEvent(&event);
    }
}

extern "C" void waveCallback( int channel )
{
    SDL_Event event;
    event.type = ONS_CHUNK_EVENT;
    event.user.code = channel;
    SDL_PushEvent(&event);
}

bool ONScripter::trapHandler()
{
    if (trap_mode & TRAP_STOP){
        trap_mode |= TRAP_CLICKED;
        return false;
    }
    
    trap_mode = TRAP_NONE;
    stopAnimation( clickstr_state );
    setCurrentLabel( trap_dist );
    current_button_state.button = 0; // to escape from screen effect

    return true;
}

/* **************************************** *
 * Event handlers
 * **************************************** */
bool ONScripter::mouseMoveEvent( SDL_MouseMotionEvent *event )
{
    current_button_state.x = event->x * screen_width / screen_device_width;
    current_button_state.y = event->y * screen_width / screen_device_width;

    if ( event_mode & WAIT_BUTTON_MODE ){
        mouseOverCheck( current_button_state.x, current_button_state.y );
        if (getmouseover_flag &&
            current_over_button >= getmouseover_lower &&
            current_over_button <= getmouseover_upper){
            current_button_state.button = current_over_button;
            return true;
        }
    }

    return false;
}

bool ONScripter::mousePressEvent( SDL_MouseButtonEvent *event )
{
    if ( variable_edit_mode ) return false;
    
    if ( automode_flag ){
        automode_flag = false;
        return false;
    }

    if ( (event->button == SDL_BUTTON_RIGHT && trap_mode & TRAP_RIGHT_CLICK) ||
         (event->button == SDL_BUTTON_LEFT  && trap_mode & TRAP_LEFT_CLICK) ){
        if (trapHandler()) return true;
    }

    current_button_state.x = event->x * screen_width / screen_device_width;
    current_button_state.y = event->y * screen_width / screen_device_width;
    current_button_state.down_flag = false;
    skip_mode &= ~SKIP_NORMAL;

    if ( event->button == SDL_BUTTON_RIGHT &&
         event->type == SDL_MOUSEBUTTONUP &&
         ((rmode_flag && event_mode & WAIT_TEXT_MODE) ||
          (event_mode & (WAIT_BUTTON_MODE | WAIT_RCLICK_MODE))) ){
        current_button_state.button = -1;
        sprintf(current_button_state.str, "RCLICK");
        if (event_mode & WAIT_TEXT_MODE){
            if (root_rmenu_link.next)
                system_menu_mode = SYSTEM_MENU;
            else
                system_menu_mode = SYSTEM_WINDOWERASE;
        }
    }
    else if ( event->button == SDL_BUTTON_LEFT &&
              ( event->type == SDL_MOUSEBUTTONUP || btndown_flag ) ){
        current_button_state.button = current_over_button;
        if (current_over_button == 0)
            sprintf(current_button_state.str, "LCLICK");
        else
            sprintf(current_button_state.str, "S%d", current_over_button);
            
        if ( event->type == SDL_MOUSEBUTTONDOWN )
            current_button_state.down_flag = true;
    }
#if SDL_VERSION_ATLEAST(1, 2, 5)
    else if (event->button == SDL_BUTTON_WHEELUP &&
             (bexec_flag ||
              (event_mode & WAIT_TEXT_MODE) ||
              (usewheel_flag && event_mode & WAIT_BUTTON_MODE) || 
              system_menu_mode == SYSTEM_LOOKBACK)){
        current_button_state.button = -2;
        sprintf(current_button_state.str, "WHEELUP");
        if (event_mode & WAIT_TEXT_MODE) system_menu_mode = SYSTEM_LOOKBACK;
    }
    else if ( event->button == SDL_BUTTON_WHEELDOWN &&
              (bexec_flag ||
               (enable_wheeldown_advance_flag && event_mode & WAIT_TEXT_MODE) ||
               (usewheel_flag && event_mode & WAIT_BUTTON_MODE) || 
               system_menu_mode == SYSTEM_LOOKBACK ) ){
        if (event_mode & WAIT_TEXT_MODE)
            current_button_state.button = 0;
        else
            current_button_state.button = -3;
        sprintf(current_button_state.str, "WHEELDOWN");
    }
#endif
    else return false;

    if ( event_mode & (WAIT_INPUT_MODE | WAIT_BUTTON_MODE) ){
        if (!(event_mode & (WAIT_TEXT_MODE)))
            skip_mode |= SKIP_TO_EOL;
        playClickVoice();
        stopAnimation( clickstr_state );

        return true;
    }

    return false;
}

void ONScripter::variableEditMode( SDL_KeyboardEvent *event )
{
    int  i;
    const char *var_name;
    char var_index[12];

    switch ( event->keysym.sym ) {
      case SDLK_m:
        if ( variable_edit_mode != EDIT_SELECT_MODE ) return;
        variable_edit_mode = EDIT_MP3_VOLUME_MODE;
        variable_edit_num = music_volume;
        break;

      case SDLK_s:
        if ( variable_edit_mode != EDIT_SELECT_MODE ) return;
        variable_edit_mode = EDIT_SE_VOLUME_MODE;
        variable_edit_num = se_volume;
        break;

      case SDLK_v:
        if ( variable_edit_mode != EDIT_SELECT_MODE ) return;
        variable_edit_mode = EDIT_VOICE_VOLUME_MODE;
        variable_edit_num = voice_volume;
        break;

      case SDLK_n:
        if ( variable_edit_mode != EDIT_SELECT_MODE ) return;
        variable_edit_mode = EDIT_VARIABLE_INDEX_MODE;
        variable_edit_num = 0;
        break;

      case SDLK_9: case SDLK_KP9: variable_edit_num = variable_edit_num * 10 + 9; break;
      case SDLK_8: case SDLK_KP8: variable_edit_num = variable_edit_num * 10 + 8; break;
      case SDLK_7: case SDLK_KP7: variable_edit_num = variable_edit_num * 10 + 7; break;
      case SDLK_6: case SDLK_KP6: variable_edit_num = variable_edit_num * 10 + 6; break;
      case SDLK_5: case SDLK_KP5: variable_edit_num = variable_edit_num * 10 + 5; break;
      case SDLK_4: case SDLK_KP4: variable_edit_num = variable_edit_num * 10 + 4; break;
      case SDLK_3: case SDLK_KP3: variable_edit_num = variable_edit_num * 10 + 3; break;
      case SDLK_2: case SDLK_KP2: variable_edit_num = variable_edit_num * 10 + 2; break;
      case SDLK_1: case SDLK_KP1: variable_edit_num = variable_edit_num * 10 + 1; break;
      case SDLK_0: case SDLK_KP0: variable_edit_num = variable_edit_num * 10 + 0; break;

      case SDLK_MINUS: case SDLK_KP_MINUS:
        if ( variable_edit_mode == EDIT_VARIABLE_NUM_MODE && variable_edit_num == 0 ) variable_edit_sign = -1;
        break;

      case SDLK_BACKSPACE:
        if ( variable_edit_num ) variable_edit_num /= 10;
        else if ( variable_edit_sign == -1 ) variable_edit_sign = 1;
        break;

      case SDLK_RETURN: case SDLK_KP_ENTER:
        switch( variable_edit_mode ){

          case EDIT_VARIABLE_INDEX_MODE:
            variable_edit_index = variable_edit_num;
            variable_edit_num = script_h.getVariableData(variable_edit_index).num;
            if ( variable_edit_num < 0 ){
                variable_edit_num = -variable_edit_num;
                variable_edit_sign = -1;
            }
            else{
                variable_edit_sign = 1;
            }
            break;

          case EDIT_VARIABLE_NUM_MODE:
            script_h.setNumVariable( variable_edit_index, variable_edit_sign * variable_edit_num );
            break;

          case EDIT_MP3_VOLUME_MODE:
            music_volume = variable_edit_num;
            Mix_VolumeMusic( music_volume * MIX_MAX_VOLUME / 100 );
            break;

          case EDIT_SE_VOLUME_MODE:
            se_volume = variable_edit_num;
            for ( i=1 ; i<ONS_MIX_CHANNELS ; i++ )
                if ( wave_sample[i] ) Mix_Volume( i, se_volume * MIX_MAX_VOLUME / 100 );
            if ( wave_sample[MIX_LOOPBGM_CHANNEL0] ) Mix_Volume( MIX_LOOPBGM_CHANNEL0, se_volume * MIX_MAX_VOLUME / 100 );
            if ( wave_sample[MIX_LOOPBGM_CHANNEL1] ) Mix_Volume( MIX_LOOPBGM_CHANNEL1, se_volume * MIX_MAX_VOLUME / 100 );
            break;

          case EDIT_VOICE_VOLUME_MODE:
            voice_volume = variable_edit_num;
            if ( wave_sample[0] ) Mix_Volume( 0, se_volume * MIX_MAX_VOLUME / 100 );

          default:
            break;
        }
        if ( variable_edit_mode == EDIT_VARIABLE_INDEX_MODE )
            variable_edit_mode = EDIT_VARIABLE_NUM_MODE;
        else
            variable_edit_mode = EDIT_SELECT_MODE;
        break;

      case SDLK_ESCAPE:
        if ( variable_edit_mode == EDIT_SELECT_MODE ){
            variable_edit_mode = NOT_EDIT_MODE;
            SDL_WM_SetCaption( DEFAULT_WM_TITLE, DEFAULT_WM_ICON );
            SDL_Delay( 100 );
            SDL_WM_SetCaption( wm_title_string, wm_icon_string );
            return;
        }
        variable_edit_mode = EDIT_SELECT_MODE;

      default:
        break;
    }

    if ( variable_edit_mode == EDIT_SELECT_MODE ){
        sprintf( wm_edit_string, "%s%s", EDIT_MODE_PREFIX, EDIT_SELECT_STRING );
    }
    else if ( variable_edit_mode == EDIT_VARIABLE_INDEX_MODE ) {
        sprintf( wm_edit_string, "%s%s%d", EDIT_MODE_PREFIX, "Variable Index?  %", variable_edit_sign * variable_edit_num );
    }
    else if ( variable_edit_mode >= EDIT_VARIABLE_NUM_MODE ){
        int p=0;
        
        switch( variable_edit_mode ){

          case EDIT_VARIABLE_NUM_MODE:
            sprintf( var_index, "%%%d", variable_edit_index );
            var_name = var_index; p = script_h.getVariableData(variable_edit_index).num; break;

          case EDIT_MP3_VOLUME_MODE:
            var_name = "MP3 Volume"; p = music_volume; break;

          case EDIT_VOICE_VOLUME_MODE:
            var_name = "Voice Volume"; p = voice_volume; break;

          case EDIT_SE_VOLUME_MODE:
            var_name = "Sound effect Volume"; p = se_volume; break;

          default:
            var_name = "";
        }
        sprintf( wm_edit_string, "%sCurrent %s=%d  New value? %s%d",
                 EDIT_MODE_PREFIX, var_name, p, (variable_edit_sign==1)?"":"-", variable_edit_num );
    }

    SDL_WM_SetCaption( wm_edit_string, wm_icon_string );
}

void ONScripter::shiftCursorOnButton( int diff )
{
    int num;
    ButtonLink *button = root_button_link.next;
    for (num=0 ; button ; num++) 
        button = button->next;

    shortcut_mouse_line += diff;
    if      (shortcut_mouse_line < 0)    shortcut_mouse_line = num-1;
    else if (shortcut_mouse_line >= num) shortcut_mouse_line = 0;

    button = root_button_link.next;
    for (int i=0 ; i<shortcut_mouse_line ; i++) 
        button  = button->next;
    
    if (button){
        int x = button->select_rect.x + button->select_rect.w/2;
        int y = button->select_rect.y + button->select_rect.h/2;
        if      (x < 0)             x = 0;
        else if (x >= screen_width) x = screen_width-1;
        if      (y < 0)              y = 0;
        else if (y >= screen_height) y = screen_height-1;
        x = x * screen_device_width / screen_width;
        y = y * screen_device_width / screen_width;
        shift_over_button = button->no;
        SDL_WarpMouse(x, y);
    }
}

bool ONScripter::keyDownEvent( SDL_KeyboardEvent *event )
{
    switch ( event->keysym.sym ) {
      case SDLK_RCTRL:
        ctrl_pressed_status  |= 0x01;
        sprintf(current_button_state.str, "CTRL");
        goto ctrl_pressed;
      case SDLK_LCTRL:
        ctrl_pressed_status  |= 0x02;
        sprintf(current_button_state.str, "CTRL");
        goto ctrl_pressed;
      case SDLK_RSHIFT:
        shift_pressed_status |= 0x01;
        break;
      case SDLK_LSHIFT:
        shift_pressed_status |= 0x02;
        break;
      default:
        break;
    }

    return false;

  ctrl_pressed:
    current_button_state.button  = 0;
    playClickVoice();
    stopAnimation( clickstr_state );

    return true;
}

void ONScripter::keyUpEvent( SDL_KeyboardEvent *event )
{
    switch ( event->keysym.sym ) {
      case SDLK_RCTRL:
        ctrl_pressed_status  &= ~0x01;
        break;
      case SDLK_LCTRL:
        ctrl_pressed_status  &= ~0x02;
        break;
      case SDLK_RSHIFT:
        shift_pressed_status &= ~0x01;
        break;
      case SDLK_LSHIFT:
        shift_pressed_status &= ~0x02;
        break;
      default:
        break;
    }
}

bool ONScripter::keyPressEvent( SDL_KeyboardEvent *event )
{
    current_button_state.button = 0;
    current_button_state.down_flag = false;
    if ( automode_flag ){
        automode_flag = false;
        return false;
    }
    
    if ( event->type == SDL_KEYUP ){
        if ( variable_edit_mode ){
            variableEditMode( event );
            return false;
        }

        if ( edit_flag && event->keysym.sym == SDLK_z ){
            variable_edit_mode = EDIT_SELECT_MODE;
            variable_edit_sign = 1;
            variable_edit_num = 0;
            sprintf( wm_edit_string, "%s%s", EDIT_MODE_PREFIX, EDIT_SELECT_STRING );
            SDL_WM_SetCaption( wm_edit_string, wm_icon_string );
        }
    }
    
    if (event->type == SDL_KEYUP)
        skip_mode &= ~SKIP_NORMAL;
    
    if ( shift_pressed_status && event->keysym.sym == SDLK_q && current_mode == NORMAL_MODE ){
        endCommand();
    }

    if ( (trap_mode & TRAP_LEFT_CLICK) && 
         (event->keysym.sym == SDLK_RETURN ||
          event->keysym.sym == SDLK_KP_ENTER ||
          event->keysym.sym == SDLK_SPACE ) ){
        if (trapHandler()) return true;
    }
    else if ( (trap_mode & TRAP_RIGHT_CLICK) && 
              (event->keysym.sym == SDLK_ESCAPE) ){
        if (trapHandler()) return true;
    }
    
    if ( event_mode & WAIT_BUTTON_MODE &&
         (((event->type == SDL_KEYUP || btndown_flag) &&
           ((!getenter_flag && event->keysym.sym == SDLK_RETURN) ||
            (!getenter_flag && event->keysym.sym == SDLK_KP_ENTER))) ||
          ((spclclk_flag || !useescspc_flag) && event->keysym.sym == SDLK_SPACE)) ){
        if ( event->keysym.sym == SDLK_RETURN ||
             event->keysym.sym == SDLK_KP_ENTER ||
             (spclclk_flag && event->keysym.sym == SDLK_SPACE) ){
            current_button_state.button = current_over_button;
            if (current_over_button == 0)
                sprintf(current_button_state.str, "RETURN");
            else
                sprintf(current_button_state.str, "S%d", current_over_button);
            if ( event->type == SDL_KEYDOWN )
                current_button_state.down_flag = true;
        }
        else{
            current_button_state.button = 0;
            sprintf(current_button_state.str, "SPACE");
        }
        playClickVoice();
        stopAnimation( clickstr_state );

        return true;
    }

    if ( event->type == SDL_KEYDOWN ) return false;
    
    if ( ( event_mode & (WAIT_INPUT_MODE | WAIT_BUTTON_MODE) ) &&
         ( autoclick_time == 0 || (event_mode & WAIT_BUTTON_MODE)) ){
        if ( !useescspc_flag && event->keysym.sym == SDLK_ESCAPE){
            current_button_state.button  = -1;
            sprintf(current_button_state.str, "RCLICK");
            if (rmode_flag && event_mode & WAIT_TEXT_MODE){
                if (root_rmenu_link.next)
                    system_menu_mode = SYSTEM_MENU;
                else
                    system_menu_mode = SYSTEM_WINDOWERASE;
            }
        }
        else if ( useescspc_flag && event->keysym.sym == SDLK_ESCAPE ){
            current_button_state.button  = -10;
        }
        else if ( !spclclk_flag && useescspc_flag && event->keysym.sym == SDLK_SPACE ){
            current_button_state.button  = -11;
        }
        else if (((!getcursor_flag && event->keysym.sym == SDLK_LEFT) ||
                  event->keysym.sym == SDLK_h) &&
                 (event_mode & WAIT_TEXT_MODE ||
                  (usewheel_flag && !getcursor_flag && event_mode & WAIT_BUTTON_MODE) || 
                  system_menu_mode == SYSTEM_LOOKBACK)){
            current_button_state.button = -2;
            if (event_mode & WAIT_TEXT_MODE) system_menu_mode = SYSTEM_LOOKBACK;
        }
        else if (((!getcursor_flag && event->keysym.sym == SDLK_RIGHT) ||
                  event->keysym.sym == SDLK_l) &&
                 ((enable_wheeldown_advance_flag && event_mode & WAIT_TEXT_MODE) ||
                  (usewheel_flag && event_mode & WAIT_BUTTON_MODE) || 
                  system_menu_mode == SYSTEM_LOOKBACK)){
            if (event_mode & WAIT_TEXT_MODE)
                current_button_state.button = 0;
            else
                current_button_state.button = -3;
        }
        else if (((!getcursor_flag && event->keysym.sym == SDLK_UP) ||
                  event->keysym.sym == SDLK_k ||
                  event->keysym.sym == SDLK_p) &&
                 event_mode & WAIT_BUTTON_MODE){
            shiftCursorOnButton(1);
            return false;
        }
        else if (((!getcursor_flag && event->keysym.sym == SDLK_DOWN) ||
                  event->keysym.sym == SDLK_j ||
                  event->keysym.sym == SDLK_n) &&
                 event_mode & WAIT_BUTTON_MODE){
            shiftCursorOnButton(-1);
            return false;
        }
        else if ( getpageup_flag && event->keysym.sym == SDLK_PAGEUP ){
            current_button_state.button  = -12;
            sprintf(current_button_state.str, "PAGEUP");
        }
        else if ( getpagedown_flag && event->keysym.sym == SDLK_PAGEDOWN ){
            current_button_state.button  = -13;
            sprintf(current_button_state.str, "PAGEDOWN");
        }
        else if ( (getenter_flag && event->keysym.sym == SDLK_RETURN) ||
                  (getenter_flag && event->keysym.sym == SDLK_KP_ENTER) ){
            current_button_state.button  = -19;
        }
        else if ( gettab_flag && event->keysym.sym == SDLK_TAB ){
            current_button_state.button  = -20;
        }
        else if ( getcursor_flag && event->keysym.sym == SDLK_UP ){
            current_button_state.button  = -40;
            sprintf(current_button_state.str, "UP");
        }
        else if ( getcursor_flag && event->keysym.sym == SDLK_RIGHT ){
            current_button_state.button  = -41;
            sprintf(current_button_state.str, "RIGHT");
        }
        else if ( getcursor_flag && event->keysym.sym == SDLK_DOWN ){
            current_button_state.button  = -42;
            sprintf(current_button_state.str, "DOWN");
        }
        else if ( getcursor_flag && event->keysym.sym == SDLK_LEFT ){
            current_button_state.button  = -43;
            sprintf(current_button_state.str, "LEFT");
        }
        else if ( getinsert_flag && event->keysym.sym == SDLK_INSERT ){
            current_button_state.button  = -50;
        }
        else if ( getzxc_flag && event->keysym.sym == SDLK_z ){
            current_button_state.button  = -51;
        }
        else if ( getzxc_flag && event->keysym.sym == SDLK_x ){
            current_button_state.button  = -52;
        }
        else if ( getzxc_flag && event->keysym.sym == SDLK_c ){
            current_button_state.button  = -53;
        }
        else if ( getfunction_flag && 
                  event->keysym.sym >= SDLK_F1 && event->keysym.sym <= SDLK_F12 ){
            current_button_state.button = -21-(event->keysym.sym - SDLK_F1);
            sprintf(current_button_state.str, "F%d", event->keysym.sym - SDLK_F1+1);
        }
        else if ( bexec_flag && 
                  event->keysym.sym >= SDLK_0 && event->keysym.sym <= SDLK_9 ){
            current_button_state.button = -1; // dummy
            sprintf(current_button_state.str, "%d", event->keysym.sym - SDLK_0);
        }
        else if ( bexec_flag && 
                  event->keysym.sym >= SDLK_a && event->keysym.sym <= SDLK_z ){
            current_button_state.button = -1; // dummy
            sprintf(current_button_state.str, "%c", 'A' + event->keysym.sym - SDLK_a);
        }
        else if ( bexec_flag && 
                  (event->keysym.sym == SDLK_RSHIFT || event->keysym.sym == SDLK_LSHIFT) ){
            current_button_state.button = -1; // dummy
            sprintf(current_button_state.str, "SHIFT");
        }
        
        if ( current_button_state.button != 0 ){
            stopAnimation( clickstr_state );

            return true;
        }
    }

    if ( event_mode & WAIT_INPUT_MODE &&
         ( autoclick_time == 0 || (event_mode & WAIT_BUTTON_MODE)) ){
        if (event->keysym.sym == SDLK_RETURN || 
            event->keysym.sym == SDLK_KP_ENTER ||
            event->keysym.sym == SDLK_SPACE ){
            if (!(event_mode & WAIT_TEXT_MODE))
                skip_mode |= SKIP_TO_EOL;
            playClickVoice();
            stopAnimation( clickstr_state );

            return true;
        }
    }
    
    if ( event_mode & WAIT_INPUT_MODE ){
        if (event->keysym.sym == SDLK_s && !automode_flag ){
            skip_mode |= SKIP_NORMAL;
            printf("toggle skip to true\n");
            stopAnimation( clickstr_state );

            return true;
        }
        else if (event->keysym.sym == SDLK_o){
            if (skip_mode & SKIP_TO_EOP)
                skip_mode &= ~SKIP_TO_EOP;
            else
                skip_mode |= SKIP_TO_EOP;
            printf("toggle draw one page flag to %s\n", (skip_mode & SKIP_TO_EOP?"true":"false") );
            if ( skip_mode & SKIP_TO_EOP ){
                stopAnimation( clickstr_state );

                return true;
            }
        }
        else if ( event->keysym.sym == SDLK_a && mode_ext_flag && !automode_flag ){
            automode_flag = true;
            skip_mode &= ~SKIP_NORMAL;
            printf("change to automode\n");
            stopAnimation( clickstr_state );

            return true;
        }
        else if ( event->keysym.sym == SDLK_0 ){
            if (++text_speed_no > 2) text_speed_no = 0;
            sentence_font.wait_time = -1;
        }
        else if ( event->keysym.sym == SDLK_1 ){
            text_speed_no = 0;
            sentence_font.wait_time = -1;
        }
        else if ( event->keysym.sym == SDLK_2 ){
            text_speed_no = 1;
            sentence_font.wait_time = -1;
        }
        else if ( event->keysym.sym == SDLK_3 ){
            text_speed_no = 2;
            sentence_font.wait_time = -1;
        }
    }

    if ( event_mode & ( WAIT_INPUT_MODE | WAIT_BUTTON_MODE ) ){
        if ( event->keysym.sym == SDLK_f ){
            if ( fullscreen_mode ) menu_windowCommand();
            else                   menu_fullCommand();
        }
    }

    return false;
}

void ONScripter::timerEvent()
{
    if (remaining_time == 0){
        SDL_Event event;
        event.type = ONS_BREAK_EVENT;
        SDL_PushEvent(&event);
        return;
    }
    
    int duration = 0;
    if (event_mode & WAIT_TIMER_MODE){
        proceedAnimation();
        duration = calcDurationToNextAnimation();
    }
            
    if (duration > 0){
        if (remaining_time > duration){
            remaining_time -= duration;
        }
        else if (remaining_time > 0){
            duration = remaining_time;
            remaining_time = 0;
        }
        stepAnimation(duration);
        if (timer_id) SDL_RemoveTimer(timer_id);
        timer_id = SDL_AddTimer(duration, timerCallback, NULL);
    }
    else if (remaining_time > 0){
        if (timer_id) SDL_RemoveTimer(timer_id);
        timer_id = SDL_AddTimer(remaining_time, timerCallback, NULL);
        remaining_time = 0;
    }
}

void ONScripter::runEventLoop()
{
    SDL_Event event, tmp_event;

    while ( SDL_WaitEvent(&event) ) {
        bool ret = false;
        // ignore continous SDL_MOUSEMOTION
        while (event.type == SDL_MOUSEMOTION){
#if SDL_VERSION_ATLEAST(1, 3, 0)
            if ( SDL_PeepEvents( &tmp_event, 1, SDL_PEEKEVENT, SDL_FIRSTEVENT, SDL_LASTEVENT ) == 0 ) break;
            if (tmp_event.type != SDL_MOUSEMOTION) break;
            SDL_PeepEvents( &tmp_event, 1, SDL_GETEVENT, SDL_FIRSTEVENT, SDL_LASTEVENT );
#else
            if ( SDL_PeepEvents( &tmp_event, 1, SDL_PEEKEVENT, SDL_ALLEVENTS ) == 0 ) break;
            if (tmp_event.type != SDL_MOUSEMOTION) break;
            SDL_PeepEvents( &tmp_event, 1, SDL_GETEVENT, SDL_ALLEVENTS );
#endif
            event = tmp_event;
        }

        switch (event.type) {
#if defined(IOS) // || defined(ANDROID)
          case SDL_FINGERMOTION:
            {
                SDL_Touch *touch = SDL_GetTouch(event.tfinger.touchId);
                tmp_event.motion.x = device_width *event.tfinger.x/touch->xres - (device_width -screen_device_width)/2;
                tmp_event.motion.y = device_height*event.tfinger.y/touch->yres - (device_height-screen_device_height)/2;
                if (mouseMoveEvent( &tmp_event.motion )) return;
                if (btndown_flag){
                    event.button.type = SDL_MOUSEBUTTONDOWN;
                    event.button.button = SDL_BUTTON_LEFT;
                    if (touch->num_fingers >= 2)
                        event.button.button = SDL_BUTTON_RIGHT;
                    event.button.x = tmp_event.motion.x;
                    event.button.y = tmp_event.motion.y;
                    ret = mousePressEvent( &event.button );
                    if (ret) return;
                }
            }
            break;
          case SDL_FINGERDOWN:
          {
                SDL_Touch *touch = SDL_GetTouch(event.tfinger.touchId);
                tmp_event.motion.x = device_width *event.tfinger.x/touch->xres - (device_width -screen_device_width)/2;
                tmp_event.motion.y = device_height*event.tfinger.y/touch->yres - (device_height-screen_device_height)/2;
                if (mouseMoveEvent( &tmp_event.motion )) return;
          }
            if ( btndown_flag ){
                SDL_Touch *touch = SDL_GetTouch(event.tfinger.touchId);
                tmp_event.button.type = SDL_MOUSEBUTTONDOWN;
                tmp_event.button.button = SDL_BUTTON_LEFT;
                if (touch->num_fingers >= 2)
                    tmp_event.button.button = SDL_BUTTON_RIGHT;
                tmp_event.button.x = device_width *event.tfinger.x/touch->xres - (device_width -screen_device_width)/2;
                tmp_event.button.y = device_height*event.tfinger.y/touch->yres - (device_height-screen_device_height)/2;
                ret = mousePressEvent( &tmp_event.button );
            }
            {
                SDL_Touch *touch = SDL_GetTouch(event.tfinger.touchId);
                num_fingers = touch->num_fingers;
                if (num_fingers >= 3){
                    tmp_event.key.keysym.sym = SDLK_LCTRL;
                    ret |= keyDownEvent( &tmp_event.key );
                }
            }
            if (ret) return;
            break;
          case SDL_FINGERUP:
            if (num_fingers == 0) break;
            {
                SDL_Touch *touch = SDL_GetTouch(event.tfinger.touchId);
                tmp_event.button.type = SDL_MOUSEBUTTONUP;
                tmp_event.button.button = SDL_BUTTON_LEFT;
                if (touch->num_fingers >= 1)
                    tmp_event.button.button = SDL_BUTTON_RIGHT;
                tmp_event.button.x = device_width *event.tfinger.x/touch->xres - (device_width -screen_device_width)/2;
                tmp_event.button.y = device_height*event.tfinger.y/touch->yres - (device_height-screen_device_height)/2;
                ret = mousePressEvent( &tmp_event.button );
            }
            tmp_event.key.keysym.sym = SDLK_LCTRL;
            keyUpEvent( &tmp_event.key );
            num_fingers = 0;
            if (ret) return;
            break;
#else
          case SDL_MOUSEMOTION:
            if (mouseMoveEvent( &event.motion )) return;
            if (btndown_flag){
                if (event.motion.state & SDL_BUTTON(SDL_BUTTON_LEFT))
                    tmp_event.button.button = SDL_BUTTON_LEFT;
                else if (event.motion.state & SDL_BUTTON(SDL_BUTTON_RIGHT))
                    tmp_event.button.button = SDL_BUTTON_RIGHT;
                else
                    break;

                tmp_event.button.type = SDL_MOUSEBUTTONDOWN;
                tmp_event.button.x = event.motion.x;
                tmp_event.button.y = event.motion.y;
                ret = mousePressEvent( &tmp_event.button );
                if (ret) return;
            }
            break;
            
          case SDL_MOUSEBUTTONDOWN:
            if ( !btndown_flag ) break;
          case SDL_MOUSEBUTTONUP:
            ret = mousePressEvent( &event.button );
            if (ret) return;
            break;
#endif
          case SDL_JOYBUTTONDOWN:
            event.key.type = SDL_KEYDOWN;
            event.key.keysym.sym = transJoystickButton(event.jbutton.button);
            if(event.key.keysym.sym == SDLK_UNKNOWN)
                break;
            
          case SDL_KEYDOWN:
            event.key.keysym.sym = transKey(event.key.keysym.sym);
            ret = keyDownEvent( &event.key );
            if ( btndown_flag )
                ret |= keyPressEvent( &event.key );
            if (ret) return;
            break;

          case SDL_JOYBUTTONUP:
            event.key.type = SDL_KEYUP;
            event.key.keysym.sym = transJoystickButton(event.jbutton.button);
            if(event.key.keysym.sym == SDLK_UNKNOWN)
                break;
            
          case SDL_KEYUP:
            event.key.keysym.sym = transKey(event.key.keysym.sym);
            keyUpEvent( &event.key );
            ret = keyPressEvent( &event.key );
            if (ret) return;
            break;

          case SDL_JOYAXISMOTION:
          {
              SDL_KeyboardEvent ke = transJoystickAxis(event.jaxis);
              if (ke.keysym.sym != SDLK_UNKNOWN){
                  if (ke.type == SDL_KEYDOWN){
                      keyDownEvent( &ke );
                      if (btndown_flag)
                          keyPressEvent( &ke );
                  }
                  else if (ke.type == SDL_KEYUP){
                      keyUpEvent( &ke );
                      keyPressEvent( &ke );
                  }
              }
              break;
          }

          case ONS_TIMER_EVENT:
            timerEvent();
            break;

          case ONS_MUSIC_EVENT:
          case ONS_BGMFADE_EVENT:
          case ONS_CDAUDIO_EVENT:
          case ONS_MIDI_EVENT:
            flushEventSub( event );
            break;

          case ONS_CHUNK_EVENT:
            flushEventSub( event );
            //printf("ONS_CHUNK_EVENT %d: %x %d %x\n", event.user.code, wave_sample[0], automode_flag, event_mode);
            if ( event.user.code != 0 ||
                 !(event_mode & WAIT_VOICE_MODE) ) break;

            event_mode &= ~WAIT_VOICE_MODE;

          case ONS_BREAK_EVENT:
            if (event_mode & WAIT_VOICE_MODE && wave_sample[0]){
                remaining_time = -1;
                timerEvent();
                break;
            }

            if (automode_flag || autoclick_time > 0)
                current_button_state.button = 0;
            else if ( usewheel_flag ){
                current_button_state.button = -5;
                sprintf(current_button_state.str, "TIMEOUT");
            }
            else{
                current_button_state.button = -2;
                sprintf(current_button_state.str, "TIMEOUT");
            }

            if (event_mode & (WAIT_INPUT_MODE | WAIT_BUTTON_MODE) && 
                ( clickstr_state == CLICK_WAIT || 
                  clickstr_state == CLICK_NEWPAGE ) ){
                playClickVoice(); 
                stopAnimation( clickstr_state ); 
            }

            return;
            
          case SDL_ACTIVEEVENT:
            if ( !event.active.gain ) break;
#ifdef ANDROID
            if (event.active.state == SDL_APPACTIVE){
                screen_surface = SDL_SetVideoMode( screen_width, screen_height, screen_bpp, DEFAULT_VIDEO_SURFACE_FLAG );
                repaintCommand();
                break;
            }
#endif
          case SDL_VIDEOEXPOSE:
#ifdef USE_SDL_RENDERER
            SDL_RenderPresent(renderer);
#else
            SDL_UpdateRect( screen_surface, 0, 0, screen_width, screen_height );
#endif
            break;

          case SDL_QUIT:
            endCommand();
            break;
            
          default:
            break;
        }
    }
}
