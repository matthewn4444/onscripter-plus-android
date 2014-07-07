LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := bz2
LOCAL_CFLAGS += -D_FILE_OFFSET_BITS=64
LOCAL_SRC_FILES := blocksort.c \
	huffman.c    \
	crctable.c   \
	randtable.c  \
	compress.c   \
	decompress.c \
	bzlib.c

include $(BUILD_SHARED_LIBRARY)
