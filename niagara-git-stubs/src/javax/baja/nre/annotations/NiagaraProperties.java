package javax.baja.nre.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** STAND-IN. Not a Niagara SDK class. Never ship this. Container for repeatable NiagaraProperty. */
@Retention(RetentionPolicy.CLASS)
public @interface NiagaraProperties {
    NiagaraProperty[] value();
}
