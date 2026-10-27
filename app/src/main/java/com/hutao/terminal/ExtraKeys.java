package com.hutao.terminal;

import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extra-keys rows, read from ~/.hutao/extra-keys.json in Termux's layout: an array of rows, each key
 * either a name/text ("ESC", "UP", "x") or {"key": ..., "popup": ...} where popup fires on swipe up.
 */
public final class ExtraKeys {
    public static final String DEFAULT = "[\n"
            + "  [\"ESC\", {\"key\": \"/\", \"popup\": \"~\"}, {\"key\": \"-\", \"popup\": \"|\"}, \"HOME\", \"UP\", \"END\", \"PGUP\", \"PASTE\"],\n"
            + "  [\"TAB\", \"CTRL\", \"ALT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\", \"KEYBOARD\"]\n"
            + "]\n";

    public static final class Key {
        public final String key, popup;

        Key(String key, String popup) {
            this.key = key;
            this.popup = popup;
        }
    }

    // Map.of/ofEntries need API 30; minSdk is 26.
    private static final Map<String, Integer> KEY_CODES = new HashMap<>();
    private static final Map<String, String> LABELS = new HashMap<>();

    static {
        String[] names = {"ESC", "TAB", "ENTER", "BKSP", "DEL", "INS", "UP", "DOWN", "LEFT", "RIGHT",
                "HOME", "END", "PGUP", "PGDN"};
        int[] codes = {KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL,
                KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN};
        for (int i = 0; i < names.length; i++) KEY_CODES.put(names[i], codes[i]);
        for (int f = 1; f <= 12; f++) KEY_CODES.put("F" + f, KeyEvent.KEYCODE_F1 + f - 1); // F1..F12 are consecutive
        String[][] labels = {{"UP", "↑"}, {"DOWN", "↓"}, {"LEFT", "←"}, {"RIGHT", "→"},
                {"KEYBOARD", "⌨"}, {"BKSP", "⌫"}, {"ENTER", "↵"}};
        for (String[] l : labels) LABELS.put(l[0], l[1]);
    }

    private ExtraKeys() {}

    public static List<List<Key>> parse(String json) throws JSONException {
        JSONArray rows = new JSONArray(json);
        List<List<Key>> out = new ArrayList<>();
        for (int r = 0; r < rows.length(); r++) {
            JSONArray row = rows.getJSONArray(r);
            List<Key> keys = new ArrayList<>();
            for (int k = 0; k < row.length(); k++) {
                Object item = row.get(k);
                if (item instanceof JSONObject) {
                    JSONObject o = (JSONObject) item;
                    keys.add(new Key(o.getString("key"), o.has("popup") ? o.getString("popup") : null));
                } else {
                    keys.add(new Key(row.getString(k), null));
                }
                if (keys.get(keys.size() - 1).key.isEmpty()) throw new JSONException("empty key in row " + (r + 1));
            }
            out.add(keys);
        }
        return out;
    }

    /** Android key code for a named key, or 0 when the key is text to type. */
    public static int keyCode(String key) {
        Integer code = KEY_CODES.get(key);
        return code == null ? 0 : code;
    }

    public static String label(String key) {
        String l = LABELS.get(key);
        return l != null ? l : key;
    }
}
