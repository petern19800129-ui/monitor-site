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
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_BT = 1001;

    // Exact service-UUID payloads captured from Smart Life/Tuya Smart.
    // These are intentionally NOT modified or re-checksummed.
    private static final byte[] EXACT_OFF_B4 = hex("0B6E51000200B4058D21A3CC2B274044AD19D5E6CB6EFFAFE8C4");
    private static final byte[] EXACT_ON_B5  = hex("0B6E51000200B505C077FC43F921BACD2CB276B9464BF8E58BEB");

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private final List<AdvertiseCallback> callbacks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private int startedCount;
    private int failedCount;
    private String currentName = "";
    private String currentPacket = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        buildUi();
        requestBtPermissions();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Bluetooth Bulb Test v0.3");
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Exact captured Tuya Beacon replay test");
        sub.setTextSize(15f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        TextView info = new TextView(this);
        info.setText("This version replays the ORIGINAL packets byte-for-byte:\nOFF = sequence B4   •   ON = sequence B5");
        info.setGravity(Gravity.CENTER);
        info.setTextSize(15f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(24);
        root.addView(info, infoLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(24);
        root.addView(row, rowLp);

        Button off = new Button(this);
        off.setText("REPLAY OFF B4");
        off.setTextSize(18f);
        off.setOnClickListener(v -> sendExact("OFF B4", EXACT_OFF_B4));
        row.addView(off, new LinearLayout.LayoutParams(0, dp(80), 1f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

        Button on = new Button(this);
        on.setText("REPLAY ON B5");
        on.setTextSize(18f);
        on.setOnClickListener(v -> sendExact("ON B5", EXACT_ON_B5));
        row.addView(on, new LinearLayout.LayoutParams(0, dp(80), 1f));

        status = new TextView(this);
        status.setText("Ready. Power-cycle the bulb, force-stop Smart Life, then try REPLAY ON B5 first.");
        status.setGravity(Gravity.CENTER);
        status.setTextSize(14f);
        status.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(28);
        root.addView(status, statusLp);

        TextView note = new TextView(this);
        note.setText("Important: Tuya documents anti-replay and command integrity. This is only a diagnostic. If the bulb rejects the old B4/B5 sequence numbers, the proper controller must generate fresh encrypted packets using the Beacon key.");
        note.setGravity(Gravity.CENTER);
        note.setTextSize(14f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(30);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ArrayList<String> missing = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_BT);
        }
    }

    private boolean hasBtPermissions() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                 checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    private void sendExact(String name, byte[] serviceBytes) {
        if (!hasBtPermissions()) {
            requestBtPermissions();
            status.setText("Allow Nearby devices / Bluetooth permission first.");
            return;
        }
        if (adapter == null) {
            status.setText("No Bluetooth adapter found.");
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                status.setText("Turn Bluetooth on first.");
                return;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
        } catch (SecurityException e) {
            status.setText("Bluetooth permission error: " + e.getMessage());
            return;
        }
        if (advertiser == null) {
            status.setText("BLE advertising is unavailable on this phone.");
            return;
        }

        stopActive();
        currentName = name;
        currentPacket = toHex(serviceBytes);
        startedCount = 0;
        failedCount = 0;

        AdvertiseData data = buildAdvertiseData(serviceBytes);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(3000)
                .build();

        status.setText(currentName + ": starting 2 advertisers...\n" + currentPacket);
        startOne(settings, data, 1);
        handler.postDelayed(() -> startOne(settings, data, 2), 20);
    }

    private AdvertiseData buildAdvertiseData(byte[] serviceBytes) {
        AdvertiseData.Builder data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false);
        for (int i = 0; i < serviceBytes.length; i += 2) {
            int uuid16 = (serviceBytes[i] & 0xFF) | ((serviceBytes[i + 1] & 0xFF) << 8);
            String uuidText = String.format(Locale.US,
                    "0000%04x-0000-1000-8000-00805f9b34fb", uuid16);
            data.addServiceUuid(new ParcelUuid(UUID.fromString(uuidText)));
        }
        return data.build();
    }

    private void startOne(AdvertiseSettings settings, AdvertiseData data, int number) {
        if (advertiser == null) return;
        AdvertiseCallback cb = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                startedCount++;
                updateResult();
            }
            @Override public void onStartFailure(int errorCode) {
                failedCount++;
                status.setText(String.format(Locale.US,
                        "%s: advertiser %d failed (error %d), started %d/2\n%s",
                        currentName, number, errorCode, startedCount, currentPacket));
            }
        };
        callbacks.add(cb);
        try {
            advertiser.startAdvertising(settings, data, cb);
        } catch (Exception e) {
            failedCount++;
            status.setText("Could not start advertiser " + number + ": " + e.getMessage());
        }
    }

    private void updateResult() {
        status.setText(String.format(Locale.US,
                "%s: %d/2 advertisers started for 3 seconds%s\n%s",
                currentName, startedCount,
                failedCount > 0 ? " (" + failedCount + " failed)" : "",
                currentPacket));
    }

    private void stopActive() {
        handler.removeCallbacksAndMessages(null);
        if (advertiser != null && hasBtPermissions()) {
            for (AdvertiseCallback cb : callbacks) {
                try { advertiser.stopAdvertising(cb); } catch (Exception ignored) {}
            }
        }
        callbacks.clear();
    }

    @Override protected void onDestroy() {
        stopActive();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        return sb.toString();
    }
}
