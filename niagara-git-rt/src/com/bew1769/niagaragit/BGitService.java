package com.bew1769.niagaragit;

import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.baja.nre.annotations.NiagaraAction;
import javax.baja.nre.annotations.NiagaraProperty;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.security.BPassword;
import javax.baja.security.BPermissions;
import javax.baja.sys.Action;
import javax.baja.sys.BAbstractService;
import javax.baja.sys.BRelTime;
import javax.baja.sys.BValue;
import javax.baja.sys.Clock;
import javax.baja.sys.Context;
import javax.baja.sys.Flags;
import javax.baja.sys.Property;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import com.tridium.platform.BSystemPlatformService;

/**
 * Versions this station directory (config.bog and shared/) with JGit.
 * Drag it from the niagaraGit palette onto Services.
 *
 * <p>Actions require {@link BPermissions#ADMIN_INVOKE}. The password slot is a
 * {@code baja:Password}; the value is read through {@link AccessController} and
 * is never written to the station log.
 */
@NiagaraType
@NiagaraProperty(name = "remoteUrl", type = "String", defaultValue = "")
@NiagaraProperty(name = "branch", type = "String", defaultValue = "main")
@NiagaraProperty(name = "authorName", type = "String", defaultValue = "niagara-git")
@NiagaraProperty(name = "authorEmail", type = "String", defaultValue = "niagara-git@localhost")
@NiagaraProperty(name = "credentialUser", type = "String", defaultValue = "git")
@NiagaraProperty(name = "credentials", type = "baja:Password", defaultValue = "BPassword.DEFAULT")
@NiagaraProperty(name = "autoCommit", type = "baja:RelTime", defaultValue = "BRelTime.DEFAULT")
@NiagaraProperty(name = "restartStationAfterPull", type = "boolean", defaultValue = "false")
@NiagaraProperty(name = "storeXml", type = "boolean", defaultValue = "false")
@NiagaraProperty(name = "status", type = "String", defaultValue = "", flags = Flags.READONLY | Flags.TRANSIENT | Flags.SUMMARY)
@NiagaraAction(name = "init", flags = Flags.CONFIRM_REQUIRED)
@NiagaraAction(name = "commitSnapshot", flags = Flags.ASYNC | Flags.CONFIRM_REQUIRED)
@NiagaraAction(name = "pull", flags = Flags.ASYNC | Flags.CONFIRM_REQUIRED)
@NiagaraAction(name = "push", flags = Flags.ASYNC | Flags.CONFIRM_REQUIRED)
@NiagaraAction(name = "refreshStatus", flags = Flags.ASYNC)
@NiagaraAction(name = "autoCommitTick", flags = Flags.HIDDEN | Flags.ASYNC)
public class BGitService extends BAbstractService {
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.bew1769.niagaragit.BGitService(1)1.0$ @*/
/* Hand-written to match the annotations above. Slot-o-matic overwrites this block. */

  public static final Property remoteUrl = newProperty(0, "", null);
  public String getRemoteUrl() { return getString(remoteUrl); }
  public void setRemoteUrl(String v) { setString(remoteUrl, v, null); }

  public static final Property branch = newProperty(0, "main", null);
  public String getBranch() { return getString(branch); }
  public void setBranch(String v) { setString(branch, v, null); }

  public static final Property authorName = newProperty(0, "niagara-git", null);
  public String getAuthorName() { return getString(authorName); }
  public void setAuthorName(String v) { setString(authorName, v, null); }

  public static final Property authorEmail = newProperty(0, "niagara-git@localhost", null);
  public String getAuthorEmail() { return getString(authorEmail); }
  public void setAuthorEmail(String v) { setString(authorEmail, v, null); }

  public static final Property credentialUser = newProperty(0, "git", null);
  public String getCredentialUser() { return getString(credentialUser); }
  public void setCredentialUser(String v) { setString(credentialUser, v, null); }

