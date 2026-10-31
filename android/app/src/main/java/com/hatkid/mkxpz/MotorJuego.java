package com.hatkid.mkxpz;

import org.libsdl.app.SDLActivity;

/** The existing UI sends input and display options to the selected runtime. */
public interface MotorJuego {
    void keyDown(int code);
    void keyUp(int code);
    void text(String text);
    void speed(int value);
    void stretch(int value);
    void fps(int target, int hz);
    int frames();
    default boolean web() { return false; }

    final class Rgss implements MotorJuego {
        public void keyDown(int code) { SDLActivity.onNativeKeyDown(code); }
        public void keyUp(int code) { SDLActivity.onNativeKeyUp(code); }
        public void text(String text) { SDLActivity.commitText(text); }
        public void speed(int value) { MainActivity.nativeSetSpeed(value); }
        public void stretch(int value) { MainActivity.nativeSetStretch(value); MainActivity.nativeSetVideo(0,0); }
        public int frames() { return MainActivity.nativeGetDrawnFrames(); }
        public void fps(int target, int hz) {
            MainActivity.nativeSetFps(target);
            android.view.Surface surface=SDLActivity.getNativeSurface();
            if(surface!=null && surface.isValid()) {
                if(android.os.Build.VERSION.SDK_INT>=31)
                    surface.setFrameRate(hz,android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,android.view.Surface.CHANGE_FRAME_RATE_ALWAYS);
                else if(android.os.Build.VERSION.SDK_INT>=30)
                    surface.setFrameRate(hz,android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }
        }
    }
}
