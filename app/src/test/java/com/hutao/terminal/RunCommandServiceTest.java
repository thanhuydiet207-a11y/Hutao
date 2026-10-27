package com.hutao.terminal;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RunCommandServiceTest {
    @Test public void readCappedKeepsFirst100KbAndDrainsTheRest() {
        byte[] big = new byte[300 * 1024];
        big[0] = 'a';
        ByteArrayInputStream in = new ByteArrayInputStream(big);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RunCommandService.readCapped(in, out);
        assertEquals(100 * 1024, out.size());
        assertEquals('a', out.toByteArray()[0]);
        assertEquals(0, in.available()); // fully drained, so the writer never blocks
    }
}
