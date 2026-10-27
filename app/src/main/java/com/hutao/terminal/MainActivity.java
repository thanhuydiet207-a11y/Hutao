package com.hutao.terminal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.terminal.TerminalColors;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MainActivity extends Activity implements TerminalSessionClient, TerminalViewClient {
    private static final String TAG = "Hutao";
    private static final int SCROLLBACK_ROWS = 10000;
    private static final List<TerminalSession> SESSIONS = TermService.SESSIONS;

    // Extra keys: label -> Android keycode, or 0 for "type the label".
    private static final String[][] EXTRA_KEYS = {
            {"ESC", "" + KeyEvent.KEYCODE_ESCAPE}, {"TAB", "" + KeyEvent.KEYCODE_TAB},
            {"CTRL", "-1"}, {"ALT", "-2"},
            {"←", "" + KeyEvent.KEYCODE_DPAD_LEFT}, {"↓", "" + KeyEvent.KEYCODE_DPAD_DOWN},
            {"↑", "" + KeyEvent.KEYCODE_DPAD_UP}, {"→", "" + KeyEvent.KEYCODE_DPAD_RIGHT},
            {"HOME", "" + KeyEvent.KEYCODE_MOVE_HOME}, {"END", "" + KeyEvent.KEYCODE_MOVE_END},
            {"PGUP", "" + KeyEvent.KEYCODE_PAGE_UP}, {"PGDN", "" + KeyEvent.KEYCODE_PAGE_DOWN},
            {"-", "0"}, {"/", "0"}, {"|", "0"}, {"~", "0"}, {"$", "0"},
            {"⌨", "-3"}, {"PASTE", "-4"},
    };

    private TerminalView terminal;
    private LinearLayout tabs;
    private TextView ctrlButton, altButton;
    private boolean ctrl, alt;
    private int fontSize = 28;
    // User-chosen tab names win over titles set by programs (escape sequences).
    private static final Map<TerminalSession, String> CUSTOM_NAMES = new java.util.WeakHashMap<>();
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorScheme();
        setContentView(R.layout.main);
        terminal = findViewById(R.id.terminal);
        tabs = findViewById(R.id.tabs);
        terminal.setTerminalViewClient(this);
        prefs = getPreferences(MODE_PRIVATE);
        fontSize = prefs.getInt("fontSize", fontSize);
        terminal.setTextSize(fontSize);
        applySettings();
        View menu = findViewById(R.id.menu);
        menu.setOnClickListener(v -> showMenu(menu));
        findViewById(R.id.new_tab).setOnClickListener(v -> switchTo(newSession()));
        TextView linux = findViewById(R.id.linux);
        linux.setOnClickListener(v -> openLinux(linux));
        buildExtraKeys();
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        // Process may have outlived a previous activity: re-adopt its sessions.
        for (TerminalSession s : SESSIONS) s.updateTerminalSessionClient(this);
        switchTo(SESSIONS.isEmpty() ? newSession() : SESSIONS.get(SESSIONS.size() - 1));
    }

    private TerminalSession newSession() {
        return newSession(false);
    }

    private TerminalSession newSession(boolean linux) {
        File home = new File(getFilesDir(), "home");
        File bin = new File(home, "bin");
        bin.mkdirs();
        linkStorage(home);
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", getCacheDir().getAbsolutePath());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("LANG", "en_US.UTF-8");
        env.put("PATH", bin + ":" + env.getOrDefault("PATH", "/system/bin"));
        env.put("ENV", home + "/.shrc"); // startup script, like ~/.bashrc
        Linux.putProotEnv(this, env);
        try {
            Linux.writeLauncherScript(this, bin, env);
        } catch (IOException e) {
            logStackTrace(TAG, e);
        }
        String[] envArr = new String[env.size()];
        int i = 0;
        for (Map.Entry<String, String> e : env.entrySet()) envArr[i++] = e.getKey() + "=" + e.getValue();

        String[] argv = linux ? Linux.command(this) : new String[]{"/system/bin/sh", "-sh"};
        TerminalSession s = new TerminalSession(argv[0], home.getAbsolutePath(),
                java.util.Arrays.copyOfRange(argv, linux ? 0 : 1, argv.length), envArr, SCROLLBACK_ROWS, this);
        s.mSessionName = (linux ? "alpine " : "sh ") + (SESSIONS.size() + 1);
        SESSIONS.add(s);
        startKeepAlive();
        return s;
    }

    private void openLinux(TextView button) {
        if (Linux.isInstalled(this)) { switchTo(newSession(true)); return; }
        button.setEnabled(false);
        new Thread(() -> {
            try {
                Linux.install(this, msg -> runOnUiThread(() -> button.setText(msg)));
                runOnUiThread(() -> switchTo(newSession(true)));
            } catch (Exception e) {
                logStackTrace(TAG, e);
                runOnUiThread(() -> Toast.makeText(this, "Cài Linux lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            runOnUiThread(() -> { button.setText("🐧 Linux"); button.setEnabled(true); });
        }).start();
    }

    /** ~/storage -> shared storage (/sdcard), so files are reachable from the shell. */
    private static void linkStorage(File home) {
        java.nio.file.Path link = new File(home, "storage").toPath();
        if (java.nio.file.Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try {
            java.nio.file.Files.createSymbolicLink(link,
                    android.os.Environment.getExternalStorageDirectory().toPath());
        } catch (IOException e) {
            android.util.Log.w(TAG, "storage link", e);
        }
    }

    private void startKeepAlive() {
        Intent i = new Intent(this, TermService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void switchTo(TerminalSession s) {
        terminal.attachSession(s);
        terminal.requestFocus();
        refreshTabs();
    }

    /** Terminal palette matching the app theme; applies to sessions created afterwards. */
    private void applyColorScheme() {
        Properties p = new Properties();
        p.put("background", hex(R.color.bg));
        p.put("foreground", hex(R.color.text));
        p.put("cursor", hex(R.color.accent));
        TerminalColors.COLOR_SCHEME.updateWith(p);
    }

    private String hex(int colorRes) {
        return String.format("#%06X", getColor(colorRes) & 0xFFFFFF);
    }

    private TextView chip(String text) {
        // Same look as the @style/Chip views in main.xml.
        TextView v = new TextView(this);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int) (32 * dp));
        lp.setMarginEnd((int) (6 * dp));
        v.setLayoutParams(lp);
        v.setGravity(android.view.Gravity.CENTER);
        v.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
        v.setTextSize(13);
        v.setText(text);
        return v;
    }

    private void refreshTabs() {
        tabs.removeAllViews();
        for (TerminalSession s : SESSIONS) {
            TextView b = chip(tabName(s));
            boolean current = s == terminal.getCurrentSession();
            b.setBackgroundResource(current ? R.drawable.chip_selected : R.drawable.chip);
            b.setTextColor(getColor(current ? R.color.on_accent : R.color.text));
            b.setOnClickListener(v -> switchTo(s));
            b.setOnLongClickListener(v -> { showTabMenu(v, s); return true; });
            tabs.addView(b);
        }
    }

    private static String tabName(TerminalSession s) {
        String name = CUSTOM_NAMES.get(s);
        if (name != null) return name;
        String title = s.getTitle();
        return title != null && !title.isEmpty() ? title : s.mSessionName;
    }

    private void showTabMenu(View anchor, TerminalSession s) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add("Đổi tên").setOnMenuItemClickListener(i -> { renameTab(s); return true; });
        m.getMenu().add("Đóng").setOnMenuItemClickListener(i -> { s.finishIfRunning(); return true; });
        m.show();
    }

    private void renameTab(TerminalSession s) {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setText(tabName(s));
        input.selectAll();
        new android.app.AlertDialog.Builder(this)
                .setTitle("Đổi tên tab")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) CUSTOM_NAMES.remove(s);
                    else CUSTOM_NAMES.put(s, name);
                    refreshTabs();
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        addToggle(m, "Ảnh nền", "background", true);
        addToggle(m, "Font JetBrains Mono", "jetbrainsFont", true);
        addToggle(m, "Giữ màn hình sáng", "keepScreenOn", false);
        addToggle(m, "Rung khi có chuông (bell)", "vibrateOnBell", true);
        m.getMenu().add("Wake lock (chạy khi tắt màn hình)").setCheckable(true)
                .setChecked(TermService.isWakeLocked())
                .setOnMenuItemClickListener(i -> {
                    if (!TermService.isWakeLocked()) askIgnoreBatteryOptimizations();
                    startService(new Intent(this, TermService.class).setAction(TermService.ACTION_WAKE_LOCK));
                    return true;
                });
        m.show();
    }

    /** A wake lock alone does not stop Doze/vendor battery savers from killing the app; this exemption does. */
    private void askIgnoreBatteryOptimizations() {
        if (getSystemService(android.os.PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Máy không hỗ trợ tắt tối ưu pin cho app", Toast.LENGTH_SHORT).show();
        }
    }

    private void addToggle(PopupMenu m, String label, String key, boolean def) {
        m.getMenu().add(label).setCheckable(true).setChecked(prefs.getBoolean(key, def))
                .setOnMenuItemClickListener(i -> {
                    prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply();
                    applySettings();
                    return true;
                });
    }

    private void applySettings() {
        findViewById(R.id.background).setVisibility(prefs.getBoolean("background", true) ? View.VISIBLE : View.GONE);
        terminal.setTypeface(prefs.getBoolean("jetbrainsFont", true)
                ? Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMono-Regular.ttf")
                : Typeface.MONOSPACE);
        terminal.setKeepScreenOn(prefs.getBoolean("keepScreenOn", false));
    }

    private void buildExtraKeys() {
        LinearLayout row = findViewById(R.id.extra_keys);
        float dp = getResources().getDisplayMetrics().density;
        for (String[] key : EXTRA_KEYS) {
            TextView b = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (int) (40 * dp));
            lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
            b.setLayoutParams(lp);
            b.setMinWidth((int) (44 * dp));
            b.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
            b.setGravity(android.view.Gravity.CENTER);
            b.setBackgroundResource(R.drawable.key);
            b.setTextColor(getColor(R.color.text));
            b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            b.setTextSize(13);
            b.setText(key[0]);
            int code = Integer.parseInt(key[1]);
            if (code == -1) ctrlButton = b;
            if (code == -2) altButton = b;
            b.setOnClickListener(v -> onExtraKey(key[0], code));
            row.addView(b);
        }
    }

    private void onExtraKey(String label, int code) {
        if (code == -1) { ctrl = !ctrl; updateModifierButtons(); return; }
        if (code == -2) { alt = !alt; updateModifierButtons(); return; }
        if (code == -3) { getSystemService(InputMethodManager.class).toggleSoftInput(0, 0); return; }
        if (code == -4) { onPasteTextFromClipboard(terminal.getCurrentSession()); return; }
        if (code == 0) {
            terminal.inputCodePoint(label.codePointAt(0), ctrl, alt);
        } else {
            int mod = (ctrl ? com.termux.terminal.KeyHandler.KEYMOD_CTRL : 0)
                    | (alt ? com.termux.terminal.KeyHandler.KEYMOD_ALT : 0);
            terminal.handleKeyCode(code, mod);
        }
        clearModifiers();
    }

    private void clearModifiers() {
        if (!ctrl && !alt) return;
        ctrl = alt = false;
        updateModifierButtons();
    }

    private void updateModifierButtons() {
        ctrlButton.setTextColor(getColor(ctrl ? R.color.accent : R.color.text));
        altButton.setTextColor(getColor(alt ? R.color.accent : R.color.text));
    }

    // ---- TerminalSessionClient ----

    @Override public void onTextChanged(TerminalSession s) {
        if (s == terminal.getCurrentSession()) terminal.onScreenUpdated();
    }

    @Override public void onTitleChanged(TerminalSession s) { refreshTabs(); }

    @Override public void onSessionFinished(TerminalSession s) {
        int idx = SESSIONS.indexOf(s);
        SESSIONS.remove(s);
        if (SESSIONS.isEmpty()) {
            stopService(new Intent(this, TermService.class));
            finish();
            return;
        }
        if (s == terminal.getCurrentSession()) switchTo(SESSIONS.get(Math.max(0, idx - 1)));
        else refreshTabs();
    }

    @Override public void onCopyTextToClipboard(TerminalSession s, String text) {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("terminal", text));
    }

    @Override public void onPasteTextFromClipboard(TerminalSession s) {
        ClipData clip = getSystemService(ClipboardManager.class).getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null) s.getEmulator().paste(text.toString());
    }

    @Override public void onBell(TerminalSession s) {
        if (!prefs.getBoolean("vibrateOnBell", true)) return;
        android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
        if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
    }
    @Override public void onColorsChanged(TerminalSession s) { terminal.invalidate(); }
    @Override public void onTerminalCursorStateChange(boolean state) {}
    @Override public Integer getTerminalCursorStyle() { return null; }

    // ---- TerminalViewClient ----

    @Override public float onScale(float scale) {
        // Pinch to zoom: step the font size once the gesture passes a threshold.
        if (scale > 0.9f && scale < 1.1f) return scale;
        fontSize = Math.max(12, Math.min(72, fontSize + (scale > 1 ? 2 : -2)));
        terminal.setTextSize(fontSize);
        prefs.edit().putInt("fontSize", fontSize).apply();
        return 1f;
    }

    @Override public void onSingleTapUp(MotionEvent e) {
        terminal.requestFocus();
        getSystemService(InputMethodManager.class).showSoftInput(terminal, 0);
    }

    @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
    @Override public boolean shouldEnforceCharBasedInput() { return true; }
    @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
    @Override public boolean isTerminalViewSelected() { return true; }
    @Override public void copyModeChanged(boolean copyMode) {}
    @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession s) {
        // Volume down = CTRL, volume up = ALT for the next key, like Termux.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) ctrl = true;
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) alt = true;
        else return false;
        updateModifierButtons();
        return true;
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent e) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP;
    }
    @Override public boolean onLongPress(MotionEvent e) { return false; }
    @Override public boolean readControlKey() { return ctrl; }
    @Override public boolean readAltKey() { return alt; }
    @Override public boolean readShiftKey() { return false; }
    @Override public boolean readFnKey() { return false; }

    @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession s) {
        // Soft keyboard typed a key: CTRL/ALT from the extra-keys row apply once.
        runOnUiThread(this::clearModifiers);
        return false;
    }

    @Override public void onEmulatorSet() {}

    // ---- logging (both interfaces) ----
    @Override public void logError(String tag, String msg) { android.util.Log.e(tag, msg); }
    @Override public void logWarn(String tag, String msg) { android.util.Log.w(tag, msg); }
    @Override public void logInfo(String tag, String msg) {}
    @Override public void logDebug(String tag, String msg) {}
    @Override public void logVerbose(String tag, String msg) {}
    @Override public void logStackTraceWithMessage(String tag, String msg, Exception e) { android.util.Log.e(tag, msg, e); }
    @Override public void logStackTrace(String tag, Exception e) { android.util.Log.e(tag, "error", e); }
}
