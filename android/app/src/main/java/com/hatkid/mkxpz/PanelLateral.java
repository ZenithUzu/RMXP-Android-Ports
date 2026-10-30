package com.hatkid.mkxpz;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared appearance for editor and controller settings; no floating system dialogs. */
public class PanelLateral {
    public final LinearLayout content, top, footer;
    public final Activity activity;
    private final FrameLayout root, shell;
    private final LinearLayout panel;

    public PanelLateral(Activity activity, int title) {
        this.activity = activity;
        ViewGroup host = activity.findViewById(android.R.id.content);
        View old = host.findViewWithTag("rmxp_side_panel");
        if (old != null) host.removeView(old);
        root = new FrameLayout(activity) {
            @Override public View focusSearch(View focused, int direction) {
                return android.view.FocusFinder.getInstance().findNextFocus(this, focused, direction);
            }
        };
        root.setTag("rmxp_side_panel");
        root.setBackgroundColor(activity.getColor(R.color.rmxp_velo));
        root.setOnClickListener(v -> dismiss());
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.rmxp_panel);
        panel.setPadding(dp(16), dp(8), dp(16), dp(8));
        panel.setOnClickListener(v -> {});
        int width = Math.min(dp(400), activity.getResources().getDisplayMetrics().widthPixels);
        shell = new FrameLayout(activity);
        shell.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        root.addView(shell, new FrameLayout.LayoutParams(width, -1, Gravity.END));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBaselineAligned(false);
        TextView text = label(activity.getString(title), 18);
        text.setMinHeight(dp(44));
        header.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = button(activity.getString(R.string.rmxp_edit_done), this::dismiss);
        close.setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(R.color.rmxp_ambar)));
        close.setTextColor(activity.getColor(R.color.rmxp_tinta));
        header.addView(close, new LinearLayout.LayoutParams(dp(76), dp(40)));
        panel.addView(header);
        top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.VERTICAL);
        panel.addView(top);
        ScrollView scroll = new ScrollView(activity);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        scroll.setClipToPadding(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(footer);
        host.addView(root, new ViewGroup.LayoutParams(-1, -1));
        close.requestFocus();
    }

    public static boolean dismissOpen(Activity activity) {
        ViewGroup host = activity.findViewById(android.R.id.content);
        View panel = host.findViewWithTag("rmxp_side_panel");
        if (panel == null) return false;
        View sheet = panel.findViewWithTag("rmxp_mapping_sheet");
        if (sheet != null) ((ViewGroup) sheet.getParent()).removeView(sheet);
        else host.removeView(panel);
        return true;
    }

    /** Modal sheet stays inside the drawer; background entries cannot receive focus. */
    public View showSheet(LinearLayout body) {
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setTag("rmxp_mapping_sheet");
        overlay.setBackgroundColor(0x99000000);
        overlay.setOnClickListener(v -> {});
        panel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(activity.getColor(R.color.rmxp_pizarra));
        bg.setCornerRadii(new float[]{dp(20),dp(20),dp(20),dp(20),0,0,0,0});
        body.setBackground(bg); body.setPadding(dp(20),dp(16),dp(20),dp(16));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false); scroll.addView(body);
        overlay.addView(scroll, new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        overlay.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int max = Math.max(dp(100), (b-t)*9/10);
            body.measure(View.MeasureSpec.makeMeasureSpec(r-l,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            ViewGroup.LayoutParams lp = scroll.getLayoutParams();
            int height = Math.min(max,body.getMeasuredHeight());
            if (lp.height != height) { lp.height=height; scroll.setLayoutParams(lp); }
        });
        overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) {}
            public void onViewDetachedFromWindow(View v) {
                panel.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        });
        shell.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        return overlay;
    }

    public int dp(float n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    public void dismiss() { if (root.getParent() != null) ((ViewGroup) root.getParent()).removeView(root); }
    public TextView label(String text, int size) {
        TextView v = new TextView(activity);
        v.setText(text); v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL);
        v.setTextColor(activity.getColor(R.color.rmxp_niebla));
        v.setPadding(0, dp(8), 0, dp(8));
        return v;
    }
    public void section(int text) { content.addView(label(activity.getString(text), 16)); }
    public Button button(String text, Runnable action) {
        Button b = new Button(activity);
        b.setAllCaps(false); b.setText(text); b.setTextSize(14);
        b.setGravity(Gravity.CENTER); b.setIncludeFontPadding(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setTextColor(activity.getColor(R.color.rmxp_niebla));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(activity.getColor(R.color.rmxp_pizarra)); bg.setCornerRadius(dp(12));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(activity.getColor(R.color.rmxp_pizarra)); focused.setCornerRadius(dp(12));
        focused.setStroke(dp(2), activity.getColor(R.color.rmxp_ambar));
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, bg);
        b.setBackground(states); b.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(4), 0, dp(4)); b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }
}
