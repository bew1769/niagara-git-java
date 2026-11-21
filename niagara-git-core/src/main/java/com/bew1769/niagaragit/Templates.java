package com.bew1769.niagaragit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Templates {
    static final String ATTRIBUTES = load("gitattributes");
    static final String IGNORE = load("gitignore");
    static final String ATTRIBUTES_STORE = load("gitattributes-store");

    private Templates() {
    }

    private static String load(String name) {
        InputStream in = Templates.class.getResourceAsStream(name);
        if (in == null) {
            throw new IllegalStateException("missing template " + name);
        }
        try {
            byte[] data = ZipArchives.readLimited(in, 1024 * 1024);
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // resource close
            }
        }
    }
}
