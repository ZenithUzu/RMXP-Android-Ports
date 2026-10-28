package com.hatkid.mkxpz;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/** Corre en su propio proceso: cierra el juego y lo vuelve a abrir limpio. */
public class Reinicio extends Activity
{
    @Override
    protected void onCreate(Bundle b)
    {
        super.onCreate(b);
        Process.killProcess(getIntent().getIntExtra("pid", 0));
        Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (i != null)
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
        Process.killProcess(Process.myPid());
    }
}
