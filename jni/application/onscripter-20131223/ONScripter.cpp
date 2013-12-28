/* -*- C++ -*-
 * 
 *  ONScripter.cpp - Execution block parser of ONScripter
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
#ifdef USE_FONTCONFIG
#include <fontconfig/fontconfig.h>
#endif

extern void initSJIS2UTF16();
extern "C" void waveCallback( int channel );

#define DEFAULT_AUDIOBUF  4096

#define FONT_FILE "default.ttf"
#define REGISTRY_FILE "registry.txt"
#define DLL_FILE "dll.txt"
#define DEFAULT_ENV_FONT "ÇlÇr ÉSÉVÉbÉN"
#define DEFAULT_AUTOMODE_TIME 1000

static void SDL_Quit_Wrapper()
{
    SDL_Quit();
}

void ONScripter::initSDL()
{
    /* ---------------------------------------- */
    /* Initialize SDL */

    if ( SDL_Init( SDL_INIT_VIDEO | SDL_INIT_TIMER | SDL_INIT_AUDIO ) < 0 ){
        fprintf( stderr, "Couldn't initialize SDL: %s\n", SDL_GetError() );
        exit(-1);
    }

#ifdef __OS2__
    atexit(SDL_Quit_Wrapper); // work-around for OS/2
#endif

#ifdef USE_CDROM
    if( cdaudio_flag && SDL_InitSubSystem( SDL_INIT_CDROM ) < 0 ){
        fprintf( stderr, "Couldn't initialize CD-ROM: %s\n", SDL_GetError() );
        exit(-1);
    }
#endif

#if !defined(IOS)
    if(SDL_InitSubSystem( SDL_INIT_JOYSTICK ) == 0 && SDL_JoystickOpen(0) != NULL)
        printf( "Initialize JOYSTICK\n");
#endif
    
#if defined(PSP) || defined(IPODLINUX) || defined(GP2X) || defined(WINCE)
    SDL_ShowCursor(SDL_DISABLE);
#endif

    /* ---------------------------------------- */
    /* Initialize SDL */
    if ( TTF_Init() < 0 ){
        fprintf( stderr, "can't initialize SDL TTF\n");
        exit(-1);
    }

#if defined(BPP16)
    screen_bpp = 16;
#else
    screen_bpp = 32;
#endif
    
#if defined(PDA_WIDTH)
    screen_ratio1 = PDA_WIDTH;
    screen_ratio2 = script_h.screen_width;
    screen_width  = PDA_WIDTH;
#elif defined(PDA_AUTOSIZE)
    SDL_Rect **modes;
    modes = SDL_ListModes(NULL, SDL_FULLSCREEN);
    if (modes == (SDL_Rect **)0){
        fprintf(stderr, "No Video mode available.\n");
        exit(-1);
    }
    else if (modes == (SDL_Rect **)-1){
        // no restriction
    }
 	else{
        int width;
        if (modes[0]->w * screen_height > modes[0]->h * screen_width)
            width = (modes[0]->h*screen_width/screen_height) & (~0x01); // to be 2 bytes aligned
        else
            width = modes[0]->w;
        screen_ratio1 = width;
        screen_ratio2 = script_h.screen_width;
        screen_width  = width;
    }
#endif

    screen_height = screen_width*script_h.screen_height/script_h.screen_width;

    screen_device_width  = screen_width;
    screen_device_height = screen_height;
#if defined(USE_SDL_RENDERER)
    // use hardware scaling
    screen_ratio1 = 1;
    screen_ratio2 = 1;
    screen_width  = script_h.screen_width;
    screen_height = script_h.screen_height;

    SDL_GL_SetAttribute(SDL_GL_RED_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_BLUE_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_GREEN_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_BUFFER_SIZE, 32);
    SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, 0);
    SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "linear");

    window = SDL_CreateWindow(NULL, 0, 0, screen_device_width, screen_device_height, SDL_WINDOW_OPENGL|SDL_WINDOW_SHOWN|SDL_WINDOW_BORDERLESS);
    SDL_GetWindowSize(window, &device_width, &device_height);
    renderer = SDL_CreateRenderer(window, -1, 0);
    texture_format = SDL_PIXELFORMAT_ARGB8888;
    SDL_RendererInfo info;
    SDL_GetRendererInfo(renderer, &info);
    if (info.texture_formats[0] == SDL_PIXELFORMAT_ABGR8888)
        texture_format = SDL_PIXELFORMAT_ABGR8888;
    SDL_RenderClear(renderer);
#else
#if defined(ANDROID)
    // use hardware scaling
    screen_ratio1 = 1;
    screen_ratio2 = 1;
    screen_width  = script_h.screen_width;
    screen_height = script_h.screen_height;
#endif
    screen_surface = SDL_SetVideoMode( screen_width, screen_height, screen_bpp, DEFAULT_VIDEO_SURFACE_FLAG|(fullscreen_mode?SDL_FULLSCREEN:0) );
#ifdef BPP16
    texture_format = SDL_PIXELFORMAT_RGB565;
#else
#if defined(ANDROID)
    SDL_SetSurfaceBlendMode(screen_surface, SDL_BLENDMODE_NONE);
    texture_format = SDL_PIXELFORMAT_ABGR8888;
#else
    texture_format = SDL_PIXELFORMAT_ARGB8888;
#endif
#endif
#endif

    /* ---------------------------------------- */
    /* Check if VGA screen is available. */
#if !defined(USE_SDL_RENDERER) && (PDA_WIDTH==640)
    if ( screen_surface == NULL ){
        screen_ratio1 /= 2;
        screen_width  /= 2;
        screen_height /= 2;
        screen_device_width  = screen_width;
        screen_device_height = screen_height;
        screen_surface = SDL_SetVideoMode( screen_device_width, screen_device_height, screen_bpp, DEFAULT_VIDEO_SURFACE_FLAG|(fullscreen_mode?SDL_FULLSCREEN:0) );
    }
