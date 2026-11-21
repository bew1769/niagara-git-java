package javax.baja.nre.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** STAND-IN. Not a Niagara SDK class. Never ship this. Container for repeatable NiagaraAction. */
@Retention(RetentionPolicy.CLASS)
public @interface NiagaraActions {
    NiagaraAction[] value();
}
