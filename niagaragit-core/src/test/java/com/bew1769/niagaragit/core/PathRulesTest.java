package com.bew1769.niagaragit.core;

import static com.bew1769.niagaragit.core.PathRules.Kind.NOISY;
import static com.bew1769.niagaragit.core.PathRules.Kind.SECRET;
import static com.bew1769.niagaragit.core.PathRules.Kind.SHOW;
import static com.bew1769.niagaragit.core.PathRules.Kind.UNSAFE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PathRulesTest {

  @Test
  void stationFilesAreShown() {
    assertEquals(SHOW, PathRules.classify("config.bog"));
    assertEquals(SHOW, PathRules.classify("shared/px/AHU1.px"));
    assertEquals(SHOW, PathRules.classify("shared/nav/demo.nav"));
    assertEquals(SHOW, PathRules.classify("shared\\palette\\demo.palette"));
  }

  @Test
  void noise() {
    assertEquals(NOISY, PathRules.classify("history/AHU1/data.hdb"));
    assertEquals(NOISY, PathRules.classify("alarm/alarm.adb"));
    assertEquals(NOISY, PathRules.classify("config.backup.20261121.bog"));
    assertEquals(NOISY, PathRules.classify("config_backup_1.bog"));
    assertEquals(NOISY, PathRules.classify("shared/logs/x.txt"));
    assertEquals(NOISY, PathRules.classify("station.out"));
    assertEquals(NOISY, PathRules.classify("shared/Thumbs.db"));
  }

  @Test
  void secretsWinOverNoise() {
    assertEquals(SECRET, PathRules.classify("logs/client.pem"));
    assertEquals(SECRET, PathRules.classify("security/keystore.jceks"));
    assertEquals(SECRET, PathRules.classify("shared/certManagement/a.txt"));
    assertEquals(SECRET, PathRules.classify("shared/export.P12"));
    assertEquals(SECRET, PathRules.classify("exemptions.tes"));
  }

  @Test
  void unsafePaths() {
    assertEquals(UNSAFE, PathRules.classify("/etc/passwd"));
    assertEquals(UNSAFE, PathRules.classify("C:/Windows/x"));
    assertEquals(UNSAFE, PathRules.classify("shared/../../x"));
    assertEquals(UNSAFE, PathRules.classify(""));
  }

  @Test
  void dotfileHasNoExtension() {
    assertEquals("", PathRules.extOf(".pem"));
    assertEquals(".pem", PathRules.extOf("a.PEM"));
  }
}
