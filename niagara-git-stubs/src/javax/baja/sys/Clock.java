package javax.baja.sys;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public final class Clock {
    private Clock() {
    }

    public static Ticket schedulePeriodically(BComponent component, BRelTime period, Action action, BValue arg) {
        return new Ticket();
    }

    public static final class Ticket {
        public void cancel() {
        }
    }
}
