package com.bew1769.niagaragit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zip read/write used by textconv and the store container.
 * Byte arrays are read from memory so a JACE security manager is not asked
 * to create a temp file outside the station directory.
 */
final class ZipArchives {
    static final class TooLargeException extends IOException {
        TooLargeException() {
            super("too large");
        }
    }

    static final class EncryptedException extends IOException {
        EncryptedException(String name) {
            super("encrypted entry " + name);
        }
    }

    static final class Member {
        final String name;
        final byte[] data;
        final long declaredSize;
        final boolean directory;
        final String unreadable;

        Member(String name, byte[] data, long declaredSize, boolean directory, String unreadable) {
            this.name = name;
            this.data = data;
            this.declaredSize = declaredSize;
            this.directory = directory;
            this.unreadable = unreadable;
        }
    }

    static final class Archive {
        final String comment;
        final List<Member> entries;

        Archive(String comment, List<Member> entries) {
            this.comment = comment == null ? "" : comment;
            this.entries = entries;
        }
    }

    private ZipArchives() {
    }

    static Archive read(byte[] data) throws IOException {
        if (!looksLikeZip(data)) {
            throw new ZipException("File is not a zip file");
        }
        String comment = endComment(data);
        List<Member> entries = new ArrayList<Member>();
        ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(data));
        try {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.add(readEntry(entry, in));
                in.closeEntry();
            }
        } catch (ZipException e) {
            throw new ZipException("File is not a zip file");
        } finally {
            in.close();
        }
        return new Archive(comment, entries);
    }

    static Archive read(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            List<Member> entries = new ArrayList<Member>();
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry entry = en.nextElement();
                InputStream in = null;
                try {
                    boolean directory = entry.isDirectory() || entry.getName().endsWith("/");
                    if (!directory && (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED)) {
                        // Fall through; ZipFile throws on encrypted entries when opened.
                    }
                    if (!directory) {
                        in = zip.getInputStream(entry);
                    }
                    entries.add(readEntry(entry, in));
                } catch (ZipException e) {
                    entries.add(unreadable(entry, e));
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
            }
            return new Archive(zip.getComment(), entries);
        } finally {
            zip.close();
        }
    }

    static boolean looksLikeZip(byte[] data) {
        return data.length >= 2 && data[0] == 'P' && data[1] == 'K';
    }

    static byte[] build(String comment, List<Stored> entries) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ZipOutputStream out = new ZipOutputStream(buf);
        try {
            if (comment != null && comment.length() > 0) {
                out.setComment(comment);
            }
            for (int i = 0; i < entries.size(); i++) {
                Stored entry = entries.get(i);
                ZipEntry header = new ZipEntry(entry.name);
                boolean directory = entry.name.endsWith("/");
                header.setTime(fixedDosTime());
                if (directory) {
                    header.setMethod(ZipEntry.STORED);
                    header.setSize(0);
                    header.setCompressedSize(0);
                    header.setCrc(0);
                } else {
                    header.setMethod(ZipEntry.DEFLATED);
                }
                out.putNextEntry(header);
                if (!directory && entry.data != null && entry.data.length > 0) {
                    out.write(entry.data);
                }
                out.closeEntry();
            }
        } finally {
            out.close();
        }
        return buf.toByteArray();
    }

    static final class Stored {
        final String name;
        final byte[] data;

        Stored(String name, byte[] data) {
            this.name = name;
            this.data = data == null ? new byte[0] : data;
        }
    }

    /** 1980-01-01 00:00:00 UTC, the fixed zip timestamp the Go tool writes. */
    static long fixedDosTime() {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.set(1980, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static Member readEntry(ZipEntry entry, InputStream in) throws IOException {
        boolean directory = entry.isDirectory() || entry.getName().endsWith("/");
        long declared = entry.getSize();
        if (directory) {
            return new Member(entry.getName(), new byte[0], declared < 0 ? 0 : declared, true, null);
        }
        if (declared > NiagaraGit.MAX_ENTRY_BYTES) {
            return new Member(entry.getName(), null, declared, false, "ValueError");
        }
        try {
            byte[] payload = readLimited(in, declared);
            long size = declared >= 0 ? declared : payload.length;
            return new Member(entry.getName(), payload, size, false, null);
        } catch (TooLargeException e) {
            return new Member(entry.getName(), null, declared < 0 ? NiagaraGit.MAX_ENTRY_BYTES + 1L : declared, false, "ValueError");
        } catch (ZipException e) {
            return unreadable(entry, e);
        }
    }

    private static Member unreadable(ZipEntry entry, ZipException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        String kind = message.contains("encrypt") ? "RuntimeError" : "BadZipFile";
        long declared = entry.getSize();
        return new Member(entry.getName(), null, declared < 0 ? 0 : declared, false, kind);
    }

    static byte[] readLimited(InputStream in, long declared) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        if (declared > NiagaraGit.MAX_ENTRY_BYTES) {
            throw new TooLargeException();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(declared > 0 && declared < Integer.MAX_VALUE ? (int) declared : 256);
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > NiagaraGit.MAX_ENTRY_BYTES) {
                throw new TooLargeException();
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Classic EOCD comment. ZIP64 comments are left empty. */
    static String endComment(byte[] data) {
        int min = Math.max(0, data.length - (22 + 65535));
        for (int i = data.length - 22; i >= min; i--) {
            if ((data[i] & 0xff) == 0x50 && (data[i + 1] & 0xff) == 0x4b
                    && (data[i + 2] & 0xff) == 0x05 && (data[i + 3] & 0xff) == 0x06) {
                int commentLen = (data[i + 20] & 0xff) | ((data[i + 21] & 0xff) << 8);
                if (i + 22 + commentLen == data.length) {
                    return new String(data, i + 22, commentLen, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
            }
        }
        return "";
    }
}
