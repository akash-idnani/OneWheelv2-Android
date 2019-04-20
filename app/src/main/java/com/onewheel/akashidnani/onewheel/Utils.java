package com.onewheel.akashidnani.onewheel;

import android.annotation.SuppressLint;
import android.util.Log;

import com.polidea.rxandroidble2.RxBleConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

import static com.onewheel.akashidnani.onewheel.MainActivity.ANGLES_CHARACTERISTIC_UUID;

public class Utils {
    public static byte boolToByte(boolean b) {
        return (byte) (b ? 1 : 0);
    }

    @SuppressLint("CheckResult")
    public static void sendData(Observable<RxBleConnection> connectionObservable,
                                UUID charUUID, byte[] data, ErrorHandler errorHandler) {
        if (connectionObservable == null) return;
        connectionObservable
                .flatMapSingle(rxBleConnection -> rxBleConnection.writeCharacteristic(charUUID, data))
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