  public static final Property credentials = newProperty(0, BPassword.DEFAULT, null);
  public BPassword getCredentials() { return (BPassword) get(credentials); }
  public void setCredentials(BPassword v) { set(credentials, v, null); }

  public static final Property autoCommit = newProperty(0, BRelTime.DEFAULT, null);
  public BRelTime getAutoCommit() { return (BRelTime) get(autoCommit); }
  public void setAutoCommit(BRelTime v) { set(autoCommit, v, null); }

  public static final Property restartStationAfterPull = newProperty(0, false, null);
  public boolean getRestartStationAfterPull() { return getBoolean(restartStationAfterPull); }
  public void setRestartStationAfterPull(boolean v) { setBoolean(restartStationAfterPull, v, null); }

  public static final Property storeXml = newProperty(0, false, null);
  public boolean getStoreXml() { return getBoolean(storeXml); }
  public void setStoreXml(boolean v) { setBoolean(storeXml, v, null); }

  public static final Property status = newProperty(Flags.READONLY | Flags.TRANSIENT | Flags.SUMMARY, "", null);
  public String getStatus() { return getString(status); }
  public void setStatus(String v) { setString(status, v, null); }

  public static final Action init = newAction(Flags.CONFIRM_REQUIRED, null);
  public void init() { invoke(init, null, null); }

  public static final Action commitSnapshot = newAction(Flags.ASYNC | Flags.CONFIRM_REQUIRED, null);
  public void commitSnapshot() { invoke(commitSnapshot, null, null); }

  public static final Action pull = newAction(Flags.ASYNC | Flags.CONFIRM_REQUIRED, null);
  public void pull() { invoke(pull, null, null); }

  public static final Action push = newAction(Flags.ASYNC | Flags.CONFIRM_REQUIRED, null);
  public void push() { invoke(push, null, null); }

  public static final Action refreshStatus = newAction(Flags.ASYNC, null);
  public void refreshStatus() { invoke(refreshStatus, null, null); }

