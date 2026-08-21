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

    // Fresh capture after re-pairing. The capture ended at outer sequence 0x41.
    // v0.4 deliberately starts at the next sequence, 0x42, while reusing
    // encrypted 17-byte bodies captured earlier in the same pairing session.
    private static final byte[] PREFIX = hex("0B6E51000401");
    private static final byte[] BODY_A = hex("D772E30583C975D22FA01AD641374B42BD"); // captured at seq 38
    private static final byte[] BODY_B = hex("9CBB7F76AFD5E5021CBB91BA7801C6656B"); // captured at seq 3A
    private static final byte[] BODY_C = hex("C0CD0F34F45F6C1E37B3ED67E5A224CE83"); // captured at seq 3C
    private static final byte[] BODY_D = hex("32343A8846554727C0C44DAAE70C9C4905"); // captured at seq 3F

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
        sequence = getPreferences(MODE_PRIVATE).getInt("sequence_v04", 0x42) & 0xFF;
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
        title.setText("Bluetooth Bulb Test v0.4");
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("Fresh-sequence encrypted-body reuse test");
        sub.setTextSize(15f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(8);
        root.addView(sub, subLp);

        TextView info = new TextView(this);
        info.setText("Your re-pairing log ended at sequence 41.\nThis app starts at the next value: 42.");
        info.setGravity(Gravity.CENTER);
        info.setTextSize(15f);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(-1, -2);
        infoLp.topMargin = dp(20);
        root.addView(info, infoLp);

        seqView = new TextView(this);
        seqView.setTextSize(18f);
        seqView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams seqLp = new LinearLayout.LayoutParams(-1, -2);
        seqLp.topMargin = dp(18);
        root.addView(seqView, seqLp);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row1Lp = new LinearLayout.LayoutParams(-1, -2);
        row1Lp.topMargin = dp(22);
        root.addView(row1, row1Lp);

        Button a = makeButton("TEST A\n(captured 38)");
        a.setOnClickListener(v -> sendFresh("TEST A / body 38", BODY_A));
        row1.addView(a, new LinearLayout.LayoutParams(0, dp(82), 1f));
        View gap1 = new View(this);
        row1.addView(gap1, new LinearLayout.LayoutParams(dp(12), 1));
        Button b = makeButton("TEST B\n(captured 3A)");
        b.setOnClickListener(v -> sendFresh("TEST B / body 3A", BODY_B));
        row1.addView(b, new LinearLayout.LayoutParams(0, dp(82), 1f));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, -2);
        row2Lp.topMargin = dp(12);
        root.addView(row2, row2Lp);

        Button c = makeButton("TEST C\n(captured 3C)");
        c.setOnClickListener(v -> sendFresh("TEST C / body 3C", BODY_C));
        row2.addView(c, new LinearLayout.LayoutParams(0, dp(82), 1f));
        View gap2 = new View(this);
        row2.addView(gap2, new LinearLayout.LayoutParams(dp(12), 1));
        Button d = makeButton("TEST D\n(captured 3F)");
        d.setOnClickListener(v -> sendFresh("TEST D / body 3F", BODY_D));
        row2.addView(d, new LinearLayout.LayoutParams(0, dp(82), 1f));

        status = new TextView(this);
        status.setText("Ready. IMPORTANT: do not open Smart Life, power-cycle, or re-pair the bulb before this test. Start with TEST A and wait 4 seconds between buttons.");
        status.setGravity(Gravity.CENTER);
        status.setTextSize(14f);
        status.setTextIsSelectable(true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(24);
        root.addView(status, statusLp);

        TextView note = new TextView(this);
        note.setText("If TEST A at sequence 42 works, it proves the encrypted command body can be reused while only the outer sequence and CRC advance. Each press automatically uses the next sequence.");
        note.setGravity(Gravity.CENTER);
        note.setTextSize(13f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.topMargin = dp(24);
        root.addView(note, noteLp);

        setContentView(root);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16f);
        return button;
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

    private void sendFresh(String name, byte[] body) {
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
                "%s using seq %02X: starting 2 advertisers...\n%s",
                currentName, currentSeq, currentPacket));

        startOne(settings, data, 1);
        handler.postDelayed(() -> startOne(settings, data, 2), 20);

        sequence = (sequence + 1) & 0xFF;
        getPreferences(MODE_PRIVATE).edit().putInt("sequence_v04", sequence).apply();
        updateSequenceView();
    }

    private static byte[] buildServiceBytes(int seq, byte[] body) {
        if (body.length != 17) throw new IllegalArgumentException("Body must be 17 bytes");
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
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
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
                        "%s seq %02X: advertiser %d failed (error %d), started %d/2\n%s",
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

    private void updateSequenceView() {
        if (seqView != null)
            seqView.setText(String.format(Locale.US, "Next sequence: %02X", sequence & 0xFF));
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
