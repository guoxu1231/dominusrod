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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import dominus.dominusrod.util.DebugUtil;
import dominus.dominusrod.util.annotation.NonPublicApi;
import dominus.dominusrod.util.devops.AppAnalytics;


/**
 * State-aware Receiver:
 *      identify initial state and trigger state transition;
 *      switch mobile/wifi in async manner to avoid connectivity state chaos;
 *      1, wifi connect to clear-guest;
 *      2, if no cached wifi, trigger "mobile request wifi key" step; elsewise login wifi portal by cached key;
 *      3, In mobile network, request wifi key and cache it, restore network state and move to login step;
 *      4, login wifi portal
 *
 * One-time Receiver(Retired) :
 *      1, check if WIFI connected and SSID is clear-guest <br>
 *      2, check GPRS status, enable the GPRS if not.<br>
 *      3, request for wifi key from website<br>
 *      4, authenticate WIFI;<br>
 *      5, restore GPRS status;<br>
 */
public class ClearGuestConnectedReceiver extends BroadcastReceiver {

    private static String EXPECTED_SSID = "clear-guest";
    private static String WIFI_KEY_URL = "http://dominusxyz.appsp0t.com/intranet/wifi_password";
    private static String PREFERENCES_FILE_NAME = "WIFI_KEY";
    private static String LOGIN_USER = "guest";
    private static String LOGIN_URL = "https://webauth-redirect.oracle.com/login.html";
    private static String AUTO_LOGIN_STATE = "AUTO_LOGIN_STATE";
    private static int INVALID_NETWORK_ID = -99;

    private Date loginDate = new Date(0L);

    public ClearGuestConnectedReceiver() {
    }

    /**
     * Trigger auto-login process once connect to wifi(clear-guest);
     */
    private enum AutoLoginState {
        WIFI_CONNECTED_INITIAL,  //initial state, then disable wifi, enable mobile,
        MOBILE_REQUEST_KEY_REQUIRED, //get wifi key, then restore network. disable mobile and enable wifi
        WIFI_AUTHENCATE_REQUIRED, //has wifi key and do wifi authentication.
    }

