package com.hutao.terminal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements TerminalSessionClient, TerminalViewClient {
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
    };

    private TerminalView terminal;
    private LinearLayout tabs;
    private Button ctrlButton, altButton;
    private boolean ctrl, alt;
    private int fontSize = 28;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        terminal = findViewById(R.id.terminal);
        tabs = findViewById(R.id.tabs);
        terminal.setTerminalViewClient(this);
        terminal.setTextSize(fontSize);
        terminal.setTypeface(Typeface.MONOSPACE);
        findViewById(R.id.new_tab).setOnClickListener(v -> switchTo(newSession()));
        buildExtraKeys();

        // Process may have outlived a previous activity: re-adopt its sessions.
        for (TerminalSession s : SESSIONS) s.updateTerminalSessionClient(this);
        switchTo(SESSIONS.isEmpty() ? newSession() : SESSIONS.get(SESSIONS.size() - 1));
    }

    private TerminalSession newSession() {
        File home = new File(getFilesDir(), "home");
        new File(home, "bin").mkdirs();
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("HOME", home.getAbsolutePath());
        env.put("TMPDIR", getCacheDir().getAbsolutePath());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("LANG", "en_US.UTF-8");
        env.put("PATH", home + "/bin:" + env.getOrDefault("PATH", "/system/bin"));
        env.put("ENV", home + "/.shrc"); // startup script, like ~/.bashrc
        String[] envArr = new String[env.size()];
        int i = 0;
        for (Map.Entry<String, String> e : env.entrySet()) envArr[i++] = e.getKey() + "=" + e.getValue();

        TerminalSession s = new TerminalSession("/system/bin/sh", home.getAbsolutePath(),
                new String[]{"-sh"}, envArr, SCROLLBACK_ROWS, this);
        s.mSessionName = "sh " + (SESSIONS.size() + 1);
        SESSIONS.add(s);
        startKeepAlive();
        return s;
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

    private void refreshTabs() {
        tabs.removeAllViews();
        for (TerminalSession s : SESSIONS) {
            Button b = new Button(this);
            String title = s.getTitle();
            b.setText(title != null && !title.isEmpty() ? title : s.mSessionName);
            b.setAllCaps(false);
            b.setTextColor(s == terminal.getCurrentSession() ? Color.WHITE : Color.GRAY);
            b.setOnClickListener(v -> switchTo(s));
            b.setOnLongClickListener(v -> { s.finishIfRunning(); return true; }); // long-press closes
            tabs.addView(b);
        }
    }

    private void buildExtraKeys() {
        LinearLayout row = findViewById(R.id.extra_keys);
        for (String[] key : EXTRA_KEYS) {
            Button b = new Button(this);
            b.setText(key[0]);
            b.setAllCaps(false);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
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
        ctrlButton.setTextColor(ctrl ? Color.CYAN : Color.WHITE);
        altButton.setTextColor(alt ? Color.CYAN : Color.WHITE);
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

    @Override public void onBell(TerminalSession s) {}
    @Override public void onColorsChanged(TerminalSession s) { terminal.invalidate(); }
    @Override public void onTerminalCursorStateChange(boolean state) {}
    @Override public Integer getTerminalCursorStyle() { return null; }

    // ---- TerminalViewClient ----

    @Override public float onScale(float scale) {
        // Pinch to zoom: step the font size once the gesture passes a threshold.
        if (scale > 0.9f && scale < 1.1f) return scale;
        fontSize = Math.max(12, Math.min(72, fontSize + (scale > 1 ? 2 : -2)));
        terminal.setTextSize(fontSize);
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
    @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession s) { return false; }
    @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
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
