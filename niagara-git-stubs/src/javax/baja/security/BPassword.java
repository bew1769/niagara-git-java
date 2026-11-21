package javax.baja.security;

import javax.baja.sys.BSimple;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public class BPassword extends BSimple {
    public static final BPassword DEFAULT = new BPassword("");
    private final String value;

    private BPassword(String value) {
        this.value = value;
    }

    public static BPassword make(String value, String encoding) {
        return new BPassword(value);
    }

    public String getValue() {
        return value;
    }
}
