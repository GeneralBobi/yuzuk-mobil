package app.claudian.yuzuk;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onResume() {
        ArkaPlanIsci.onPlanda = true;
        super.onResume();
    }
    @Override public void onPause() {
        super.onPause();
        ArkaPlanIsci.onPlanda = false;
        ArkaPlanIsci.hemen(this);
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(YuzukEklentisi.class);
        super.onCreate(savedInstanceState);
    }
}
