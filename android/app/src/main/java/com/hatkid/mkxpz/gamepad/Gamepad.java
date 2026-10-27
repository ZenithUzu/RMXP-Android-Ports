package com.hatkid.mkxpz.gamepad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.animation.AlphaAnimation;
import android.widget.RelativeLayout;

import com.hatkid.mkxpz.R;
import com.hatkid.mkxpz.utils.ViewUtils;

public class Gamepad
{
    private GamepadConfig mGamepadConfig = null;
    private boolean mInvisible = false;

    private OnKeyDownListener mOnKeyDownListener = key -> {};
    private OnKeyUpListener mOnKeyUpListener = key -> {};

    public interface OnKeyDownListener
    {
        void onKeyDown(int key);
    }

    public interface OnKeyUpListener
    {
        void onKeyUp(int key);
    }

    public void setOnKeyDownListener(OnKeyDownListener onKeyDownListener)
    {
        mOnKeyDownListener = onKeyDownListener;
    }

    public void setOnKeyUpListener(OnKeyUpListener onKeyUpListener)
    {
        mOnKeyUpListener = onKeyUpListener;
    }

    private RelativeLayout mGamepadLayout;

    // Gamepad buttons
    private GamepadButton gpadBtnA;
    private GamepadButton gpadBtnB;
    private GamepadButton gpadBtnC;
    private GamepadButton gpadBtnX;
    private GamepadButton gpadBtnY;
    private GamepadButton gpadBtnZ;
    private GamepadButton gpadBtnL;
    private GamepadButton gpadBtnR;
    private GamepadButton gpadBtnCTRL;
    private GamepadButton gpadBtnALT;
    private GamepadButton gpadBtnSHIFT;

    public void init(GamepadConfig gpadConfig, boolean invisible)
    {
        mGamepadConfig = gpadConfig;
        mInvisible = invisible;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void attachTo(Context context, ViewGroup viewGroup)
    {
        // Setup layout of in-screen gamepad
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.gamepad_layout, viewGroup);
        mGamepadLayout = layout.findViewById(R.id.gamepad_layout);

        if (mInvisible) {
            mGamepadLayout.setAlpha(0);
        }

        // Setup D-Pad and buttons
        GamepadDPad gpadDPad = layout.findViewById(R.id.dpad);
        gpadBtnA = layout.findViewById(R.id.button_A);
        gpadBtnB = layout.findViewById(R.id.button_B);
        gpadBtnC = layout.findViewById(R.id.button_C);
        gpadBtnX = layout.findViewById(R.id.button_X);
        gpadBtnY = layout.findViewById(R.id.button_Y);
        gpadBtnZ = layout.findViewById(R.id.button_Z);
        gpadBtnL = layout.findViewById(R.id.button_L);
        gpadBtnR = layout.findViewById(R.id.button_R);
        gpadBtnCTRL = layout.findViewById(R.id.button_CTRL);
        gpadBtnALT = layout.findViewById(R.id.button_ALT);
        gpadBtnSHIFT = layout.findViewById(R.id.button_SHIFT);

        // Setup in-screen gamepad listeners
        mGamepadLayout.setOnTouchListener((view, motionEvent) -> false);
        gpadDPad.setOnKeyDownListener(key -> mOnKeyDownListener.onKeyDown(key));
        gpadDPad.setOnKeyUpListener(key -> mOnKeyUpListener.onKeyUp(key));

        // Configure gamepad
        gpadDPad.isDiagonal = mGamepadConfig.diagonalMovement;

        // Setup buttons for gamepad
        initGamepadButtons();

        // Apply scale and opacity from gamepad config
        ViewUtils.resize(mGamepadLayout, mGamepadConfig.scale);
        ViewUtils.changeOpacity(mGamepadLayout, mGamepadConfig.opacity);
    }

    private void setGamepadButtonKey(GamepadButton gpadBtn, Integer keycode)
    {
        // Prepare label for gamepad button
        String btnLabel = KeyEvent.keyCodeToString(keycode)
            .replace("KEYCODE_", "")
            .replace("_LEFT", "")
            .replace("_RIGHT", "");

        // Set gamepad button
        gpadBtn.setForegroundText(btnLabel);
        gpadBtn.setKey(keycode);
        gpadBtn.setOnKeyDownListener(key -> mOnKeyDownListener.onKeyDown(key));
        gpadBtn.setOnKeyUpListener(key -> mOnKeyUpListener.onKeyUp(key));
    }

