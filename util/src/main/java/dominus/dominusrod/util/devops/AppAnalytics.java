package dominus.dominusrod.util.devops;

import android.content.Context;

import java.net.HttpURLConnection;


/**
 * Collect usage information for statistics usage.
 * <blockquote>* Must be in mobile network and interact with GAE;</blockquote>
 */
public class AppAnalytics {

    public static String TAG = "[APP Analytics]";

    /**
     * Re-use current HttpURLConnection and enrich cookies with usage information.
     *
     * @param conn
     */
    public static void enrichCookies(Context context, HttpURLConnection conn) {

        conn.setRequestProperty("Cookie", DeviceInfo.getMetrics(context));

    }


}
