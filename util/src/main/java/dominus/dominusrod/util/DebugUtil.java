package dominus.dominusrod.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/**
 * Debug Utility
 * <p/>
 * Created by shawguo on 3/22/15.
 */
public abstract class DebugUtil {

    public static void debugIntent(String TAG, Intent intent) {

        Log.d(TAG, intent.toString());

        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, String.format("\t%s %s (%s)", key,
                        value.toString(), value.getClass().getName()));
            }
        } else {
            Log.d(TAG, "no extras");

        }
    }

    /**
     * Toast message on the main thread.
     * <p/>
     * <blockquote > you can only use Toast in the main GUI thread, or else you
     * run into problems where the Toast message doesn't disappear after a
     * period (because the main GUI context doesn't know anything about Toast
     * messages used in a separate thread context)
     * <p/>
     * Android: How can i show a toast from a thread running in a remote
     * service?
     * http://stackoverflow.com/questions/6134013/android-how-can-i-show
     * -a-toast-from-a-thread-running-in-a-remote-service </blockquote >
     *
     * @param context
     * @param text
     */
    public static void toast(final Context context, final CharSequence text) {

        // Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        Log.i(context.getPackageName(), text.toString());

        Handler h = new Handler(context.getMainLooper());

        h.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        });

    }


}
