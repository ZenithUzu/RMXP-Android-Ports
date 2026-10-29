package com.hatkid.mkxpz;

import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import com.hatkid.mkxpz.controles.ControlView;

public final class Haptics {
    private Haptics() {}
    public static void pulse(View view) {
        if (!ControlView.vibrar) return;
        Vibrator vibrator = (Vibrator) view.getContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26)
            vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
        else vibrator.vibrate(12);
    }
}
