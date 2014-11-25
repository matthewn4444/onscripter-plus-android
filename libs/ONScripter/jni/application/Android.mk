LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := application

APP_SUBDIR := $(firstword $(patsubst $(LOCAL_PATH)/%, %, onscripter))

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(APP_SUBDIR) \
	$(LOCAL_PATH)/.. \
	$(LOCAL_PATH)/../sdl/include \
	$(LOCAL_PATH)/../sdl_mixer \
	$(LOCAL_PATH)/../sdl_image \
	$(LOCAL_PATH)/../sdl_ttf \
	$(LOCAL_PATH)/../lua/src \
	$(LOCAL_PATH)/../lua/etc \
	$(LOCAL_PATH)/../bzip2-1.0.5 \
	$(LOCAL_PATH)/../libmad-0.15.1b \
	$(LOCAL_PATH)/../tremor

# optional: enable English mode
DEFS += -DENABLE_ENGLISH

# optional: enable Korean text
DEFS += -DENABLE_KOREAN

LOCAL_CFLAGS := $(LOCAL_C_INCLUDES:%=-I%) \
	-DSDL_JAVA_PACKAGE_PATH=$(SDL_JAVA_PACKAGE_PATH) \
	-DLINUX -DMP3_MAD -DPDA_AUTOSIZE -DUSE_OGG_VORBIS -DINTEGER_OGG_VORBIS -DUTF8_FILESYSTEM -DUSE_LUA \
	$(DEFS)

#Change C++ file extension as appropriate
LOCAL_CPP_EXTENSION := .cpp

OBJSUFFIX := .o
EXT_OBJS = LUAHandler.o
include $(LOCAL_PATH)/$(APP_SUBDIR)/Makefile.onscripter
LOCAL_SRC_FILES := $(addprefix $(APP_SUBDIR)/,$(patsubst %.o, %.cpp, $(ONSCRIPTER_OBJS)))

LOCAL_STATIC_LIBRARIES := sdl sdl_mixer sdl_image sdl_ttf lua bz2 mad tremor

LOCAL_LDLIBS := -lGLESv1_CM -ldl -llog -lz -lGLESv1_CM

LIBS_WITH_LONG_SYMBOLS := $(strip $(shell \
	for f in $(LOCAL_PATH)/../../libs/armeabi/*.so ; do \
		if echo $$f | grep "libapplication[.]so" > /dev/null ; then \
			continue ; \
		fi ; \
		if [ -e "$$f" ] ; then \
			if nm -g $$f | cut -c 12- | egrep '.{128}' > /dev/null ; then \
				echo $$f | grep -o 'lib[^/]*[.]so' ; \
			fi ; \
		fi ; \
	done \
) )

ifneq "$(LIBS_WITH_LONG_SYMBOLS)" ""
$(foreach F, $(LIBS_WITH_LONG_SYMBOLS), \
$(info Library $(F): abusing symbol names are: \
$(shell nm -g $(LOCAL_PATH)/../../libs/armeabi/$(F) | cut -c 12- | egrep '.{128}' ) ) \
$(info Library $(F) contains symbol names longer than 128 bytes, \
YOUR CODE WILL DEADLOCK WITHOUT ANY WARNING when you'll access such function - \
please make this library static to avoid problems. ) )
$(error Detected libraries with too long symbol names. Remove all files under project/libs/armeabi, make these libs static, and recompile)
endif

include $(BUILD_SHARED_LIBRARY)
