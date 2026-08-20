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
    private static final byte[] BODY_OFF = hex("8D21A3CC2B274044AD19D5E6CB6EFFAFE8");
    private static final byte[] BODY_ON  = hex("C077FC43F921BACD2CB276B9464BF8E58B");
    private static final byte[] PREFIX = hex("0B6E51000200");

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private final List<AdvertiseCallback> callbacks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView seqView;
    private int sequence;
    private int startedCount;
    private int failedCount;
    private String currentName = "";
    private int currentSeq;
    private String currentPacket = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sequence = getPreferences(MODE_PRIVATE).getInt("sequence", 0xC0) & 0xFF;
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        buildUi();
        requestBtPermissions();
        refreshStatus();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Bluetooth Bulb Test v0.2");
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Tuya Beacon replay — dual 3-second advertising");
        sub.setTextSize(15f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        seqView = new TextView(this);
        seqView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams seqLp = new LinearLayout.LayoutParams(-1, -2);
        seqLp.topMargin = dp(24);
        root.addView(seqView, seqLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(24);
        root.addView(row, rowLp);

        Button off = new Button(this);
        off.setText("OFF");
        off.setTextSize(22f);
        off.setOnClickListener(v -> sendCommand("OFF", BODY_OFF));
        row.addView(off, new LinearLayout.LayoutParams(0, dp(80), 1f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

        Button on = new Button(this);
        on.setText("ON");
        on.setTextSize(22f);
        on.setOnClickListener(v -> sendCommand("ON", BODY_ON));
        row.addView(on, new LinearLayout.LayoutParams(0, dp(80), 1f));

        Button reset = new Button(this);
        reset.setText("Reset next sequence to C0");
        reset.setOnClickListener(v -> {
            stopActive();
            sequence = 0xC0;
            saveSequence();
            updateSequenceView();
            status.setText("Sequence reset to C0");
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(-1, -2);
        resetLp.topMargin = dp(16);
        root.addView(reset, resetLp);

        status = new TextView(this);
        status.setTextSize(14f);
        status.setGravity(Gravity.CENTER);
        status.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        TextView note = new TextView(this);
        note.setText("Force-stop Smart Life/Tuya Smart first. Tap once, then wait at least 4 seconds before the next button.");
        note.setTextSize(14f);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(28);
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

    private void sendCommand(String name, byte[] body) {
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
        currentSeq = sequence & 0xFF;
        byte[] serviceBytes = buildServiceBytes(currentSeq, body);
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

        status.setText(String.format(Locale.US,
                "%s seq %02X: starting 2 advertisers...\n%s", currentName, currentSeq, currentPacket));

        startOne(settings, data, 1);
        handler.postDelayed(() -> startOne(settings, data, 2), 20);

        sequence = (sequence + 1) & 0xFF;
        saveSequence();
        updateSequenceView();
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
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                startedCount++;
                updateResult();
            }

            @Override
            public void onStartFailure(int errorCode) {
                failedCount++;
                status.setText(String.format(Locale.US,
                        "%s seq %02X: advertiser %d failed (error %d); started %d/2\n%s",
                        currentName, currentSeq, number, errorCode, startedCount, currentPacket));
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
                "%s seq %02X: %d/2 advertisers started for 3 seconds%s\n%s",
                currentName, currentSeq, startedCount,
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

    private static byte[] buildServiceBytes(int seq, byte[] body) {
        byte[] checksumInput = new byte[19];
        checksumInput[0] = (byte) seq;
        checksumInput[1] = 0x05;
        System.arraycopy(body, 0, checksumInput, 2, 17);
        byte checksum = (byte) (crc8Poly07(checksumInput) ^ 0xB8);
        byte[] out = new byte[26];
        System.arraycopy(PREFIX, 0, out, 0, 6);
        out[6] = (byte) seq;
        out[7] = 0x05;
        System.arraycopy(body, 0, out, 8, 17);
        out[25] = checksum;
        return out;
    }

    private static int crc8Poly07(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++)
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
        }
        return crc;
    }

    private void refreshStatus() {
        updateSequenceView();
        if (status != null) status.setText("Ready. Force-stop Smart Life, then try OFF.");
    }

    private void updateSequenceView() {
        if (seqView != null) seqView.setText(String.format(Locale.US, "Next sequence: %02X", sequence & 0xFF));
    }

    private void saveSequence() {
        getPreferences(MODE_PRIVATE).edit().putInt("sequence", sequence & 0xFF).apply();
    }

    @Override
    protected void onDestroy() {
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
