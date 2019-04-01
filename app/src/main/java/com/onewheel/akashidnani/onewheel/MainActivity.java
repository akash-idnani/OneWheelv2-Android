package com.onewheel.akashidnani.onewheel;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.polidea.rxandroidble2.NotificationSetupMode;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.exceptions.BleException;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;
import com.polidea.rxandroidble2.utils.ConnectionSharingAdapter;


public class MainActivity extends AppCompatActivity {

    public static String DEVICE_MAC_ADDRESS = "24:0A:C4:1C:9E:8E";
    public static ParcelUuid SERVICE_UUID = new ParcelUuid(UUID.fromString("000000FF-0000-1000-8000-00805F9B34FB"));
    public static UUID CHARACTERISTIC_UUID = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB");

    private AngleView angleView;
    private LineChart angleChart;

    private int angleIndex = 1;
    private static int DATA_POINTS_PER_SCREEN = 500;

    private RxBleClient bleClient;
    private Observable<RxBleConnection> connectionObservable;

    private Disposable scanSubscription;

    private Utils.ErrorHandler errorHandler = throwable -> {
        throwable.printStackTrace();
        if (throwable instanceof BleDisconnectedException) {
            setState(State.Discovering);
        }
    };

    private enum State {
        Initial, Discovering, Connected
    }

    private State state = State.Initial;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        1);
            }
        }

        RxJavaPlugins.setErrorHandler(error -> {

        });

        bleClient = RxBleClient.create(this);
        setState(State.Discovering);
    }

    private void initUI() {
        angleView = findViewById(R.id.angleView);

        angleChart = findViewById(R.id.angleChart);
        angleChart.getAxisLeft().setAxisMinimum(-20);
        angleChart.getAxisLeft().setAxisMaximum(20);

        LineData lineData = new LineData(
                new LineDataSet(new ArrayList<>(), "Data")
        );
        angleChart.setData(lineData);
        angleChart.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state == State.Discovering) startScanning();
    }

    @Override
    protected void onPause() {
        unsubscribeScan();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Utils.clearSubscriptions();

        super.onDestroy();
    }

    private void unsubscribeScan() {
        if (scanSubscription != null && !scanSubscription.isDisposed()) scanSubscription.dispose();
    }

    private void startScanning() {
        unsubscribeScan();
        scanSubscription = bleClient.scanBleDevices(
                new ScanSettings.Builder()
                        .build(),
                new ScanFilter.Builder()
                        .setDeviceAddress(DEVICE_MAC_ADDRESS)
                        .build()
        ).take(1)
                .subscribe(
                        scanResult -> connect(scanResult.getBleDevice()),
                        throwable -> errorHandler.onError(throwable)
                );
    }

    private void connect(RxBleDevice device) {

        connectionObservable = device.establishConnection(false).
                doOnError(throwable -> startScanning()).
                compose(new ConnectionSharingAdapter());

        Utils.addActiveSubscription(connectionObservable
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(CHARACTERISTIC_UUID, NotificationSetupMode.COMPAT))
                .doOnNext(notificationObservable -> {
                    setState(State.Connected);
                    scanSubscription.dispose();
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> setState(State.Discovering))
                .subscribe(
                        this::onReceive,
                        throwable -> errorHandler.onError(throwable)
                ));
    }

    private float getAngleFromByteArray(byte[] arr, int startIdx) {
        int value = (arr[startIdx + 3] << (Byte.SIZE * 3));
        value |= (arr[startIdx + 2] & 0xFF) << (Byte.SIZE * 2);
        value |= (arr[startIdx + 1] & 0xFF) << (Byte.SIZE);
        value |= (arr[startIdx] & 0xFF);

        return value / 65536.f;
    }

    private void onReceive(byte[] angles) {

        float pitch = getAngleFromByteArray(angles, 0);
        float roll = getAngleFromByteArray(angles, 4);
        float yaw = getAngleFromByteArray(angles, 8);

        angleView.setAngles(-pitch, roll);

        if (angleIndex > DATA_POINTS_PER_SCREEN) {
            angleChart.getLineData().getDataSetByIndex(0).removeFirst();
        }

        angleChart.getLineData().addEntry(new Entry(angleIndex, pitch), 0);
        angleChart.getLineData().notifyDataChanged();
        angleChart.notifyDataSetChanged();
        angleChart.invalidate();
        angleIndex++;
    }

    private void setState(final State newState) {

        runOnUiThread(() -> {
            if (newState == State.Discovering) {
                if (state == State.Discovering) return;
                startScanning();

            } else if (newState == State.Connected) {

            }
            state = newState;
        });
    }

    public void sendCommand(byte command) {
        Utils.sendCommand(connectionObservable, command, errorHandler);
    }

    public void sendCommand(byte command, byte value) {
        Utils.sendCommand(connectionObservable, command, value, errorHandler);
    }
}