    private AutoLoginState getState(Context context) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        AutoLoginState state = AutoLoginState.valueOf(mySharedPreferences.getString(AUTO_LOGIN_STATE,
                AutoLoginState.WIFI_CONNECTED_INITIAL.toString()));
        return state;
    }

    private void saveState(Context context, AutoLoginState state) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        editor.putString(AUTO_LOGIN_STATE, state.toString());
        editor.commit();
        Log.i("[ClearGuest]", "Set State:" + state.toString());
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
        Log.i("[ClearGuest]", "Save Wifi Key to SharedPreferences " + PREFERENCES_FILE_NAME);
    }

    private void saveNetworkId(Context context, int clearGuestId) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        editor.putInt("CLEAR_GUEST_NETWORK_ID", clearGuestId);
        editor.commit();
    }

    private int getNetworkId(Context context) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        return mySharedPreferences.getInt("CLEAR_GUEST_NETWORK_ID", INVALID_NETWORK_ID);
    }


    /**
     * get cached wifi key for today.return null if not existed.
     *
     * @param context
     * @return
     */
    protected String getCachedWifiKey(Context context) {
        SharedPreferences mySharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
        if (mySharedPreferences.getInt("WIFI_KEY_DATE", -1) == new Date().getDate())
            return mySharedPreferences.getString("WIFI_KEY", null);
        else
            return null;
    }

    /**
     * Request Wifi Key from WIFI_KEY_URL.(Must be in mobile network)
     * @param context
     * @return
     */
    protected String requestWifiKey(Context context) {

        String wifiKey = null;

        //TODO NetworkOnMainThreadException workaround, will move to async thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Date start, end;
        try {
            start = new Date();
            URL url = new URL(WIFI_KEY_URL);
            URLConnection connection = url.openConnection();
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setReadTimeout(5000);
            httpConnection.setConnectTimeout(5000 /* milliseconds */);
            httpConnection.setRequestMethod("GET");
            AppAnalytics.enrichCookies(context,httpConnection);
            httpConnection.connect();
            int responseCode = httpConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()));
                wifiKey = reader.readLine();
                httpConnection.disconnect();
                end = new Date();
                if (wifiKey.length() > 0 && wifiKey.length() < 15)
                    DebugUtil.toast(context, "[ClearGuest] Wifi Key Requst Success: " + wifiKey + " Total Time(ms): " + (end.getTime() - start.getTime()));
                else {
                    DebugUtil.toast(context, "[ClearGuest] Wifi Key Request Failed:" + wifiKey); //use unauthenticated wifi
                    return null;
                }
            } else {
                DebugUtil.toast(context, httpConnection.getResponseMessage());
            }
        } catch (IOException e) {
            DebugUtil.toast(context, e.toString());
        }

        return wifiKey;
    }


    /**
     * Login wifi portal by wifi connection;
     * 1, not able to identify current login status;
     * 2, repeat login when re-connect to clear-guest
     *
     * @param context
     * @param wifiKey
     * @return
     */
    protected boolean loginWifiPortal(Context context, String wifiKey) {

        String postData = String.format("username=%s&password=%s&buttonClicked=%s&err_flag=%sredirect_url=%s",
                LOGIN_USER, wifiKey, "4", "0", "www.my.oracle.com");
        Log.d("[ClearGuest]", "Login Form Post Data:" + postData);

        //TODO NetworkOnMainThreadException workaround, will move to async thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        try {
            URL url = new URL(LOGIN_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(5000);
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(postData.length());

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(postData);
            writer.flush();
            writer.close();
            os.close();
            conn.connect();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                DebugUtil.toast(context, "[ClearGuest] Login Success");
                return true;
            } else {
                DebugUtil.toast(context, conn.getResponseMessage());
            }
        } catch (IOException e) {
            DebugUtil.toast(context, e.toString());
        }
        return false;

    }


    @NonPublicApi
    protected void setMobileDataEnabled(Context context, boolean enabled) {
        final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Class conmanClass;

        try {
            conmanClass = Class.forName(conman.getClass().getName());

            final Field connectivityManagerField = conmanClass.getDeclaredField("mService");
            connectivityManagerField.setAccessible(true);
            final Object connectivityManager = connectivityManagerField.get(conman);
            final Class connectivityManagerClass = Class.forName(connectivityManager.getClass().getName());
            final Method setMobileDataEnabledMethod = connectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
            setMobileDataEnabledMethod.setAccessible(true);
            setMobileDataEnabledMethod.invoke(connectivityManager, enabled);
            Log.i("[ClearGuest]", "setMobileDataEnabled: " + enabled);
        } catch (Exception e) {
            DebugUtil.toast(context, e.toString());
        }
    }


    @Override
    public void onReceive(Context context, Intent intent) {

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);


        DebugUtil.debugIntent(context.getPackageName(), intent);

        /**
         * Sumsang Note 2 Network switch Events
         *      wifi and mobile are mutually exclusive;
         *
         * For example:
         * scen-1,
         *      mobile is on, enable wifi => open wifi event, mobile close event(isFailover=true), open wifi event
         * scen-2
         *      mobile is on, wifi is on, disable wifi => close wifi event, enable mobile event
         * scen-3
         *      wifi is on, enable mobile => close mobile(isFailover=true), enable wifi
         **/
        //TODO add error report mechanism

        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

            NetworkInfo activeNetwork = connectivity.getActiveNetworkInfo();
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            NetworkInfo mobile = connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            NetworkInfo wifi = connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            NetworkInfo event = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            AutoLoginState currentState = getState(context);

            Log.i("[ClearGuest]", "Current State: " + currentState.toString());//TODO

            //initial state - WIFI_CONNECTED_INITIAL
            if (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI &&
                    wifi.isConnected() &&
                    wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().equalsIgnoreCase(EXPECTED_SSID)) {

                String cachedWifiKey = getCachedWifiKey(context);

                if (currentState.equals(AutoLoginState.WIFI_CONNECTED_INITIAL) &&
                        new Date().getTime() - loginDate.getTime() > 15 * 1000) { //avoid repeat wifi login

                    DebugUtil.toast(context, "[ClearGuest] Connecting to WIFI " + EXPECTED_SSID);//TODO

                    if (cachedWifiKey == null) { // => go to 2rd state;
                        //wifi/mobile are exclusive
                        wifiManager.setWifiEnabled(false);
                        this.setMobileDataEnabled(context, true);
                        saveNetworkId(context, wifiInfo.getNetworkId());
                        saveState(context, AutoLoginState.MOBILE_REQUEST_KEY_REQUIRED);
                        return;
                    } else {
                        DebugUtil.toast(context, "[ClearGuest] Cached Wifi Key:" + cachedWifiKey);
                        loginWifiPortal(context, getCachedWifiKey(context));
                        saveState(context, AutoLoginState.WIFI_CONNECTED_INITIAL);
                    }
                    return;
                } else {
                    saveState(context, AutoLoginState.WIFI_CONNECTED_INITIAL);
                }
            }

            //second state - MOBILE_REQUEST_KEY_REQUIRED
            if (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE &&
                    mobile.isConnected() && currentState.equals(AutoLoginState.MOBILE_REQUEST_KEY_REQUIRED)) {

                Log.i("[ClearGuest]", "Will request Wifi Key from " + WIFI_KEY_URL);
                String wifiKey = requestWifiKey(context);

                if (wifiKey != null)
                    saveWifiKey(context, wifiKey);

                //restore network
                this.setMobileDataEnabled(context, false);

                wifiManager.setWifiEnabled(true);
                //re-connect to clear-guest
                int clearGuestId = getNetworkId(context);
                if (clearGuestId != INVALID_NETWORK_ID)
                    wifiManager.enableNetwork(clearGuestId, true);

                saveState(context, AutoLoginState.WIFI_AUTHENCATE_REQUIRED);
                return;
            }

            //third state - WIFI_AUTHENCATE_REQUIRED
            if (activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI &&
                    wifi.isConnected() &&
                    wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().equalsIgnoreCase(EXPECTED_SSID) &&
                    currentState.equals(AutoLoginState.WIFI_AUTHENCATE_REQUIRED)) {

                String wifiKey = getCachedWifiKey(context);
                if (wifiKey != null && loginWifiPortal(context, wifiKey))
                    loginDate = new Date();
                saveState(context, AutoLoginState.WIFI_CONNECTED_INITIAL);
                return;
            }


            /**
             *  Retired Solution:
             *      1, enable/disable wifi or mobile is async process; we can not finish all three steps in one time;
             *      2, mobile and wifi are mutually exclusive radio, we can not finish frquently network switch in one time;
             */
            /**
             if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
             Log.i("\t[Active][Mobile State]", mobile.toString());
             Log.i("\t[Wifi State]", wifi.toString());
             }
             if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
             Log.i("\t[Active][Wifi State]", wifi.toString());
             Log.i("\t[Active][Wifi Detail]", info.toString());
             Log.i("\t[Mobile State]", mobile.toString());

             **/

            /**
             //1, check if WIFI connected and SSID is clear-guest
             if (wifi.isConnected() &&
             wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().equalsIgnoreCase(EXPECTED_SSID)) {

             DebugUtil.toast(context, "Connected to WIFI " + EXPECTED_SSID);

             String wifiKey = null;
             wifiKey = getCachedWifiKey(context);
             if (wifiKey == null) {

             //switch to mobile network
             DebugUtil.toast(context, "Switch to Mobile Network");


             //3, request for wifi key from website
             if (mobile.isConnected()) {
             connectivity.setNetworkPreference(ConnectivityManager.TYPE_MOBILE);
             wifiKey = requestWifiKey(context);
             } else { //2, check GPRS status, enable the GPRS if not.
             try {
             Log.i("[ClearGuest]", "Enabling Mobile Network");
             this.setMobileDataEnabled(context, true);
             } catch (Exception e) {
             Log.e("[ClearGuest]", "ConnectivityManager.setMobileDataEnabled Failed " + e.getCause());
             }
             if (mobile.isConnected()) { //Always call this before attempting to perform data transactions!!!
             connectivity.setNetworkPreference(ConnectivityManager.TYPE_MOBILE);
             wifiKey = requestWifiKey(context);
             }
             try {
             Log.i("[ClearGuest]", "Disabling Mobile Network");
             this.setMobileDataEnabled(context, false);
             } catch (Exception e) {
             Log.e("[ClearGuest]", "ConnectivityManager.setMobileDataEnabled Failed " + e.getCause());
             }
             }
             //switch back to default network preference
             connectivity.setNetworkPreference(ConnectivityManager.DEFAULT_NETWORK_PREFERENCE);

             }
             //4, authenticate WIFI
             if (wifiKey != null)
             loginWifiPortal(context, wifiKey);
             } **/

        }
    }

}
