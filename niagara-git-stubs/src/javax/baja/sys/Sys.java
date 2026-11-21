package javax.baja.sys;

import java.io.File;

/**
 * STAND-IN. Not a Niagara SDK class. Never ship this.
 * Real Niagara 4: {@code Sys.getStation()}, {@code Sys.getStationHome()},
 * {@code Sys.getService(Type)}, {@code Sys.loadType(Class)}.
 */
public final class Sys {
    private Sys() {
    }

    public static Type loadType(Class<?> type) {
        return new Type(type);
    }

    public static BStation getStation() {
        return new BStation();
    }

    public static File getStationHome() {
        return new File(".");
    }

    public static BComponent getService(Type type) {
        return null;
    }
}
