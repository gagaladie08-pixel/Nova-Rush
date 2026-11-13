package com.novarush.app;

import android.os.Bundle;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;
import com.novarush.app.separation.SeparationPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Séparation de la voix sur l'appareil (service en arrière-plan).
        registerPlugin(SeparationPlugin.class);
        super.onCreate(savedInstanceState);
        // L'écran reste allumé pendant que Nova Rush est au premier plan (parties sans toucher prolongées).
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().getSettings().setMediaPlaybackRequiresUserGesture(false);
            getBridge().getWebView().setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        }
    }
}
