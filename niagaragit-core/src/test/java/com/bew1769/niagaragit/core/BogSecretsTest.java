package com.bew1769.niagaragit.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BogSecretsTest {

  @TempDir
  Path tmp;

  // Made-up values in the shapes Niagara 4.15 writes. None is a real secret.
  static final String HASH = "[pbkdf2-sha256.1]=00112233445566778899aabbccddeeff:100000:"
      + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  static final String HISTORY = Base64.getEncoder().encodeToString(
      ("[pbkdf2-sha256.1]=ffeeddccbbaa99887766554433221100:100000:"
          + "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210").getBytes(StandardCharsets.ISO_8859_1));
  static final String REVERSIBLE = "[aliased-aes-256.2]q8Zr0dummyCipherTextNotASecret==";
  static final String SAVED_HASH = "[pbkdf2-aes-256.1]dummyWrappedHashNotASecret";

  static String bog() {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<bajaObjectGraph version=\"4.0\" reversibleEncodingKeySource=\"none\" reversibleEncodingValidator=\"[null.1]=\" >\n"
        + "<p h=\"1\" m=\"b=baja\" t=\"b:Station\">\n"
        + " <p n=\"pwtest\" h=\"1e\" t=\"b:User\">\n"
        + "  <p n=\"authenticator\" h=\"1f\" t=\"b:PasswordAuthenticator\">\n"
        + "   <p n=\"password\" t=\"b:Password\" v=\"" + HASH + "\"/>\n"
        + "   <p n=\"passwordConfig\" h=\"20\" t=\"b:UserPasswordConfiguration\">\n"
        + "    <p n=\"passwordHistory\" t=\"b:PasswordHistory\" v=\"" + HISTORY + "\"/>\n"
        + "    <p n=\"lastPasswordChange\" t=\"b:AbsTime\" v=\"2026-11-21T17:10:35.819-05:00\"/>\n"
        + "   </p>\n"
        + "  </p>\n"
        + " </p>\n"
        + " <p n=\"SecretPw\" t=\"b:Password\" v=\"" + REVERSIBLE + "\"/>\n"
        + " <p n=\"Saved\" t=\"b:Password\" v=\"" + SAVED_HASH + "\"/>\n"
        + " <p n=\"Empty\" t=\"b:Password\" v=\"\"/>\n"
        + " <p n=\"program\" v=\"REM [not a password] 10 END\"/>\n"
        + " <p n=\"note\" v=\"QUJDREVGR0hJSktMTU5PUA==\"/>\n"
        + " <p n=\"remoteUrl\" v=\"https://127.0.0.1:8443/scratchTest.git\"/>\n"
        + "</p>\n</bajaObjectGraph>\n";
  }

  @Test
  void blanksPasswordsAndDropsHistory() throws IOException {
    BogSecrets.Result r = BogSecrets.redact(bog().getBytes(StandardCharsets.UTF_8));
    String out = new String(r.bog, StandardCharsets.UTF_8);
    assertEquals(4, r.removed);
    for (String secret : new String[] {HASH, HISTORY, REVERSIBLE, SAVED_HASH, "100000:"}) {
      assertFalse(out.contains(secret), secret);
    }
    assertTrue(out.contains("<p n=\"password\" t=\"b:Password\" v=\"\"/>"));
    assertTrue(out.contains("<p n=\"SecretPw\" t=\"b:Password\" v=\"\"/>"));
    assertFalse(out.contains("passwordHistory"));
    // Everything else is untouched.
    assertTrue(out.contains("reversibleEncodingValidator=\"[null.1]=\""));
    assertTrue(out.contains("lastPasswordChange"));
    assertTrue(out.contains("REM [not a password] 10 END"));
    assertTrue(out.contains("QUJDREVGR0hJSktMTU5PUA=="));
    assertTrue(out.contains("https://127.0.0.1:8443/scratchTest.git"));
  }

  @Test
  void cleanBogIsReturnedByteForByte() throws IOException {
    byte[] in = "<p n=\"x\" v=\"1\"/>".getBytes(StandardCharsets.UTF_8);
    BogSecrets.Result r = BogSecrets.redact(in);
    assertEquals(0, r.removed);
    assertArrayEquals(in, r.bog);
  }

  @Test
  void redactsInsideZippedBog() throws IOException {
    ByteArrayOutputStream zip = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(zip)) {
      z.putNextEntry(new ZipEntry("file.xml"));
      z.write(bog().getBytes(StandardCharsets.UTF_8));
      z.closeEntry();
    }
    BogSecrets.Result r = BogSecrets.redact(zip.toByteArray());
    assertEquals(4, r.removed);
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(r.bog))) {
      ZipEntry e = in.getNextEntry();
      assertEquals("file.xml", e.getName());
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) {
        b.write(buf, 0, n);
      }
      String out = new String(b.toByteArray(), StandardCharsets.UTF_8);
      assertFalse(out.contains(HASH));
      assertFalse(out.contains(REVERSIBLE));
    }
  }

  @Test
  void snapshotNeverWritesPasswordsToTheWorkTree() throws IOException {
    Path station = SnapshotTest.fakeStation(tmp.resolve("station"));
    Path work = tmp.resolve("work");
    byte[] live = bog().getBytes(StandardCharsets.UTF_8);
    Snapshot.Result r = Snapshot.toWorkTree(station, () -> live, work, false);
    assertEquals(4, r.passwordsRemoved);
    assertTrue(r.toString().contains("4 password values removed"));
    String committed = new String(Files.readAllBytes(work.resolve("config.bog")), StandardCharsets.UTF_8);
    assertFalse(committed.contains(HASH));
    assertFalse(committed.contains(REVERSIBLE));
    assertFalse(committed.contains(HISTORY));
  }
}
