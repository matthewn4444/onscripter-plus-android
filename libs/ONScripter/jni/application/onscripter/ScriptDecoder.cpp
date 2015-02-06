#include "ScriptDecoder.h"
#include "utf8_decode.h"

#ifdef ENABLE_KOREAN
extern unsigned short convKOR2UTF16(unsigned short code);
#endif

#ifdef ENABLE_CHINESE
extern unsigned short convGBK2UTF16(unsigned short code);
#endif

extern unsigned short convSJIS2UTF16( unsigned short in );

const char* ScriptDecoder::name = "One byte";

bool ScriptDecoder::canConvertNextChar(char* buffer, int* outNumBytes)
{
    *outNumBytes = 1;
    return isOneByte(*buffer);
}

unsigned short ScriptDecoder::convertNextChar(char* buffer)
{
    if ((buffer[0] & 0xe0) == 0xa0 || (buffer[0] & 0xe0) == 0xc0) {
        return ((unsigned char*) buffer)[0] - 0xa0 + 0xff60;
    } else {
        return *buffer;
    }
}

bool ScriptDecoder::canConvertSJISNumber(int n)
{   // 0x824f = '0' and 0x8258 = '9' in shift-jis
    return n >= 0x824f && n <= 0x8258;
}

unsigned short ScriptDecoder::convertSJISNumber(int n)
{
    return canConvertSJISNumber(n) ? convSJIS2UTF16(n) : 0;
}

bool ScriptDecoder::isMonospaced()
{
    return false;
}

ScriptDecoder* ScriptDecoder::detectAndAllocateScriptDecoder(char* buffer, size_t size)
{
    const int TOTAL_COUNT_FINISHED = 20;
    ScriptDecoder* decoders[] = { new JapaneseDecoder()
#ifdef ENABLE_KOREAN
                                , new KoreanDecoder()
#endif
#ifdef ENABLE_CHINESE
                                , new ChineseDecoder()
#endif
                                };

    size_t numDecoders = sizeof(decoders) / sizeof(decoders[0]);
    int* langCounter = new int[numDecoders]();
    ScriptDecoder* foundDecoder = 0;
    int lineNum = 1;
    size_t i = 0;

    // Read line routine
    while(i < size && !foundDecoder) {
        // Skip the spaces at the beginning of each line
        while(i < size && buffer[i] == ' ') i++;
        if (i >= size) break;

        // Skip square brackets if they exist
        if (buffer[i] == '[') {
            do i++; while(i < size && buffer[i] != ']' && buffer[i] != '\n');
            if (++i >= size) break;

            // Incorrect format, skip this line
            if (buffer[i] == '\n') {
                lineNum++;
                continue;
            }
        }

        char c = buffer[i];
        if (c == '`') {
            // This says that the text is guarenteed to be English/UTF8
            foundDecoder = new UTF8Decoder();
            break;
        } else if (!ScriptDecoder::isOneByte(c)) {
            // We need to detect Asian language
            for (size_t j = 0; j < numDecoders && !foundDecoder; j++) {
                // Get line length
                char* start = buffer + i;
                char* ptr = start;
                while(*ptr != '\n' && *ptr != '\0') ptr++;
                size_t lineLength = ptr - start;
                if (*ptr == '\0' || !lineLength) break;

                // Parse through each character on this line and see which decoders work
                ScriptDecoder* decoder = decoders[j];
                int k = 0, outNumBytes = 0;
                while (k < lineLength && !foundDecoder) {
                    if (!decoder->canConvertNextChar(buffer + k + i, &outNumBytes)) {
                        break;
                    }
                    k += outNumBytes;

                    // Successfully read the entire line
                    if (k == lineLength) {
                        langCounter[j]++;

                        // Found our decoder
                        if (langCounter[j] >= TOTAL_COUNT_FINISHED) {
                            foundDecoder = decoders[j];
                        }
                    }
                }
            }
        }
        lineNum++;

        // Skip to the next line
        while(i < size && buffer[i] != '\n') i++;
        if (++i >= size) break;
    }

    delete langCounter;

    for (size_t i = 0; i < numDecoders; i++) {
        // Delete everything except found decoder
        if (foundDecoder != decoders[i]) {
            delete decoders[i];
        }
    }
    return foundDecoder;
}

ScriptDecoder* ScriptDecoder::chooseDecoderForTextFromList(const char* buffer, ScriptDecoder** decoders, size_t n)
{
    int size = 0;
    while(*(buffer + size)) size++;     // strlen without include <string.h>

    int i, b = 0;
    for (int k = 0; k < n; k++) {
        i = 0;
        while(i < size) {
            if (!decoders[k]->canConvertNextChar(const_cast<char*>(buffer + i), &b)) {
                break;
            }
            i += b;

            // Read the entire buffer
            if (i == size) {
                return decoders[k];
            }
        }
    }
    return NULL;
}

