package javax.baja.util;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
public class Lexicon {
    public static Lexicon make(String module) {
        return new Lexicon();
    }

    public String getText(String key) {
        return key;
    }
}
