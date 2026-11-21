package javax.baja.sys;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public class BComplex extends BValue {
    protected static Property newProperty(int flags, String defaultValue, Object facets) {
        return new Property();
    }

    protected static Property newProperty(int flags, boolean defaultValue, Object facets) {
        return new Property();
    }

    protected static Property newProperty(int flags, BValue defaultValue, Object facets) {
        return new Property();
    }

    protected static Action newAction(int flags, Object facets) {
        return new Action();
    }

    public String getString(Property property) {
        return "";
    }

    public void setString(Property property, String value, Context cx) {
    }

    public boolean getBoolean(Property property) {
        return false;
    }

    public void setBoolean(Property property, boolean value, Context cx) {
    }

    public BValue get(Property property) {
        return null;
    }

    public void set(Property property, BValue value, Context cx) {
    }

    public void changed(Property property, Context cx) {
    }
}
