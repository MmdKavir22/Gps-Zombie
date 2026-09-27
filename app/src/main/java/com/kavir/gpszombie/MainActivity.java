package com.kavir.gpszombie;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
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
    private android.location.LocationListener listener;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        web.setWebChromeClient(new WebChromeClient()); web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidGPS"); setContentView(web);
        web.loadUrl("file:///android_asset/game.html");
    }
    public void startGameLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION); return;
        }
        LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        boolean on = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!on) { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); web.evaluateJavascript("window.gpsStatus('location_off')", null); return; }
        listener = new android.location.LocationListener(){ public void onLocationChanged(Location l){ send(l); } public void onProviderEnabled(String p){} public void onProviderDisabled(String p){} public void onStatusChanged(String p,int s,Bundle x){} };
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 2f, listener); lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 3f, listener); Location last=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); if(last==null) last=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); if(last!=null) send(last); } catch(SecurityException ignored){}
    }
    private void send(Location l){ String js="window.onNativeLocation("+l.getLatitude()+","+l.getLongitude()+","+l.getAccuracy()+")"; runOnUiThread(()->web.evaluateJavascript(js,null)); }
    @Override protected void onDestroy(){ if(listener!=null){ try{((LocationManager)getSystemService(LOCATION_SERVICE)).removeUpdates(listener);}catch(Exception ignored){} } super.onDestroy(); }
    public class Bridge { @JavascriptInterface public void start(){ startGameLocation(); } }
}
