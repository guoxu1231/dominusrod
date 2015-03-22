package dominus.dominusrod.ora112.wifi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.StrictMode;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import dominus.dominusrod.util.DebugUtil;


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


    public ClearGuestConnectedReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);


        DebugUtil.debugIntent(context.getPackageName(), intent);
        boolean debug = false;

        //TODO ConnectivityManager state diagram
        if (debug) {
            //Debugging network  IGNORED
            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                Log.i(context.getPackageName(), intent.getAction() + " [wifi_state]= " +
                        String.valueOf(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)));
            }

            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

                Log.i(context.getPackageName(), "isFailover: " + String.valueOf(intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false)));

                NetworkInfo activeNetwork = connectivity.getActiveNetworkInfo();
                if (activeNetwork != null) {
                    Log.i("[ActiveNetwork]", activeNetwork.getTypeName() + "  " + activeNetwork.getDetailedState().toString());
                    NetworkInfo[] networkInfos = connectivity.getAllNetworkInfo();
                    for (NetworkInfo info : networkInfos)
                        Log.i("[Network Info]", info.getTypeName() + "  " + info.getDetailedState().toString());
                }
            }
        }
        //Debugging end

        // connected to WIFI supplicant
        if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

            WifiInfo info = wifiManager.getConnectionInfo();
            NetworkInfo mobile = connectivity.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            NetworkInfo wifi = connectivity.getNetworkInfo(ConnectivityManager.TYPE_WIFI);


            if (info != null)
                Log.d("[WifiInfo]", info.toString());
            Log.i("\t[Mobile State]", mobile.getDetailedState().toString());
            Log.i("\t[Wifi State]", wifi.getDetailedState().toString());

            //1, check if WIFI connected and SSID is clear-guest  //TODO wifi authentication status?
            if (wifi.isConnected() &&
                    info != null && info.getSSID() != null && info.getSSID().equalsIgnoreCase(EXPECTED_SSID)) {

                DebugUtil.toast(context, "Connected to WIFI " + EXPECTED_SSID);


                if (mobile.isConnected() || wifi.isConnected()) { //TODO Wifi priority high than mobile
                    Log.i("", "      Will request Wifi Key from " + WIFI_KEY_URL);

                    //NetworkOnMainThreadException
                    StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
                    StrictMode.setThreadPolicy(policy);

                    String wifiKey;
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
                            DebugUtil.toast(context, "Wifi Key Requst Success: " + wifiKey + " Total Time: " +
                                    DateUtils.formatElapsedTime(end.getTime() - start.getTime()));
                        } else {
                            //TODO http error
                            DebugUtil.toast(context, httpConnection.getResponseMessage());
                        }

                    } catch (IOException e) {
                        DebugUtil.toast(context, e.getCause().toString());
                    }
                } else { //2, check GPRS status, enable the GPRS if not.

                }

            }
        }

    }


}
