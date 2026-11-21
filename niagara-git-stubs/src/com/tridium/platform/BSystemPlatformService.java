package com.tridium.platform;

import javax.baja.sys.Action;
import javax.baja.sys.BComponent;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;

/**
 * STAND-IN. Not a Niagara SDK class. Never ship this.
 *
 * <p>The real class lives in the platform module. axCommunity (Niagara 4 port)
 * restarts a station with:
 * {@code Sys.getService(BSystemPlatformService.TYPE).invoke(BSystemPlatformService.restartStation, null)}
 * after {@code lease(1)}. Confirm the action field name against your baja jar.
 */
public class BSystemPlatformService extends BComponent {
    public static final Type TYPE = Sys.loadType(BSystemPlatformService.class);
    public static final Action restartStation = new Action();

    public void lease(int count) {
    }
}
