package com.pete.tuyabeacon;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_BT = 1001;
    private static final byte[] PREFIX = hex("0B6E51000200");
    private static final byte[] OFF = hex("8D21A3CC2B274044AD19D5E6CB6EFFAFE8");
    private static final byte[] ON  = hex("C077FC43F921BACD2CB276B9464BF8E58B");

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback callback;
    private TextView status;
    private TextView seqText;
    private int seq;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        seq = getPreferences(MODE_PRIVATE).getInt("seq", 0xC0) & 0xFF;
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        buildUi();
        requestBtPermissions();
        updateStatus("Ready — start with OFF, then ON");
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Bluetooth Bulb Test");
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Tuya Beacon replay — ON / OFF test");
        sub.setTextSize(16f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        seqText = new TextView(this);
        seqText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams seqLp = new LinearLayout.LayoutParams(-1, -2);
        seqLp.topMargin = dp(24);
        root.addView(seqText, seqLp);
        updateSeq();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(24);
        root.addView(row, rowLp);

        Button off = new Button(this);
        off.setText("OFF");
        off.setTextSize(22f);
        off.setOnClickListener(v -> send("OFF", OFF));
        row.addView(off, new LinearLayout.LayoutParams(0, dp(80), 1f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

        Button on = new Button(this);
        on.setText("ON");
        on.setTextSize(22f);
        on.setOnClickListener(v -> send("ON", ON));
        row.addView(on, new LinearLayout.LayoutParams(0, dp(80), 1f));

        Button reset = new Button(this);
        reset.setText("Reset sequence to C0");
        reset.setOnClickListener(v -> {
            seq = 0xC0;
            saveSeq();
            updateSeq();
            updateStatus("Sequence reset to C0");
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, -2);
        resetLp.topMargin = dp(16);
        root.addView(reset, resetLp);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(15f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        TextView note = new TextView(this);
        note.setText("Force-close Smart Life/Tuya Smart while testing. Each tap advertises for about 1.3 seconds.");
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(28);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] perms = {Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT};
            if (checkSelfPermission(perms[0]) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(perms[1]) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(perms, REQ_BT);
            }
        }
    }

    private boolean hasBtPermissions() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                 checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    private void send(String name, byte[] body) {
        if (!hasBtPermissions()) {
            requestBtPermissions();
            updateStatus("Allow Nearby devices / Bluetooth permission first");
            return;
        }
        if (adapter == null) {
            updateStatus("No Bluetooth adapter found");
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                updateStatus("Turn Bluetooth on first");
                return;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
        } catch (SecurityException e) {
            updateStatus("Bluetooth permission error: " + e.getMessage());
            return;
        }
        if (advertiser == null) {
            updateStatus("BLE advertising is not available on this phone");
            return;
        }

        if (callback != null) {
            try { advertiser.stopAdvertising(callback); } catch (Exception ignored) {}
            callback = null;
        }

        final int thisSeq = seq & 0xFF;
        final byte[] bytes = packet(thisSeq, body);
        AdvertiseData.Builder data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false);

        for (int i = 0; i < bytes.length; i += 2) {
            int u16 = (bytes[i] & 0xFF) | ((bytes[i + 1] & 0xFF) << 8);
            String u = String.format(Locale.US, "0000%04x-0000-1000-8000-00805f9b34fb", u16);
            data.addServiceUuid(new ParcelUuid(UUID.fromString(u)));
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(1300)
                .build();

        callback = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings inEffect) {
                runOnUiThread(() -> updateStatus(String.format(Locale.US,
                        "%s sent — sequence %02X\n%s", name, thisSeq, toHex(bytes))));
            }
            @Override public void onStartFailure(int errorCode) {
                runOnUiThread(() -> updateStatus("Advertising failed — error " + errorCode));
            }
        };

        try {
            advertiser.startAdvertising(settings, data.build(), callback);
            seq = (seq + 1) & 0xFF;
            saveSeq();
            updateSeq();
        } catch (Exception e) {
            updateStatus("Could not advertise: " + e.getMessage());
        }
    }

    private static byte[] packet(int seq, byte[] body) {
        byte[] crcInput = new byte[19];
        crcInput[0] = (byte) seq;
        crcInput[1] = 0x05;
        System.arraycopy(body, 0, crcInput, 2, 17);
        byte checksum = (byte) (crc8(crcInput) ^ 0xB8);
        byte[] out = new byte[26];
        System.arraycopy(PREFIX, 0, out, 0, 6);
        out[6] = (byte) seq;
        out[7] = 0x05;
        System.arraycopy(body, 0, out, 8, 17);
        out[25] = checksum;
        return out;
    }

    private static int crc8(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int i = 0; i < 8; i++)
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
        }
        return crc;
    }

    private void updateStatus(String text) { if (status != null) status.setText(text); }
    private void updateSeq() { if (seqText != null) seqText.setText(String.format(Locale.US, "Next sequence: %02X", seq)); }
    private void saveSeq() { getPreferences(MODE_PRIVATE).edit().putInt("seq", seq).apply(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        if (advertiser != null && callback != null && hasBtPermissions()) {
            try { advertiser.stopAdvertising(callback); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
    private static String toHex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(String.format(Locale.US, "%02X", v & 0xFF));
        return s.toString();
    }
}
