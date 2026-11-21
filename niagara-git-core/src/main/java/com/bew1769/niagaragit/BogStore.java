package com.bew1769.niagaragit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Clean/smudge container for a .bog zip.
 *
 * <p>The blob records every zip entry in central-directory order: the name and
 * the exact uncompressed bytes. Zip timestamps and compression are not stored,
 * so rewriting the zip wrapper does not change the git object. smudge rebuilds
 * a zip Niagara 4 can open.
 *
 * <p>.dist archives stay zip blobs. A distribution file bundles history databases,
 * alarm databases, and keystores.
 */
public final class BogStore {
    private BogStore() {
    }

    public static boolean isStore(byte[] data) {
        byte[] magic = NiagaraGit.STORE_MAGIC.getBytes(StandardCharsets.US_ASCII);
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    public static byte[] clean(byte[] data) throws IOException {
        if (data.length == 0) {
            throw new IOException("niagara-git: clean: empty input");
        }
        if (isStore(data)) {
            Parsed parsed = parse(data);
            return encode(parsed.comment, parsed.entries);
        }
        if (!ZipArchives.looksLikeZip(data)) {
            throw new IOException("niagara-git: clean: input is not a .bog zip or a niagara-git store");
        }
        ZipArchives.Archive archive;
        try {
            archive = ZipArchives.read(data);
        } catch (IOException e) {
            throw new IOException("niagara-git: clean: not a readable zip archive", e);
        }
        List<ZipArchives.Stored> entries = new ArrayList<ZipArchives.Stored>();
        for (int i = 0; i < archive.entries.size(); i++) {
            ZipArchives.Member member = archive.entries.get(i);
            if (member.unreadable != null) {
                if ("RuntimeError".equals(member.unreadable)) {
                    throw new IOException("niagara-git: clean: encrypted entry " + member.name);
                }
                if ("ValueError".equals(member.unreadable)) {
                    throw new IOException("niagara-git: clean: entry " + member.name + " larger than "
                            + NiagaraGit.MAX_ENTRY_BYTES + " bytes");
                }
                throw new IOException("niagara-git: clean: " + member.name);
            }
            entries.add(new ZipArchives.Stored(member.name, member.data));
        }
        return encode(archive.comment, entries);
    }

    public static byte[] smudge(byte[] data) throws IOException {
        if (data.length == 0) {
            throw new IOException("niagara-git: smudge: empty input");
        }
        if (ZipArchives.looksLikeZip(data)) {
            return Arrays.copyOf(data, data.length);
        }
        if (!isStore(data)) {
            throw new IOException("niagara-git: smudge: input is not a niagara-git store or a .bog zip");
        }
        Parsed parsed = parse(data);
        return ZipArchives.build(parsed.comment, parsed.entries);
    }

    static byte[] encode(String comment, List<ZipArchives.Stored> entries) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeAscii(b, NiagaraGit.STORE_MAGIC);
        if (comment != null && comment.length() > 0) {
            byte[] raw = comment.getBytes(StandardCharsets.ISO_8859_1);
            writeAscii(b, "comment " + raw.length + "\n");
            b.write(raw, 0, raw.length);
        }
        writeAscii(b, "entries " + entries.size() + "\n");
        for (int i = 0; i < entries.size(); i++) {
            ZipArchives.Stored entry = entries.get(i);
            String enc;
            byte[] body;
            if (Digests.rawUtf8(entry.data)) {
                enc = "raw";
                body = entry.data;
            } else {
                enc = "b64";
                body = Base64.getEncoder().encode(entry.data);
            }
            byte[] name = entry.name.getBytes(StandardCharsets.ISO_8859_1);
            writeAscii(b, "entry " + name.length + " " + body.length + " " + enc + "\n");
            b.write(name, 0, name.length);
            b.write(body, 0, body.length);
        }
        return b.toByteArray();
    }

