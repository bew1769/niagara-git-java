package javax.baja.ui;

import javax.baja.util.Lexicon;

/**
 * STAND-IN. Not a Niagara SDK class. Never ship this.
 * The {@code (BWidget, Lexicon, String)} constructor matches Tridium's N4 view sample.
 */
public abstract class Command {
    public Command(BWidget owner, Lexicon lexicon, String key) {
    }

    public Command(BWidget owner, String label) {
    }

    public abstract CommandArtifact doInvoke() throws Exception;
}
