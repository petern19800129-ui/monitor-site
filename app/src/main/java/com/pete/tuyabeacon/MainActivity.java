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

    private static final byte[] PREFIX = hex("0B6E51000401");
    private static final byte[] BODY_RED  = hex("D772E30583C975D22FA01AD641374B42BD");
    private static final byte[] BODY_BLUE = hex("C0CD0F34F45F6C1E37B3ED67E5A224CE83");

    private static final int RESYNC_START = 0x45;
    private static final int RESYNC_END   = 0x63;
    private static final int RESYNC_ADV_MS = 700;
    private static final int RESYNC_STEP_MS = 900;

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private final List<AdvertiseCallback> callbacks = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView status;
    private TextView seqView;
    private Button resyncButton;
    private Button redButton;
    private Button blueButton;

    private int sequence = 0x64;
    private int startedCount;
    private int failedCount;
    private String currentName = "";
    private int currentSeq;
    private String currentPacket = "";
    private boolean resyncRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        buildUi();
        requestBtPermissions();
        updateSequenceView();
    }

    private void buildUi() {
        int pad = dp(22);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Bluetooth Bulb Test v0.5");
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Tuya Beacon sequence re-sync test");
        sub.setTextSize(15f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        TextView info = new TextView(this);
        info.setText("RED and BLUE are confirmed command bodies.\nRESYNC walks 45 → 63 in order, then leaves next sequence at 64.");
        info.setGravity(Gravity.CENTER);
        info.setTextSize(15f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(18);
        root.addView(info, infoLp);

        seqView = new TextView(this);
        seqView.setTextSize(18f);
        seqView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams seqLp = new LinearLayout.LayoutParams(-1, -2);
        seqLp.topMargin = dp(18);
        root.addView(seqView, seqLp);

        resyncButton = new Button(this);
        resyncButton.setText("RESYNC 45 → 63");
        resyncButton.setTextSize(19f);
        resyncButton.setOnClickListener(v -> startResync());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(78));
        rlp.topMargin = dp(22);
        root.addView(resyncButton, rlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(14);
        root.addView(row, rowLp);

        redButton = new Button(this);
        redButton.setText("RED");
        redButton.setTextSize(20f);
        redButton.setOnClickListener(v -> sendNormal("RED", BODY_RED));
        row.addView(redButton, new LinearLayout.LayoutParams(0, dp(78), 1f));

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

        blueButton = new Button(this);
        blueButton.setText("BLUE");
        blueButton.setTextSize(20f);
        blueButton.setOnClickListener(v -> sendNormal("BLUE", BODY_BLUE));
        row.addView(blueButton, new LinearLayout.LayoutParams(0, dp(78), 1f));

        status = new TextView(this);
        status.setText("Do not open Smart Life. Tap RESYNC once and let it finish (~28 seconds).");
        status.setGravity(Gravity.CENTER);
        status.setTextSize(14f);
        status.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        TextView note = new TextView(this);
        note.setText("During RESYNC the bulb may turn red part-way through. Do not press anything until the app says RESYNC COMPLETE.");
        note.setGravity(Gravity.CENTER);
        note.setTextSize(13f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(24);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private void startResync() {
        if (!prepareBluetooth()) return;
        if (resyncRunning) return;
        resyncRunning = true;
        setButtonsEnabled(false);
        status.setText("Starting RESYNC at sequence 45...");
        resyncStep(RESYNC_START);
    }

    private void resyncStep(int seq) {
        if (!resyncRunning) return;

        if (seq > RESYNC_END) {
            stopAdvertisersOnly();
            resyncRunning = false;
            sequence = 0x64;
            updateSequenceView();
            setButtonsEnabled(true);
            status.setText("RESYNC COMPLETE.\nNext sequence: 64\nNow press BLUE once.");
            return;
        }

        stopAdvertisersOnly();

        currentName = "RESYNC RED";
        currentSeq = seq & 0xFF;
        byte[] serviceBytes = buildServiceBytes(currentSeq, BODY_RED);
        currentPacket = toHex(serviceBytes);
        startedCount = 0;
        failedCount = 0;

        AdvertiseData data = buildAdvertiseData(serviceBytes);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(RESYNC_ADV_MS)
                .build();

        status.setText(String.format(Locale.US,
                "RESYNC %02X / %02X\nDo not touch anything...", currentSeq, RESYNC_END));

        startOne(settings, data, 1, false);
        handler.postDelayed(() -> startOne(settings, data, 2, false), 20);
        handler.postDelayed(() -> resyncStep(seq + 1), RESYNC_STEP_MS);
    }

    private void sendNormal(String name, byte[] body) {
        if (resyncRunning) {
            status.setText("Wait for RESYNC to finish first.");
            return;
        }
        if (!prepareBluetooth()) return;

        stopAdvertisersOnly();
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
                "%s seq %02X: starting...", currentName, currentSeq));

        startOne(settings, data, 1, true);
        handler.postDelayed(() -> startOne(settings, data, 2, true), 20);

        sequence = (sequence + 1) & 0xFF;
        updateSequenceView();
    }

    private boolean prepareBluetooth() {
        if (!hasBtPermissions()) {
            requestBtPermissions();
            status.setText("Allow Nearby devices / Bluetooth permission first.");
            return false;
        }
        if (adapter == null) {
            status.setText("No Bluetooth adapter found.");
            return false;
        }
        try {
            if (!adapter.isEnabled()) {
                status.setText("Turn Bluetooth on first.");
                return false;
            }
            advertiser = adapter.getBluetoothLeAdvertiser();
        } catch (SecurityException e) {
            status.setText("Bluetooth permission error: " + e.getMessage());
            return false;
        }
        if (advertiser == null) {
            status.setText("BLE advertising is unavailable on this phone.");
            return false;
        }
        return true;
    }

    private void setButtonsEnabled(boolean enabled) {
        resyncButton.setEnabled(enabled);
        redButton.setEnabled(enabled);
        blueButton.setEnabled(enabled);
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

    private static byte[] buildServiceBytes(int seq, byte[] body) {
        byte[] out = new byte[26];
        System.arraycopy(PREFIX, 0, out, 0, PREFIX.length);
        out[6] = (byte) seq;
        out[7] = 0x05;
        System.arraycopy(body, 0, out, 8, body.length);
        out[25] = (byte) (crc8Poly07(out, 25) ^ 0xB1);
        return out;
    }

    private static int crc8Poly07(byte[] data, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc ^= data[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++)
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
        }
        return crc;
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

    private void startOne(AdvertiseSettings settings, AdvertiseData data, int number, boolean showResult) {
        if (advertiser == null) return;
        AdvertiseCallback cb = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                startedCount++;
                if (showResult) updateResult();
            }

            @Override public void onStartFailure(int errorCode) {
                failedCount++;
                if (showResult) {
                    status.setText(String.format(Locale.US,
                            "%s seq %02X: advertiser %d failed (error %d), started %d/2\n%s",
                            currentName, currentSeq, number, errorCode, startedCount, currentPacket));
                }
            }
        };
        callbacks.add(cb);
        try {
            advertiser.startAdvertising(settings, data, cb);
        } catch (Exception e) {
            failedCount++;
            if (showResult) status.setText("Could not start advertiser " + number + ": " + e.getMessage());
        }
    }

    private void updateResult() {
        status.setText(String.format(Locale.US,
                "%s seq %02X: %d/2 advertisers started for 3 seconds%s\n%s",
                currentName, currentSeq, startedCount,
                failedCount > 0 ? " (" + failedCount + " failed)" : "",
                currentPacket));
    }

    private void updateSequenceView() {
        if (seqView != null)
            seqView.setText(String.format(Locale.US, "Next sequence: %02X", sequence & 0xFF));
    }

    private void stopAdvertisersOnly() {
        if (advertiser != null && hasBtPermissions()) {
            for (AdvertiseCallback cb : callbacks) {
                try { advertiser.stopAdvertising(cb); } catch (Exception ignored) {}
            }
        }
        callbacks.clear();
    }

    private void stopEverything() {
        resyncRunning = false;
        handler.removeCallbacksAndMessages(null);
        stopAdvertisersOnly();
    }

    @Override protected void onDestroy() {
        stopEverything();
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
