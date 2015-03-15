/* -*- C++ -*-
 * 
 *  ONScripter_text.cpp - Text parser of ONScripter
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

#include "utf8_decode.h"
#include "ONScripter.h"

#define IS_ROTATION_REQUIRED(x)	\
    (ScriptDecoder::isOneByte(*x) ||                                    \
     (*(x) == (char)0x81 && *((x)+1) == (char)0x50) ||                  \
     (*(x) == (char)0x81 && *((x)+1) == (char)0x51) ||                  \
     (*(x) == (char)0x81 && *((x)+1) >= 0x5b && *((x)+1) <= 0x5d) ||    \
     (*(x) == (char)0x81 && *((x)+1) >= 0x60 && *((x)+1) <= 0x64) ||    \
     (*(x) == (char)0x81 && *((x)+1) >= 0x69 && *((x)+1) <= 0x7a) ||    \
     (*(x) == (char)0x81 && *((x)+1) == (char)0x80) )

#define IS_TRANSLATION_REQUIRED(x)	\
        ( *(x) == (char)0x81 && *((x)+1) >= 0x41 && *((x)+1) <= 0x44 )

void ONScripter::shiftHalfPixelX(SDL_Surface *surface)
{
    SDL_LockSurface( surface );
    unsigned char *buf = (unsigned char*)surface->pixels;
    for (int i=surface->h ; i!=0 ; i--){
        unsigned char c = buf[0];
        for (int j=1 ; j<surface->w ; j++){
            buf[j-1] = (buf[j]+c)>>1;
            c = buf[j];
        }
        buf[surface->w-1] = c>>1;
        buf += surface->pitch;
    }
    SDL_UnlockSurface( surface );
}

void ONScripter::shiftHalfPixelY(SDL_Surface *surface)
{
    SDL_LockSurface( surface );
    for (int j=surface->w-1 ; j>=0 ; j--){
        unsigned char *buf = (unsigned char*)surface->pixels + j;
        unsigned char c = buf[0];
        for (int i=1 ; i<surface->h ; i++){
            buf += surface->pitch;
            *(buf-surface->pitch) = (*buf+c)>>1;
            c = *buf;
        }
        *buf = c>>1;
    }
    SDL_UnlockSurface( surface );
}

void ONScripter::drawGlyph( SDL_Surface *dst_surface, FontInfo *info, SDL_Color &color, char* text, int xy[2], bool shadow_flag, AnimationInfo *cache_info, SDL_Rect *clip, SDL_Rect &dst_rect, ScriptDecoder* decoder )
{
    unsigned index = ((unsigned char*)text)[0];
    index = index << 8 | ((unsigned char*)text)[1];
    unsigned short unicode = decoder->convertNextChar(text);

    int minx, maxx, miny, maxy, advanced;
#if 0
    if (TTF_GetFontStyle( (TTF_Font*)info->ttf_font[0] ) !=
        (info->is_bold?TTF_STYLE_BOLD:TTF_STYLE_NORMAL) )
        TTF_SetFontStyle( (TTF_Font*)info->ttf_font[0], (info->is_bold?TTF_STYLE_BOLD:TTF_STYLE_NORMAL));
#endif    
    TTF_GlyphMetrics( (TTF_Font*)info->ttf_font[0], unicode,
                      &minx, &maxx, &miny, &maxy, &advanced );
    //printf("min %d %d %d %d %d %d\n", minx, maxx, miny, maxy, advanced,TTF_FontAscent((TTF_Font*)info->ttf_font[0])  );

    // Use the glyth's advance for non-Japanese characters
    if (!ScriptDecoder::isOneByte(text[0]) && decoder->isMonospaced()) {
        info->addMonospacedCharacterAdvance();
    } else {
        info->addProportionalCharacterAdvance(advanced);
    }

    static SDL_Color fcol={0xff, 0xff, 0xff}, bcol={0, 0, 0};
    SDL_Surface *tmp_surface = TTF_RenderGlyph_Shaded((TTF_Font*)info->ttf_font[0], unicode, fcol, bcol);
    
    SDL_Surface *tmp_surface_s = tmp_surface;
    if (shadow_flag && render_font_outline){
        tmp_surface_s = TTF_RenderGlyph_Shaded((TTF_Font*)info->ttf_font[1], unicode, fcol, bcol);
        if (tmp_surface && tmp_surface_s){
            if ((tmp_surface_s->w-tmp_surface->w) & 1) shiftHalfPixelX(tmp_surface_s);
            if ((tmp_surface_s->h-tmp_surface->h) & 1) shiftHalfPixelY(tmp_surface_s);
        }
    }

    bool rotate_flag = false;
    if ( info->getTateyokoMode() == FontInfo::TATE_MODE && IS_ROTATION_REQUIRED(text) ) rotate_flag = true;
    
    dst_rect.x = xy[0] + minx;
    dst_rect.y = xy[1] + TTF_FontAscent((TTF_Font*)info->ttf_font[0]) - maxy;
    dst_rect.y -= (TTF_FontHeight((TTF_Font*)info->ttf_font[0]) - info->font_size_xy[1]*screen_ratio1/screen_ratio2)/2;

    if ( rotate_flag ) dst_rect.x += miny - minx;
        
    if ( info->getTateyokoMode() == FontInfo::TATE_MODE && IS_TRANSLATION_REQUIRED(text) ){
        dst_rect.x += info->font_size_xy[0]/2;
        dst_rect.y -= info->font_size_xy[0]/2;
    }

    if (shadow_flag && tmp_surface_s){
        SDL_Rect dst_rect_s = dst_rect;
        if (render_font_outline){
            dst_rect_s.x -= (tmp_surface_s->w - tmp_surface->w)/2;
            dst_rect_s.y -= (tmp_surface_s->h - tmp_surface->h)/2;
        }
        else{
            dst_rect_s.x += shade_distance[0];
            dst_rect_s.y += shade_distance[1];
        }

        if (rotate_flag){
            dst_rect_s.w = tmp_surface_s->h;
            dst_rect_s.h = tmp_surface_s->w;
        }
        else{
            dst_rect_s.w = tmp_surface_s->w;
            dst_rect_s.h = tmp_surface_s->h;
        }

        if (cache_info)
            cache_info->blendText( tmp_surface_s, dst_rect_s.x, dst_rect_s.y, bcol, clip, rotate_flag );
        
        if (dst_surface)
            alphaBlendText( dst_surface, dst_rect_s, tmp_surface_s, bcol, clip, rotate_flag );
    }

    if ( tmp_surface ){
        if (rotate_flag){
            dst_rect.w = tmp_surface->h;
            dst_rect.h = tmp_surface->w;
        }
        else{
            dst_rect.w = tmp_surface->w;
            dst_rect.h = tmp_surface->h;
        }

        if (cache_info)
            cache_info->blendText( tmp_surface, dst_rect.x, dst_rect.y, color, clip, rotate_flag );
        
        if (dst_surface)
            alphaBlendText( dst_surface, dst_rect, tmp_surface, color, clip, rotate_flag );
    }

    if (tmp_surface_s && tmp_surface_s != tmp_surface)
        SDL_FreeSurface(tmp_surface_s);
    if (tmp_surface)
        SDL_FreeSurface(tmp_surface);
}

void ONScripter::drawChar( char* text, FontInfo *info, bool flush_flag, bool lookback_flag, SDL_Surface *surface, AnimationInfo *cache_info, SDL_Rect *clip, ScriptDecoder* decoder )
{
    //printf("draw %x-%x[%s] %d, %d\n", text[0], text[1], text, info->xy[0], info->xy[1] );
    
    if (!decoder) decoder = script_h.decoder;

    if ( info->ttf_font[0] == NULL ){
        if ( info->openFont( font_file, screen_ratio1, screen_ratio2 ) == NULL ){
            loge( stderr, "can't open font file: %s\n", font_file );
            quit();
            exit(-1);
        }
    }
#if defined(PSP)
    else
        info->openFont( font_file, screen_ratio1, screen_ratio2 );
#endif

    if ( info->isEndOfLine() ){
        info->newLine();
        for (int i=0 ; i<indent_offset ; i++){
            if (lookback_flag){
#ifdef ANDROID
                current_page->add(script_h.getSystemLanguageText()->get_space_char()[0]);
                current_page->add(script_h.getSystemLanguageText()->get_space_char()[1]);
#else
                current_page->add(0x81);
                current_page->add(0x40);
#endif
            }
            info->advanceCharInHankaku(2);
        }
    }

    info->old_xy[0] = info->x();
    info->old_xy[1] = info->y();

    char text2[3] = {text[0], 0, 0};
    int numBytes = decoder->getNumBytes(text[0]);
    if (numBytes >= 2) {
        text2[1] = text[1];
        if (numBytes == 3) {
            text2[2] = text[2];
        }
    }

    for (int i=0 ; i<2 ; i++){
        int xy[2];
        xy[0] = info->x() * screen_ratio1 / screen_ratio2;
        xy[1] = info->y() * screen_ratio1 / screen_ratio2;
    
        SDL_Color color = {info->color[0], info->color[1], info->color[2]};
        SDL_Rect dst_rect;
        drawGlyph( surface, info, color, text2, xy, info->is_shadow, cache_info, clip, dst_rect, decoder );

        if ( surface == accumulation_surface &&
             !flush_flag &&
             (!clip || AnimationInfo::doClipping( &dst_rect, clip ) == 0) ){
            dirty_rect.add( dst_rect );
        }
        else if ( flush_flag ){
            if (info->is_shadow){
                if (render_font_outline)
                    info->addShadeArea(dst_rect, -1, -1, 2, 2);
                else
                    info->addShadeArea(dst_rect, 0, 0, shade_distance[0], shade_distance[1]);
            }
            flushDirect( dst_rect, REFRESH_NONE_MODE );
        }

        if (decoder->getNumBytes(text[0]) >= 2) {
            info->advanceCharInHankaku(2);
            break;
        }
        info->advanceCharInHankaku(1);
        text2[0] = text[1];
        if (text2[0] == 0) break;
    }

    if ( lookback_flag ){
        current_page->add( text[0] );
        if (text[1]) current_page->add( text[1] );
    }
}

void ONScripter::drawString( const char *str, uchar3 color, FontInfo *info, bool flush_flag, SDL_Surface *surface, SDL_Rect *rect, AnimationInfo *cache_info, bool single_line, ScriptDecoder* decoder )
{
    if (!decoder && *str) {
        ScriptDecoder* decoders[2] = { script_h.getSystemLanguageText()->decoder, script_h.decoder };
        decoder = ScriptDecoder::chooseDecoderForTextFromList(str, decoders, 2);
        if (!decoder) {
            logw(stderr, "drawString: Cannot detect a decoder provided from system and script. Choosing script...");
            decoder = script_h.decoder;
        }
    }

    int i;

    int start_xy[2];
    start_xy[0] = info->xy[0];
    start_xy[1] = info->xy[1];

    /* ---------------------------------------- */
    /* Draw selected characters */
    uchar3 org_color;
    for ( i=0 ; i<3 ; i++ ) org_color[i] = info->color[i];
    for ( i=0 ; i<3 ; i++ ) info->color[i] = color[i];

    bool skip_whitespace_flag = true;
    char text[3] = { '\0', '\0', '\0' };
    while( *str ){
    #ifndef ENABLE_KOREAN
        while (*str == ' ' && skip_whitespace_flag) str++;
    #endif

#ifdef ENABLE_1BYTE_CHAR
        if ( *str == '`' ){
            str++;
            skip_whitespace_flag = false;
            continue;
        }
#endif            

#ifndef FORCE_1BYTE_CHAR            
        if (cache_info && !cache_info->is_tight_region){
            if (*str == '('){
                startRuby(str+1, *info);
                str++;
                continue;
            }
            else if (*str == '/' && ruby_struct.stage == RubyStruct::BODY ){
                info->addLineOffset(ruby_struct.margin);
                str = ruby_struct.ruby_end;
                if (*ruby_struct.ruby_end == ')'){
                    endRuby(false, false, NULL, cache_info);
                    str++;
                }
                continue;
            }
            else if (*str == ')' && ruby_struct.stage == RubyStruct::BODY ){
                ruby_struct.stage = RubyStruct::NONE;
                str++;
                continue;
            }
            else if (*str == '<'){
                str++;
                int no = 0;
                while(*str>='0' && *str<='9')
                    no=no*10+(*str++)-'0';
                in_textbtn_flag = true;
                continue;
            }
            else if (*str == '>' && in_textbtn_flag){
                str++;
                in_textbtn_flag = false;
                continue;
            }
        }
#endif
        if (*str) {
            if (ScriptDecoder::isOneByte(*str)) {
                if (*str == 0x0a || (*str == '\\' && info->is_newline_accepted)){
                    if (single_line) break;
                    info->newLine();
                    str++;
                }
                else {
                    text[0] = *str++;
                    if (*str && *str != 0x0a && *str != '`' && ScriptDecoder::isOneByte(*str)) text[1] = *str++;
                    else                                                        text[1] = 0;
                    if (single_line && info->isEndOfLine()) break;
                    drawChar( text, info, false, false, surface, cache_info, NULL, decoder );
                }
            } else {
                if ( checkLineBreak( str, info ) ){
                    if (single_line) break;
                    info->newLine();
                    for (int i=0 ; i<indent_offset ; i++)
                        info->advanceCharInHankaku(2);
                }

                text[0] = *str++;
                text[1] = *str++;
                if (decoder->getNumBytes(text[0]) == 3) text[2] = *str++;
                drawChar( text, info, false, false, surface, cache_info, NULL, decoder );
            }
        }
    }
    for ( i=0 ; i<3 ; i++ ) info->color[i] = org_color[i];

    /* ---------------------------------------- */
    /* Calculate the area of selection */
    SDL_Rect clipped_rect = info->calcUpdatedArea(start_xy, screen_ratio1, screen_ratio2);

    SDL_Rect scaled_clipped_rect;
    scaled_clipped_rect.x = clipped_rect.x * screen_ratio1 / screen_ratio2;
    scaled_clipped_rect.y = clipped_rect.y * screen_ratio1 / screen_ratio2;
    scaled_clipped_rect.w = clipped_rect.w * screen_ratio1 / screen_ratio2;
    scaled_clipped_rect.h = clipped_rect.h * screen_ratio1 / screen_ratio2;

    if (info->is_shadow){
        if (render_font_outline)
            info->addShadeArea(scaled_clipped_rect, -1, -1, 2, 2);
        else
            info->addShadeArea(scaled_clipped_rect, 0, 0, shade_distance[0], shade_distance[1]);
    }
    
    if ( flush_flag )
        flush( refresh_shadow_text_mode, &scaled_clipped_rect );
    
    if ( rect ) *rect = clipped_rect;
}

