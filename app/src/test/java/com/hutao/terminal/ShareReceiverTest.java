package com.hutao.terminal;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class ShareReceiverTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void uniqueFileStaysInDirAndNeverOverwrites() throws IOException {
        File dir = tmp.newFolder("downloads");
        assertEquals(new File(dir, "passwd"), ShareReceiverActivity.uniqueFile(dir, "../../etc/passwd"));
        assertEquals(new File(dir, "file"), ShareReceiverActivity.uniqueFile(dir, ".."));
        assertEquals(new File(dir, "file"), ShareReceiverActivity.uniqueFile(dir, null));
        assertTrue(new File(dir, "a.txt").createNewFile());
        assertEquals(new File(dir, "1_a.txt"), ShareReceiverActivity.uniqueFile(dir, "a.txt"));
    }
}