    static Parsed parse(byte[] data) throws IOException {
        if (!isStore(data)) {
            throw new IOException("niagara-git: not a store blob");
        }
        Reader r = new Reader(data, NiagaraGit.STORE_MAGIC.length());
        String line = r.line();
        String comment = "";
        if (line.startsWith("comment ")) {
            int n = parseNonNeg(line.substring("comment ".length()), "bad comment length");
            comment = new String(r.take(n), StandardCharsets.ISO_8859_1);
            line = r.line();
        }
        if (!line.startsWith("entries ")) {
            throw new IOException("niagara-git: store: missing entries header");
        }
        int count = parseNonNeg(line.substring("entries ".length()), "bad entry count");
        List<ZipArchives.Stored> entries = new ArrayList<ZipArchives.Stored>(count);
        for (int i = 0; i < count; i++) {
            String header = r.line();
            EntryHeader h = parseEntryHeader(header);
            byte[] name = r.take(h.nameLen);
            byte[] body = r.take(h.bodyLen);
            if ("raw".equals(h.enc)) {
                // stored as-is
            } else if ("b64".equals(h.enc)) {
                try {
                    body = Base64.getDecoder().decode(body);
                } catch (IllegalArgumentException e) {
                    throw new IOException("niagara-git: store: " + new String(name, StandardCharsets.ISO_8859_1)
                            + ": " + e.getMessage());
                }
            } else {
                throw new IOException("niagara-git: store: unknown encoding " + h.enc);
            }
            entries.add(new ZipArchives.Stored(new String(name, StandardCharsets.ISO_8859_1), body));
        }
        if (r.i != r.b.length) {
            throw new IOException("niagara-git: store: " + (r.b.length - r.i) + " trailing bytes");
        }
        return new Parsed(comment, entries);
    }

    static final class Parsed {
        final String comment;
        final List<ZipArchives.Stored> entries;

        Parsed(String comment, List<ZipArchives.Stored> entries) {
            this.comment = comment;
            this.entries = entries;
        }
    }

    private static final class EntryHeader {
        final int nameLen;
        final int bodyLen;
        final String enc;

        EntryHeader(int nameLen, int bodyLen, String enc) {
            this.nameLen = nameLen;
            this.bodyLen = bodyLen;
            this.enc = enc;
        }
    }

    /** Mirrors fmt.Sscanf(header, "entry %d %d %s"). */
    private static EntryHeader parseEntryHeader(String header) throws IOException {
        if (!header.startsWith("entry ")) {
            throw new IOException("niagara-git: store: bad entry header \"" + header + "\"");
        }
        String rest = header.substring("entry ".length());
        int sp1 = rest.indexOf(' ');
        int sp2 = sp1 < 0 ? -1 : rest.indexOf(' ', sp1 + 1);
        if (sp1 < 0 || sp2 < 0) {
            throw new IOException("niagara-git: store: bad entry header \"" + header + "\"");
        }
        int nameLen = parseNonNeg(rest.substring(0, sp1), "bad entry header \"" + header + "\"");
        int bodyLen = parseNonNeg(rest.substring(sp1 + 1, sp2), "bad entry header \"" + header + "\"");
        String enc = rest.substring(sp2 + 1);
        if (enc.isEmpty() || enc.indexOf(' ') >= 0) {
            throw new IOException("niagara-git: store: bad entry header \"" + header + "\"");
        }
        return new EntryHeader(nameLen, bodyLen, enc);
    }

    private static int parseNonNeg(String text, String what) throws IOException {
        try {
            int n = Integer.parseInt(text);
            if (n < 0) {
                throw new IOException("niagara-git: store: " + what);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IOException("niagara-git: store: " + what);
        }
    }

    private static void writeAscii(ByteArrayOutputStream b, String s) {
        byte[] raw = s.getBytes(StandardCharsets.US_ASCII);
        b.write(raw, 0, raw.length);
    }

    private static final class Reader {
        final byte[] b;
        int i;

        Reader(byte[] b, int i) {
            this.b = b;
            this.i = i;
        }

        String line() throws IOException {
            if (i >= b.length) {
                throw new IOException("niagara-git: store: unexpected EOF");
            }
            int rel = indexOf(b, i, (byte) '\n');
            if (rel < 0) {
                throw new IOException("niagara-git: store: unexpected EOF");
            }
            String s = new String(b, i, rel - i, StandardCharsets.ISO_8859_1);
            i = rel + 1;
            return s;
        }

        byte[] take(int n) throws IOException {
            if (n < 0 || i + n > b.length) {
                throw new IOException("niagara-git: store: unexpected EOF");
            }
            byte[] out = Arrays.copyOfRange(b, i, i + n);
            i += n;
            return out;
        }
    }

    private static int indexOf(byte[] data, int from, byte needle) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == needle) {
                return i;
            }
        }
        return -1;
    }
}
