LOCAL_PATH := $(call my-dir)
LOCAL_BUILD_PATH := $(call my-dir)/build-$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)

# FluidSynth (MIDI). mkxp-z loads it at run time (SDL_LoadObject), it is not linked.
LOCAL_MODULE := fluidsynth
LOCAL_SRC_FILES := $(LOCAL_BUILD_PATH)/lib/libfluidsynth.so

include $(PREBUILT_SHARED_LIBRARY)