/*
 *  JapaneseDecoder
 */
const char* JapaneseDecoder::name = "Japanese";

unsigned short JapaneseDecoder::convertNextChar(char* buffer)
{
    if (isOneByte(*buffer)) {
        return ScriptDecoder::convertNextChar(buffer);
    }
    unsigned short index = (*buffer & 0xFF) << 8 | (buffer[1] & 0xFF);
    return convSJIS2UTF16(index);
}

bool JapaneseDecoder::canConvertNextChar(char* buffer, int* outNumBytes)
{
    if (ScriptDecoder::canConvertNextChar(buffer, outNumBytes)) return true;
    *outNumBytes = 2;
    return ( ((*buffer) & 0xe0) == 0xe0 || ((*buffer) & 0xe0) == 0x80 );
}

bool JapaneseDecoder::isMonospaced()
{
    return true;
}

#ifdef ENABLE_KOREAN
/*
 *  KoreanDecoder
 */
const char* KoreanDecoder::name = "Korean";

unsigned short KoreanDecoder::convertNextChar(char* buffer)
{
    if (isOneByte(*buffer)) {
        return ScriptDecoder::convertNextChar(buffer);
    }
    unsigned short index = (*buffer & 0xFF) << 8 | (buffer[1] & 0xFF);
    unsigned short ret = convertSJISNumber(index);
    return ret ? ret : convKOR2UTF16(index);
}

bool KoreanDecoder::canConvertNextChar(char* buffer, int* outNumBytes)
{
    if (ScriptDecoder::canConvertNextChar(buffer, outNumBytes)) return true;
    *outNumBytes = 2;
    int x = (*buffer & 0xFF) << 8 | (buffer[1] & 0xFF);
    if (x == 0x8140 || canConvertSJISNumber(x)) return true;    // Hardcoded Space and Japanese numbers
    // http://ftp.unicode.org/Public/MAPPINGS/VENDORS/APPLE/KOREAN.TXT
    return  /* Hangul syllables */  ((x >= 0xB0A1 && x <= 0xC8FE) \
            /* Standard Korean */ || (x >= 0xA141 && x <= 0xA974) \
            /* Chinese Gylphs */  || (x >= 0xEFA1 && x <= 0xFDFE) \
                                    ) == true;
}

bool KoreanDecoder::isMonospaced()
{
    return true;
}
#endif

 #ifdef ENABLE_CHINESE
/*
 *  ChineseDecoder
 */
const char* ChineseDecoder::name = "Chinese";

unsigned short ChineseDecoder::convertNextChar(char* buffer)
{
    if (isOneByte(*buffer)) {
        return ScriptDecoder::convertNextChar(buffer);
    }
    unsigned short index = (*buffer & 0xFF) << 8 | (buffer[1] & 0xFF);
    unsigned short ret = convertSJISNumber(index);
    return ret ? ret : convGBK2UTF16(index);
}

bool ChineseDecoder::canConvertNextChar(char* buffer, int* outNumBytes)
{
    if (ScriptDecoder::canConvertNextChar(buffer, outNumBytes)) return true;
    *outNumBytes = 2;
    int n = (*buffer & 0xFF) << 8 | (buffer[1] & 0xFF);
    if (n == 0x8140 || canConvertSJISNumber(n)) return true;    // Hardcoded Space and Japanese numbers
    return (n >= 0xA1A0 && n <= 0xfcfc) == true;
}

bool ChineseDecoder::isMonospaced()
{
    return true;
}
#endif

/*
 *  UTF8Decoder
 */
const char* UTF8Decoder::name = "UTF8";
int UTF8Decoder::UTF8_ERROR = -1;

int UTF8Decoder::getByteLength(char c)
{
    // 1 Byte (0 to 127)
    if ((c & 0x80) == 0) {
        return 1;
    }
    // 2 Bytes (128 - 2047)
    else if ((c & 0xE0) == 0xC0) {
        return 2;
    }
    // 3 Bytes (2048 to 55295 and 57344 to 65535)
    else if ((c & 0xF0) == 0xE0) {
        return 3;
    }
    // 4 Bytes (65536 to 1114111)
    else if ((c & 0xF8) == 0xF0) {
        return 4;
    }
    return UTF8_ERROR;
}

unsigned short UTF8Decoder::convertNextChar(char* buffer)
{
    if (isOneByte(*buffer)) {
        return ScriptDecoder::convertNextChar(buffer);
    }
    return decodeUTF8Character(buffer, NULL);
}

bool UTF8Decoder::canConvertNextChar(char* buffer, int* outNumBytes)
{
    if (ScriptDecoder::canConvertNextChar(buffer, outNumBytes)) return true;
    int length = getByteLength(*buffer);
    *outNumBytes = length;
    return (bool)(length > 1);
}