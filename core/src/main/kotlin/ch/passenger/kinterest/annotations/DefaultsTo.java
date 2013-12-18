package ch.passenger.kinterest.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by svd on 12/12/13.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultsTo {
    String value();
}
