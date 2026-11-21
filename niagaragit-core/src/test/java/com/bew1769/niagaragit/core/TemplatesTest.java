package com.bew1769.niagaragit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplatesTest {

  @TempDir
  Path tmp;

  @Test
  void templatesMatchGoNiagaraGit() {
    assertTrue(Templates.gitignore().contains("history/"));
    assertTrue(Templates.gitignore().contains("*.jceks"));
    assertTrue(Templates.gitattributes().contains("*.bog diff=niagara-bog"));
  }

  @Test
  void mergeKeepsUserLinesAndIsIdempotent() throws Exception {
    Path f = tmp.resolve(".gitignore");
    Files.write(f, "mine.txt\n".getBytes(StandardCharsets.UTF_8));
    assertTrue(Templates.mergeBlock(f, Templates.gitignore()));
    String once = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
    assertTrue(once.startsWith("mine.txt\n# niagara-git:begin"));
    assertFalse(Templates.mergeBlock(f, Templates.gitignore()));
    assertEquals(once, new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
  }

  @Test
  void mergeReplacesOldBlock() {
    String merged = Templates.merge("a\n# niagara-git:begin\nold\n# niagara-git:end\nb\n",
        "# niagara-git:begin\nnew\n# niagara-git:end\n");
    assertEquals("a\n# niagara-git:begin\nnew\n# niagara-git:end\nb\n", merged);
  }
}
