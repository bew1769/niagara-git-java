package javax.baja.nre.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** STAND-IN. Not a Niagara SDK class. Never ship this. */
@Retention(RetentionPolicy.CLASS)
public @interface AgentOn {
    String[] types() default {};
    String requiredPermissions() default "";
}