void ONScripter::restoreTextBuffer(SDL_Surface *surface)
{
    text_info.fill( 0, 0, 0, 0 );

#ifdef ANDROID
    reassureSentenceFontSize();
#endif

    char out_text[3] = { '\0','\0','\0' };
    FontInfo f_info = sentence_font;
    f_info.clear();
    for ( int i=0 ; i<current_page->text_count ; i++ ){
        if ( current_page->text[i] == 0x0a ){
            f_info.newLine();
        }
        else{
            out_text[0] = current_page->text[i];
#ifndef FORCE_1BYTE_CHAR            
            if (out_text[0] == '('){
                startRuby(current_page->text + i + 1, f_info);
                continue;
            }
            else if (out_text[0] == '/' && ruby_struct.stage == RubyStruct::BODY ){
                f_info.addLineOffset(ruby_struct.margin);
                i = ruby_struct.ruby_end - current_page->text - 1;
                if (*ruby_struct.ruby_end == ')'){
                    endRuby(false, false, surface, &text_info);
                    i++;
                }
                continue;
            }
            else if (out_text[0] == ')' && ruby_struct.stage == RubyStruct::BODY ){
                ruby_struct.stage = RubyStruct::NONE;
                continue;
            }
            else if (out_text[0] == '<'){
                int no = 0;
                while(current_page->text[i+1]>='0' && current_page->text[i+1]<='9')
                    no=no*10+current_page->text[(i++)+1]-'0';
                in_textbtn_flag = true;
                continue;
            }
            else if (out_text[0] == '>' && in_textbtn_flag){
                in_textbtn_flag = false;
                continue;
            }
#endif
            if (!ScriptDecoder::isOneByte(out_text[0])) {
                out_text[1] = current_page->text[i+1];
                if (script_h.decoder->getNumBytes(out_text[0]) == 3) {
                    out_text[2] = current_page->text[++i];
                }

                if ( checkLineBreak( current_page->text+i, &f_info ) )
                    f_info.newLine();
                i++;
            }
            else{
                out_text[1] = 0;
            }

#ifdef ENABLE_ENGLISH
            // Scan text for next whitespace to break on if at the end
            bool newLineEarly = false;
            if (out_text[0] == ' ') {
                char* script = current_page->text;
                int index = i + 1;
                int advanced, accum_advance = 0;

                // Scan the next characters till the next space and get the accumulated character advance (word width)
                while(index < current_page->text_count) {
                    advanced = 0;
                    if (f_info.ttf_font[0]) {
                        TTF_GlyphMetrics( (TTF_Font*)f_info.ttf_font[0], script[index], NULL, NULL, NULL, NULL, &advanced );
                    }
                    accum_advance += advanced;
                    if (script[index] == ' ') break;
                    index++;
                }

                // If we draw the next word it will be split to the next line, so make new line before adding word
                newLineEarly = f_info.willBeEndOfLine(accum_advance);
                if (newLineEarly) {
                    f_info.newLine();
                }
            }
            if (!newLineEarly)
#endif
            drawChar( out_text, &f_info, false, false, surface, &text_info );
        }
    }
}

