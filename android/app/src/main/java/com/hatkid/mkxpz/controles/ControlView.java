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

    /** "Vibración" del menú (botones en pantalla y teclado). */
    public static boolean vibrar = true;

    public final Control control;
    private final Listener mListener;
    private final int mAccent;
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private boolean mPressed;
    /** Margen alrededor del botón para que la insignia de la tecla quepa entera (lo pone TouchControls). */
    float pad, badge;
    int skin; // 0 Moderno, 1 Contorno
    /** Transparencia del botón y de su insignia (por separado); color propio de la insignia (0 = el normal). */
    float bodyAlpha = 1, badgeAlpha = 1;
    boolean labelOwn, symbolsPass;
    int labelColor = Color.WHITE;
    float labelAlpha = 1;
    int badgeColor;
    boolean showBadge = true;
    boolean badgeLeft; // insignia en la esquina izquierda (botones del lado izquierdo, como L)
    /** Editor: borde punteado en todos y borde lleno en el elegido, para saber cuál se edita. */
    boolean editando, elegido;
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
        // El botón se dibuja dentro del margen; la insignia ocupa la esquina sin salirse de la vista
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(bodyAlpha * 255));
        canvas.translate(pad, pad);
        float w = getWidth() - 2 * pad, h = getHeight() - 2 * pad, sw = dp(2);
        symbolsPass = false;
        drawBody(canvas, w, h, sw);
        canvas.restore();
        if (labelOwn) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(labelAlpha * 255));
            canvas.translate(pad, pad);
            symbolsPass = true;
            drawBody(canvas, w, h, sw);
            canvas.restore();
            symbolsPass = false;
        }

        String key = Teclas.nombre(control.tecla);
        String label = control.etiqueta.isEmpty() ? key : control.etiqueta;
        if (showBadge && !control.cruceta && control.tecla > 0 && !label.equalsIgnoreCase(key)) {
            float bx = badgeLeft ? badge : getWidth() - badge, by = badge;
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(badgeAlpha * 255));
            mText.setShadowLayer(0, 0, 0, 0);
            if (skin == 0) {
                int c = badgeColor != 0 ? badgeColor : mAccent;
                mFill.setColor(0xFF000000 | c);
                canvas.drawCircle(bx, by, badge, mFill);
                mText.setColor(Color.luminance(c | 0xFF000000) > 0.5f ? 0xFF101010 : Color.WHITE);
            } else { // Contorno: insignia también solo con borde (blanco o el color elegido)
                int c = 0xFF000000 | (badgeColor != 0 ? badgeColor : 0xFFFFFF);
                mStroke.setColor(c);
                mStroke.setStrokeWidth(dp(1.5f));
                canvas.drawCircle(bx, by, badge - dp(1), mStroke);
                mText.setColor(c);
            }
            String k = key.length() > 4 ? key.substring(0, 4) : key;
            mText.setTextSize(badge * (k.length() > 2 ? 0.62f : k.length() == 2 ? 0.85f : 1.1f));
            canvas.drawText(k, bx, by - (mText.descent() + mText.ascent()) / 2, mText);
            canvas.restore();
        }
        if (editando) {
            mStroke.setColor(0xFF000000 | mAccent);
            mStroke.setStrokeWidth(dp(elegido ? 3 : 1.5f));
            mStroke.setPathEffect(elegido ? null : new android.graphics.DashPathEffect(new float[] { dp(5), dp(4) }, 0));
            float m = dp(2);
            canvas.drawRoundRect(m, m, getWidth() - m, getHeight() - m, dp(10), dp(10), mStroke);
            mStroke.setPathEffect(null);
        }
    }

    /** Color base: el elegido, o gris (Moderno) / blanco (Contorno) si no se eligió ninguno. */
    private int base()
    {
        if (control.systemColor)
            return skin == 0 ? getContext().getColor(com.hatkid.mkxpz.R.color.rmxp_pizarra) & 0xFFFFFF : 0xFFFFFF;
        return control.color & 0xFFFFFF;
    }

    /**
     * Estilos (pref "skin"). 0 Moderno: relleno gris translúcido y plano, sin borde, como los
     * controles de Lemuroid. 1 Contorno: fondo transparente, solo el borde y el texto en blanco.
     */
    private void paints(boolean pressed, float sw)
    {
        int c = base();
        mStroke.setStrokeWidth(sw);
        if (skin == 0) {
            mFill.setColor(pressed ? 0xE6000000 | (mAccent & 0xFFFFFF) : 0xC0000000 | c);
            mStroke.setColor(pressed ? mAccent : 0x30FFFFFF);
        } else {
            mFill.setColor(pressed ? 0xB3000000 | (mAccent & 0xFFFFFF) : 0x50101010);
            mStroke.setColor(0xF0000000 | c);
        }
        mText.setColor(pressed ? getContext().getColor(com.hatkid.mkxpz.R.color.rmxp_tinta) : Color.WHITE);
        if (labelOwn) mText.setColor(labelColor | 0xFF000000);
        mText.setShadowLayer(dp(skin == 0 ? 1.5f : 2.5f), 0, 0, skin == 0 ? 0x66000000 : 0xAA000000);
    }

    private void drawBody(Canvas canvas, float w, float h, float sw)
    {
        if (control.cruceta) {
            drawDpad(canvas, w, h, sw);
            return;
        }
        paints(mPressed, sw);
        if (control.tecla == Control.TECLADO) {
            drawKeyboard(canvas, w, h, sw);
            return;
        }
        mRect.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
        // Redondos; los anchos, rectángulos con esquinas suaves (no óvalos)
        float r = control.pastilla ? dp(14) : mRect.height() * 0.38f;
        if (!symbolsPass) {
            canvas.drawRoundRect(mRect, r, r, mFill);
            canvas.drawRoundRect(mRect, r, r, mStroke);
        }
        if (labelOwn && !symbolsPass) return;

        if (control.tecla < 0) {
            drawActionIcon(canvas, w, h);
        } else {
            String key = Teclas.nombre(control.tecla);
            String label = control.etiqueta.isEmpty() ? key : control.etiqueta;
            mText.setTextSize(Math.min(h * 0.4f, w * 0.75f / Math.max(1, label.length() * 0.6f)));
            canvas.drawText(label, w / 2, h / 2 - (mText.descent() + mText.ascent()) / 2, mText);
        }
    }

    /** El botón de teclado es el propio teclado (sin círculo alrededor): marco, teclas y espacio. */
    private void drawKeyboard(Canvas canvas, float w, float h, float sw)
    {
        float kw = w * 0.92f, kh = h * 0.62f, x0 = (w - kw) / 2, y0 = (h - kh) / 2, r = kh * 0.18f;
        mRect.set(x0, y0, x0 + kw, y0 + kh);
        if (!symbolsPass) {
            canvas.drawRoundRect(mRect, r, r, mFill);
            canvas.drawRoundRect(mRect, r, r, mStroke);
        }
        if (labelOwn && !symbolsPass) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(labelOwn ? labelColor | 0xFF000000 : Color.WHITE);
        p.setShadowLayer(dp(1.5f), 0, 0, 0x66000000);
        float gap = kw * 0.045f, key = (kw - 7 * gap) / 6, rowH = (kh - 4 * gap) / 3, kr = key * 0.18f;
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 6; col++) {
                float kx = x0 + gap + col * (key + gap), ky = y0 + gap + row * (rowH + gap);
                canvas.drawRoundRect(kx, ky, kx + key, ky + rowH, kr, kr, p);
            }
        float sy = y0 + gap + 2 * (rowH + gap);
        canvas.drawRoundRect(x0 + gap + key + gap, sy, x0 + kw - gap - key - gap, sy + rowH, kr, kr, p);
    }

    private void drawActionIcon(Canvas canvas, float w, float h)
    {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(labelOwn ? labelColor | 0xFF000000 : control.tecla == Control.TURBO && isActivated() ? mAccent : Color.WHITE);
        p.setShadowLayer(dp(1.5f), 0, 0, 0x66000000);
        float s = Math.min(w, h) * 0.2f, cx = w / 2, cy = h / 2;
        Path path = new Path();
        switch (control.tecla) {
            case Control.TURBO:
                path.moveTo(cx - 2 * s, cy - s); path.lineTo(cx, cy); path.lineTo(cx - 2 * s, cy + s); path.close();
                path.moveTo(cx, cy - s); path.lineTo(cx + 2 * s, cy); path.lineTo(cx, cy + s); path.close();
                canvas.drawPath(path, p);
                break;
            case Control.OCULTAR: {
                // Ojo tachado
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.3f);
                p.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawOval(cx - 1.7f * s, cy - 1f * s, cx + 1.7f * s, cy + 1f * s, p);
                canvas.drawCircle(cx, cy, 0.45f * s, p);
                canvas.drawLine(cx - 1.5f * s, cy + 1.3f * s, cx + 1.5f * s, cy - 1.3f * s, p);
                break;
            }
            default: // menú: tres líneas iguales, centradas y con puntas redondas
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.32f);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = -1; i <= 1; i++)
                    canvas.drawLine(cx - 1.3f * s, cy + i * 0.85f * s, cx + 1.3f * s, cy + i * 0.85f * s, p);
        }
    }

    private void drawDpad(Canvas canvas, float w, float h, float sw)
    {
        float t = w / 3, gap = dp(3), r = dp(12);
        for (int i = 0; i < 4; i++) {
            float x = i < 2 ? t : i == 2 ? 0 : 2*t;
            float y = i < 2 ? i*2*t : t;
            paints((mDir & (1 << i)) != 0, dp(1));
            mRect.set(x + gap, y + gap, x + t - gap, y + t - gap);
            if (!symbolsPass) {
                canvas.drawRoundRect(mRect, r, r, mFill);
                canvas.drawRoundRect(mRect, r, r, mStroke);
            }
        }
        if (labelOwn && !symbolsPass) return;
        // Flechas
        Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
        a.setColor(Color.WHITE);
        a.setShadowLayer(dp(1.5f), 0, 0, 0x66000000);
        float s = t * 0.15f, c = w / 2;
        a.setColor(labelOwn ? labelColor | 0xFF000000 : (mDir & 1) != 0 ? getContext().getColor(com.hatkid.mkxpz.R.color.rmxp_tinta) : Color.WHITE);
        drawArrow(canvas, a, c, t * 0.5f, 0, -s);
        a.setColor(labelOwn ? labelColor | 0xFF000000 : (mDir & 2) != 0 ? getContext().getColor(com.hatkid.mkxpz.R.color.rmxp_tinta) : Color.WHITE);
        drawArrow(canvas, a, c, h - t * 0.5f, 0, s);
        a.setColor(labelOwn ? labelColor | 0xFF000000 : (mDir & 4) != 0 ? getContext().getColor(com.hatkid.mkxpz.R.color.rmxp_tinta) : Color.WHITE);
        drawArrow(canvas, a, t * 0.5f, h / 2, -s, 0);
        a.setColor(labelOwn ? labelColor | 0xFF000000 : (mDir & 8) != 0 ? getContext().getColor(com.hatkid.mkxpz.R.color.rmxp_tinta) : Color.WHITE);
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

    public void releasePressed()
    {
        int[] keys = { KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT };
        for (int i = 0; i < keys.length; i++)
            if ((mDir & (1 << i)) != 0)
                mListener.release(keys[i]);
        if (mPressed)
            mListener.release(control.tecla);
        mDir = 0;
        mPressed = false;
        invalidate();
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
                float dead = (getWidth() - 2 * pad) * 0.12f;
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
                if (dir != 0) com.hatkid.mkxpz.Haptics.pulse(this);
                mDir = dir;
                invalidate();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN && !mPressed) {
            mPressed = true;
            mListener.press(control.tecla);
            com.hatkid.mkxpz.Haptics.pulse(this);
            invalidate();
        } else if (up && mPressed) {
            mPressed = false;
            mListener.release(control.tecla);
            invalidate();
        }
        return true;
    }
}
