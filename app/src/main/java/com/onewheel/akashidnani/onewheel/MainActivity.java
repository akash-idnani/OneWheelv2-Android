package com.onewheel.akashidnani.onewheel;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.akexorcist.roundcornerprogressbar.RoundCornerProgressBar;
import com.polidea.rxandroidble2.NotificationSetupMode;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;
import com.polidea.rxandroidble2.utils.ConnectionSharingAdapter;

import java.util.Locale;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.plugins.RxJavaPlugins;


public class MainActivity extends AppCompatActivity
        implements View.OnClickListener {

    public static String DEVICE_MAC_ADDRESS = "24:0A:C4:1C:9E:8E";
    public static ParcelUuid SERVICE_UUID = new ParcelUuid(UUID.fromString("000000FF-0000-1000-8000-00805F9B34FB"));
    public static UUID ANGLES_CHARACTERISTIC_UUID = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB");
    public static UUID PID_CHARACTERISTIC_UUID = UUID.fromString("0000FF02-0000-1000-8000-00805F9B34FB");
    public static UUID INFO_CHARACTERISTIC_UUID = UUID.fromString("0000FF03-0000-1000-8000-00805F9B34FB");

    private AngleView angleView;
    private LiveGraph angleChart;

    private RoundCornerProgressBar motorOutBar;
    private TextView motorOutText;

    private NumberPicker propPicker;
    private NumberPicker integralPicker;
    private NumberPicker derivPicker;
    private NumberPicker[] pickers;

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
        angleView.setOnClickListener(this);

        angleChart = findViewById(R.id.angleChart);
        angleChart.setOnClickListener(this);

        motorOutBar = findViewById(R.id.motorOutBar);
        motorOutText = findViewById(R.id.motorOutText);

        propPicker = findViewById(R.id.propPicker);
        integralPicker = findViewById(R.id.integralPicker);
        derivPicker = findViewById(R.id.derivPicker);

        pickers = new NumberPicker[]{propPicker, integralPicker, derivPicker};

        for (NumberPicker np : pickers) {
            np.setMinValue(0);
            np.setMaxValue(65000);
            np.setWrapSelectorWheel(false);
            np.setEnabled(false);
            np.setOnValueChangedListener((picker, oldVal, newVal) -> sendPID());
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.angleView:
                angleView.setVisibility(View.GONE);
                angleChart.setVisibility(View.VISIBLE);
                break;

            case R.id.angleChart:
                angleChart.setVisibility(View.GONE);
                angleView.setVisibility(View.VISIBLE);
                break;
        }
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

    @SuppressLint("CheckResult")
    private void connect(RxBleDevice device) {

        connectionObservable = device.establishConnection(false).
                doOnError(throwable -> startScanning()).
                compose(new ConnectionSharingAdapter());

        Utils.addActiveSubscription(connectionObservable
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(ANGLES_CHARACTERISTIC_UUID, NotificationSetupMode.COMPAT))
                .doOnNext(notificationObservable -> {
                    setState(State.Connected);
                    scanSubscription.dispose();
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> setState(State.Discovering))
                .subscribe(
                        this::onReceiveAngles,
                        throwable -> errorHandler.onError(throwable)
                ));

        Utils.addActiveSubscription(connectionObservable
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(INFO_CHARACTERISTIC_UUID, NotificationSetupMode.COMPAT))
                .doOnNext(notificationObservable -> {
                    setState(State.Connected);
                    scanSubscription.dispose();
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate(() -> setState(State.Discovering))
                .subscribe(
                        this::onReceiveInfo,
                        throwable -> errorHandler.onError(throwable)
                ));

        Utils.addActiveSubscription(connectionObservable
                .flatMapSingle(rxBleConnection -> rxBleConnection.readCharacteristic(PID_CHARACTERISTIC_UUID))
                .subscribe(
                        pidValues -> runOnUiThread(() -> onReceiverPID(pidValues)),
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

    private void onReceiveAngles(byte[] angles) {

        float pitch = getAngleFromByteArray(angles, 0);
        float roll = getAngleFromByteArray(angles, 4);
        float yaw = getAngleFromByteArray(angles, 8);

        angleView.setAngles(-roll, -pitch);
        angleChart.addDataPoint(-roll);
    }

    private void onReceiveInfo(byte[] info) {
        motorOutBar.setProgress(info[0] & 0xFF);
        if (info[1] != 0) {
            motorOutBar.setProgressColor(getResources().getColor(R.color.motorRed));
            motorOutText.setText(String.format(Locale.US, "-%d", info[0] & 0xFF));
        } else {
            motorOutBar.setProgressColor(getResources().getColor(R.color.motorGreen));
            motorOutText.setText(String.format(Locale.US, "+%d", info[0] & 0xFF));
        }

    }

    private void onReceiverPID(byte[] gains) {
        for (NumberPicker np : pickers) np.setEnabled(true);

        propPicker.setValue((gains[0] << 8) | gains[1]);
        integralPicker.setValue((gains[2] << 8) | gains[3]);
        derivPicker.setValue((gains[4] << 8) | gains[5]);
    }

    private void setState(final State newState) {

        runOnUiThread(() -> {
            if (newState == State.Discovering) {
                if (state == State.Discovering) return;
                for (NumberPicker np : pickers) np.setEnabled(false);
                startScanning();

            } else if (newState == State.Connected) {

            }
            state = newState;
        });
    }

    public void sendPID() {
        Utils.sendData(connectionObservable, PID_CHARACTERISTIC_UUID,
                new byte[]{
                        (byte) (propPicker.getValue() >> 8),
                        (byte) (propPicker.getValue() & 0xFF),
                        (byte) (integralPicker.getValue() >> 8),
                        (byte) (integralPicker.getValue() & 0xFF),
                        (byte) (derivPicker.getValue() >> 8),
                        (byte) (derivPicker.getValue() & 0xFF),
                },
                errorHandler
        );
    }
}