void ONScripter::enterTextDisplayMode(bool text_flag)
{
    if (line_enter_status <= 1 && (!pretextgosub_label || saveon_flag) && internal_saveon_flag && text_flag){
        saveSaveFile(false);
        internal_saveon_flag = false;
    }
    
    if (!(display_mode & DISPLAY_MODE_TEXT)){
        refreshSurface( effect_dst_surface, NULL, refresh_shadow_text_mode );
        dirty_rect.clear();
        dirty_rect.add( sentence_font_info.pos );

        if (setEffect(&window_effect, false, true)) return;
        while(doEffect(&window_effect, false));

        display_mode = DISPLAY_MODE_TEXT;
        text_on_flag = true;
    }
}

void ONScripter::leaveTextDisplayMode(bool force_leave_flag)
{
    if (display_mode & DISPLAY_MODE_TEXT &&
        (force_leave_flag || erase_text_window_mode != 0)){

        SDL_BlitSurface(backup_surface, NULL, effect_dst_surface, NULL);
        dirty_rect.add(sentence_font_info.pos);
            
        if (setEffect(&window_effect, false, false)) return;
        while(doEffect(&window_effect, false));

        display_mode = DISPLAY_MODE_NORMAL;
    }
}

bool ONScripter::doClickEnd()
{
    bool ret = false;
    
    draw_cursor_flag = true;

    if ( automode_flag ){
        event_mode =  WAIT_TEXT_MODE | WAIT_INPUT_MODE | WAIT_VOICE_MODE | WAIT_TIMER_MODE;
        if ( automode_time < 0 )
            ret = waitEvent( -automode_time * num_chars_in_sentence );
        else
            ret = waitEvent( automode_time );
    }
    else if ( autoclick_time > 0 ){
        event_mode = WAIT_TIMER_MODE;
        ret = waitEvent( autoclick_time );
    }
    else{
        event_mode = WAIT_TEXT_MODE | WAIT_INPUT_MODE | WAIT_TIMER_MODE;
        ret = waitEvent(-1);
    }

    num_chars_in_sentence = 0;
    draw_cursor_flag = false;

    return ret;
}

