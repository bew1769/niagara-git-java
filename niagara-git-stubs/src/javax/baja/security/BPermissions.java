package javax.baja.security;

/**
 * STAND-IN. Not a Niagara SDK class. Never ship this.
 * Constant names match nhaystack's use of {@code BPermissions.ADMIN_INVOKE} and
 * {@code BComponent.getPermissions(Context)}. The integer values are placeholders.
 */
public class BPermissions {
    public static final int OPERATOR_READ = 1;
    public static final int OPERATOR_WRITE = 2;
    public static final int OPERATOR_INVOKE = 4;
    public static final int ADMIN_READ = 8;
    public static final int ADMIN_WRITE = 16;
    public static final int ADMIN_INVOKE = 32;
    public static final BPermissions DEFAULT = new BPermissions(0);
    private final int mask;

    public BPermissions(int mask) {
        this.mask = mask;
    }

    public boolean has(int permission) {
        return (mask & permission) != 0;
    }
}
