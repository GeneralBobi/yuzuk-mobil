package app.claudian.yuzuk;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(YuzukEklentisi.class);
        super.onCreate(savedInstanceState);
    }
}
