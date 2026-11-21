package javax.baja.sys;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public class BRelTime extends BSimple {
    public static final BRelTime DEFAULT = make(0L);
    private final long millis;

    private BRelTime(long millis) {
        this.millis = millis;
    }

    public static BRelTime make(long millis) {
        return new BRelTime(millis);
    }

    public long getMillis() {
        return millis;
    }
}
