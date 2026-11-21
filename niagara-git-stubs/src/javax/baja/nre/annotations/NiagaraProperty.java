package javax.baja.nre.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
@Retention(RetentionPolicy.CLASS)
@Repeatable(NiagaraProperties.class)
public @interface NiagaraProperty {
    String name();
    String type();
    String defaultValue();
    int flags() default 0;
}
