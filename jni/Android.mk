LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_LDLIBS := -lm -llog
LOCAL_MODULE := iotest
LOCAL_SRC_FILES := com_example_vod_MainActivity.c
include $(BUILD_SHARED_LIBRARY)