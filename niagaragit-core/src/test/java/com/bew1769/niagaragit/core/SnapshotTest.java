package com.bew1769.niagaragit.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotTest {

  @TempDir
  Path tmp;

  static void write(Path root, String rel, String text) throws IOException {
    Path p = root.resolve(rel);
    Files.createDirectories(p.getParent());
    Files.write(p, text.getBytes(StandardCharsets.UTF_8));
  }

  static Path fakeStation(Path dir) throws IOException {
    write(dir, "config.bog", "bog-v1");
    write(dir, "config.backup.20261121.bog", "old");
    write(dir, "history/AHU1/data.hdb", "trend");
    write(dir, "alarm/alarm.adb", "alarms");
    write(dir, "station.out", "log");
    write(dir, "shared/px/AHU1.px", "<px/>");
    write(dir, "shared/nav/demo.nav", "<nav/>");
    write(dir, "shared/certManagement/client.pem", "KEY");
    write(dir, "shared/export.p12", "KEY");
    write(dir, "shared/logs/debug.txt", "noise");
    return dir;
  }

  @Test
  void copiesBogAndSharedOnly() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    Path work = tmp.resolve("work");
    Snapshot.Result r = Snapshot.toWorkTree(station, work, true);
    assertEquals(Arrays.asList("config.bog", "shared/nav/demo.nav", "shared/px/AHU1.px"), r.written);
    assertTrue(Files.exists(work.resolve("config.bog")));
    assertFalse(Files.exists(work.resolve("history")));
    assertFalse(Files.exists(work.resolve("alarm")));
    assertFalse(Files.exists(work.resolve("config.backup.20261121.bog")));
    assertFalse(Files.exists(work.resolve("shared/certManagement")));
    assertFalse(Files.exists(work.resolve("shared/export.p12")));
    assertFalse(Files.exists(work.resolve("shared/logs")));
  }

  @Test
  void bogSourceReplacesTheSavedFile() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    Path work = tmp.resolve("work");
    byte[] live = "bog-live".getBytes(StandardCharsets.UTF_8);
    Snapshot.Result r = Snapshot.toWorkTree(station, () -> live, work, true);
    assertTrue(r.written.contains("config.bog"));
    assertArrayEquals(live, Files.readAllBytes(work.resolve("config.bog")));
    assertFalse(Snapshot.toWorkTree(station, () -> live, work, true).changed());
  }

  @Test
  void secondRunWritesNothingAndDeletionsFollow() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    Path work = tmp.resolve("work");
    Snapshot.toWorkTree(station, work, true);
    assertFalse(Snapshot.toWorkTree(station, work, true).changed());

    Files.delete(station.resolve("shared/nav/demo.nav"));
    write(station, "config.bog", "bog-v2");
    Snapshot.Result r = Snapshot.toWorkTree(station, work, true);
    assertEquals(Arrays.asList("config.bog"), r.written);
    assertEquals(Arrays.asList("shared/nav/demo.nav"), r.deleted);
    assertFalse(Files.exists(work.resolve("shared/nav")), "empty folder pruned");
  }

  @Test
  void withoutSharedLeavesSharedAlone() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    Path work = tmp.resolve("work");
    Snapshot.toWorkTree(station, work, true);
    Snapshot.Result r = Snapshot.toWorkTree(station, work, false);
    assertFalse(r.changed());
    assertTrue(Files.exists(work.resolve("shared/px/AHU1.px")));
  }

  @Test
  void missingBogIsAnError() {
    assertThrows(NoSuchFileException.class,
        () -> Snapshot.toWorkTree(tmp.resolve("empty"), tmp.resolve("work"), true));
  }

  @Test
  void refusesWorkTreeInsideShared() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    assertThrows(IOException.class,
        () -> Snapshot.toWorkTree(station, station.resolve("shared/git"), true));
  }

  @Test
  void workTreeInsideProtectedHomeIsNotCopiedIntoItself() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    Path work = station.resolve("git");
    Snapshot.toWorkTree(station, work, true);
    assertFalse(Snapshot.toWorkTree(station, work, true).changed());
    assertFalse(Files.exists(work.resolve("git")));
  }

  @Test
  void sharedToStationNeverWritesBog() throws IOException {
    Path station = fakeStation(tmp.resolve("station"));
    Path work = tmp.resolve("work");
    Snapshot.toWorkTree(station, work, true);
    write(work, "config.bog", "from-remote");
    write(work, "shared/px/New.px", "<new/>");
    Snapshot.Result r = Snapshot.sharedToStation(work, station);
    assertEquals(Arrays.asList("shared/px/New.px"), r.written);
    assertArrayEquals("bog-v1".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(station.resolve("config.bog")));
  }
}
