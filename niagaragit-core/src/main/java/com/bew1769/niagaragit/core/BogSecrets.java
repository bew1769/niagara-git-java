package com.bew1769.niagaragit.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Removes password material from a bog before it is committed.
 *
 * <p>Niagara writes a password slot as {@code v="[encoding]value"}. In a saved
 * bog the value is encrypted with the station key ({@code [aliased-aes-256.2]},
 * {@code [pbkdf2-aes-256.1]}); in a bog encoded without a key, reversible
 * passwords come out empty but user passwords come out as the bare PBKDF2 hash
 * ({@code [pbkdf2-sha256.1]=salt:iterations:hash}), and a user's password
 * history is that same text in base64. None of it may leave the station.
 *
 * <p>A password value is blanked ({@code v=""}, which Niagara reads as the
 * default password). A self-closing element whose value is base64 of a password
 * value (password history) is dropped, so the slot falls back to its default.
 */
public final class BogSecrets {

  /** The bog and how many values were removed. */
  public static final class Result {
    public final byte[] bog;
    public final int removed;

    Result(byte[] bog, int removed) {
      this.bog = bog;
      this.removed = removed;
    }
  }

  static final Pattern ENCODED = Pattern.compile(
      "^\\[[A-Za-z0-9.\\-]*(pbkdf2|aes|plain|sha|bcrypt|scrypt|hash)[A-Za-z0-9.\\-]*\\]",
      Pattern.CASE_INSENSITIVE);

  private static final Pattern ELEMENT = Pattern.compile("<p\\s[^>]*?/>");
  private static final Pattern VALUE = Pattern.compile("\\sv=\"([^\"]*)\"");
  private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/]{16,}={0,2}$");

  private BogSecrets() {
  }

  /** Redacts a bog: a zip with {@code file.xml}, or plain XML text. */
  public static Result redact(byte[] bog) throws IOException {
    if (isZip(bog)) {
      return redactZip(bog);
    }
    String in = new String(bog, StandardCharsets.UTF_8);
    int[] count = new int[1];
    String out = redactXml(in, count);
    return new Result(count[0] == 0 ? bog : out.getBytes(StandardCharsets.UTF_8), count[0]);
  }

  static String redactXml(String xml, int[] count) {
    Matcher el = ELEMENT.matcher(xml);
    StringBuffer sb = new StringBuffer(xml.length());
    while (el.find()) {
      String element = el.group();
      Matcher v = VALUE.matcher(element);
      String replacement = element;
      if (v.find()) {
        String value = v.group(1);
        if (ENCODED.matcher(value).find()) {
          if (value.matches("^\\[[^\\]]*\\]$")) {
            // Only the encoding tag, no secret.
          } else {
            replacement = element.substring(0, v.start()) + " v=\"\"" + element.substring(v.end());
            count[0]++;
          }
        } else if (isEncodedInBase64(value)) {
          replacement = "";
          count[0]++;
        }
      }
      el.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    el.appendTail(sb);
    return sb.toString();
  }

  private static boolean isEncodedInBase64(String value) {
    if (!BASE64.matcher(value).matches()) {
      return false;
    }
    try {
      String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.ISO_8859_1);
      return ENCODED.matcher(decoded).find();
    } catch (IllegalArgumentException notBase64) {
      return false;
    }
  }

  private static boolean isZip(byte[] b) {
    return b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4;
  }

  private static Result redactZip(byte[] bog) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(bog.length);
    int removed = 0;
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bog));
        ZipOutputStream zout = new ZipOutputStream(out)) {
      ZipEntry e;
      while ((e = zin.getNextEntry()) != null) {
        byte[] data = readAll(zin);
        if (e.getName().endsWith(".xml")) {
          int[] count = new int[1];
          String text = redactXml(new String(data, StandardCharsets.UTF_8), count);
          if (count[0] > 0) {
            data = text.getBytes(StandardCharsets.UTF_8);
            removed += count[0];
          }
        }
        ZipEntry copy = new ZipEntry(e.getName());
        copy.setTime(e.getTime());
        zout.putNextEntry(copy);
        zout.write(data);
        zout.closeEntry();
      }
    }
    return new Result(removed == 0 ? bog : out.toByteArray(), removed);
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) > 0) {
      b.write(buf, 0, n);
    }
    return b.toByteArray();
  }
}
