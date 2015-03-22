package dominus.dominusrod.wifi.ora112.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.StrictMode;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import dominus.dominusrod.util.DebugUtil;
import dominus.dominusrod.util.annotation.NonPublicApi;


/**
 * 1, check if WIFI connected and SSID is clear-guest <br>
 * 2, check GPRS status, enable the GPRS if not.<br>
 * 3, request for wifi key from website<br>
 * 4, authenticate WIFI;<br>
 * 5, restore GPRS status;<br>
 */
public class ClearGuestConnectedReceiver extends BroadcastReceiver {

    private static String EXPECTED_SSID = "PWC";
    private static String WIFI_KEY_URL = "http://dominusxyz.appsp0t.com/intranet/wifi_password";
    private static String PREFERENCES_FILE_NAME = "WIFI_KEY";


    public ClearGuestConnectedReceiver() {
    }


    /**
     * Save wifi key to shared preferences file.
     *
     * @param context
     * @param wifiKey
     */
    private void saveWifiKey(Context context, String wifiKey) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        editor.putString("WIFI_KEY", wifiKey);
        editor.putInt("WIFI_KEY_DATE", new Date().getDate());
        editor.commit();
    }


    /**
     * get cached wifi key for today.
     *
     * @param context
     * @return
     */
    private String getCachedWifiKey(Context context) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        if (mySharedPreferences.getInt("WIFI_KEY_DATE", -1) == new Date().getDate())
            return mySharedPreferences.getString("WIFI_KEY", null);
        else
            return null;
    }


    private String requestWifiKey(Context context) {

        String wifiKey = null;
        wifiKey = getCachedWifiKey(context);
        if (wifiKey == null)
            Log.i("[ClearGuest]", "Will request Wifi Key from " + WIFI_KEY_URL);
        else {
            Log.i("[ClearGuest] ", "Cached Wifi Key:" + wifiKey);
            return wifiKey;
        }

        //TODO NetworkOnMainThreadException workaround, will move to async thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);


        Date start, end;
        try {
            start = new Date();
            URL url = new URL(WIFI_KEY_URL);
            URLConnection connection = url.openConnection();
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setReadTimeout(2500);
            httpConnection.setConnectTimeout(2500 /* milliseconds */);
            httpConnection.setRequestMethod("GET");
//                        httpConnection.setDoInput(true);
            httpConnection.connect();
            int responseCode = httpConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
                wifiKey = reader.readLine();
                httpConnection.disconnect();
                end = new Date();
                DebugUtil.toast(context, "Wifi Key Requst Success: " + wifiKey + " Total Time(ms): " + (end.getTime() - start.getTime()));
            } else {
                //TODO http error
                DebugUtil.toast(context, httpConnection.getResponseMessage());
            }
        } catch (IOException e) {
            DebugUtil.toast(context, e.toString());
        }

        saveWifiKey(context, wifiKey);
        return wifiKey;
    }


    @NonPublicApi
    private void setMobileDataEnabled(Context context, boolean enabled) throws ClassNotFoundException, NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Class conmanClass = Class.forName(conman.getClass().getName());
        final Field connectivityManagerField = conmanClass.getDeclaredField("mService");
        connectivityManagerField.setAccessible(true);
        final Object connectivityManager = connectivityManagerField.get(conman);
        final Class connectivityManagerClass = Class.forName(connectivityManager.getClass().getName());
        final Method setMobileDataEnabledMethod = connectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
        setMobileDataEnabledMethod.setAccessible(true);

        setMobileDataEnabledMethod.invoke(connectivityManager, enabled);
    }


    @Override
    public void onReceive(Context context, Intent intent) {

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);


        DebugUtil.debugIntent(context.getPackageName(), intent);

        // connected to WIFI supplicant
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

            NetworkInfo activeNetwork = connectivity.getActiveNetworkInfo();
            WifiInfo info = wifiManager.getConnectionInfo();
            NetworkInfo mobile = connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            NetworkInfo wifi = connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo event = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

            if (activeNetwork == null || //no connected networks at all
                    event.getType() != ConnectivityManager.TYPE_WIFI) //not wifi event
                return;

            if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                Log.i("\t[Active][Mobile State]", mobile.toString());
                Log.i("\t[Wifi State]", wifi.toString());
            }
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                Log.i("\t[Active][Wifi State]", wifi.toString());
                Log.i("\t[Active][Wifi Detail]", info.toString());
                Log.i("\t[Mobile State]", mobile.toString());
            }

            //1, check if WIFI connected and SSID is clear-guest  //TODO wifi authentication status?
            if (wifi.isConnected() &&
                    info != null && info.getSSID() != null && info.getSSID().equalsIgnoreCase(EXPECTED_SSID)) {

                DebugUtil.toast(context, "Connected to WIFI " + EXPECTED_SSID);

                String wifiKey;

                if (getCachedWifiKey(context) == null) {
                    //3, request for wifi key from website
                    if (mobile.isConnected()) { //TODO Wifi priority high than mobile
                        wifiKey = requestWifiKey(context);
                    } else { //2, check GPRS status, enable the GPRS if not.
                        try {
                            Log.i("[ClearGuest]", "Enabling Mobile Network");
                            this.setMobileDataEnabled(context, true);
                        } catch (Exception e) {
                            Log.e("[ClearGuest]", "ConnectivityManager.setMobileDataEnabled Failed " + e.getCause());
                        }
                        wifiKey = requestWifiKey(context);
                        try {
                            Log.i("[ClearGuest]", "Disabling Mobile Network");
                            this.setMobileDataEnabled(context, false);
                        } catch (Exception e) {
                            Log.e("[ClearGuest]", "ConnectivityManager.setMobileDataEnabled Failed " + e.getCause());
                        }
                    }
                }

                //4, authenticate WIFI  TODO avoid to repeat authenticate


            }

        }
    }

}
