#ifndef __MENUTEXT_H__
#define __MENUTEXT_H__

#include "MenuText_UTF8.h"
#include "ScriptDecoder.h"

// English Menu Text
#define ENGLISH_MSG_SAVE_EXIST "`%s%s    Date %s/%s    Time %s:%s"
#define ENGLISH_MSG_SAVE_EMPTY "`%s%s    ------------------------"
#define ENGLISH_MSG_SAVE_CONFIRM "`Save in slot %s%s?"
#define ENGLISH_MSG_LOAD_CONFIRM "`Load from slot %s%s?"
#define ENGLISH_MSG_RESET_CONFIRM "`Return to Title Menu?"
#define ENGLISH_MSG_END_CONFIRM "`Quit?"
#define ENGLISH_MSG_YES "Yes"
#define ENGLISH_MSG_NO "No"
#define ENGLISH_MSG_OK "OK"
#define ENGLISH_MSG_CANCEL "Cancel"
#define ENGLISH_SAVE_MENU_NAME "<Save>"
#define ENGLISH_LOAD_MENU_NAME "<Load>"
#define ENGLISH_SAVE_ITEM_NAME "Slot"
#define ENGLISH_DECODER UTF8Decoder

// Japanese Menu Text
#define JAPANESE_MSG_SAVE_EXIST "%s%sÅ@%såé%sì˙%séû%sï™"
#define JAPANESE_MSG_SAVE_EMPTY "%s%sÅ@Å|Å|Å|Å|Å|Å|Å|Å|Å|Å|Å|Å|"
#define JAPANESE_MSG_SAVE_CONFIRM "%s%sÇ…ÉZÅ[ÉuÇµÇ‹Ç∑ÅBÇÊÇÎÇµÇ¢Ç≈Ç∑Ç©ÅH"
#define JAPANESE_MSG_LOAD_CONFIRM "%s%sÇÉçÅ[ÉhÇµÇ‹Ç∑ÅBÇÊÇÎÇµÇ¢Ç≈Ç∑Ç©ÅH"
#define JAPANESE_MSG_RESET_CONFIRM "ÉäÉZÉbÉgÇµÇ‹Ç∑ÅBÇÊÇÎÇµÇ¢Ç≈Ç∑Ç©ÅH"
#define JAPANESE_MSG_END_CONFIRM "èIóπÇµÇ‹Ç∑ÅBÇÊÇÎÇµÇ¢Ç≈Ç∑Ç©ÅH"
#define JAPANESE_MSG_YES "ÇÕÇ¢"
#define JAPANESE_MSG_NO "Ç¢Ç¢Ç¶"
#define JAPANESE_MSG_OK "ÇnÇj"
#define JAPANESE_MSG_CANCEL "ÉLÉÉÉìÉZÉã"
#define JAPANESE_SAVE_MENU_NAME "ÅÉÉZÅ[ÉuÅÑ"
#define JAPANESE_LOAD_MENU_NAME "ÅÉÉçÅ[ÉhÅÑ"
#define JAPANESE_SAVE_ITEM_NAME "ÇµÇ®ÇË"
#define JAPANESE_DECODER JapaneseDecoder

// Korean Menu Text
#define KOREAN_MSG_SAVE_EXIST "%s%s°°%sø˘%s¿œ%sΩ√%s∫–"
#define KOREAN_MSG_SAVE_EMPTY "%s%s°°£≠£≠£≠£≠£≠£≠£≠£≠£≠£≠£≠£≠"
#define KOREAN_MSG_SAVE_CONFIRM "%s%sø°°°¿˙¿ÂµÀ¥œ¥Ÿ£Æ°°»ÆΩ««’¥œ±Ó£ø"
#define KOREAN_MSG_LOAD_CONFIRM "%s%s¿ª°°∫“∑Øø√±Óø‰£ø"
#define KOREAN_MSG_RESET_CONFIRM "∞‘¿”¿Ã°°√ ±‚»≠µÀ¥œ¥Ÿ£Æ°°»ÆΩ««’¥œ±Ó£ø"
#define KOREAN_MSG_END_CONFIRM "¡æ∑·«œΩ√∞⁄Ω¿¥œ±Ó£ø"
#define KOREAN_MSG_YES "øπ"
#define KOREAN_MSG_NO "æ∆¥œø¿"
#define KOREAN_MSG_OK "»Æ¿Œ"
#define KOREAN_MSG_CANCEL "√Îº“"
#define KOREAN_SAVE_MENU_NAME "°¥ºº¿Ã∫Í°µ"
#define KOREAN_LOAD_MENU_NAME "°¥∑ŒµÂ°µ"
#define KOREAN_SAVE_ITEM_NAME "√•∞•««"
#define KOREAN_DECODER KoreanDecoder

