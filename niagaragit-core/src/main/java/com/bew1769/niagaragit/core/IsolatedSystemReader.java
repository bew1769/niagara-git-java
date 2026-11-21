package com.bew1769.niagaragit.core;

import java.io.File;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;

/**
 * Keeps JGit inside the repository it was given. A station runs under the
 * Java security manager and often as a service account, so JGit must not read
 * {@code ~/.gitconfig}, the system gitconfig (finding it runs {@code git}), or
 * {@code GIT_*} environment variables, and must not write its own
 * {@code ~/.config/jgit} cache. Repository config ({@code .git/config}) still
 * applies.
 */
final class IsolatedSystemReader extends SystemReader {

  private static volatile boolean installed;

  private final SystemReader base;

  private IsolatedSystemReader(SystemReader base) {
    this.base = base;
  }

  /** Installs the reader once per JVM. Safe to call from every operation. */
  static synchronized void install() {
    if (installed) {
      return;
    }
    SystemReader current = SystemReader.getInstance();
    if (!(current instanceof IsolatedSystemReader)) {
      SystemReader.setInstance(new IsolatedSystemReader(current));
    }
    installed = true;
  }

  @Override
  public String getHostname() {
    return base.getHostname();
  }

  @Override
  public String getenv(String variable) {
    return null;
  }

  @Override
  public String getProperty(String key) {
    return base.getProperty(key);
  }

  @Override
  public FileBasedConfig openUserConfig(Config parent, FS fs) {
    return new EmptyConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openSystemConfig(Config parent, FS fs) {
    return new EmptyConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openJGitConfig(Config parent, FS fs) {
    return new EmptyConfig(parent, fs);
  }

  @Override
  public long getCurrentTime() {
    return base.getCurrentTime();
  }

  @Override
  public int getTimezone(long when) {
    return base.getTimezone(when);
  }

  /** An in-memory config with no backing file. Load and save do nothing. */
  private static final class EmptyConfig extends FileBasedConfig {
    EmptyConfig(Config parent, FS fs) {
      super(parent, (File) null, fs);
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public boolean isOutdated() {
      return false;
    }
  }
}
