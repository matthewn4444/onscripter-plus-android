/* -*- C++ -*-
 * 
 *  ONScripter_effect.cpp - Effect executer of ONScripter
 *
 *  Copyright (c) 2001-2014 Ogapee. All rights reserved.
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

#define EFFECT_STRIPE_WIDTH (16 * screen_ratio1 / screen_ratio2)
#define EFFECT_STRIPE_CURTAIN_WIDTH (24 * screen_ratio1 / screen_ratio2)
#define EFFECT_QUAKE_AMP (12 * screen_ratio1 / screen_ratio2)

bool ONScripter::setEffect( EffectLink *effect, bool generate_effect_dst, bool update_backup_surface )
{
    if ( effect->effect == 0 ) return true;

    if (update_backup_surface)
        refreshSurface(backup_surface, &dirty_rect.bounding_box, REFRESH_NORMAL_MODE);
    
    int effect_no = effect->effect;
    if (effect_cut_flag && (skip_mode & SKIP_NORMAL || ctrl_pressed_status)) 
        effect_no = 1;

    SDL_BlitSurface( accumulation_surface, NULL, effect_src_surface, NULL );
        
    if (generate_effect_dst){
        int refresh_mode = refreshMode();
        if (update_backup_surface && refresh_mode == REFRESH_NORMAL_MODE){
            SDL_BlitSurface( backup_surface, &dirty_rect.bounding_box, effect_dst_surface, &dirty_rect.bounding_box );
        }
        else{
            if (effect_no == 1)
                refreshSurface( effect_dst_surface, &dirty_rect.bounding_box, refresh_mode );
            else
                refreshSurface( effect_dst_surface, NULL, refresh_mode );
        }
    }
    
    /* Load mask image */
    if ( effect_no == 15 || effect_no == 18 ){
        if ( !effect->anim.image_surface ){
            parseTaggedString( &effect->anim );
            setupAnimationInfo( &effect->anim );
        }
    }
    if ( effect_no == 11 || effect_no == 12 || effect_no == 13 || effect_no == 14 ||
         effect_no == 16 || effect_no == 17 )
        dirty_rect.fill( screen_width, screen_height );

    if (effect_no == 99){ // dll-based
        if (effect->anim.image_name != NULL){
            printf("dll effect: Got dll '%s'\n", effect->anim.image_name);
            if (!strncmp(effect->anim.image_name, "breakup.dll", 11))
                initBreakup(effect->anim.image_name);
            dirty_rect.fill( screen_width, screen_height );
        }
    }

    effect_counter = 0;

    effect_duration = effect->duration;
    if (skip_mode & SKIP_NORMAL || ctrl_pressed_status){
        // shorten the duration of effects while skipping
        if ( effect_cut_flag ) effect_duration = 0;
        else if (effect_duration > 100){
            effect_duration = effect_duration / 10;
        } else if (effect_duration > 10){
            effect_duration = 10;
        } else {
            effect_duration = 1;
        }
    }
    
    return false;
}

