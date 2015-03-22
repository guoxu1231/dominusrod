package dominus.dominusrod.util;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by shawguo on 3/22/15.
 */
public abstract class DebugUtil {

    public static void debugIntent(String TAG, Intent intent) {

        Log.d(TAG, intent.toString());

        Bundle bundle = intent.getExtras();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, String.format("\t%s %s (%s)", key,
                    value.toString(), value.getClass().getName()));
        }
    }


}
