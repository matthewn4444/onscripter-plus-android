package com.onscripter.plus;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class GameUtils {
    /* Supported encodings */
    private enum Encoding {
        UTF8, JAPANESE, KOREAN, CHINESE
    };

    /**
     * Basic parsing operations on a byte buffer to handle
     * its position and parsing through the script
     * @author Matthew Ng
     *
     */
    private static class BasicParser {
        public BasicParser(byte[] buffer) {
            mBuffer = buffer;
            mSize = buffer.length;
            mPos = 0;
        }

        public void skipWhiteText() {
            while(!eof() && (internalGetByte() == ' ' || internalGetByte() < '\n')) mPos++;
        }

        public void passCharacter(char c) {
            while(!eof() && getByte() != c);
        }

        public int getPosition() {
            return mPos;
        }

        public boolean eof() {
            return mPos >= mSize;
        }

        public byte[] copyRange(int from, int to) {
            return Arrays.copyOfRange(mBuffer, from, to);
        }

        protected byte internalGetByte() {
            return mBuffer[mPos];
        }

        public boolean nextMatchesAndSkip(String text) {
            if (internalGetByte() == text.charAt(0)){
                for (int i = 1; i < text.length(); i++) {
                    mPos++;
                    if (internalGetByte() != text.charAt(i)) {
                        return false;
                    }
                }
                mPos++;
                return true;
            }
            return false;
        }

        public byte getByte() {
            if (eof()) return 0;
            byte ret = internalGetByte();
            mPos++;
            return ret;
        }

        protected final byte[] mBuffer;
        protected final int mSize;
        protected int mPos;
    }

    /**
     * This extends basic parser that xors 0x84 on each byte to decode the script
     * @author Matthew Ng
     *
     */
    private static class NScriptParser extends BasicParser {
        public NScriptParser(byte[] buffer) {
            super(buffer);
        }

        private byte get(int pos) {
            return (byte) ((byte)(mBuffer[pos] ^ 0x84) & 0xFF);
        }

        @Override
        protected byte internalGetByte() {
            return get(mPos);
        }

        @Override
        public byte[] copyRange(int from, int to) {
            byte[] ret = new byte[to - from];
            for (int i = from; i < to; i++) {
                ret[i - from] = get(i);
            }
            return ret;
        }
    }

    /* Getting caption from file */

    /**
     * This gets the name of passed path. It gets the caption of the game
     * and returns it regardless of the language.
     *
     * Currently only nscript.dat, 0.txt and 00.txt are supported (in terms
     * of encryption). If these files are not found or if the file is
     * corrupted, it will return null.
     *
     * This is synchronized as it does I/O operations, please place this on
     * a thread.
     * @param gamePath is the folder with nscript.dat, 0.txt or 00.txt in it
     * @return the name of the game (by its caption)
     */
    public static String getGameName(String path) {
        if (path != null) {
            final String[] names = {"nscript.dat", "0.txt", "00.txt"};      // Too lazy to support "nscr_sec.dat & nscript.___" encryption
            File filepath = null;
            for (int i = 0; i < names.length; i++) {
                filepath = new File(path + "/" + names[i]);
                if (filepath.exists()) {
                    return getCaptionName(filepath);
                }
            }
        }
        return null;
    }

    private static String getCaptionName(File filepath) {
        DataInputStream dis = null;
        try {
            int size = filepath.length() > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE / 2 : (int)filepath.length();
            byte[] buffer = new byte[size];
            dis = new DataInputStream(new FileInputStream(filepath));
            dis.readFully(buffer, 0, size);

            BasicParser parser = filepath.getPath().endsWith(".txt") ? new BasicParser(buffer) : new NScriptParser(buffer);
            while(!parser.eof()) {
                // Skip spaces/tabs
                parser.skipWhiteText();

                if (parser.eof()) {
                    return null;
                }

                if (parser.nextMatchesAndSkip("caption")) {
                    parser.skipWhiteText();

                    if (parser.getByte() != '"') {
                        // Did not follow with quote
                        return null;
                    }

                    // Scan line till the end quote
                    int start = parser.getPosition();
                    parser.passCharacter('"');
                    byte[] data = parser.copyRange(start, parser.getPosition() - 1);
                    return convertLocaleBytes(data);
                }

                // Go to the end of the line
                parser.passCharacter('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    /**
     * Tries to decode the text using one of the supported encodings and outputs it
     * @param encoding
     * @param data
     * @return
     */
    static private String tryToDecodeString(Encoding encoding, byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if ((b & 0x80) == 0) {
                // 1 Byte character
                sb.append((char)b);
            } else {
                // 2 Byte characters
                if (i + 1 >= data.length) {
                    // Misaligned bytes in buffer
                    return null;
                }
                try {
                    byte nextByte = data[i + 1];
                    byte[] bytes = new byte[]{b, nextByte };
                    switch (encoding) {
                    case KOREAN:
                        if (isKorean(b, nextByte)) {
                            sb.append(new String(bytes, "EUC-KR"));
                        } else {
                            return null;
                        }
                        break;
                    case JAPANESE:
                        if (isJapanese(b)) {
                            sb.append(new String(bytes, "SHIFT_JIS"));
                        } else {
                            return null;
                        }
                        break;
                    case CHINESE:
                        if (isChinese(b, nextByte)) {
                            sb.append(new String(bytes, "GBK"));
                        } else {
                            return null;
                        }
                        break;
                    case UTF8:
                        int utf8Size = 0;
                        if ((utf8Size = UTF8ByteLength(b)) > 1) {
                            byte[] utf8Char = Arrays.copyOfRange(data, i, i + utf8Size);
                            sb.append(new String(utf8Char, "UTF-8").trim());
                        } else {
                            return null;
                        }
                        i += utf8Size - 2;
                        break;
                    }
                    i++;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return sb.toString();
    }

    /**
     * Decodes the text given the languages in its order
     * @param data
     * @return
     */
    static private String convertLocaleBytes(byte[] data) {
        // Tries each language till one works and we return it
        String text = null;
        if ( (text = tryToDecodeString(Encoding.JAPANESE, data)) != null
            || (text = tryToDecodeString(Encoding.KOREAN, data)) != null
            || (text = tryToDecodeString(Encoding.UTF8, data)) != null
            || (text = tryToDecodeString(Encoding.CHINESE, data)) != null) {
            return text;
        }
        return null;
    }

    // http://ash.jp/code/cn/gb2312tbl.htm
    static private boolean isChinese(byte c, byte c2) {
        int n = (c & 0xFF) << 8 | (c2 & 0xFF);
        return (n >= 0xA1A0 && n <= 0xfcfc) == true;
    }

    static private int UTF8ByteLength(byte c) {
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
        return -1;
    }

    static private boolean isJapanese(byte c) {
        return ( (c & 0xe0) == 0xe0 || (c & 0xe0) == 0x80 );
    }

    // http://ftp.unicode.org/Public/MAPPINGS/VENDORS/APPLE/KOREAN.TXT
    static private boolean isKorean(byte c, byte c2) {
        int n = (c & 0xFF) << 8 | (c2 & 0xFF);
        return
        /* Hangul syllables */  ((n >= 0xB0A1 && n <= 0xC8FE)
        /* Standard Korean */ || (n >= 0xA141 && n <= 0xA974)
        /* Chinese Gylphs */  || (n >= 0xD0A1 && n <= 0xFDFE)
                                    ) == true;
    }
}
