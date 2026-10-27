package com.hatkid.mkxpz.controles;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Dibuja un control translúcido (relleno suave del color elegido + contorno claro),
 * con su etiqueta grande y, si la tecla es distinta, una insignia pequeña con la tecla.
 */
@SuppressLint("ViewConstructor")
public class ControlView extends View
{
    public interface Listener
    {
        void press(int tecla);
        void release(int tecla);
        /** En el editor: los toques mueven o abren la edición en vez de pulsar. */
        boolean editTouch(ControlView v, MotionEvent e);
    }

    public final Control control;
    private final Listener mListener;
    private final int mAccent;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private boolean mPressed;
    private int mDir; // cruceta: bits 1 arriba, 2 abajo, 4 izquierda, 8 derecha

    public ControlView(Context ctx, Control c, int accent, Listener listener)
    {
        super(ctx);
        control = c;
        mAccent = accent;
        mListener = listener;
        mStroke.setStyle(Paint.Style.STROKE);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setContentDescription(c.etiqueta.isEmpty() ? Teclas.nombre(c.tecla) : c.etiqueta);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onDraw(Canvas canvas)
    {
        float w = getWidth(), h = getHeight(), sw = dp(2);
        int rgb = control.color & 0xFFFFFF;
        mStroke.setStrokeWidth(sw);
        mStroke.setColor(0xD0000000 | (control.color == 0xFFFFFFFF ? 0xFFFFFF : rgb));
        mRect.set(sw, sw, w - sw, h - sw);

        if (control.cruceta) {
            drawDpad(canvas, w, h, rgb);
            return;
        }

        mFill.setColor((mPressed ? 0x90000000 : 0x40000000) | rgb);
        float r = control.pastilla ? (h - 2 * sw) / 2 : Math.min(w, h) / 2 - sw;
        canvas.drawRoundRect(mRect, r, r, mFill);
        canvas.drawRoundRect(mRect, r, r, mStroke);

        String key = Teclas.nombre(control.tecla);
        String label = control.etiqueta.isEmpty() ? key : control.etiqueta;
        mText.setColor(Color.WHITE);
        mText.setShadowLayer(dp(2), 0, 0, 0x99000000);
        if (control.tecla < 0) {
            drawActionIcon(canvas, w, h);
        } else {
            mText.setTextSize(Math.min(h * 0.42f, w * 0.8f / Math.max(1, label.length() * 0.6f)));
            canvas.drawText(label, w / 2, h / 2 - (mText.descent() + mText.ascent()) / 2, mText);
        }

        // Insignia con la tecla real, como en Kirin, solo si la etiqueta no lo dice ya
        if (control.tecla > 0 && !label.equalsIgnoreCase(key)) {
            float br = Math.max(dp(9), h * 0.17f);
            float bx = w - br * 0.9f, by = br * 0.9f;
            mFill.setColor(mAccent);
            canvas.drawCircle(bx, by, br, mFill);
            mText.setShadowLayer(0, 0, 0, 0);
            mText.setTextSize(br * (key.length() > 2 ? 0.7f : 1.05f));
            mText.setColor(0xFF101010);
            canvas.drawText(key.length() > 4 ? key.substring(0, 4) : key, bx, by - (mText.descent() + mText.ascent()) / 2, mText);
        }
    }

    private void drawActionIcon(Canvas canvas, float w, float h)
    {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(control.tecla == Control.TURBO && isActivated() ? mAccent : Color.WHITE);
        float s = Math.min(w, h) * 0.22f, cx = w / 2, cy = h / 2;
        Path path = new Path();
        switch (control.tecla) {
            case Control.TURBO:
                path.moveTo(cx - 2 * s, cy - s); path.lineTo(cx, cy); path.lineTo(cx - 2 * s, cy + s); path.close();
                path.moveTo(cx, cy - s); path.lineTo(cx + 2 * s, cy); path.lineTo(cx, cy + s); path.close();
                canvas.drawPath(path, p);
                break;
            case Control.TECLADO: {
                // Marco del teclado, dos filas de teclas y la barra espaciadora
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(1.6f));
                canvas.drawRoundRect(cx - 2.2f * s, cy - 1.4f * s, cx + 2.2f * s, cy + 1.4f * s, dp(3), dp(3), p);
                p.setStyle(Paint.Style.FILL);
                float k = s * 0.5f;
                for (int row = 0; row < 2; row++)
                    for (int col = 0; col < 5; col++) {
                        float kx = cx - 1.6f * s + col * 0.8f * s, ky = cy - 0.8f * s + row * 0.7f * s;
                        canvas.drawRect(kx - k / 2, ky - k / 2, kx + k / 2, ky + k / 2, p);
                    }
                canvas.drawRect(cx - s, cy + 0.65f * s, cx + s, cy + 0.65f * s + k, p);
                break;
            }
            default: // menú u ocultar: tres líneas
                for (int i = -1; i <= 1; i++)
                    canvas.drawRect(cx - 1.6f * s, cy + i * 0.8f * s - dp(1.2f), cx + 1.6f * s, cy + i * 0.8f * s + dp(1.2f), p);
        }
    }