  public static final Action autoCommitTick = newAction(Flags.HIDDEN | Flags.ASYNC, null);
  public void autoCommitTick() { invoke(autoCommitTick, null, null); }

  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BGitService.class);

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  private static final Logger LOG = Logger.getLogger("niagaraGit");
  private Clock.Ticket autoCommitTicket;

  @Override
  public Type[] getServiceTypes() {
    return new Type[] { TYPE };
  }

  @Override
  public void serviceStarted() throws Exception {
    LOG.info("niagara-git service started");
    String started = StationWorkflow.onStarted(host());
    if (started != null && started.length() > 0) {
      setStatus(started);
      LOG.info(started);
    }
    reschedule();
  }

  @Override
  public void serviceStopped() throws Exception {
    if (autoCommitTicket != null) {
      autoCommitTicket.cancel();
      autoCommitTicket = null;
    }
    LOG.info("niagara-git service stopped");
  }

  @Override
  public void changed(Property prop, Context cx) {
    super.changed(prop, cx);
    if (prop == autoCommit) {
      reschedule();
    }
  }

  public void doInit(Context cx) {
    requireAdmin(cx);
    run("init", new Job() {
      public String run() throws Exception {
        return StationWorkflow.init(host(), options());
      }
    });
  }

  public void doCommitSnapshot(Context cx) {
    requireAdmin(cx);
    run("commitSnapshot", new Job() {
      public String run() throws Exception {
        return StationWorkflow.commitSnapshot(host(), options());
      }
    });
  }

  public void doPull(Context cx) {
    requireAdmin(cx);
    run("pull", new Job() {
      public String run() throws Exception {
        return StationWorkflow.pull(host(), options());
      }
    });
  }

  public void doPush(Context cx) {
    requireAdmin(cx);
    run("push", new Job() {
      public String run() throws Exception {
        return StationWorkflow.push(host(), options());
      }
    });
  }

  public void doRefreshStatus(Context cx) {
    requireAdmin(cx);
    run("status", new Job() {
      public String run() throws Exception {
        return StationWorkflow.status(host(), options());
      }
    });
  }

  /** Clock callback. A user context is checked; the station clock passes null. */
  public void doAutoCommitTick(Context cx) {
    if (cx != null && cx.getUser() != null) {
      requireAdmin(cx);
    }
    run("autoCommit", new Job() {
      public String run() throws Exception {
        return StationWorkflow.commitSnapshot(host(), options());
      }
    });
  }

  private void reschedule() {
    if (autoCommitTicket != null) {
      autoCommitTicket.cancel();
      autoCommitTicket = null;
    }
    BRelTime period = getAutoCommit();
    if (period == null || period.getMillis() <= 0L) {
      return;
    }
    autoCommitTicket = Clock.schedulePeriodically(this, period, autoCommitTick, null);
    LOG.info("niagara-git auto-commit every " + period.getMillis() + " ms");
  }

  private void requireAdmin(Context cx) {
    if (cx == null || cx.getUser() == null) {
      throw new SecurityException("niagara-git: admin user required");
    }
    BPermissions permissions = getPermissions(cx);
    if (permissions == null || !permissions.has(BPermissions.ADMIN_INVOKE)) {
      throw new SecurityException("niagara-git: admin permission required");
    }
  }

  private StationWorkflow.Options options() {
    StationWorkflow.Options options = new StationWorkflow.Options();
    options.remoteUrl = getRemoteUrl();
    options.branch = getBranch();
    options.authorName = getAuthorName();
    options.authorEmail = getAuthorEmail();
    options.credentialUser = getCredentialUser();
    options.credentialSecret = readSecret(getCredentials());
    options.storeXml = getStoreXml();
    options.restartAfterPull = getRestartStationAfterPull();
    return options;
  }

  private StationWorkflow.Host host() {
    return new StationWorkflow.Host() {
      public File stationHome() {
        return Sys.getStationHome();
      }

      public void save() {
        // Save histories and the live database before a snapshot or a restart.
        // Pull applies the fetched config.bog again after this returns, because
        // a station save rewrites config.bog from memory.
        Sys.getStation().save();
      }

      public void restart() {
        // The platform's restartStation action is the same one Application
        // Director uses. lease keeps the service subscribed across the call.
        // UNCERTAIN: confirm BSystemPlatformService.restartStation on this
        // Niagara build. See the README.
        try {
          PullApplier.registerHook(Sys.getStationHome());
        } catch (Exception e) {
          LOG.log(Level.WARNING, "could not register pull shutdown hook", e);
        }
        BSystemPlatformService platform = (BSystemPlatformService) Sys.getService(BSystemPlatformService.TYPE);
        platform.lease(1);
        platform.invoke(BSystemPlatformService.restartStation, null, null);
      }
    };
  }

  /**
   * BPassword stores the token with the station's reversible encoder.
   * getValue is privileged so the station security manager allows the read.
   */
  static String readSecret(final BPassword password) {
    if (password == null) {
      return "";
    }
    String value = AccessController.doPrivileged(new PrivilegedAction<String>() {
      public String run() {
        return password.getValue();
      }
    });
    return value == null ? "" : value;
  }

  private void run(String action, Job job) {
    try {
      String message = job.run();
      if (message != null && message.length() > 0) {
        setStatus(message);
        LOG.info(action + ": " + message);
      }
    } catch (SecurityException e) {
      setStatus(e.getMessage());
      LOG.warning(e.getMessage());
      throw e;
    } catch (Exception e) {
      String message = e.getMessage() == null ? e.toString() : e.getMessage();
      setStatus(message);
      LOG.log(Level.SEVERE, action + " failed: " + message, e);
      throw new IllegalStateException(message);
    }
  }

  private interface Job {
    String run() throws Exception;
  }
}