bool ONScripter::doEffect( EffectLink *effect, bool clear_dirty_region )
{
    effect_start_time = SDL_GetTicks();
    if ( effect_counter == 0 ) effect_start_time_old = effect_start_time - 1;
    //printf("effect_counter %d timer between %d %d\n",effect_counter,effect_start_time,effect_start_time_old);
    effect_timer_resolution = effect_start_time - effect_start_time_old;
    effect_start_time_old = effect_start_time;
    
    int effect_no = effect->effect;
    if (effect_cut_flag && (skip_mode & SKIP_NORMAL || ctrl_pressed_status)) 
        effect_no = 1;

    int i, amp;
    int width, width2;
    int height, height2;
    SDL_Rect src_rect = screen_rect, dst_rect = screen_rect;
    SDL_Rect quake_rect = screen_rect;

    /* ---------------------------------------- */
    /* Execute effect */
    //printf("Effect number %d %d\n", effect_no, effect_duration );

    bool not_implemented = false;
    switch ( effect_no ){
      case 0: // Instant display
      case 1: // Instant display
        //drawEffect( &src_rect, &src_rect, effect_dst_surface );
        break;

      case 2: // Left shutter
        width = EFFECT_STRIPE_WIDTH * effect_counter / effect_duration;
        for ( i=0 ; i<screen_width/EFFECT_STRIPE_WIDTH ; i++ ){
            src_rect.x = i * EFFECT_STRIPE_WIDTH;
            src_rect.y = 0;
            src_rect.w = width;
            src_rect.h = screen_height;
            drawEffect(&src_rect, &src_rect, effect_dst_surface);
        }
        break;

      case 3: // Right shutter
        width = EFFECT_STRIPE_WIDTH * effect_counter / effect_duration;
        for ( i=1 ; i<=screen_width/EFFECT_STRIPE_WIDTH ; i++ ){
            src_rect.x = i * EFFECT_STRIPE_WIDTH - width - 1;
            src_rect.y = 0;
            src_rect.w = width;
            src_rect.h = screen_height;
            drawEffect(&src_rect, &src_rect, effect_dst_surface);
        }
        break;

      case 4: // Top shutter
        height = EFFECT_STRIPE_WIDTH * effect_counter / effect_duration;
        for ( i=0 ; i<screen_height/EFFECT_STRIPE_WIDTH ; i++ ){
            src_rect.x = 0;
            src_rect.y = i * EFFECT_STRIPE_WIDTH;
            src_rect.w = screen_width;
            src_rect.h = height;
            drawEffect(&src_rect, &src_rect, effect_dst_surface);
        }
        break;

      case 5: // Bottom shutter
        height = EFFECT_STRIPE_WIDTH * effect_counter / effect_duration;
        for ( i=1 ; i<=screen_height/EFFECT_STRIPE_WIDTH ; i++ ){
            src_rect.x = 0;
            src_rect.y = i * EFFECT_STRIPE_WIDTH - height - 1;
            src_rect.w = screen_width;
            src_rect.h = height;
            drawEffect(&src_rect, &src_rect, effect_dst_surface);
        }
        break;

      case 6: // Left curtain
        width = EFFECT_STRIPE_CURTAIN_WIDTH * effect_counter * 2 / effect_duration;
        for ( i=0 ; i<=screen_width/EFFECT_STRIPE_CURTAIN_WIDTH ; i++ ){
            width2 = width - EFFECT_STRIPE_CURTAIN_WIDTH * EFFECT_STRIPE_CURTAIN_WIDTH * i / screen_width;
            if ( width2 >= 0 ){
                src_rect.x = i * EFFECT_STRIPE_CURTAIN_WIDTH;
                src_rect.y = 0;
                src_rect.w = width2;
                src_rect.h = screen_height;
                drawEffect(&src_rect, &src_rect, effect_dst_surface);
            }
        }
        break;

      case 7: // Right curtain
        width = EFFECT_STRIPE_CURTAIN_WIDTH * effect_counter * 2 / effect_duration;
        for ( i=0 ; i<=screen_width/EFFECT_STRIPE_CURTAIN_WIDTH ; i++ ){
            width2 = width - EFFECT_STRIPE_CURTAIN_WIDTH * EFFECT_STRIPE_CURTAIN_WIDTH * i / screen_width;
            if ( width2 >= 0 ){
                if ( width2 > EFFECT_STRIPE_CURTAIN_WIDTH ) width2 = EFFECT_STRIPE_CURTAIN_WIDTH;
                src_rect.x = screen_width - i * EFFECT_STRIPE_CURTAIN_WIDTH - width2;
                src_rect.y = 0;
                src_rect.w = width2;
                src_rect.h = screen_height;
                drawEffect(&src_rect, &src_rect, effect_dst_surface);
            }
        }
        break;

      case 8: // Top curtain
        height = EFFECT_STRIPE_CURTAIN_WIDTH * effect_counter * 2 / effect_duration;
        for ( i=0 ; i<=screen_height/EFFECT_STRIPE_CURTAIN_WIDTH ; i++ ){
            height2 = height - EFFECT_STRIPE_CURTAIN_WIDTH * EFFECT_STRIPE_CURTAIN_WIDTH * i / screen_height;
            if ( height2 >= 0 ){
                src_rect.x = 0;
                src_rect.y = i * EFFECT_STRIPE_CURTAIN_WIDTH;
                src_rect.w = screen_width;
                src_rect.h = height2;
                drawEffect(&src_rect, &src_rect, effect_dst_surface);
            }
        }
        break;

      case 9: // Bottom curtain
        height = EFFECT_STRIPE_CURTAIN_WIDTH * effect_counter * 2 / effect_duration;
        for ( i=0 ; i<=screen_height/EFFECT_STRIPE_CURTAIN_WIDTH ; i++ ){
            height2 = height - EFFECT_STRIPE_CURTAIN_WIDTH * EFFECT_STRIPE_CURTAIN_WIDTH * i / screen_height;
            if ( height2 >= 0 ){
                src_rect.x = 0;
                src_rect.y = screen_height - i * EFFECT_STRIPE_CURTAIN_WIDTH - height2;
                src_rect.w = screen_width;
                src_rect.h = height2;
                drawEffect(&src_rect, &src_rect, effect_dst_surface);
            }
        }
        break;

      default:
        not_implemented = true;
        
      case 10: // Cross fade
        height = 256 * effect_counter / effect_duration;
        alphaBlend( NULL, ALPHA_BLEND_CONST, height, &dirty_rect.bounding_box );
        break;
        
      case 11: // Left scroll
        width = screen_width * effect_counter / effect_duration;
        src_rect.x = 0;
        dst_rect.x = width;
        src_rect.y = dst_rect.y = 0;
        src_rect.w = dst_rect.w = screen_width - width;
        src_rect.h = dst_rect.h = screen_height;
        drawEffect(&dst_rect, &src_rect, effect_src_surface);
        
        src_rect.x = screen_width - width - 1;
        dst_rect.x = 0;
        src_rect.y = dst_rect.y = 0;
        src_rect.w = dst_rect.w = width;
        src_rect.h = dst_rect.h = screen_height;
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);
        break;

      case 12: // Right scroll
        width = screen_width * effect_counter / effect_duration;
        src_rect.x = width;
        dst_rect.x = 0;
        src_rect.y = dst_rect.y = 0;
        src_rect.w = dst_rect.w = screen_width - width;
        src_rect.h = dst_rect.h = screen_height;
        drawEffect(&dst_rect, &src_rect, effect_src_surface);

        src_rect.x = 0;
        dst_rect.x = screen_width - width - 1;
        src_rect.y = dst_rect.y = 0;
        src_rect.w = dst_rect.w = width;
        src_rect.h = dst_rect.h = screen_height;
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);
        break;

      case 13: // Top scroll
        width = screen_height * effect_counter / effect_duration;
        src_rect.x = dst_rect.x = 0;
        src_rect.y = 0;
        dst_rect.y = width;
        src_rect.w = dst_rect.w = screen_width;
        src_rect.h = dst_rect.h = screen_height - width;
        drawEffect(&dst_rect, &src_rect, effect_src_surface);

        src_rect.x = dst_rect.x = 0;
        src_rect.y = screen_height - width - 1;
        dst_rect.y = 0;
        src_rect.w = dst_rect.w = screen_width;
        src_rect.h = dst_rect.h = width;
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);
        break;

      case 14: // Bottom scroll
        width = screen_height * effect_counter / effect_duration;
        src_rect.x = dst_rect.x = 0;
        src_rect.y = width;
        dst_rect.y = 0;
        src_rect.w = dst_rect.w = screen_width;
        src_rect.h = dst_rect.h = screen_height - width;
        drawEffect(&dst_rect, &src_rect, effect_src_surface);

        src_rect.x = dst_rect.x = 0;
        src_rect.y = 0;
        dst_rect.y = screen_height - width - 1;
        src_rect.w = dst_rect.w = screen_width;
        src_rect.h = dst_rect.h = width;
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);
        break;

      case 15: // Fade with mask
        alphaBlend( effect->anim.image_surface, ALPHA_BLEND_FADE_MASK, 256 * effect_counter / effect_duration, &dirty_rect.bounding_box );
        break;

      case 16: // Mosaic out
        generateMosaic( effect_src_surface, 5 - 6 * effect_counter / effect_duration );
        break;
        
      case 17: // Mosaic in
        generateMosaic( effect_dst_surface, 6 * effect_counter / effect_duration );
        break;
        
      case 18: // Cross fade with mask
        alphaBlend( effect->anim.image_surface, ALPHA_BLEND_CROSSFADE_MASK, 256 * effect_counter * 2 / effect_duration, &dirty_rect.bounding_box );
        break;

      case (MAX_EFFECT_NUM + 0): // quakey
        if ( effect_timer_resolution > effect_duration / 4 / effect->no )
            effect_timer_resolution = effect_duration / 4 / effect->no;
        amp = (Sint16)(sin(M_PI * 2.0 * effect->no * effect_counter / effect_duration) *
                              EFFECT_QUAKE_AMP * effect->no * (effect_duration -  effect_counter) / effect_duration);
        dst_rect.x = 0;
        dst_rect.y = amp;
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);

        if (amp >= 0){
            quake_rect.y = 0;
            quake_rect.h = amp;
        }
        else{
            quake_rect.y = screen_height + amp;
            quake_rect.h = -amp;
        }
        SDL_FillRect( accumulation_surface, &quake_rect, SDL_MapRGBA( accumulation_surface->format, 0, 0, 0, 0xff ) );
        break;
        
      case (MAX_EFFECT_NUM + 1): // quakex
        if ( effect_timer_resolution > effect_duration / 4 / effect->no )
            effect_timer_resolution = effect_duration / 4 / effect->no;
        amp = (Sint16)(sin(M_PI * 2.0 * effect->no * effect_counter / effect_duration) *
                              EFFECT_QUAKE_AMP * effect->no * (effect_duration -  effect_counter) / effect_duration);
        dst_rect.x = amp;
        dst_rect.y = 0;
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);

        if (amp >= 0){
            quake_rect.x = 0;
            quake_rect.w = amp;
        }
        else{
            quake_rect.x = screen_width + amp;
            quake_rect.w = -amp;
        }
        SDL_FillRect( accumulation_surface, &quake_rect, SDL_MapRGBA( accumulation_surface->format, 0, 0, 0, 0xff ) );
        break;
        
      case (MAX_EFFECT_NUM + 2): // quake
        dst_rect.x = effect->no*((int)(3.0*rand()/(RAND_MAX+1.0)) - 1) * 2;
        dst_rect.y = effect->no*((int)(3.0*rand()/(RAND_MAX+1.0)) - 1) * 2;
        SDL_FillRect( accumulation_surface, NULL, SDL_MapRGBA( accumulation_surface->format, 0, 0, 0, 0xff ) );
        drawEffect(&dst_rect, &src_rect, effect_dst_surface);
        break;

      case 99: // dll-based
        if (effect->anim.image_name != NULL){
            if (!strncmp(effect->anim.image_name, "breakup.dll", 11)){
                effectBreakup(effect->anim.image_name, effect_duration);
            } else {
                // do crossfade
                height = 256 * effect_counter / effect_duration;
                alphaBlend( NULL, ALPHA_BLEND_CONST, height, &dirty_rect.bounding_box );
                not_implemented = true;
            }
        } else { //just in case no dll is given
            // do crossfade
            height = 256 * effect_counter / effect_duration;
            alphaBlend( NULL, ALPHA_BLEND_CONST, height, &dirty_rect.bounding_box );
            not_implemented = true;
        }
        break;
    }

    if (effect_counter == 0 && not_implemented)
        printf("effect No. %d not implemented; substituting crossfade\n", effect_no);

    //printf("effect conut %d / dur %d\n", effect_counter, effect_duration);
    
    effect_counter += effect_timer_resolution;

    event_mode = WAIT_INPUT_MODE;
    waitEvent(0);
    if ( !((automode_flag || autoclick_time > 0) ||
           (usewheel_flag  && current_button_state.button == -5) ||
           (!usewheel_flag && current_button_state.button == -2)) ){
        effect_counter = effect_duration; // interrupted
    }

    if ( effect_counter < effect_duration && effect_no != 1 ){
        if ( effect_no != 0 ) flush( REFRESH_NONE_MODE, NULL, false );
    
        return true;
    }
    else{
        SDL_BlitSurface( effect_dst_surface, &dirty_rect.bounding_box, accumulation_surface, &dirty_rect.bounding_box );

        if ( effect_no != 0 ) flush(REFRESH_NONE_MODE, NULL, clear_dirty_region);
        if ( effect_no == 1 ) effect_counter = 0;
        skip_mode &= ~SKIP_TO_EOL;

        event_mode = IDLE_EVENT_MODE;
        if (effect_blank != 0 && effect_counter != 0)
            waitEvent(effect_blank);
        
        return false;
    }
}

