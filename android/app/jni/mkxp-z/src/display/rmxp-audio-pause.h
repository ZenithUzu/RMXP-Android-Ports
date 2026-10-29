#pragma once
#include <alc.h>

// Pause the mixer, preserving all source cursors, including music and sound effects.
inline bool rmxpSetAudioPaused(bool paused) {
    ALCcontext *context = alcGetCurrentContext();
    if (!context) return false;
    ALCdevice *device = alcGetContextsDevice(context);
    if (!device) return false;
    typedef void (ALC_APIENTRY *DeviceControl)(ALCdevice *);
    DeviceControl control = reinterpret_cast<DeviceControl>(alcGetProcAddress(device,
        paused ? "alcDevicePauseSOFT" : "alcDeviceResumeSOFT"));
    if (!control) return false;
    control(device);
    return true;
}
