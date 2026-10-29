package com.hatkid.mkxpz;
/** Only the native engine is simulated. All UI classes/resources come from app/src/main. */
public class MainActivity extends android.app.Activity {
    public static int speed=1, fps;
    public static void nativeSetSpeed(int v) { speed=v; }
    public static void nativeSetFps(int v) { fps=v; }
    public static void nativeSetStretch(int v) {}
    public static void nativeSetVideo(int a,int b) {}
    public static int nativeGetDrawnFrames() { return 0; }
}
