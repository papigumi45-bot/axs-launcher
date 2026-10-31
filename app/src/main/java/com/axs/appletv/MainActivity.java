package com.axs.appletv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Launcher ligero estilo Apple TV.
 * - Fondo: imagen opcional res/drawable-nodpi/bg_cinema.jpg, si no hay, degradado oscuro.
 * - Dock grande abajo (apps de config.xml) y segunda fila con el resto.
 * - Sin blur, sin video, sin red. El reloj se actualiza con el aviso del sistema cada minuto.
 */
public class MainActivity extends Activity {

    private TextView clock, focusName;
    private HorizontalScrollView dockScroll, appsScroll;
    private LinearLayout dockRow, appsRow;
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private AppEntry settingsEntry;
    private boolean dirty = false;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    static class AppEntry {
        final String label, pkg;
        final Drawable banner, icon;
        final Intent intent;
        AppEntry(String label, String pkg, Drawable banner, Drawable icon, Intent intent) {
            this.label = label; this.pkg = pkg; this.banner = banner; this.icon = icon; this.intent = intent;
        }
    }

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { dirty = true; }
    };

    // ---------- ciclo de vida ----------

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        hideSystemUi();
        buildScreen();
        loadApps();
        renderRows();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgReceiver, pf);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tickReceiver, tf);
        updateClock();
        if (dirty) {
            dirty = false;
            loadApps();
            renderRows();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(tickReceiver); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Al pulsar Home: volver al inicio del dock
        focusFirst();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true; // un launcher no se cierra con Atras
        return super.onKeyDown(keyCode, event);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ---------- helpers ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable ring() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(3), Color.WHITE);
        return g;
    }

    // ---------- pantalla ----------

    private void buildScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Fondo
        Bitmap bmp = loadBackground();
        if (bmp != null) {
            ImageView bg = new ImageView(this);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setImageBitmap(bmp);
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        } else {
            View bg = new View(this);
            bg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2B3347, 0xFF12151D, 0xFF000000}));
            root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        }

        // Sombra inferior para que se lea el texto
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00000000, 0xFA000000}));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, dp(340));
        sp.gravity = Gravity.BOTTOM;
        root.addView(scrim, sp);

        // Contenido
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(20), dp(28), dp(16));

        clock = text("", 15, 0xCCFFFFFF);
        clock.setGravity(Gravity.RIGHT);
        clock.setPadding(0, 0, dp(14), 0);
        content.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        focusName = text("", 30, Color.WHITE);
        focusName.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        focusName.setMaxLines(1);
        focusName.setEllipsize(TextUtils.TruncateAt.END);
        focusName.setPadding(dp(14), 0, 0, dp(2));
        content.addView(focusName, new LinearLayout.LayoutParams(-1, -2));

        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        dockScroll.addView(dockRow);
        content.addView(dockScroll, new LinearLayout.LayoutParams(-1, -2));

        appsScroll = new HorizontalScrollView(this);
        appsScroll.setHorizontalScrollBarEnabled(false);
        appsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        appsRow.setPadding(dp(14), dp(8), dp(14), dp(8));
        appsScroll.addView(appsRow);
        content.addView(appsScroll, new LinearLayout.LayoutParams(-1, -2));

        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private Bitmap loadBackground() {
        try {
            int id = getResources().getIdentifier("bg_cinema", "drawable", getPackageName());
            if (id == 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), id, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1280) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(getResources(), id, d);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- apps ----------

    private List<ResolveInfo> query(PackageManager pm, String category) {
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(category);
        List<ResolveInfo> r = pm.queryIntentActivities(q, 0);
        return r == null ? new ArrayList<ResolveInfo>() : r;
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        String cat = Intent.CATEGORY_LEANBACK_LAUNCHER;
        List<ResolveInfo> found = query(pm, cat);
        if (found.isEmpty()) {
            cat = Intent.CATEGORY_LAUNCHER;
            found = query(pm, cat);
        }
        String own = getPackageName();
        for (ResolveInfo r : found) {
            ActivityInfo ai = r.activityInfo;
            if (ai == null || ai.packageName.equals(own)) continue;
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(cat);
                i.setClassName(ai.packageName, ai.name);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Drawable banner = null;
                try { banner = ai.loadBanner(pm); } catch (Throwable ignored) {}
                apps.add(new AppEntry(r.loadLabel(pm).toString(), ai.packageName,
                    banner, r.loadIcon(pm), i));
            } catch (Throwable ignored) {}
        }
        apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

        Intent s = new Intent(android.provider.Settings.ACTION_SETTINGS);
        s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        settingsEntry = new AppEntry("Ajustes", own, null, getDrawable(R.drawable.ic_settings), s);
    }

    private void renderRows() {
        dockRow.removeAllViews();
        appsRow.removeAllViews();

        ArrayList<AppEntry> dock = new ArrayList<>();
        String[] pins = getResources().getStringArray(R.array.dock_apps);
        for (String p : pins) {
            for (AppEntry a : apps) {
                if (a.pkg.equals(p) && !dock.contains(a)) dock.add(a);
            }
        }
        if (dock.isEmpty()) {
            for (int i = 0; i < apps.size() && i < 6; i++) dock.add(apps.get(i));
        }

        for (AppEntry a : dock) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160), dp(90));
            lp.rightMargin = dp(16);
            dockRow.addView(makeTile(a, 90), lp);
        }
        for (AppEntry a : apps) {
            if (dock.contains(a)) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(63));
            lp.rightMargin = dp(14);
            appsRow.addView(makeTile(a, 63), lp);
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(112), dp(63));
        sl.rightMargin = dp(14);
        appsRow.addView(makeTile(settingsEntry, 63), sl);

        dockRow.post(new Runnable() {
            @Override public void run() { focusFirst(); }
        });
    }

    private View makeTile(final AppEntry app, int heightDp) {
        FrameLayout card = new FrameLayout(this);

        GradientDrawable base = new GradientDrawable();
        base.setColor(0xFF1F232C);
        base.setCornerRadius(dp(14));
        card.setBackground(base);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
            }
        });

        if (app.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(app.banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.setPadding(dp(4), 0, dp(4), 0);
            int s = heightDp > 70 ? 44 : 30;
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(app.icon);
            col.addView(iv, new LinearLayout.LayoutParams(dp(s), dp(s)));
            TextView t = text(app.label, heightDp > 70 ? 12 : 10, Color.WHITE);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.topMargin = dp(3);
            col.addView(t, tp);
            card.addView(col, new FrameLayout.LayoutParams(-1, -1));
        }

        final GradientDrawable focusRing = ring();
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnFocusChangeListener((v, has) -> {
            float scale = has ? 1.08f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(110).start();
            v.setForeground(has ? focusRing : null);
            if (has) focusName.setText(app.label);
        });
        card.setOnClickListener(v -> launch(app));
        return card;
    }

    private void launch(AppEntry app) {
        try {
            startActivity(app.intent);
        } catch (Exception ignored) {}
    }

    private void focusFirst() {
        if (dockScroll == null) return;
        dockScroll.scrollTo(0, 0);
        appsScroll.scrollTo(0, 0);
        View f = null;
        if (dockRow.getChildCount() > 0) f = dockRow.getChildAt(0);
        else if (appsRow.getChildCount() > 0) f = appsRow.getChildAt(0);
        if (f != null) f.requestFocus();
    }

    private void updateClock() {
        if (clock != null) clock.setText(timeFmt.format(new Date()));
    }
}
