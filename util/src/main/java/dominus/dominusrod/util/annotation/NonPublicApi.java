package dominus.dominusrod.util.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * These non-public APIs then you should be aware that your application is at great risk.
 * Basically there are no guarantees that APIs will not be broken with next update to Android OS.
 * There are even no guarantees about consistent behavior across devices from different vendors.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NonPublicApi {
}