bool ONScripter::clickWait( char *out_text )
{
    flush( REFRESH_NONE_MODE );
    skip_mode &= ~SKIP_TO_EOL;

    if (script_h.checkClickstr(script_h.getStringBuffer() + string_buffer_offset) != 1) string_buffer_offset++;
    string_buffer_offset++;

    if ( (skip_mode & (SKIP_NORMAL | SKIP_TO_EOP) || ctrl_pressed_status) && !textgosub_label ){
        clickstr_state = CLICK_NONE;
        if ( out_text ){
            drawChar( out_text, &sentence_font, false, true, accumulation_surface, &text_info );
        }
        else{ // called on '@'
            flush(refreshMode());
        }
        num_chars_in_sentence = 0;

        event_mode = IDLE_EVENT_MODE;
        if ( waitEvent(0) ) return false;
    }
    else{
        if ( out_text ){
            drawChar( out_text, &sentence_font, true, true, accumulation_surface, &text_info );
            num_chars_in_sentence++;
        }

        while( (!(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR) &&
                script_h.getStringBuffer()[ string_buffer_offset ] == ' ') ||
               script_h.getStringBuffer()[ string_buffer_offset ] == '\t' ) string_buffer_offset ++;

        if ( textgosub_label ){
            saveon_flag = false;

            textgosub_clickstr_state = CLICK_WAIT;
            if (script_h.getStringBuffer()[string_buffer_offset] == 0x0)
                textgosub_clickstr_state |= CLICK_EOL;
            gosubReal( textgosub_label, script_h.getNext(), true );

            event_mode = IDLE_EVENT_MODE;
            waitEvent(0);

            return false;
        }

        // if this is the end of the line, pretext becomes enabled
        if (script_h.getStringBuffer()[string_buffer_offset] == 0x0)
            line_enter_status = 0;

        clickstr_state = CLICK_WAIT;
        if (doClickEnd()) return false;

        clickstr_state = CLICK_NONE;

        if (pagetag_flag) processEOT();
        page_enter_status = 0;
    }

    return true;
}

