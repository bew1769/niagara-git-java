package com.bew1769.niagaragit;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Digests {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Digests() {
    }

    static String sha256(byte[] data) {
        try {
            byte[] dig = MessageDigest.getInstance("SHA-256").digest(data);
            char[] out = new char[dig.length * 2];
            for (int i = 0; i < dig.length; i++) {
                int v = dig[i] & 0xff;
                out[i * 2] = HEX[v >>> 4];
                out[i * 2 + 1] = HEX[v & 0x0f];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean hasZero(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** True when data is UTF-8 and contains no NUL. Those bodies are stored raw. */
    static boolean rawUtf8(byte[] data) {
        if (hasZero(data)) {
            return false;
        }
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            dec.decode(java.nio.ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
