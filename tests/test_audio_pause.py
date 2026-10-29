"""Exercise the production pause helper against a real OpenAL Soft mixer (null output)."""
import ctypes as C
import ctypes.util
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AudioPauseTest(unittest.TestCase):
    @unittest.skipUnless(ctypes.util.find_library('openal'), 'OpenAL Soft unavailable')
    def test_freezes_and_resumes_sample_cursor(self):
        os.environ['ALSOFT_DRIVERS'] = 'null'
        with tempfile.TemporaryDirectory() as tmp:
            d = Path(tmp)
            (d/'alc.h').write_text('''#define ALC_APIENTRY
extern "C" {
struct ALCcontext; struct ALCdevice;
ALCcontext *alcGetCurrentContext();
ALCdevice *alcGetContextsDevice(ALCcontext *);
void *alcGetProcAddress(ALCdevice *, const char *);
}
''')
            (d/'pause.cpp').write_text('#include "rmxp-audio-pause.h"\nextern "C" bool pauseAudio(bool p){return rmxpSetAudioPaused(p);}')
            subprocess.run(['c++','-shared','-fPIC','-I'+tmp,'-I'+str(ROOT/'android/app/jni/mkxp-z/src/display'),str(d/'pause.cpp'),'-l:libopenal.so.1','-o',str(d/'pause.so')],check=True,capture_output=True)
            lib = C.CDLL(ctypes.util.find_library('openal'))
            lib.alcOpenDevice.restype=C.c_void_p
            lib.alcCreateContext.argtypes=[C.c_void_p,C.c_void_p];lib.alcCreateContext.restype=C.c_void_p
            lib.alcMakeContextCurrent.argtypes=[C.c_void_p]
            lib.alcDestroyContext.argtypes=[C.c_void_p];lib.alcCloseDevice.argtypes=[C.c_void_p]
            helper=C.CDLL(str(d/'pause.so'));helper.pauseAudio.argtypes=[C.c_bool];helper.pauseAudio.restype=C.c_bool
            self.assertFalse(helper.pauseAudio(True))
            device=lib.alcOpenDevice(None);self.assertTrue(device)
            context=lib.alcCreateContext(device,None);self.assertTrue(context)
            lib.alcMakeContextCurrent(context)
            buffer=C.c_uint();source=C.c_uint()
            try:
                lib.alGenBuffers(1,C.byref(buffer));lib.alGenSources(1,C.byref(source))
                pcm=(C.c_short*32000)()
                lib.alBufferData(buffer.value,0x1101,pcm,C.sizeof(pcm),8000)
                lib.alSourcei(source.value,0x1009,buffer.value)
                lib.alSourcePlay(source.value)
                time.sleep(.10)
                self.assertTrue(helper.pauseAudio(True));self.assertTrue(helper.pauseAudio(True))
                offset=C.c_int();lib.alGetSourcei(source.value,0x1025,C.byref(offset));paused=offset.value
                time.sleep(.15);lib.alGetSourcei(source.value,0x1025,C.byref(offset))
                self.assertEqual(offset.value,paused)
                self.assertTrue(helper.pauseAudio(False));self.assertTrue(helper.pauseAudio(False))
                time.sleep(.15);lib.alGetSourcei(source.value,0x1025,C.byref(offset))
                self.assertGreater(offset.value,paused)
            finally:
                lib.alSourceStop(source.value);lib.alDeleteSources(1,C.byref(source));lib.alDeleteBuffers(1,C.byref(buffer))
                lib.alcMakeContextCurrent(None);lib.alcDestroyContext(context);lib.alcCloseDevice(device)