bool ONScripter::clickNewPage( char *out_text )
{
    if ( out_text ){
        drawChar( out_text, &sentence_font, true, true, accumulation_surface, &text_info );
        num_chars_in_sentence++;
    }

    flush( REFRESH_NONE_MODE );
    skip_mode &= ~SKIP_TO_EOL;
    
    if (script_h.checkClickstr(script_h.getStringBuffer() + string_buffer_offset) != 1) string_buffer_offset++;
    string_buffer_offset++;

    if ( (skip_mode & SKIP_NORMAL || ctrl_pressed_status) && !textgosub_label  ){
        num_chars_in_sentence = 0;
        clickstr_state = CLICK_NEWPAGE;

        event_mode = IDLE_EVENT_MODE;
        if (waitEvent(0)) return false;
    }
    else{
        while( (!(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR) &&
                script_h.getStringBuffer()[ string_buffer_offset ] == ' ') ||
               script_h.getStringBuffer()[ string_buffer_offset ] == '\t' ) string_buffer_offset ++;

        if ( textgosub_label ){
            saveon_flag = false;

            textgosub_clickstr_state = CLICK_NEWPAGE;
            gosubReal( textgosub_label, script_h.getNext(), true );

            event_mode = IDLE_EVENT_MODE;
            waitEvent(0);

            return false;
        }

        // if this is the end of the line, pretext becomes enabled
        if (script_h.getStringBuffer()[string_buffer_offset] == 0x0)
            line_enter_status = 0;

        clickstr_state = CLICK_NEWPAGE;
        if (doClickEnd()) return false;
    }

    newPage();
    clickstr_state = CLICK_NONE;

    return true;
}

void ONScripter::startRuby(const char *buf, FontInfo &info)
{
    ruby_struct.stage = RubyStruct::BODY;
    ruby_font = info;
    ruby_font.ttf_font[0] = NULL;
    ruby_font.ttf_font[1] = NULL;
    if ( ruby_struct.font_size_xy[0] != -1 )
        ruby_font.font_size_xy[0] = ruby_struct.font_size_xy[0];
    else
        ruby_font.font_size_xy[0] = info.font_size_xy[0]/2;
    if ( ruby_struct.font_size_xy[1] != -1 )
        ruby_font.font_size_xy[1] = ruby_struct.font_size_xy[1];
    else
        ruby_font.font_size_xy[1] = info.font_size_xy[1]/2;
                
    ruby_struct.body_count = 0;
    ruby_struct.ruby_count = 0;

    while(1){
        if ( *buf == '/' ){
            ruby_struct.stage = RubyStruct::RUBY;
            ruby_struct.ruby_start = buf+1;
        }
        else if ( *buf == ')' || *buf == '\0' ){
            break;
        }
        else{
            if ( ruby_struct.stage == RubyStruct::BODY )
                ruby_struct.body_count++;
            else if ( ruby_struct.stage == RubyStruct::RUBY )
                ruby_struct.ruby_count++;
        }
        buf++;
    }
    ruby_struct.ruby_end = buf;
    ruby_struct.stage = RubyStruct::BODY;
    ruby_struct.margin = ruby_font.initRuby(info, ruby_struct.body_count/2, ruby_struct.ruby_count/2);
}

void ONScripter::endRuby(bool flush_flag, bool lookback_flag, SDL_Surface *surface, AnimationInfo *cache_info)
{
    char out_text[3]= {'\0', '\0', '\0'};
    if ( sentence_font.rubyon_flag ){
        ruby_font.clear();
        const char *buf = ruby_struct.ruby_start;
        while( buf < ruby_struct.ruby_end ){
            out_text[0] = *buf;
            out_text[1] = *(buf+1);
            drawChar( out_text, &ruby_font, flush_flag, lookback_flag, surface, cache_info );
            buf+=2;
        }
    }
    ruby_struct.stage = RubyStruct::NONE;
}

int ONScripter::textCommand()
{
    if (line_enter_status <= 1 && (!pretextgosub_label || saveon_flag) && internal_saveon_flag){
        saveSaveFile(false);
        internal_saveon_flag = false;
    }

    char *buf = script_h.getStringBuffer();

    bool tag_flag = true;
    if (buf[string_buffer_offset] == '[')
        string_buffer_offset++;
    else if (zenkakko_flag && 
    #ifdef ENABLE_KOREAN
            buf[string_buffer_offset  ] == "¡¼"[0] &&
            buf[string_buffer_offset+1] == "¡¼"[1])
    #else
             buf[string_buffer_offset  ] == "y"[0] && 
             buf[string_buffer_offset+1] == "y"[1])
    #endif
        string_buffer_offset += 2;
    else
        tag_flag = false;

    int start_offset = string_buffer_offset;
    int end_offset = start_offset;
    while (tag_flag && buf[string_buffer_offset]){
        if (zenkakko_flag && 
        #ifdef ENABLE_KOREAN
            buf[string_buffer_offset  ] == "¡¼"[0] &&
            buf[string_buffer_offset+1] == "¡¼"[1]){
        #else
            buf[string_buffer_offset  ] == "z"[0] && 
            buf[string_buffer_offset+1] == "z"[1]){
        #endif
            end_offset = string_buffer_offset;
            string_buffer_offset += 2;
            break;
        }
        else if (buf[string_buffer_offset] == ']'){
            end_offset = string_buffer_offset;
            string_buffer_offset++;
            break;
        }
        else
            string_buffer_offset += script_h.decoder->getNumBytes(buf[string_buffer_offset]);
    }

    if (pretextgosub_label && 
        (!pagetag_flag || page_enter_status == 0) &&
        line_enter_status == 0){

        if (current_page->tag) delete[] current_page->tag;
        if (end_offset > start_offset){
            int len = end_offset - start_offset;
            current_page->tag = new char[len+1];
            memcpy(current_page->tag, buf + start_offset, len);
            current_page->tag[len] = 0;
        }
        else{
            current_page->tag = NULL;
        }

        saveon_flag = false;
        gosubReal( pretextgosub_label, script_h.getCurrent() );
        line_enter_status = 1;

        return RET_CONTINUE;
    }

    enterTextDisplayMode();

    line_enter_status = 2;
    if (pagetag_flag) page_enter_status = 1;

    while(processText());

    return RET_CONTINUE;
}

