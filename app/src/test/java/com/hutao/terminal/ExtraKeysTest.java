package com.hutao.terminal;

import static org.junit.Assert.*;

import android.view.KeyEvent;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;

public class ExtraKeysTest {
    @Test public void defaultLayoutHasTwoRowsWithPopups() throws JSONException {
        List<List<ExtraKeys.Key>> rows = ExtraKeys.parse(ExtraKeys.DEFAULT);
        assertEquals(2, rows.size());
        assertEquals("ESC", rows.get(0).get(0).key);
        assertNull(rows.get(0).get(0).popup);
        assertEquals("-", rows.get(0).get(2).key);
        assertEquals("|", rows.get(0).get(2).popup);
        assertEquals("KEYBOARD", rows.get(1).get(7).key);
    }

    @Test public void rejectsMalformedConfig() {
        assertThrows(JSONException.class, () -> ExtraKeys.parse("[[\"ESC\""));
        assertThrows(JSONException.class, () -> ExtraKeys.parse("[[\"\"]]"));
        assertThrows(JSONException.class, () -> ExtraKeys.parse("[[{\"popup\": \"x\"}]]"));
    }

    @Test public void mapsNamesToKeyCodesAndText() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, ExtraKeys.keyCode("UP"));
        assertEquals(KeyEvent.KEYCODE_F12, ExtraKeys.keyCode("F12"));
        assertEquals(0, ExtraKeys.keyCode("|"));
        assertEquals("↑", ExtraKeys.label("UP"));
        assertEquals("ls", ExtraKeys.label("ls"));
    }
}