void ONScripter::drawEffect(SDL_Rect *dst_rect, SDL_Rect *src_rect, SDL_Surface *surface)
{
    SDL_Rect clipped_rect;
    if (AnimationInfo::doClipping(dst_rect, &dirty_rect.bounding_box, &clipped_rect)) return;
    if (src_rect != dst_rect){
        src_rect->x += clipped_rect.x;
        src_rect->y += clipped_rect.y;
        src_rect->w = clipped_rect.w;
        src_rect->h = clipped_rect.h;
    }
    
    SDL_BlitSurface(surface, src_rect, accumulation_surface, dst_rect);
}

void ONScripter::generateMosaic( SDL_Surface *src_surface, int level )
{
    int i, j, ii, jj;
    int width = 160;
    for ( i=0 ; i<level ; i++ ) width >>= 1;

#if defined(BPP16)
    int total_width = accumulation_surface->pitch / 2;
#else
    int total_width = accumulation_surface->pitch / 4;
#endif    
    SDL_LockSurface( src_surface );
    SDL_LockSurface( accumulation_surface );
    ONSBuf *src_buffer = (ONSBuf *)src_surface->pixels;

    for ( i=screen_height-1 ; i>=0 ; i-=width ){
        for ( j=0 ; j<screen_width ; j+=width ){
            ONSBuf p = src_buffer[ i*total_width+j ];
            ONSBuf *dst_buffer = (ONSBuf *)accumulation_surface->pixels + i*total_width + j;

            int height2 = width;
            if (i+1-width < 0) height2 = i+1;
            int width2 = width;
            if (j+width > screen_width) width2 = screen_width - j;
            for ( ii=height2 ; ii!=0 ; ii-- ){
                for ( jj=width2 ; jj!=0 ; jj-- ){
                    *dst_buffer++ = p;
                }
                dst_buffer -= total_width + width2;
            }
        }
    }
    
    SDL_UnlockSurface( accumulation_surface );
    SDL_UnlockSurface( src_surface );
}
