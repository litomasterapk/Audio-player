package com.litomasterapk.audioplayer;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaNotifPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
