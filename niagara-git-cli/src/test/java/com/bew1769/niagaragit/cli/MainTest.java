package com.bew1769.niagaragit.cli;

import com.bew1769.niagaragit.UsageException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {
    @Test
    public void versionAndBog(@TempDir File dir) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        System.setOut(new PrintStream(out, true, "UTF-8"));
        try {
            Main.run(new String[] {"--version"});
            assertTrue(out.toString("UTF-8").startsWith("niagara-git "));
            out.reset();
            File bog = new File(dir, "config.bog");
            byte[] bytes = MainTest.class.getResourceAsStream("/fixtures/examples/demoPlant/station/config.bog") == null
                    ? null : readResource("/fixtures/examples/demoPlant/station/config.bog");
            if (bytes == null) {
                bytes = ("<bajaObjectGraph version=\"4.0\"><p n=\"AHU1\" h=\"2\" t=\"b:Folder\"/></bajaObjectGraph>")
                        .getBytes(StandardCharsets.UTF_8);
            }
            Files.write(bog.toPath(), bytes);
            Main.run(new String[] {"bog", bog.getPath()});
            String text = out.toString("UTF-8");
            assertTrue(text.contains("<?xml") || text.contains("niagara-git"));
        } finally {
            System.setOut(previous);
        }
        assertThrows(UsageException.class, () -> Main.run(new String[0]));
    }

    private static byte[] readResource(String path) throws Exception {
        java.io.InputStream in = MainTest.class.getResourceAsStream(path);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) >= 0) {
                buf.write(b, 0, n);
            }
            return buf.toByteArray();
        } finally {
            in.close();
        }
    }
}
