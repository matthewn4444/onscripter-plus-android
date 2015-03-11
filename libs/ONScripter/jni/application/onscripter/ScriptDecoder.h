#ifndef __LANGUAGE_DECODER_H__
#define __LANGUAGE_DECODER_H__

#include <stdio.h>
#include "onscripter_log.h"

class ScriptDecoder {
public:
    static bool inline isOneByte(char c) {
        return (c & 0x80) == 0;
    }

    static ScriptDecoder* detectAndAllocateScriptDecoder(char* buffer, size_t size);
    static ScriptDecoder* chooseDecoderForTextFromList(const char* buffer, ScriptDecoder** decoders, size_t n);

    virtual unsigned short convertNextChar(char* buffer);
    virtual bool canConvertNextChar(char* buffer, int* outNumBytes);
    virtual bool isMonospaced();

    virtual inline int getNumBytes(char c) {
        return 1;
    }

    virtual inline const char* getName() {
        return name;
    }

private:
    static const char* name;
};

class MultibyteDecoder : public ScriptDecoder {
public:
    virtual unsigned short convertNextChar(char* buffer);
    virtual bool canConvertNextChar(char* buffer, int* outNumBytes);
    virtual int getNumBytes(char c);

protected:
    MultibyteDecoder() {}
};

class JapaneseDecoder : public MultibyteDecoder {
public:
    virtual unsigned short convertNextChar(char* buffer);
    virtual bool canConvertNextChar(char* buffer, int* outNumBytes);
    virtual bool isMonospaced();

    virtual inline int getNumBytes(char c) {
        return isOneByte(c) ? 1 : 2;
    }

    virtual inline const char* getName() {
        return name;
    }

private:
    static const char* name;
};

#ifdef ENABLE_KOREAN
class KoreanDecoder : public MultibyteDecoder {
public:
    virtual unsigned short convertNextChar(char* buffer);
    virtual bool canConvertNextChar(char* buffer, int* outNumBytes);
    virtual bool isMonospaced();

    virtual inline int getNumBytes(char c) {
        return isOneByte(c) ? 1 : 2;
    }

    virtual inline const char* getName() {
        return name;
    }

private:
    static const char* name;
};
#endif

#ifdef ENABLE_CHINESE
class ChineseDecoder : public MultibyteDecoder {
public:
    virtual unsigned short convertNextChar(char* buffer);
    virtual bool canConvertNextChar(char* buffer, int* outNumBytes);
    virtual bool isMonospaced();

    virtual inline int getNumBytes(char c) {
        return isOneByte(c) ? 1 : 2;
    }

    virtual inline const char* getName() {
        return name;
    }

private:
    static const char* name;
};
#endif

class UTF8Decoder : public MultibyteDecoder {
public:
    static int UTF8_ERROR;

    virtual unsigned short convertNextChar(char* buffer);
    virtual bool canConvertNextChar(char* buffer, int* outNumBytes);

    virtual inline int getNumBytes(char c) {
        int n = MultibyteDecoder::getNumBytes(c);
        if (n)
            return n;
        return getByteLength(c);
    }

    virtual inline const char* getName() {
        return name;
    }

private:
    static int getByteLength(char c);
    static const char* name;
};

#endif //__LANGUAGE_DECODER_H__