package javax.baja.sys;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public abstract class BAbstractService extends BComponent {
    public void serviceStarted() throws Exception {
    }

    public void serviceStopped() throws Exception {
    }

    public Type[] getServiceTypes() {
        return new Type[0];
    }
}