class MenuTextBase
{
public:
    enum Language { JAPANESE, ENGLISH, KOREAN, RUSSIAN };

    MenuTextBase(const Language &lang) {
        language = lang;
    }
    ~MenuTextBase() {
        if (decoder) {
            delete decoder;
            decoder = NULL;
        }
    }

    Language getLanguage() { return language; };

    virtual const char* message_save_exist() = 0;
    virtual const char* message_save_empty() = 0;
    virtual const char* message_save_confirm() = 0;
    virtual const char* message_load_confirm() = 0;
    virtual const char* message_reset_confirm() = 0;
    virtual const char* message_end_confirm() = 0;
    virtual const char* message_yes() = 0;
    virtual const char* message_no() = 0;
    virtual const char* message_ok() = 0;
    virtual const char* message_cancel() = 0;
    virtual const char* message_save_menu() = 0;
    virtual const char* message_load_menu() = 0;
    virtual const char* message_save_item() = 0;

    ScriptDecoder* decoder;
protected:
    Language language;
};

// Lazy class definition
#define lazyMenuLangMake(cls, lang)                                        \
    class cls : public MenuTextBase                                       \
    {                                                                      \
    public:                                                               \
        cls():MenuTextBase(MenuTextBase::lang) {                           \
            decoder = new lang##_DECODER();                                \
        }                                                                  \
        ~cls() {}                                                          \
        const char* message_save_exist() {                                \
            static const char* save_exist = lang##_MSG_SAVE_EXIST;       \
            return save_exist;                                            \
        }                                                                  \
        const char* message_save_empty() {                                \
            static const char* save_empty = lang##_MSG_SAVE_EMPTY;       \
            return save_empty;                                            \
        }                                                                  \
        const char* message_save_confirm() {                              \
            static const char* save_confirm = lang##_MSG_SAVE_CONFIRM;   \
            return save_confirm;                                          \
        }                                                                  \
        const char* message_load_confirm() {                              \
            static const char* load_confirm = lang##_MSG_LOAD_CONFIRM;   \
            return load_confirm;                                          \
        }                                                                  \
        const char* message_reset_confirm() {                             \
            static const char* reset_confirm = lang##_MSG_RESET_CONFIRM; \
            return reset_confirm;                                         \
        }                                                                  \
        const char* message_end_confirm() {                               \
            static const char* end_confirm = lang##_MSG_END_CONFIRM;     \
            return end_confirm;                                           \
        }                                                                  \
        const char* message_yes() {                                       \
            static const char* yes = lang##_MSG_YES;                     \
            return yes;                                                   \
        }                                                                  \
        const char* message_no() {                                        \
            static const char* no = lang##_MSG_NO;                       \
            return no;                                                    \
        }                                                                  \
        const char* message_ok() {                                        \
            static const char* ok = lang##_MSG_OK;                       \
            return ok;                                                    \
        }                                                                  \
        const char* message_cancel() {                                    \
            static const char* cancel = lang##_MSG_CANCEL;               \
            return cancel;                                                \
        }                                                                  \
        const char* message_save_menu() {                                 \
            static const char* cancel = lang##_SAVE_MENU_NAME;           \
            return cancel;                                                \
        }                                                                  \
        const char* message_load_menu() {                                 \
            static const char* cancel = lang##_LOAD_MENU_NAME;           \
            return cancel;                                                \
        }                                                                  \
        const char* message_save_item() {                                 \
            static const char* cancel = lang##_SAVE_ITEM_NAME;           \
            return cancel;                                                \
        }                                                                  \
    };

lazyMenuLangMake(EnglishMenu, ENGLISH)
lazyMenuLangMake(JapaneseMenu, JAPANESE)
lazyMenuLangMake(RussianMenu, RUSSIAN)
#ifdef ENABLE_KOREAN
lazyMenuLangMake(KoreanMenu, KOREAN)
#endif

#endif // __MENUTEXT_H__
