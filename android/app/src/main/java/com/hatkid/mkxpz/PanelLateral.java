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
    public final LinearLayout content, top;
    public final Activity activity;
    private final FrameLayout root;

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
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.rmxp_panel);
        panel.setPadding(dp(20), dp(12), dp(20), dp(12));
        panel.setOnClickListener(v -> {});
        int width = Math.min(dp(360), activity.getResources().getDisplayMetrics().widthPixels);
        root.addView(panel, new FrameLayout.LayoutParams(width, -1, Gravity.END));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = label(activity.getString(title), 20);
        header.addView(text, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button close = button(activity.getString(R.string.rmxp_edit_done), this::dismiss);
        header.addView(close, new LinearLayout.LayoutParams(-2, dp(44)));
        panel.addView(header);
        top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.VERTICAL);
        panel.addView(top);
        ScrollView scroll = new ScrollView(activity);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        host.addView(root, new ViewGroup.LayoutParams(-1, -1));
        close.requestFocus();
    }

    public static boolean dismissOpen(Activity activity) {
        ViewGroup host = activity.findViewById(android.R.id.content);
        View panel = host.findViewWithTag("rmxp_side_panel");
        if (panel == null) return false;
        host.removeView(panel);
        return true;
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
