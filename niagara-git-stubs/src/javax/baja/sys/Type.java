package javax.baja.sys;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public final class Type {
    private final Class<?> type;

    public Type(Class<?> type) {
        this.type = type;
    }

    public Class<?> getTypeClass() {
        return type;
    }
}
