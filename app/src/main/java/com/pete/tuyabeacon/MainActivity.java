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
    private static final int REQ_BT_ADVERTISE = 1001;
    private static final byte[] BODY_OFF = hex("8D21A3CC2B274044AD19D5E6CB6EFFAFE8");
    private static final byte[] BODY_ON  = hex("C077FC43F921BACD2CB276B9464BF8E58B");
    private static final byte[] PREFIX = hex("0B6E51000200");

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback activeCallback;
    private TextView status;
    private TextView seqView;
    private int sequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sequence = getPreferences(MODE_PRIVATE).getInt("sequence", 0xC0) & 0xFF;
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager != null ? manager.getAdapter() : null;
        buildUi();
        requestAdvertisePermissionIfNeeded();
        refreshStatus();
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
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("Tuya Beacon replay test — ON / OFF only");
        sub.setTextSize(16f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        seqView = new TextView(this);
        seqView.setTextSize(15f);
        seqView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams seqLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        seqLp.topMargin = dp(24);
        root.addView(seqView, seqLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dp(24);
        root.addView(buttons, buttonsLp);

        Button off = new Button(this);
        off.setText("OFF");
        off.setTextSize(22f);
        off.setMinHeight(dp(72));
        off.setOnClickListener(v -> sendCommand("OFF", BODY_OFF));
        buttons.addView(off, new LinearLayout.LayoutParams(0, dp(80), 1f));

        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));

        Button on = new Button(this);
        on.setText("ON");
        on.setTextSize(22f);
        on.setMinHeight(dp(72));
        on.setOnClickListener(v -> sendCommand("ON", BODY_ON));
        buttons.addView(on, new LinearLayout.LayoutParams(0, dp(80), 1f));

        Button reset = new Button(this);
        reset.setText("Reset sequence to C0");
        reset.setOnClickListener(v -> {
            sequence = 0xC0;
            saveSequence();
            refreshStatus();
            status.setText("Sequence reset to C0");
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        resetLp.topMargin = dp(16);
        root.addView(reset, resetLp);

        status = new TextView(this);
        status.setTextSize(15f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        TextView note = new TextView(this);
        note.setText("Keep the original Tuya/Smart Life app closed while testing. Each tap advertises the captured command for about 1.3 seconds.");
        note.setTextSize(14f);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(30);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private void requestAdvertisePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, REQ_BT_ADVERTISE);
        }
    }

    private boolean hasAdvertisePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
    }

    private void sendCommand(String name, byte[] body) {
        if (!hasAdvertisePermission()) {
            requestAdvertisePermissionIfNeeded();
            status.setText("Bluetooth advertising permission is required.");
            return;
        }
        if (adapter == null) {
            status.setText("No Bluetooth adapter found.");
            return;
        }
        if (!adapter.isEnabled()) {
            status.setText("Turn Bluetooth on first.");
            return;
        }
        if (!adapter.isMultipleAdvertisementSupported()) {
            status.setText("This phone reports that BLE advertising is not supported.");
            return;
        }

        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            status.setText("Bluetooth LE advertiser unavailable.");
            return;
        }

        if (activeCallback != null) {
            try { advertiser.stopAdvertising(activeCallback); } catch (Exception ignored) {}
            activeCallback = null;
        }

        final int seq = sequence & 0xFF;
        byte[] serviceBytes = buildServiceBytes(seq, body);
        AdvertiseData.Builder data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false);

        for (int i = 0; i < serviceBytes.length; i += 2) {
            int uuid16 = (serviceBytes[i] & 0xFF) | ((serviceBytes[i + 1] & 0xFF) << 8);
            String uuidText = String.format(Locale.US,
                    "0000%04x-0000-1000-8000-00805f9b34fb", uuid16);
            data.addServiceUuid(new ParcelUuid(UUID.fromString(uuidText)));
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(1300)
                .build();

        activeCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                runOnUiThread(() -> status.setText(String.format(Locale.US,
                        "%s sent — sequence %02X\nService bytes: %s",
                        name, seq, toHex(serviceBytes))));
            }

            @Override
            public void onStartFailure(int errorCode) {
                runOnUiThread(() -> status.setText(
                        "Advertising failed, error " + errorCode +
                                ". If error=1, another advertiser may still be active."));
            }
        };

        try {
            advertiser.startAdvertising(settings, data.build(), activeCallback);
            sequence = (sequence + 1) & 0xFF;
            saveSequence();
            updateSequenceView();
        } catch (SecurityException e) {
            status.setText("Bluetooth permission error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            status.setText("Advertisement rejected: " + e.getMessage());
        }
    }

    private static byte[] buildServiceBytes(int seq, byte[] body) {
        if (body.length != 17) throw new IllegalArgumentException("Command body must be 17 bytes");
        byte[] checksumInput = new byte[19];
        checksumInput[0] = (byte) seq;
        checksumInput[1] = 0x05;
        System.arraycopy(body, 0, checksumInput, 2, body.length);
        byte checksum = (byte) (crc8Poly07(checksumInput) ^ 0xB8);
        byte[] out = new byte[26];
        int p = 0;
        System.arraycopy(PREFIX, 0, out, p, PREFIX.length);
        p += PREFIX.length;
        out[p++] = (byte) seq;
        out[p++] = 0x05;
        System.arraycopy(body, 0, out, p, body.length);
        p += body.length;
        out[p] = checksum;
        return out;
    }

    private static int crc8Poly07(byte[] data) {
        int crc = 0;
        for (byte value : data) {
            crc ^= value & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x80) != 0) crc = ((crc << 1) ^ 0x07) & 0xFF;
                else crc = (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    private void refreshStatus() {
        updateSequenceView();
        if (status == null) return;
        if (adapter == null) status.setText("No Bluetooth adapter found.");
        else if (!adapter.isEnabled()) status.setText("Bluetooth is off.");
        else if (!hasAdvertisePermission()) status.setText("Waiting for Bluetooth advertising permission.");
        else status.setText("Ready. Start with OFF, then ON.");
    }

    private void updateSequenceView() {
        if (seqView != null) seqView.setText(String.format(Locale.US, "Next sequence: %02X", sequence & 0xFF));
    }

    private void saveSequence() {
        getPreferences(MODE_PRIVATE).edit().putInt("sequence", sequence & 0xFF).apply();
    }

    @Override
    protected void onDestroy() {
        if (advertiser != null && activeCallback != null && hasAdvertisePermission()) {
            try { advertiser.stopAdvertising(activeCallback); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static byte[] hex(String s) {
        s = s.replaceAll("\\s", "");
        if ((s.length() & 1) != 0) throw new IllegalArgumentException("Odd hex length");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int pos = i * 2;
            out[i] = (byte) Integer.parseInt(s.substring(pos, pos + 2), 16);
        }
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02X", b & 0xFF));
        return sb.toString();
    }
}