#endif
    underline_value = script_h.screen_height;

#ifndef USE_SDL_RENDERER
    if ( screen_surface == NULL ) {
        fprintf( stderr, "Couldn't set %dx%dx%d video mode: %s\n",
                 screen_width, screen_height, screen_bpp, SDL_GetError() );
        exit(-1);
    }
#endif
    printf("Display: %d x %d (%d bpp)\n", screen_width, screen_height, screen_bpp);
    dirty_rect.setDimension(screen_width, screen_height);
    
    initSJIS2UTF16();
    
    wm_title_string = new char[ strlen(DEFAULT_WM_TITLE) + 1 ];
    memcpy( wm_title_string, DEFAULT_WM_TITLE, strlen(DEFAULT_WM_TITLE) + 1 );
    wm_icon_string = new char[ strlen(DEFAULT_WM_ICON) + 1 ];
    memcpy( wm_icon_string, DEFAULT_WM_TITLE, strlen(DEFAULT_WM_ICON) + 1 );
    SDL_WM_SetCaption( wm_title_string, wm_icon_string );
}

void ONScripter::openAudio()
{
#if (defined(PDA_WIDTH) || defined(PDA_AUTOSIZE)) && !defined(PSP) && !defined(IPHONE) && !defined(IOS) && !defined(PANDORA)
    if ( Mix_OpenAudio( 22050, MIX_DEFAULT_FORMAT, MIX_DEFAULT_CHANNELS, DEFAULT_AUDIOBUF ) < 0 ){
#else        
    if ( Mix_OpenAudio( 44100, MIX_DEFAULT_FORMAT, MIX_DEFAULT_CHANNELS, DEFAULT_AUDIOBUF ) < 0 ){
#endif        
        fprintf(stderr, "Couldn't open audio device!\n"
                "  reason: [%s].\n", SDL_GetError());
        audio_open_flag = false;
    }
    else{
        int freq;
        Uint16 format;
        int channels;

        Mix_QuerySpec( &freq, &format, &channels);
        printf("Audio: %d Hz %d bit %s\n", freq,
               (format&0xFF),
               (channels > 1) ? "stereo" : "mono");
        audio_format.format = format;
        audio_format.freq = freq;
        audio_format.channels = channels;

        audio_open_flag = true;

        Mix_AllocateChannels( ONS_MIX_CHANNELS+ONS_MIX_EXTRA_CHANNELS );
        Mix_ChannelFinished( waveCallback );
    }
}

ONScripter::ONScripter()
{
    is_script_read = false;

    cdrom_drive_number = 0;
    cdaudio_flag = false;
    default_font = NULL;
    registry_file = NULL;
    setStr( &registry_file, REGISTRY_FILE );
    dll_file = NULL;
    setStr( &dll_file, DLL_FILE );
    getret_str = NULL;
    enable_wheeldown_advance_flag = false;
    disable_rescale_flag = false;
    edit_flag = false;
    key_exe_file = NULL;
    fullscreen_mode = false;
    window_mode = false;
    sprite_info  = new AnimationInfo[MAX_SPRITE_NUM];
    sprite2_info = new AnimationInfo[MAX_SPRITE2_NUM];
    current_button_state.down_flag = false;

    int i;
    for (i=0 ; i<MAX_SPRITE2_NUM ; i++)
        sprite2_info[i].affine_flag = true;

    // External Players
#if defined(WINCE)
    midi_cmd  = NULL;
#else
    midi_cmd  = getenv("MUSIC_CMD");
#endif

    makeFuncLUT();
}

ONScripter::~ONScripter()
{
    reset();

    delete[] sprite_info;
    delete[] sprite2_info;
}

void ONScripter::enableCDAudio(){
    cdaudio_flag = true;
}

void ONScripter::setCDNumber(int cdrom_drive_number)
{
    this->cdrom_drive_number = cdrom_drive_number;
}

void ONScripter::setFontFile(const char *filename)
{
    setStr(&default_font, filename);
}

void ONScripter::setRegistryFile(const char *filename)
{
    setStr(&registry_file, filename);
}

void ONScripter::setDLLFile(const char *filename)
{
    setStr(&dll_file, filename);
}

void ONScripter::setArchivePath(const char *path)
{
    if (archive_path) delete[] archive_path;
    archive_path = new char[ RELATIVEPATHLENGTH + strlen(path) + 2 ];
    sprintf( archive_path, RELATIVEPATH "%s%c", path, DELIMITER );
}

void ONScripter::setSaveDir(const char *path)
{
    if (save_dir) delete[] save_dir;
    save_dir = new char[ RELATIVEPATHLENGTH + strlen(path) + 2 ];
    sprintf( save_dir, RELATIVEPATH "%s%c", path, DELIMITER );
    script_h.setSaveDir(save_dir);
}

void ONScripter::setFullscreenMode()
{
    fullscreen_mode = true;
}

void ONScripter::setWindowMode()
{
    window_mode = true;
}

void ONScripter::enableButtonShortCut()
{
    force_button_shortcut_flag = true;
}

void ONScripter::enableWheelDownAdvance()
{
    enable_wheeldown_advance_flag = true;
}

void ONScripter::disableRescale()
{
    disable_rescale_flag = true;
}

void ONScripter::renderFontOutline()
{
#if (SDL_TTF_MAJOR_VERSION>=2) && (SDL_TTF_MINOR_VERSION>=0) && (SDL_TTF_PATCHLEVEL>=10)
    render_font_outline = true;
#else
    fprintf(stderr, "--render-font-outline is not supported with SDL_ttf %d.%d.%d\n", SDL_TTF_MAJOR_VERSION, SDL_TTF_MINOR_VERSION, SDL_TTF_PATCHLEVEL);
#endif
}

void ONScripter::enableEdit()
{
    edit_flag = true;
}

void ONScripter::setKeyEXE(const char *filename)
{
    setStr(&key_exe_file, filename);
}

int ONScripter::openScript()
{
    if (is_script_read) return 0;
    is_script_read = true;

    if (archive_path == NULL) archive_path = "";
    
    if (key_exe_file){
        createKeyTable( key_exe_file );
        script_h.setKeyTable( key_table );
    }

    return ScriptParser::openScript();
}

int ONScripter::init()
{
    initSDL();
    openAudio();

    image_surface        = AnimationInfo::alloc32bitSurface( 1, 1, texture_format );
    accumulation_surface = AnimationInfo::allocSurface( screen_width, screen_height, texture_format );
    backup_surface       = AnimationInfo::allocSurface( screen_width, screen_height, texture_format );
    effect_src_surface   = AnimationInfo::allocSurface( screen_width, screen_height, texture_format );
    effect_dst_surface   = AnimationInfo::allocSurface( screen_width, screen_height, texture_format );

#if defined(USE_SDL_RENDERER)
    screenshot_surface = AnimationInfo::alloc32bitSurface( screen_device_width, screen_device_height, texture_format );
#else
    screenshot_surface = AnimationInfo::alloc32bitSurface( screen_width, screen_height, texture_format );
#endif
    screenshot_w = screen_width;
    screenshot_h = screen_height;

#ifdef USE_SDL_RENDERER
    texture = SDL_CreateTextureFromSurface(renderer, accumulation_surface);
#endif    

    tmp_image_buf = NULL;
    tmp_image_buf_length = 0;
    mean_size_of_loaded_images = 0;
    num_loaded_images = 10; // to suppress temporal increase at the start-up

    text_info.num_of_cells = 1;
    text_info.allocImage( screen_width, screen_height, texture_format );
    text_info.fill(0, 0, 0, 0);

    // ----------------------------------------
    // Initialize font
    if ( default_font ){
        font_file = new char[ strlen(default_font) + 1 ];
        sprintf( font_file, "%s", default_font );
    }
    else{
        font_file = new char[ strlen(archive_path) + strlen(FONT_FILE) + 1 ];
        sprintf( font_file, "%s%s", archive_path, FONT_FILE );
#ifdef USE_FONTCONFIG
        FILE *fp = NULL;
        if ((fp = ::fopen(font_file, "rb")) == NULL){
            FcPattern *pat = FcPatternCreate();

            FcPatternAddString( pat, FC_LANG, (const FcChar8*)"ja" );
            FcPatternAddBool( pat, FC_OUTLINE, FcTrue );
            FcPatternAddInteger( pat, FC_SLANT, FC_SLANT_ROMAN );
            FcPatternAddInteger( pat, FC_WEIGHT, FC_WEIGHT_NORMAL );

            FcConfigSubstitute( NULL, pat, FcMatchPattern );
            FcDefaultSubstitute( pat );
            
            FcResult result;
            FcPattern *p_pat = FcFontMatch( NULL, pat, &result );
            FcPatternDestroy( pat );
            
            FcChar8* val_s;
            if (FcResultMatch == FcPatternGetString( p_pat, FC_FILE, 0, &val_s )){
                delete[] font_file;
                font_file = new char[ strlen((const char*)val_s) + 1 ];
                strcpy( font_file, (const char*)val_s );
                printf("Font: %s\n", font_file);
            }
            FcPatternDestroy( p_pat );
        }
        else{
            fclose(fp);
        }
#endif
    }
    
    // ----------------------------------------
    // variables relevant to sound
    this->cdaudio_flag = cdaudio_flag;
#ifdef USE_CDROM
    cdrom_info = NULL;
    if ( cdaudio_flag ){
        if ( cdrom_drive_number >= 0 && cdrom_drive_number < SDL_CDNumDrives() )
            cdrom_info = SDL_CDOpen( cdrom_drive_number );
        if ( !cdrom_info ){
            fprintf(stderr, "Couldn't open default CD-ROM: %s\n", SDL_GetError());
        }
        else if ( cdrom_info && !CD_INDRIVE( SDL_CDStatus( cdrom_info ) ) ) {
            fprintf( stderr, "no CD-ROM in the drive\n" );
            SDL_CDClose( cdrom_info );
            cdrom_info = NULL;
        }
    }
#endif

    wave_file_name = NULL;
    midi_file_name = NULL;
    midi_info  = NULL;
    music_file_name = NULL;
    music_buffer = NULL;
    music_info = NULL;

    loop_bgm_name[0] = NULL;
    loop_bgm_name[1] = NULL;

    int i;
    for (i=0 ; i<ONS_MIX_CHANNELS+ONS_MIX_EXTRA_CHANNELS ; i++) wave_sample[i] = NULL;

    // ----------------------------------------
    // Initialize misc variables
    
    breakup_cells = NULL;
    breakup_mask = breakup_cellforms = NULL;

    internal_timer = SDL_GetTicks();

    trap_dist = NULL;
    resize_buffer = new unsigned char[16];
    resize_buffer_size = 16;

    for (i=0 ; i<MAX_PARAM_NUM ; i++) bar_info[i] = prnum_info[i] = NULL;

    loadEnvData();
    defineresetCommand();

    readToken();

    if ( sentence_font.openFont( font_file, screen_ratio1, screen_ratio2 ) == NULL ){
        fprintf( stderr, "can't open font file: %s\n", font_file );
        return -1;
    }
    
    return 0;
}

void ONScripter::reset()
{
    automode_flag = false;
    automode_time = DEFAULT_AUTOMODE_TIME;
    autoclick_time = 0;
    btntime2_flag = false;
    btntime_value = -1;
    btnwait_time = 0;
    transbtn_flag = 0;

    disableGetButtonFlag();

    system_menu_enter_flag = false;
    system_menu_mode = SYSTEM_NULL;
    shift_pressed_status = 0;
    ctrl_pressed_status = 0;
    display_mode = DISPLAY_MODE_NORMAL;
    event_mode = IDLE_EVENT_MODE;
    all_sprite_hide_flag = false;
    all_sprite2_hide_flag = false;

    if (breakup_cells) delete[] breakup_cells;
    if (breakup_mask) delete[] breakup_mask;
    if (breakup_cellforms) delete[] breakup_cellforms;

    if (resize_buffer_size != 16){
        delete[] resize_buffer;
        resize_buffer = new unsigned char[16];
        resize_buffer_size = 16;
    }

    current_over_button = 0;
    shift_over_button = -1;
    num_fingers = 0;
    variable_edit_mode = NOT_EDIT_MODE;

    new_line_skip_flag = false;
    text_on_flag = true;
    draw_cursor_flag = false;

    setStr(&getret_str, NULL);
    getret_int = 0;
    
    // ----------------------------------------
    // variables relevant to sound
    wave_play_loop_flag = false;
    midi_play_loop_flag = false;
    music_play_loop_flag = false;
    cd_play_loop_flag = false;
    mp3save_flag = false;
    mp3fade_start = 0;
    mp3fadeout_duration = 0;
    mp3fadein_duration = 0;
    mp3fadeout_duration_internal = 0;
    mp3fadein_duration_internal = 0;
    current_cd_track = -1;
    
    resetSub();
}

void ONScripter::resetSub()
{
    int i;

    for ( i=0 ; i<script_h.global_variable_border ; i++ )
        script_h.getVariableData(i).reset(false);

    for ( i=0 ; i<3 ; i++ ) human_order[i] = 2-i; // "rcl"

    refresh_shadow_text_mode = REFRESH_NORMAL_MODE | REFRESH_SHADOW_MODE | REFRESH_TEXT_MODE;
    erase_text_window_mode = 1;
    skip_mode = SKIP_NONE;
    monocro_flag = false;
    monocro_color[0] = monocro_color[1] = monocro_color[2] = 0;
    nega_mode = 0;
    clickstr_state = CLICK_NONE;
    trap_mode = TRAP_NONE;
    setStr(&trap_dist, NULL);

    saveon_flag = true;
    internal_saveon_flag = true;

    is_kinsoku = true;
    textgosub_clickstr_state = CLICK_NONE;
    indent_offset = 0;
    line_enter_status = 0;
    page_enter_status = 0;
    in_textbtn_flag = false;
    
    resetSentenceFont();

    deleteNestInfo();
    deleteButtonLink();
    deleteSelectLink();

    stopCommand();
    loopbgmstopCommand();
    stopAllDWAVE();
    setStr(&loop_bgm_name[1], NULL);

    // ----------------------------------------
    // reset AnimationInfo
    btndef_info.reset();
    bg_info.reset();
    setStr( &bg_info.file_name, "black" );
    createBackground();
    for (i=0 ; i<3 ; i++) tachi_info[i].reset();
    for (i=0 ; i<MAX_SPRITE_NUM ; i++) sprite_info[i].reset();
    for (i=0 ; i<MAX_SPRITE2_NUM ; i++) sprite2_info[i].reset();
    barclearCommand();
    prnumclearCommand();
    for (i=0 ; i<2 ; i++) cursor_info[i].reset();
    show_dialog_flag = false;
    for (i=0 ; i<4 ; i++) lookback_info[i].reset();
    sentence_font_info.reset();

    dirty_rect.fill( screen_width, screen_height );
}

void ONScripter::resetSentenceFont()
{
    sentence_font.reset();
    sentence_font.font_size_xy[0] = DEFAULT_FONT_SIZE;
    sentence_font.font_size_xy[1] = DEFAULT_FONT_SIZE;
    sentence_font.top_xy[0] = 21;
    sentence_font.top_xy[1] = 16;// + sentence_font.font_size;
    sentence_font.num_xy[0] = 23;
    sentence_font.num_xy[1] = 16;
    sentence_font.pitch_xy[0] = sentence_font.font_size_xy[0];
    sentence_font.pitch_xy[1] = 2 + sentence_font.font_size_xy[1];
    sentence_font.wait_time = 20;
    sentence_font.window_color[0] = sentence_font.window_color[1] = sentence_font.window_color[2] = 0x99;
    sentence_font_info.orig_pos.x = 0;
    sentence_font_info.orig_pos.y = 0;
    sentence_font_info.orig_pos.w = script_h.screen_width +1;
    sentence_font_info.orig_pos.h = script_h.screen_height+1;
    sentence_font_info.scalePosXY( screen_ratio1, screen_ratio2 );
    sentence_font_info.scalePosWH( screen_ratio1, screen_ratio2 );

    sentence_font.old_xy[0] = sentence_font.x();
    sentence_font.old_xy[1] = sentence_font.y();
}

void ONScripter::flush( int refresh_mode, SDL_Rect *rect, bool clear_dirty_flag, bool direct_flag )
{
    if ( direct_flag ){
        flushDirect( *rect, refresh_mode );
    }
    else{
        if ( rect ) dirty_rect.add( *rect );

        if (dirty_rect.bounding_box.w * dirty_rect.bounding_box.h > 0)
            flushDirect( dirty_rect.bounding_box, refresh_mode );
    }
    
    if ( clear_dirty_flag ) dirty_rect.clear();
}

void ONScripter::flushDirect( SDL_Rect &rect, int refresh_mode )
{
    //printf("flush %d: %d %d %d %d\n", refresh_mode, rect.x, rect.y, rect.w, rect.h );
    
    refreshSurface( accumulation_surface, &rect, refresh_mode );
#ifdef USE_SDL_RENDERER
    SDL_Rect src_rect = {0, 0, screen_width, screen_height};
    SDL_Rect dst_rect = {(device_width -screen_device_width )/2, 
                         (device_height-screen_device_height)/2,
                         screen_device_width, screen_device_height};
    SDL_LockSurface(accumulation_surface);
    SDL_UpdateTexture(texture, &rect, (unsigned char*)accumulation_surface->pixels+accumulation_surface->pitch*rect.y+rect.x*sizeof(ONSBuf), accumulation_surface->pitch);
    SDL_UnlockSurface(accumulation_surface);
    SDL_RenderCopy(renderer, texture, &src_rect, &dst_rect);
    SDL_RenderPresent(renderer);
#else
    SDL_Rect dst_rect = rect, clip_rect = {0, 0, screen_width, screen_height};
    if (AnimationInfo::doClipping(&dst_rect, &clip_rect) || dst_rect.w==0 && dst_rect.h==0) return;
    SDL_BlitSurface( accumulation_surface, &dst_rect, screen_surface, &dst_rect );
    SDL_UpdateRect( screen_surface, dst_rect.x, dst_rect.y, dst_rect.w, dst_rect.h );
#endif
}

void ONScripter::mouseOverCheck( int x, int y )
{
    int c = 0, max_c = 0;

    last_mouse_state.x = x;
    last_mouse_state.y = y;

    /* ---------------------------------------- */
    /* Check button */
    int button = 0;
    ButtonLink *bl = root_button_link.next, *max_bl = NULL;
    unsigned int max_alpha = 0;
    while( bl ){
        if ( x >= bl->select_rect.x && x < bl->select_rect.x + bl->select_rect.w &&
             y >= bl->select_rect.y && y < bl->select_rect.y + bl->select_rect.h ){
            if (transbtn_flag == false || shift_over_button == bl->no){
                max_bl = bl;
                max_c = c;
                break;
            }
            else{
                unsigned char alpha = 0;
                if ( (bl->button_type == ButtonLink::SPRITE_BUTTON &&
                      max_alpha < (alpha = sprite_info[ bl->sprite_no ].getAlpha(x, y))) ||
                     (bl->button_type == ButtonLink::NORMAL_BUTTON &&
                      max_alpha < (alpha = bl->anim[0]->getAlpha(x, y))) ){
                    max_alpha = alpha;
                    max_bl = bl;
                    max_c = c;
                }
            }
        }
        bl = bl->next;
        c++;
    }

    if (max_bl){
        bl = max_bl;
        button = bl->no;
        c = max_c;
    }

    if ( current_over_button != button ){
        DirtyRect dirty = dirty_rect;
        dirty_rect.clear();

        SDL_Rect check_src_rect = {0, 0, 0, 0};
        SDL_Rect check_dst_rect = {0, 0, 0, 0};
        if ( current_over_button != 0 ){
            ButtonLink *cbl = current_button_link;
            cbl->show_flag = 0;
            check_src_rect = cbl->image_rect;
            if ( cbl->button_type == ButtonLink::SPRITE_BUTTON ){
                sprite_info[ cbl->sprite_no ].visible = true;
                if ( cbl->exbtn_ctl[0] )
                    decodeExbtnControl( cbl->exbtn_ctl[0], &check_src_rect, &check_dst_rect );
                else
                    sprite_info[ cbl->sprite_no ].setCell(0);
            }
            else if ( cbl->button_type == ButtonLink::TMP_SPRITE_BUTTON ){
                cbl->show_flag = 1;
                cbl->anim[0]->visible = true;
                cbl->anim[0]->setCell(0);
            }
            else if ( cbl->anim[1] != NULL ){
                cbl->show_flag = 2;
            }
            dirty_rect.add( cbl->image_rect );
        }

        if ( is_exbtn_enabled && exbtn_d_button_link.exbtn_ctl[1] ){
            decodeExbtnControl( exbtn_d_button_link.exbtn_ctl[1], &check_src_rect, &check_dst_rect );
        }
        
        if ( bl ){
            if ( system_menu_mode != SYSTEM_NULL ){
                if ( menuselectvoice_file_name[MENUSELECTVOICE_OVER] )
                    playSound(menuselectvoice_file_name[MENUSELECTVOICE_OVER], 
                              SOUND_CHUNK, false, MIX_WAVE_CHANNEL);
            }
            else{
                if ( selectvoice_file_name[SELECTVOICE_OVER] )
                    playSound(selectvoice_file_name[SELECTVOICE_OVER], 
                              SOUND_CHUNK, false, MIX_WAVE_CHANNEL);
            }
            check_dst_rect = bl->image_rect;
            if ( bl->button_type == ButtonLink::SPRITE_BUTTON ){
                sprite_info[ bl->sprite_no ].setCell(1);
                if ( bl->exbtn_ctl[1] )
                    decodeExbtnControl( bl->exbtn_ctl[1], &check_src_rect, &check_dst_rect );
                else
                    sprite_info[ bl->sprite_no ].visible = true;
            }
            else if ( bl->button_type == ButtonLink::TMP_SPRITE_BUTTON ){
                bl->show_flag = 1;
                bl->anim[0]->visible = true;
                bl->anim[0]->setCell(1);
            }
            else if ( bl->button_type == ButtonLink::NORMAL_BUTTON ||
                      bl->button_type == ButtonLink::LOOKBACK_BUTTON ){
                bl->show_flag = 1;
            }
            dirty_rect.add( bl->image_rect );
            current_button_link = bl;
            shortcut_mouse_line = c;
        }
        
        flush( refreshMode() );
        dirty_rect = dirty;
    }

    current_over_button = button;
    shift_over_button = -1;
}

void ONScripter::executeLabel()
{
  executeLabelTop:    

    while ( current_line<current_label_info.num_of_lines ){
        if ( debug_level > 0 )
            printf("*****  executeLabel %s:%d/%d:%d:%d *****\n",
                   current_label_info.name,
                   current_line,
                   current_label_info.num_of_lines,
                   string_buffer_offset, display_mode );
        
        if ( script_h.getStringBuffer()[0] == '~' ){
            last_tilde.next_script = script_h.getNext();
            readToken();
            continue;
        }
        if ( break_flag && !script_h.isName("next") ){
            if ( script_h.getStringBuffer()[string_buffer_offset] == 0x0a )
                current_line++;

            if ( script_h.getStringBuffer()[string_buffer_offset] != ':' &&
                 script_h.getStringBuffer()[string_buffer_offset] != ';' &&
                 script_h.getStringBuffer()[string_buffer_offset] != 0x0a )
                script_h.skipToken();

            readToken();
            continue;
        }

        if ( kidokuskip_flag && skip_mode & SKIP_NORMAL && kidokumode_flag && !script_h.isKidoku() ) skip_mode &= ~SKIP_NORMAL;

        int ret = parseLine();
        if ( ret & (RET_SKIP_LINE | RET_EOL) ){
            if (ret & RET_SKIP_LINE) script_h.skipLine();
            if (++current_line >= current_label_info.num_of_lines) break;
        }

        if (!(ret & RET_NO_READ)) readToken();
    }

    current_label_info = script_h.lookupLabelNext( current_label_info.name );
    current_line = 0;

    if ( current_label_info.start_address != NULL ){
        script_h.setCurrent( current_label_info.label_header );
        readToken();
        goto executeLabelTop;
    }
    
    fprintf( stderr, " ***** End *****\n");
    endCommand();
}

void ONScripter::runScript()
{
    readToken();
    parseLine();
}

int ONScripter::parseLine( )
{
    if (debug_level > 0) printf("ONScripter::Parseline %s\n", script_h.getStringBuffer() );

    const char *cmd = script_h.getStringBuffer();
    if      (cmd[0] == ';') return RET_CONTINUE;
    else if (cmd[0] == '*') return RET_CONTINUE;
    else if (cmd[0] == ':') return RET_CONTINUE;

    if (script_h.isText()){
        if ( current_mode == DEFINE_MODE ) errorAndExit( "text cannot be displayed in define section." );
        return textCommand();
    }

    if (cmd[0] != '_'){
        if (cmd[0] >= 'a' && cmd[0] <= 'z'){
            UserFuncHash &ufh = user_func_hash[cmd[0]-'a'];
            UserFuncLUT *uf = ufh.root.next;
            while(uf){
                if (!strcmp( uf->command, cmd )){
                    if (uf->lua_flag){
#ifdef USE_LUA
                        if (lua_handler.callFunction(false, cmd))
                            errorAndExit( lua_handler.error_str );
#endif
                    }
                    else{
                        gosubReal( cmd, script_h.getNext() );
                    }
                    return RET_CONTINUE;
                }
                uf = uf->next;
            }
        }
    }
    else{
        cmd++;
    }

    if (cmd[0] >= 'a' && cmd[0] <= 'z'){
        FuncHash &fh = func_hash[cmd[0]-'a'];
        for (int i=0 ; i<fh.num ; i++){
            if ( !strcmp( fh.func[i].command, cmd ) ){
                //if (saveon_flag) saveSaveFile( -1 );
                return (this->*fh.func[i].method)();
            }
        }
    }

    if ( cmd[0] == 0x0a )
        return RET_CONTINUE | RET_EOL;
    else if ( cmd[0] == 'v' && cmd[1] >= '0' && cmd[1] <= '9' )
        return vCommand();
    else if ( cmd[0] == 'd' && cmd[1] == 'v' && cmd[2] >= '0' && cmd[2] <= '9' )
        return dvCommand();

    fprintf( stderr, " command [%s] is not supported yet!!\n", cmd );

    script_h.skipToken();

    return RET_CONTINUE;
}

/* ---------------------------------------- */
void ONScripter::deleteButtonLink()
{
    ButtonLink *b1 = root_button_link.next;

    while( b1 ){
        ButtonLink *b2 = b1;
        b1 = b1->next;
        if (b2 == current_button_link) current_over_button = 0;
        delete b2;
    }
    root_button_link.next = NULL;
    
    for (int i=0 ; i<3 ; i++){
        if ( exbtn_d_button_link.exbtn_ctl[i] ){
            delete[] exbtn_d_button_link.exbtn_ctl[i];
            exbtn_d_button_link.exbtn_ctl[i] = NULL;
        }
    }
    is_exbtn_enabled = false;
}

void ONScripter::refreshMouseOverButton()
{
    int mx, my;
    current_over_button = 0;
    shift_over_button = -1;
    current_button_link = root_button_link.next;
    SDL_GetMouseState( &mx, &my );
    mx = mx * screen_width / screen_device_width;
    my = my * screen_width / screen_device_width;
    mouseOverCheck( mx, my );
}

/* ---------------------------------------- */
/* Delete select link */
void ONScripter::deleteSelectLink()
{
    SelectLink *link, *last_select_link = root_select_link.next;

    while ( last_select_link ){
        link = last_select_link;
        last_select_link = last_select_link->next;
        delete link;
    }
    root_select_link.next = NULL;
}

void ONScripter::clearCurrentPage()
{
    sentence_font.clear();

    int num = (sentence_font.num_xy[0]*2+1)*sentence_font.num_xy[1];
    if (sentence_font.getTateyokoMode() == FontInfo::TATE_MODE)
        num = (sentence_font.num_xy[1]*2+1)*sentence_font.num_xy[0];
    
    if ( current_page->text &&
         current_page->max_text != num ){
        delete[] current_page->text;
        current_page->text = NULL;
    }
    if ( !current_page->text ){
        current_page->text = new char[num];
        current_page->max_text = num;
    }
    current_page->text_count = 0;

    if (current_page->tag){
        delete[] current_page->tag;
        current_page->tag = NULL;
    }

    num_chars_in_sentence = 0;
    internal_saveon_flag = true;

    text_info.fill( 0, 0, 0, 0 );
    cached_page = current_page;
}

void ONScripter::shadowTextDisplay( SDL_Surface *surface, SDL_Rect &clip )
{
    if ( current_font->is_transparent ){

        SDL_Rect rect = {0, 0, screen_width, screen_height};
        if ( current_font == &sentence_font )
            rect = sentence_font_info.pos;

        if ( AnimationInfo::doClipping( &rect, &clip ) ) return;

        if ( rect.x + rect.w > surface->w ) rect.w = surface->w - rect.x;
        if ( rect.y + rect.h > surface->h ) rect.h = surface->h - rect.y;

        SDL_LockSurface( surface );
        ONSBuf *buf = (ONSBuf *)surface->pixels + rect.y * surface->w + rect.x;

        SDL_PixelFormat *fmt = surface->format;
        int color[3];
        color[0] = current_font->window_color[0] + 1;
        color[1] = current_font->window_color[1] + 1;
        color[2] = current_font->window_color[2] + 1;

        for ( int i=rect.y ; i<rect.y + rect.h ; i++ ){
            for ( int j=rect.x ; j<rect.x + rect.w ; j++, buf++ ){
                *buf = (((*buf & fmt->Rmask) >> fmt->Rshift) * color[0] >> 8) << fmt->Rshift |
                    (((*buf & fmt->Gmask) >> fmt->Gshift) * color[1] >> 8) << fmt->Gshift |
                    (((*buf & fmt->Bmask) >> fmt->Bshift) * color[2] >> 8) << fmt->Bshift;
            }
            buf += surface->w - rect.w;
        }

        SDL_UnlockSurface( surface );
    }
    else if ( sentence_font_info.image_surface ){
        drawTaggedSurface( surface, &sentence_font_info, clip );
    }
}

void ONScripter::newPage()
{
    if ( current_page->text_count != 0 ){
        current_page = current_page->next;
        if ( start_page == current_page )
            start_page = start_page->next;
        clearCurrentPage();
    }

    page_enter_status = 0;

    flush( refreshMode(), &sentence_font_info.pos );
}

ButtonLink *ONScripter::getSelectableSentence( char *buffer, FontInfo *info, bool flush_flag, bool nofile_flag )
{
    int current_text_xy[2];
    current_text_xy[0] = info->xy[0];
    current_text_xy[1] = info->xy[1];
    
    ButtonLink *bl = new ButtonLink();
    bl->button_type = ButtonLink::TMP_SPRITE_BUTTON;
    bl->show_flag = 1;

    AnimationInfo *ai = bl->anim[0] = new AnimationInfo();
    
    ai->trans_mode = AnimationInfo::TRANS_STRING;
    ai->is_single_line = false;
    ai->num_of_cells = 2;
    ai->color_list = new uchar3[ ai->num_of_cells ];
    for (int i=0 ; i<3 ; i++){
        if (nofile_flag)
            ai->color_list[0][i] = info->nofile_color[i];
        else
            ai->color_list[0][i] = info->off_color[i];
        ai->color_list[1][i] = info->on_color[i];
    }
    setStr( &ai->file_name, buffer );
    ai->orig_pos.x = info->x();
    ai->orig_pos.y = info->y();
    ai->scalePosXY( screen_ratio1, screen_ratio2 );
    ai->visible = true;

    setupAnimationInfo( ai, info );
    bl->select_rect = bl->image_rect = ai->pos;

    info->newLine();
    if (info->getTateyokoMode() == FontInfo::YOKO_MODE)
        info->xy[0] = current_text_xy[0];
    else
        info->xy[1] = current_text_xy[1];

    dirty_rect.add( bl->image_rect );
    
    return bl;
}

void ONScripter::decodeExbtnControl( const char *ctl_str, SDL_Rect *check_src_rect, SDL_Rect *check_dst_rect )
{
    char sound_name[256];
    int i, sprite_no, sprite_no2, cell_no;

    while( char com = *ctl_str++ ){
        if (com == 'C' || com == 'c'){
            sprite_no = getNumberFromBuffer( &ctl_str );
            sprite_no2 = sprite_no;
            cell_no = -1;
            if ( *ctl_str == '-' ){
                ctl_str++;
                sprite_no2 = getNumberFromBuffer( &ctl_str );
            }
            for (i=sprite_no ; i<=sprite_no2 ; i++)
                refreshSprite( i, false, cell_no, NULL, NULL );
        }
        else if (com == 'P' || com == 'p'){
            sprite_no = getNumberFromBuffer( &ctl_str );
            if ( *ctl_str == ',' ){
                ctl_str++;
                cell_no = getNumberFromBuffer( &ctl_str );
            }
            else
                cell_no = 0;
            refreshSprite( sprite_no, true, cell_no, check_src_rect, check_dst_rect );
        }
        else if (com == 'S' || com == 's'){
            sprite_no = getNumberFromBuffer( &ctl_str );
            if      (sprite_no < 0) sprite_no = 0;
            else if (sprite_no >= ONS_MIX_CHANNELS) sprite_no = ONS_MIX_CHANNELS-1;
            if ( *ctl_str != ',' ) continue;
            ctl_str++;
            if ( *ctl_str != '(' ) continue;
            ctl_str++;
            char *buf = sound_name;
            while (*ctl_str != ')' && *ctl_str != '\0' ) *buf++ = *ctl_str++;
            *buf++ = '\0';
            playSound(sound_name, SOUND_CHUNK, false, sprite_no);
            if ( *ctl_str == ')' ) ctl_str++;
        }
        else if (com == 'M' || com == 'm'){
            sprite_no = getNumberFromBuffer( &ctl_str );
            AnimationInfo *ai = &sprite_info[sprite_no];

            SDL_Rect rect = ai->pos;
            if ( *ctl_str != ',' ) continue;
            ctl_str++; // skip ','
            ai->orig_pos.x = getNumberFromBuffer( &ctl_str );
            if ( *ctl_str != ',' ) continue;
            ctl_str++; // skip ','
            ai->orig_pos.y = getNumberFromBuffer( &ctl_str );
            ai->scalePosXY( screen_ratio1, screen_ratio2 );
            dirty_rect.add( rect );
            ai->visible = true;
            dirty_rect.add( ai->pos );
        }
    }
}

void ONScripter::saveAll()
{
    saveEnvData();
    saveGlovalData();
    if ( filelog_flag )  writeLog( script_h.log_info[ScriptHandler::FILE_LOG] );
    if ( labellog_flag ) writeLog( script_h.log_info[ScriptHandler::LABEL_LOG] );
    if ( kidokuskip_flag ) script_h.saveKidokuData();
}

void ONScripter::loadEnvData()
{
    volume_on_flag = true;
    text_speed_no = 1;
    skip_mode &= ~SKIP_TO_EOP;
    default_env_font = NULL;
    cdaudio_on_flag = true;
    default_cdrom_drive = NULL;
    kidokumode_flag = true;
    setStr( &save_dir_envdata, NULL );
    automode_time = DEFAULT_AUTOMODE_TIME;
    
    if (loadFileIOBuf( "envdata" ) > 0){
        if (readInt() == 1 && window_mode == false) menu_fullCommand();
        if (readInt() == 0) volume_on_flag = false;
        text_speed_no = readInt();
        if (readInt() == 1) skip_mode |= SKIP_TO_EOP;
        readStr( &default_env_font );
        if (default_env_font == NULL)
            setStr(&default_env_font, DEFAULT_ENV_FONT);
        if (readInt() == 0) cdaudio_on_flag = false;
        readStr( &default_cdrom_drive );
        voice_volume = DEFAULT_VOLUME - readInt();
        se_volume = DEFAULT_VOLUME - readInt();
        music_volume = DEFAULT_VOLUME - readInt();
        if (readInt() == 0) kidokumode_flag = false;
        readInt();
        readStr( &save_dir_envdata ); // save_dir_envdata is not used directly
        if (!save_dir && save_dir_envdata){
            // a workaround not to overwrite save_dir given in command line options
            save_dir = new char[ strlen(archive_path) + strlen(save_dir_envdata) + 2 ];
            sprintf( save_dir, "%s%s%c", archive_path, save_dir_envdata, DELIMITER );
            script_h.setSaveDir(save_dir);
        }
        automode_time = readInt();
    }
    else{
        setStr( &default_env_font, DEFAULT_ENV_FONT );
        voice_volume = se_volume = music_volume = DEFAULT_VOLUME;
    }
}

void ONScripter::saveEnvData()
{
    file_io_buf_ptr = 0;
    bool output_flag = false;
    for (int i=0 ; i<2 ; i++){
        writeInt( fullscreen_mode?1:0, output_flag );
        writeInt( volume_on_flag?1:0, output_flag );
        writeInt( text_speed_no, output_flag );
        writeInt( (skip_mode & SKIP_TO_EOP)?1:0, output_flag );
        writeStr( default_env_font, output_flag );
        writeInt( cdaudio_on_flag?1:0, output_flag );
        writeStr( default_cdrom_drive, output_flag );
        writeInt( DEFAULT_VOLUME - voice_volume, output_flag );
        writeInt( DEFAULT_VOLUME - se_volume, output_flag );
        writeInt( DEFAULT_VOLUME - music_volume, output_flag );
        writeInt( kidokumode_flag?1:0, output_flag );
        writeInt( 0, output_flag ); // ?
        writeStr( save_dir_envdata, output_flag );
        writeInt( automode_time, output_flag );

        if (i==1) break;
        allocFileIOBuf();
        output_flag = true;
    }

    saveFileIOBuf( "envdata" );
}

int ONScripter::refreshMode()
{
    if (display_mode & DISPLAY_MODE_TEXT)
        return refresh_shadow_text_mode;

    return REFRESH_NORMAL_MODE;
}

void ONScripter::quit()
{
    saveAll();

#ifdef USE_CDROM
    if ( cdrom_info ){
        SDL_CDStop( cdrom_info );
        SDL_CDClose( cdrom_info );
    }
#endif

    if ( midi_info ){
        Mix_HaltMusic();
        Mix_FreeMusic( midi_info );
        midi_info = NULL;
    }
    if ( music_info ){
        Mix_HaltMusic();
        Mix_FreeMusic( music_info );
        music_info = NULL;
    }
}

void ONScripter::disableGetButtonFlag()
{
    bexec_flag = false;
    btndown_flag = false;

    getzxc_flag = false;
    gettab_flag = false;
    getpageup_flag = false;
    getpagedown_flag = false;
    getinsert_flag = false;
    getfunction_flag = false;
    getenter_flag = false;
    getcursor_flag = false;
    getmouseover_flag = false;
    spclclk_flag = false;
}

int ONScripter::getNumberFromBuffer( const char **buf )
{
    int ret = 0;
    while ( **buf >= '0' && **buf <= '9' )
        ret = ret*10 + *(*buf)++ - '0';

    return ret;
}
