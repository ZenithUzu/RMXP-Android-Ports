package org.libsdl.app;
public class SDLActivity {
    public interface KeyboardHook { void show(); void hide(); boolean shown(); }
    public static KeyboardHook sKeyboardHook;
    public static final java.util.Set<Integer> held = new java.util.HashSet<>();
    public static void onNativeKeyDown(int key) { held.add(key); }
    public static void onNativeKeyUp(int key) { held.remove(key); }
    public static void commitText(String text) {}
    public static android.view.Surface getNativeSurface() { return null; }
}