    /**
     * RMXP-Android-Ports: opacity (10-100), size (50-150 %) and visibility from the options menu.
     * Size uses view scaling (not ViewUtils.resize) so it can change live without adding up.
     */
    public void applyLook(int opacity, int sizePercent, boolean visible)
    {
        if (mGamepadLayout == null)
            return;
        mGamepadLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        ViewUtils.changeOpacity(mGamepadLayout, opacity);
        float scale = sizePercent / 100f;
        for (int i = 0; i < mGamepadLayout.getChildCount(); i++) {
            View child = mGamepadLayout.getChildAt(i);
            int[] rules = ((RelativeLayout.LayoutParams) child.getLayoutParams()).getRules();
            boolean end = rules[RelativeLayout.ALIGN_PARENT_END] != 0;
            boolean middle = rules[RelativeLayout.CENTER_HORIZONTAL] != 0 && rules[RelativeLayout.ALIGN_PARENT_START] == 0;
            boolean bottom = rules[RelativeLayout.ALIGN_PARENT_BOTTOM] != 0;
            // Grow from the screen corner each group sits in, so nothing slides off screen
            child.post(() -> {
                child.setPivotX(end ? child.getWidth() : middle ? child.getWidth() / 2f : 0);
                child.setPivotY(bottom ? child.getHeight() : 0);
                child.setScaleX(scale);
                child.setScaleY(scale);
            });
        }
    }

    public void showView()
    {
        if (mGamepadLayout != null) {
            if (mGamepadLayout.getAlpha() == 0)
                mGamepadLayout.setAlpha(1);

            AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(250);
            anim.setFillAfter(true);
            mGamepadLayout.startAnimation(anim);
        }
    }

    public void hideView()
    {
        if (mGamepadLayout != null) {
            AlphaAnimation anim = new AlphaAnimation(1.0f, 0.0f);
            anim.setDuration(500);
            anim.setFillAfter(true);
            mGamepadLayout.startAnimation(anim);
        }
    }

    private void initGamepadButtons()
    {
        setGamepadButtonKey(gpadBtnA, mGamepadConfig.keycodeA);
        setGamepadButtonKey(gpadBtnB, mGamepadConfig.keycodeB);
        setGamepadButtonKey(gpadBtnC, mGamepadConfig.keycodeC);
        setGamepadButtonKey(gpadBtnX, mGamepadConfig.keycodeX);
        setGamepadButtonKey(gpadBtnY, mGamepadConfig.keycodeY);
        setGamepadButtonKey(gpadBtnZ, mGamepadConfig.keycodeZ);
        setGamepadButtonKey(gpadBtnL, mGamepadConfig.keycodeL);
        setGamepadButtonKey(gpadBtnR, mGamepadConfig.keycodeR);
        setGamepadButtonKey(gpadBtnCTRL, mGamepadConfig.keycodeCTRL);
        setGamepadButtonKey(gpadBtnALT, mGamepadConfig.keycodeALT);
        setGamepadButtonKey(gpadBtnSHIFT, mGamepadConfig.keycodeSHIFT);
    }

    public boolean processGamepadEvent(KeyEvent evt)
    {
        InputDevice device = evt.getDevice();

        if (device == null)
            return false;

        int sources = device.getSources();

        if (
            ((sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) &&
            ((sources & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD)
        )
            return false;

        int keycode = evt.getKeyCode();

        switch (evt.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                mOnKeyDownListener.onKeyDown(keycode);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mOnKeyUpListener.onKeyUp(keycode);
                break;
        }

        return true;
    }

    public boolean processDPadEvent(MotionEvent evt)
    {
        InputDevice device = evt.getDevice();

        if (device == null)
            return false;

        int sources = device.getSources();

        if (((sources & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD))
            return false;

        float xAxis = evt.getAxisValue(MotionEvent.AXIS_HAT_X);
        float yAxis = evt.getAxisValue(MotionEvent.AXIS_HAT_Y);

        Integer keycode = null;

        if (Float.compare(yAxis, -1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_UP;
        else if (Float.compare(yAxis, 1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_DOWN;
        else if (Float.compare(xAxis, -1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_LEFT;
        else if (Float.compare(xAxis, 1.0f) == 0)
            keycode = KeyEvent.KEYCODE_DPAD_RIGHT;

        if (keycode == null)
            return false;

        switch (evt.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                mOnKeyDownListener.onKeyDown(keycode);
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mOnKeyUpListener.onKeyUp(keycode);
                break;
        }

        return true;
    }
}