bool ONScripter::checkLineBreak(const char *buf, FontInfo *fi)
{
    if (!is_kinsoku) return false;
    
    // check start kinsoku
    if (isStartKinsoku( buf+2 )){
        const char *buf2 = buf;
        int i = 2;
        while (!fi->isEndOfLine(i)){
            if      ( buf2[i+2] == 0x0a || buf2[i+2] == 0 ) break;
            else if ( ScriptDecoder::isOneByte(buf2[i+2]) ) buf2++;
            else if ( isStartKinsoku( buf2+i+2 ) ) i += 2;
            else break;
        }

        if (fi->isEndOfLine(i)) return true;
    }
        
    // check end kinsoku
    if (isEndKinsoku( buf )){
        const char *buf2 = buf;
        int i = 2;
        while (!fi->isEndOfLine(i)){
            if      ( buf2[i+2] == 0x0a || buf2[i+2] == 0 ) break;
            else if ( ScriptDecoder::isOneByte(buf2[i+2]) ) buf2++;
            else if ( isEndKinsoku( buf2+i ) ) i += 2;
            else break;
        }

        if (fi->isEndOfLine(i)) return true;
    }

    return false;
}

void ONScripter::processEOT()
{
    if ( skip_mode & SKIP_TO_EOL ){
        flush( refreshMode() );
        skip_mode &= ~SKIP_TO_EOL;
    }

    if (!sentence_font.isLineEmpty() && !new_line_skip_flag){
        // if sentence_font.isLineEmpty() is true, newPage() might be already issued
        if (!sentence_font.isEndOfLine()) current_page->add( 0x0a );
        sentence_font.newLine();
    }

    if (!new_line_skip_flag && !pagetag_flag) line_enter_status = 0;
}

