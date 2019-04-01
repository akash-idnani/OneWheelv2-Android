package com.onewheel.akashidnani.onewheel;

import android.annotation.SuppressLint;
import android.util.Log;

import com.polidea.rxandroidble2.RxBleConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import static com.onewheel.akashidnani.onewheel.MainActivity.CHARACTERISTIC_UUID;

public class Utils {
    public static byte boolToByte(boolean b) {
        return (byte) (b ? 1 : 0);
    }

    @SuppressLint("CheckResult")
    public static void sendCommand(Observable<RxBleConnection> connectionObservable, byte command, ErrorHandler errorHandler) {
        if (connectionObservable == null) return;
        connectionObservable
                .flatMapSingle(rxBleConnection -> rxBleConnection.writeCharacteristic(CHARACTERISTIC_UUID, new byte[]{command, 10}))
                .take(1)
                .subscribe(
                        characteristicValue -> {
                            Log.i("write", Arrays.toString(characteristicValue));
                        },
                        errorHandler::onError
                );

    }

    @SuppressLint("CheckResult")
    public static void sendCommand(Observable<RxBleConnection> connectionObservable, byte command, byte value, ErrorHandler errorHandler) {
        if (connectionObservable == null) return;

        connectionObservable
                .flatMapSingle(rxBleConnection -> rxBleConnection.writeCharacteristic(CHARACTERISTIC_UUID, new byte[]{command, value, 10}))
                .take(1)
                .subscribe(
                        characteristicValue -> {
                            Log.i("write", Arrays.toString(characteristicValue));
                        },
                        errorHandler::onError
                );
    }

    public static void addActiveSubscription(Disposable sub) {
        subscriptions.add(sub);
    }

    public static void clearSubscriptions() {
        for (Disposable sub : subscriptions) {
            try {
                sub.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        subscriptions.clear();
    }

    private static List<Disposable> subscriptions = new ArrayList<>();

    public interface ErrorHandler {
        void onError(Throwable throwable);
    }

}
