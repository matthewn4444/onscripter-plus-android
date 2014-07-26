#ifndef __UTF8_DECODE_H__
#define __UTF8_DECODE_H__

#define IS_UTF8(x) \
    UTF8ByteLength(x) > 1

// Get the 6-bit payload of the next continuation byte, else UTF8_ERROR
#define CONT_BYTE(b) \
    (((b & 0xFF) & 0xC0) == 0x80) ? ((b & 0xFF) & 0x3F) : UTF8_ERROR;

static const int UTF8_ERROR = -1;

static int UTF8ByteLength(char c) {
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

/*  Decode UTF8 character to Unicode (UTF16)
 *      Returns unicode and the number of bytes it used otherwise UTF8_ERROR
 */
static unsigned short decodeUTF8Character(char* bytes, int* outBytesCount) {
    if (!bytes || !*bytes) return UTF8_ERROR;
    // Parse out how many bytes for this character
    int c = bytes[0] & 0xFF, c1, c2, c3, r, len;
    len = UTF8ByteLength(bytes[0]);
    
    switch(len) {
        case 1:
            if (outBytesCount) *outBytesCount = 1;
            return c;
        case 2:
            c1 = CONT_BYTE(bytes[1]);
            if (c1 > UTF8_ERROR) {
                r = ((c & 0x1F) << 6) | c1;
                if (outBytesCount) *outBytesCount = 2;
                return r >= 128 ? r : UTF8_ERROR;
            }
            break;
        case 3:
            c1 = CONT_BYTE(bytes[1]);
            c2 = CONT_BYTE(bytes[2]);
            if ((c1 | c2) > UTF8_ERROR) {
                r = ((c & 0x0F) << 12) | (c1 << 6) | c2;
                if (outBytesCount) *outBytesCount = 3;
                return r >= 2048 && (r < 55296 || r > 57343) ? r : UTF8_ERROR;
            }
            break;
        case 4:
            c1 = CONT_BYTE(bytes[1]);
            c2 = CONT_BYTE(bytes[2]);
            c3 = CONT_BYTE(bytes[3]);
            if ((c1 | c2 | c3) > UTF8_ERROR) {
                if (outBytesCount) *outBytesCount = 4;
                return (((c & 0x0F) << 18) | (c1 << 12) | (c2 << 6) | c3) + 65536;
            }
            break;
    }
    return UTF8_ERROR;
}


#endif // __UTF8_DECODE_H__