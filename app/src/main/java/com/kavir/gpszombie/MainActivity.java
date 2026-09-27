package com.kavir.gpszombie;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 42;
    private WebView web;
    private LocationManager lm;
    private LocationListener listener;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidGPS");
        setContentView(web);
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        web.loadUrl("file:///android_asset/game.html");
    }

    public void startGameLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            sendStatus("permission_wait");
            return;
        }

        boolean gps = false, network = false;
        try {
            gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}

        if (!gps && !network) {
            sendStatus("location_off");
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        sendStatus("searching");

        if (listener != null) {
            try { lm.removeUpdates(listener); } catch (Exception ignored) {}
        }

        listener = new LocationListener() {
            @Override public void onLocationChanged(Location l) { send(l); }
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
        };

        try {
            if (gps) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener);
            if (network) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 1f, listener);

            Location last = null;
            if (gps) last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null && network) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) send(last);
        } catch (SecurityException e) {
            sendStatus("permission_denied");
        }

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                if (gps) lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, getMainExecutor(), l -> { if (l != null) send(l); });
                if (network) lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, getMainExecutor(), l -> { if (l != null) send(l); });
            } catch (SecurityException e) {
                sendStatus("permission_denied");
            }
        }
    }

    private void send(Location l) {
        if (l == null || web == null) return;
        String js = "window.onNativeLocation(" + l.getLatitude() + "," + l.getLongitude() + "," + l.getAccuracy() + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
        sendStatus("located");
    }

    private void sendStatus(String status) {
        if (web == null) return;
        runOnUiThread(() -> web.evaluateJavascript("window.gpsStatus && window.gpsStatus('" + status + "')", null));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_LOCATION) return;
        boolean ok = false;
        for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
        if (ok) startGameLocation();
        else sendStatus("permission_denied");
    }

    @Override protected void onDestroy() {
        if (listener != null && lm != null) {
            try { lm.removeUpdates(listener); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface public void start() { startGameLocation(); }
        @JavascriptInterface public void locate() { startGameLocation(); }
    }
}
