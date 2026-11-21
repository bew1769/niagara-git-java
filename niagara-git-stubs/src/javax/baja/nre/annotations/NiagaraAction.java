package javax.baja.nre.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
@Retention(RetentionPolicy.CLASS)
@Repeatable(NiagaraActions.class)
public @interface NiagaraAction {
    String name();
    int flags() default 0;
}
