package com.bew1769.niagaragit.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The starter {@code .gitignore} and {@code .gitattributes} blocks. They are the
 * same files the Go niagara-git embeds, so a repo written by the station and a
 * clone on a Workbench PC agree on what is ignored and which diff driver runs.
 */
public final class Templates {

  public static final String BEGIN = "# niagara-git:begin";
  public static final String END = "# niagara-git:end";

  private Templates() {
  }

  public static String gitignore() {
    return load("gitignore");
  }

  public static String gitattributes() {
    return load("gitattributes");
  }

  /**
   * Writes the niagara-git block into {@code file}. Lines outside the
   * begin/end markers are kept. Returns true when the file changed.
   */
  public static boolean mergeBlock(Path file, String block) throws IOException {
    String existing = Files.exists(file)
        ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n")
        : "";
    String merged = merge(existing, block.replace("\r\n", "\n"));
    if (merged.equals(existing)) {
      return false;
    }
    Files.write(file, merged.getBytes(StandardCharsets.UTF_8));
    return true;
  }

  static String merge(String existing, String block) {
    String trimmedBlock = block.endsWith("\n") ? block : block + "\n";
    int begin = existing.indexOf(BEGIN);
    int end = begin < 0 ? -1 : existing.indexOf(END, begin);
    if (begin >= 0 && end >= 0) {
      int after = existing.indexOf('\n', end);
      after = after < 0 ? existing.length() : after + 1;
      return existing.substring(0, begin) + trimmedBlock + existing.substring(after);
    }
    if (existing.isEmpty()) {
      return trimmedBlock;
    }
    String sep = existing.endsWith("\n") ? "" : "\n";
    return existing + sep + trimmedBlock;
  }

  private static String load(String name) {
    try (InputStream in = Templates.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("missing template " + name);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
      }
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("cannot read template " + name, e);
    }
  }
}