    private void drawDpad(Canvas canvas, float w, float h, int rgb)
    {
        float t = w / 3; // grosor de cada brazo
        Path cross = new Path();
        cross.addRoundRect(new RectF(t, 0, 2 * t, h), dp(10), dp(10), Path.Direction.CW);
        cross.addRoundRect(new RectF(0, t, w, 2 * t), dp(10), dp(10), Path.Direction.CW);
        cross.setFillType(Path.FillType.WINDING);
        mFill.setColor(0x40000000 | rgb);
        canvas.drawPath(cross, mFill);
        // Resalta la dirección pulsada
        mFill.setColor(0x90000000 | rgb);
        if ((mDir & 1) != 0) canvas.drawRect(t, 0, 2 * t, t, mFill);
        if ((mDir & 2) != 0) canvas.drawRect(t, 2 * t, 2 * t, h, mFill);
        if ((mDir & 4) != 0) canvas.drawRect(0, t, t, 2 * t, mFill);
        if ((mDir & 8) != 0) canvas.drawRect(2 * t, t, w, 2 * t, mFill);
        Path outline = new Path();
        outline.op(new Path() {{ addRoundRect(new RectF(t, dp(1), 2 * t, h - dp(1)), dp(10), dp(10), Direction.CW); }},
                   new Path() {{ addRoundRect(new RectF(dp(1), t, w - dp(1), 2 * t), dp(10), dp(10), Direction.CW); }},
                   Path.Op.UNION);
        canvas.drawPath(outline, mStroke);
        // Flechas
        Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
        a.setColor(Color.WHITE);
        float s = t * 0.18f, c = w / 2;
        drawArrow(canvas, a, c, t * 0.5f, 0, -s);
        drawArrow(canvas, a, c, h - t * 0.5f, 0, s);
        drawArrow(canvas, a, t * 0.5f, h / 2, -s, 0);
        drawArrow(canvas, a, w - t * 0.5f, h / 2, s, 0);
    }

    private void drawArrow(Canvas canvas, Paint p, float x, float y, float dx, float dy)
    {
        Path path = new Path();
        path.moveTo(x + dx, y + dy);
        path.lineTo(x - dx - dy, y - dy + dx);
        path.lineTo(x - dx + dy, y - dy - dx);
        path.close();
        canvas.drawPath(path, p);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        if (mListener.editTouch(this, e))
            return true;
        int action = e.getActionMasked();
        boolean down = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
        boolean up = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;

        if (control.cruceta) {
            int dir = 0;
            if (down) {
                float dx = e.getX() - getWidth() / 2f, dy = e.getY() - getHeight() / 2f;
                float dead = getWidth() * 0.12f;
                // Diagonales permitidas: cada eje cuenta si pasa la zona muerta y no es mucho menor que el otro
                if (dy < -dead && -dy > Math.abs(dx) * 0.4f) dir |= 1;
                if (dy > dead && dy > Math.abs(dx) * 0.4f) dir |= 2;
                if (dx < -dead && -dx > Math.abs(dy) * 0.4f) dir |= 4;
                if (dx > dead && dx > Math.abs(dy) * 0.4f) dir |= 8;
            }
            int[] keys = { KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT };
            for (int i = 0; i < 4; i++) {
                boolean was = (mDir & (1 << i)) != 0, is = (dir & (1 << i)) != 0;
                if (is && !was) mListener.press(keys[i]);
                if (was && !is) mListener.release(keys[i]);
            }
            if (dir != mDir) {
                mDir = dir;
                invalidate();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN && !mPressed) {
            mPressed = true;
            mListener.press(control.tecla);
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            invalidate();
        } else if (up && mPressed) {
            mPressed = false;
            mListener.release(control.tecla);
            invalidate();
        }
        return true;
    }
}
