LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := freetype

APP_SUBDIRS := src  \
    src/autofit  \
    src/base  \
    src/bdf  \
    src/cache  \
    src/cff  \
    src/cid  \
    src/gxvalid  \
    src/gzip  \
    src/lzw  \
    src/otvalid  \
    src/pcf  \
    src/pfr  \
    src/psaux  \
    src/pshinter  \
    src/psnames  \
    src/raster  \
    src/sfnt  \
    src/smooth  \
    src/truetype  \
    src/type1  \
    src/type42  \
    src/winfonts

# Add more subdirs here, like src/subdir1 src/subdir2

LOCAL_C_INCLUDES := $(foreach D, $(APP_SUBDIRS), $(LOCAL_PATH)/$(D)) $(LOCAL_PATH)/include

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%) \
	-DFT2_BUILD_LIBRARY

#Change C++ file extension as appropriate
LOCAL_CPP_EXTENSION := .cpp

LOCAL_SRC_FILES := $(foreach F, $(APP_SUBDIRS), $(addprefix $(F)/,$(notdir $(wildcard $(LOCAL_PATH)/$(F)/*.cpp))))
# Uncomment to also add C sources
LOCAL_SRC_FILES += $(foreach F, $(APP_SUBDIRS), $(addprefix $(F)/,$(notdir $(wildcard $(LOCAL_PATH)/$(F)/*.c))))

LOCAL_SHARED_LIBRARIES := 

LOCAL_STATIC_LIBRARIES := 

LOCAL_LDLIBS :=

include $(BUILD_STATIC_LIBRARY)