bool ONScripter::processText()
{
    //printf("textCommand %c %d %d %d\n", script_h.getStringBuffer()[ string_buffer_offset ], string_buffer_offset, event_mode, line_enter_status);
    char out_text[3]= {'\0', '\0', '\0'};

    //printf("*** textCommand %d (%d,%d)\n", string_buffer_offset, sentence_font.xy[0], sentence_font.xy[1]);

    while( (!(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR) &&
            script_h.getStringBuffer()[ string_buffer_offset ] == ' ') ||
           script_h.getStringBuffer()[ string_buffer_offset ] == '\t' ) string_buffer_offset ++;

    if (script_h.getStringBuffer()[string_buffer_offset] == 0x00){
#ifdef ENABLE_ENGLISH
        // Check next line for English text and see if it is lowercase then skip new line
        char* next = script_h.getNext() + 1;
        if (next[0] == '`') {       // Using 1 Byte English text
            if (next[1] == ' ') {
                // See if the first non-spaced character is lowercase, if it is then skip new line
                int i = 1;
                while(next[i] == ' ') i++;
                if (islower(next[i])) {
                    // Check for the next space in new line after text and see if it fits in previous line
                    int advanced, accum_advance = 0;
                    while(next[i] != ' ') {
                        advanced = 0;
                        if (sentence_font.ttf_font[0]) {
                            TTF_GlyphMetrics( (TTF_Font*)sentence_font.ttf_font[0], next[i], NULL, NULL, NULL, NULL, &advanced );
                        }
                        accum_advance += advanced;
                        i++;
                    }
                    if (!sentence_font.willBeEndOfLine(accum_advance)) {
                        new_line_skip_flag = true;
                    }
                }
            } else if (islower(next[1])) {
                // Check for the next space in new line and see if it fits in previous line
                int i = 0, accum_advance = 0, advanced;
                while(next[i + 1] != ' ') {
                    advanced = 0;
                    if (sentence_font.ttf_font[0]) {
                        TTF_GlyphMetrics( (TTF_Font*)sentence_font.ttf_font[0], next[i + 1], NULL, NULL, NULL, NULL, &advanced );
                    }
                    accum_advance += advanced;
                    i++;
                }
                if (!sentence_font.willBeEndOfLine(accum_advance)) {
                    // Add a space because there will not be one later
                    out_text[0] = ' ';
                    drawChar( out_text, &sentence_font, true, true, accumulation_surface, &text_info );
                    new_line_skip_flag = true;
                }
            }
        }
#endif
        processEOT();
        return false;
    }

    new_line_skip_flag = false;
    
    char ch = script_h.getStringBuffer()[string_buffer_offset];

    if ( !ScriptDecoder::isOneByte(ch) ){
        /* ---------------------------------------- */
        /* Kinsoku process */
        if ( checkLineBreak( script_h.getStringBuffer() + string_buffer_offset, &sentence_font ) ){
            sentence_font.newLine();
            for (int i=0 ; i<indent_offset ; i++){
#ifdef ANDROID
                current_page->add(script_h.getSystemLanguageText()->get_space_char()[0]);
                current_page->add(script_h.getSystemLanguageText()->get_space_char()[1]);
#else
                current_page->add(0x81);
                current_page->add(0x40);
#endif
                sentence_font.advanceCharInHankaku(2);
            }
        }
        
        out_text[0] = script_h.getStringBuffer()[string_buffer_offset];
        out_text[1] = script_h.getStringBuffer()[string_buffer_offset+1];

        if (script_h.decoder->getNumBytes(ch) == 3) {
            out_text[2] = script_h.getStringBuffer()[string_buffer_offset + 2];
        }

        if (script_h.checkClickstr(&script_h.getStringBuffer()[string_buffer_offset]) > 0){
            if (sentence_font.getRemainingLine() <= clickstr_line)
                return clickNewPage( out_text );
            else
                return clickWait( out_text );
        }
        else{
            clickstr_state = CLICK_NONE;
        }

        if ( skip_mode || ctrl_pressed_status ){
            drawChar( out_text, &sentence_font, false, true, accumulation_surface, &text_info );
        }
        else{
            drawChar( out_text, &sentence_font, true, true, accumulation_surface, &text_info );

            event_mode = WAIT_TIMER_MODE | WAIT_INPUT_MODE;
            if ( sentence_font.wait_time == -1 )
                waitEvent( default_text_speed[text_speed_no] );
            else
                waitEvent( sentence_font.wait_time );
        }

        num_chars_in_sentence++;

        string_buffer_offset += script_h.decoder->getNumBytes(ch);

        return true;
    }
    else if ( ch == '@' ){ // wait for click
        return clickWait( NULL );
    }
    else if ( ch == '\\' ){ // new page
        return clickNewPage( NULL );
    }
    else if ( ch == '_' ){ // Ignore an immediate click wait
        string_buffer_offset++;

        int matched_len = script_h.checkClickstr(script_h.getStringBuffer() + string_buffer_offset, true);
        if (matched_len > 0){
            out_text[0] = script_h.getStringBuffer()[string_buffer_offset];
            if (out_text[0] != '@' && out_text[0] != '\\'){
                if (matched_len == 2)
                    out_text[1] = script_h.getStringBuffer()[string_buffer_offset+1];
                bool flush_flag = true;
                if ( skip_mode || ctrl_pressed_status ) flush_flag = false;
                drawChar( out_text, &sentence_font, flush_flag, true, accumulation_surface, &text_info );
            }
            string_buffer_offset += matched_len;
        }
        
        return true;
    }
    else if ( ch == '!'){
        string_buffer_offset++;
        if ( script_h.getStringBuffer()[ string_buffer_offset ] == 's' ){
            string_buffer_offset++;
            if ( script_h.getStringBuffer()[ string_buffer_offset ] == 'd' ){
                sentence_font.wait_time = -1;
                string_buffer_offset++;
            }
            else{
                int t = 0;
                while( script_h.getStringBuffer()[ string_buffer_offset ] >= '0' &&
                       script_h.getStringBuffer()[ string_buffer_offset ] <= '9' ){
                    t = t*10 + script_h.getStringBuffer()[ string_buffer_offset ] - '0';
                    string_buffer_offset++;
                }
                sentence_font.wait_time = t;
                while (script_h.getStringBuffer()[ string_buffer_offset ] == ' ' ||
                       script_h.getStringBuffer()[ string_buffer_offset ] == '\t') string_buffer_offset++;
            }
        }
        else if ( script_h.getStringBuffer()[ string_buffer_offset ] == 'w' ||
                  script_h.getStringBuffer()[ string_buffer_offset ] == 'd' ){
            bool flag = false;
            if ( script_h.getStringBuffer()[ string_buffer_offset ] == 'd' ) flag = true;
            string_buffer_offset++;
            int t = 0;
            while( script_h.getStringBuffer()[ string_buffer_offset ] >= '0' &&
                   script_h.getStringBuffer()[ string_buffer_offset ] <= '9' ){
                t = t*10 + script_h.getStringBuffer()[ string_buffer_offset ] - '0';
                string_buffer_offset++;
            }
            while (script_h.getStringBuffer()[ string_buffer_offset ] == ' ' ||
                   script_h.getStringBuffer()[ string_buffer_offset ] == '\t') string_buffer_offset++;
            if (!skip_mode && !ctrl_pressed_status){
                event_mode = WAIT_TIMER_MODE;
                if (flag) event_mode |= WAIT_INPUT_MODE;
                waitEvent(t);
            }
        } else {
            string_buffer_offset--;
            goto parse_as_text;
        }
        return true;
    }
    else if ( ch == '#' && script_h.getStringBuffer()[string_buffer_offset + 1] != ' ' ){
        readColor( &sentence_font.color, script_h.getStringBuffer() + string_buffer_offset );
        readColor( &ruby_font.color, script_h.getStringBuffer() + string_buffer_offset );
        string_buffer_offset += 7;
        return true;
    }
    else if ( ch == '(' && script_h.getCurrent()[0] != '`' &&
              (!english_mode ||
               !(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR)) ){
        current_page->add('(');
        startRuby( script_h.getStringBuffer() + string_buffer_offset + 1, sentence_font );
        
        string_buffer_offset++;
        return true;
    }
    else if ( ch == '/' && !(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR)
               && script_h.getStringBuffer()[string_buffer_offset + 1] == '\n'){    // Do not crash when scripts use '/'
        if ( ruby_struct.stage == RubyStruct::BODY ){
            current_page->add('/');
            sentence_font.addLineOffset(ruby_struct.margin);
            string_buffer_offset = ruby_struct.ruby_end - script_h.getStringBuffer();
            if (*ruby_struct.ruby_end == ')'){
                if ( skip_mode || ctrl_pressed_status )
                    endRuby(false, true, accumulation_surface, &text_info);
                else
                    endRuby(true, true, accumulation_surface, &text_info);
                current_page->add(')');
                string_buffer_offset++;
            }

            return true;
        }
        else{ // skip new line
            new_line_skip_flag = true;
            string_buffer_offset++;
            if (script_h.getStringBuffer()[string_buffer_offset] != 0x00)
                errorAndExit( "'new line' must follow '/'." );
            return true; // skip the following eol
        }
    }
    else if ( ch == ')' && !(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR) &&
              ruby_struct.stage == RubyStruct::BODY ){
        current_page->add(')');
        string_buffer_offset++;
        ruby_struct.stage = RubyStruct::NONE;
        return true;
    }
    else if ( ch == '<' && 
              (!english_mode ||
               !(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR)) ){
        current_page->add('<');
        string_buffer_offset++;
        int no = 0;
        while(script_h.getStringBuffer()[string_buffer_offset]>='0' &&
              script_h.getStringBuffer()[string_buffer_offset]<='9'){
            current_page->add(script_h.getStringBuffer()[string_buffer_offset]);
            no=no*10+script_h.getStringBuffer()[string_buffer_offset++]-'0';
        }
        in_textbtn_flag = true;
        return true;
    }
    else if ( ch == '>' && in_textbtn_flag &&
              (!english_mode ||
               !(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR)) ){
        current_page->add('>');
        string_buffer_offset++;
        in_textbtn_flag = false;
        return true;
    }
    else{
        parse_as_text:          // Hack like ONScripter-EN to avoid making too many changes
        out_text[0] = ch;
        
        int matched_len = script_h.checkClickstr(script_h.getStringBuffer() + string_buffer_offset);

        if (matched_len > 0){
            if (matched_len == 2) out_text[1] = script_h.getStringBuffer()[ string_buffer_offset + 1 ];
            if (sentence_font.getRemainingLine() <= clickstr_line)
                return clickNewPage( out_text );
            else
                return clickWait( out_text );
        }
        else if (script_h.getStringBuffer()[ string_buffer_offset + 1 ] &&
                 script_h.checkClickstr(&script_h.getStringBuffer()[string_buffer_offset+1]) == 1 &&
                 script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR){
            if ( script_h.getStringBuffer()[ string_buffer_offset + 2 ] &&
                 script_h.checkClickstr(&script_h.getStringBuffer()[string_buffer_offset+2]) > 0){
                clickstr_state = CLICK_NONE;
            }
            else if (script_h.getStringBuffer()[ string_buffer_offset + 1 ] == '@'){
                return clickWait( out_text );
            }
            else if (script_h.getStringBuffer()[ string_buffer_offset + 1 ] == '\\'){
                return clickNewPage( out_text );
            }
            else{
                out_text[1] = script_h.getStringBuffer()[ string_buffer_offset + 1 ];
                if (sentence_font.getRemainingLine() <= clickstr_line)
                    return clickNewPage( out_text );
                else
                    return clickWait( out_text );
            }
        }
        else{
            clickstr_state = CLICK_NONE;
        }
        
        bool flush_flag = true;
        if ( skip_mode || ctrl_pressed_status )
            flush_flag = false;
        if ( script_h.getStringBuffer()[ string_buffer_offset + 1 ] &&
             !(script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR)){
            drawChar( out_text, &sentence_font, flush_flag, true, accumulation_surface, &text_info );
            num_chars_in_sentence++;
        }
        else if (script_h.getEndStatus() & ScriptHandler::END_1BYTE_CHAR){
#ifdef ENABLE_ENGLISH
            // Scan text for next whitespace to break on if at the end
            bool newLineEarly = false;
            if (ch == ' ') {
                char* script = script_h.getStringBuffer();
                int index = string_buffer_offset + 1;
                int advanced, accum_advance = 0;

                // Scan the next characters till the next space and get the accumulated character advance (word width)
                while(script[index] != '\0') {
                    advanced = 0;
                    if (sentence_font.ttf_font[0]) {
                        TTF_GlyphMetrics( (TTF_Font*)sentence_font.ttf_font[0], script[index], NULL, NULL, NULL, NULL, &advanced );
                    }
                    accum_advance += advanced;
                    if (script[index] == ' ') break;
                    index++;
                }

                // If we draw the next word it will be split to the next line, so make new line before adding word
                newLineEarly = sentence_font.willBeEndOfLine(accum_advance);
                if (newLineEarly) {
                    sentence_font.newLine();
                    for (int i=0 ; i<indent_offset ; i++){
#ifdef ANDROID
                        current_page->add(script_h.getSystemLanguageText()->get_space_char()[0]);
                        current_page->add(script_h.getSystemLanguageText()->get_space_char()[1]);
#else
                        current_page->add(0x81);
                        current_page->add(0x40);
#endif
                        sentence_font.advanceCharInHankaku(2);
                    }
                    current_page->add(' ');
                }
            }
            if (!newLineEarly)
#endif
            drawChar( out_text, &sentence_font, flush_flag, true, accumulation_surface, &text_info );
            num_chars_in_sentence++;
        }
        
        if (!skip_mode && !ctrl_pressed_status){
            event_mode = WAIT_TIMER_MODE | WAIT_INPUT_MODE;
            if ( sentence_font.wait_time == -1 )
                waitEvent( default_text_speed[text_speed_no] );
            else
                waitEvent( sentence_font.wait_time );
        }

        string_buffer_offset++;
        return true;
    }

    return false;
}

#ifdef ANDROID
// Converts a string (char*) to Unicode (jchar*) and returns the size
//  Does not check if buffer is smaller than input text
size_t ONScripter::basicStringToUnicode(jchar* out, const char* text)
{
    // Convert bytes to unicode
    size_t size = strlen(text);
    char* ptr = const_cast<char*>(text);

    int j = 0;
    for (int i=0; i<size ; i++) {
        jchar c = text[i];

        int n = script_h.decoder->getNumBytes(text[i]) - 1;
        if (i + n < size) {
            c = (jchar)script_h.decoder->convertNextChar(ptr + i);
            i += n;
        }
        out[j++] = c;
    }
    out[j] = 0;
    return j;
}
#endif

