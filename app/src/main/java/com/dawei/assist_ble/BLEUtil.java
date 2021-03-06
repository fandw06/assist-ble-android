package com.dawei.assist_ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.dawei.assist_ble.parser.Parser;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Created by Dawei on 2/8/2017.
 */
public class BLEUtil {

    private DisplayActivity hostActivity;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private Handler mHandler;
    private BluetoothGattService gService;
    private BluetoothGattCharacteristic controlChar;
    private BluetoothGattCharacteristic sensorChar;
    private boolean mScanning = false;
    private static final long SCAN_PERIOD = 10000;
    public enum DEMO {SOLAR, STRING, TEG}
    public Parser parser;

    private final static String UUID_ASSIST_SERVICE = "edfec62e-9910-0bac-5241-d8bda6932a2f";
    private final static String UUID_CONTROL_CHAR = "00000000-0000-0000-0000-000000000001";
    private final static String UUID_SENSOR_CHAR = "00000000-0000-0000-0000-000000000002";
    private final static String DESC_CLIENT_CHAR = "00002902-0000-1000-8000-00805f9b34fb";

    private final static String NAME_DEMO_SOLAR = "ASSIST Solar";
    private final static String NAME_DEMO_STRING = "ASSIST String";
    private final static String NAME_DEMO_TEG = "ASSIST TEG";
    private String DEVICE_NAME;


    private static final int REQUEST_BLE = 0x07;
    private static final String TAG = "BLE_UTIL";

    private Queue<BluetoothGattDescriptor> descQueue = new LinkedList<>();

    public BLEUtil(DisplayActivity hostActivity) {
        this.hostActivity = hostActivity;
        final BluetoothManager bluetoothManager = (BluetoothManager) hostActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            hostActivity.startActivityForResult(enableBtIntent, REQUEST_BLE);
        }
        else
            Log.d(TAG, "BLE is available!");
        mHandler = new Handler();
    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice btDevice = result.getDevice();
            Log.d(TAG, btDevice.getName() + "\n" + btDevice.getAddress());
            if (btDevice.getName() != null && btDevice.getName().equals(DEVICE_NAME)) {
                hostActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hostActivity.tInfo.setText("Discovered ASSIST device, connecting...");
                    }
                });
                scanLeDevice(false);
                connectToDevice(btDevice);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult sr : results) {
                Log.d("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            hostActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hostActivity.tInfo.setText("Failed to discover ASSIST device.");
                    hostActivity.bScan.setEnabled(true);
                }
            });
            Log.d("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "In onConnectionStateChange status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d(TAG, "The state is changed to connected");
                    hostActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hostActivity.tInfo.setText("Connected to ASSIST device.");
                            hostActivity.bScan.setEnabled(false);
                        }
                    });
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d(TAG, "The state is changed to disconnected.");
                    hostActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hostActivity.tInfo.setText("ASSIST device is disconnected.");
                            hostActivity.bScan.setEnabled(true);
                        }
                    });
                    break;
                default:
                    Log.d(TAG, "Unknown state change.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            // List all available services in current device.
            Log.d(TAG, "Services discovered: " + services.toString());
            int numServices = services.size();
            Log.d(TAG, "There are " + numServices + " services in this device");
            int i = 0;
            for (BluetoothGattService s : services) {
                Log.d(TAG, "Service NO. " + (i++) +" ID: " + s.getUuid());
                if (s.getUuid().equals(UUID_ASSIST_SERVICE))
                    gService = s;

                Log.d(TAG, "    There are " + s.getCharacteristics().size() + " chars in this service");
                int j = 0;
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    Log.d(TAG, "        Char NO." + (j++) +" ID: " + c.getUuid());
                    for (BluetoothGattDescriptor d : c.getDescriptors()) {
                        Log.d(TAG, "            Descriptor ID: " + d.getUuid());
                    }
                    if (c.getUuid().toString().equals(UUID_CONTROL_CHAR)) {
                        controlChar = c;
                        Log.d(TAG, "Found a control char.");
                    }
                    else if (c.getUuid().toString().equals(UUID_SENSOR_CHAR)) {
                        sensorChar = c;
                        Log.d(TAG, "Found sensor char.");

                        // Listen for notifications.
                        mGatt.setCharacteristicNotification(c, true);
                        BluetoothGattDescriptor descriptor = c.getDescriptor(
                                UUID.fromString(DESC_CLIENT_CHAR));
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        descQueue.add(descriptor);
                        if (descQueue.size() == 1)
                            mGatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        }

        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           int status) {
        }

        @Override
        public void onDescriptorWrite (BluetoothGatt gatt,
                                       BluetoothGattDescriptor descriptor,
                                       int status) {
            Log.d(TAG, "Descriptor is written successfully!");
            Log.d(TAG, "Char: " + descriptor.getCharacteristic().getUuid().toString());
            descQueue.remove();
            if (!descQueue.isEmpty()) {
                mGatt.writeDescriptor(descQueue.peek());
            }
        }

        @Override
        public void onCharacteristicChanged (BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic) {

            byte[] value = characteristic.getValue();
            byte[] ecgData = parser.getEcgBytes(value);
            byte[] accelData = parser.getAccelBytes(value);
            byte[] volData = parser.getVolBytes(value);

            if (characteristic.getUuid().toString().equals(UUID_SENSOR_CHAR)) {
                Log.d(TAG, "Accel chars changed! " + Arrays.toString(accelData));
                Log.d(TAG, "ECG chars changed! " + Arrays.toString(ecgData));
                Log.d(TAG, "Vol chars changed! " + Arrays.toString(volData));
                if (hostActivity.isStreaming) {
                    hostActivity.accelPlot.updateDataSeries(accelData);
                    hostActivity.ecgPlot.updateDataSeries(ecgData);
                    hostActivity.volPlot.updateDataSeries(volData);
                }
                if (hostActivity.enabledInfluxDB && hostActivity.influxDB != null) {
                    Log.d(TAG, "Prepare to write to InfluxDB...");
                    hostActivity.uploadCloud(accelData, ecgData, volData);
                }
                if (hostActivity.enabledLocal) {
                    hostActivity.saveToFile(accelData, ecgData, volData);
                }
            }
        }
    };

    public void scanLeDevice(final boolean enable) {
        Log.d(TAG, "Accessing ble scanner...");
        final BluetoothLeScanner mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        Log.d(TAG, "Start scanning...");
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothLeScanner.startScan(mScanCallback);
        } else {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mScanCallback);
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(hostActivity, false, gattCallback);
        }
    }

    public void startStreaming() {
        if (controlChar != null) {
            byte value[] = new byte[1];
            value[0] = 1;
            controlChar.setValue(value);
            if (mGatt.writeCharacteristic(controlChar))
                Log.d(TAG, "Start reading data.");
            else
                Log.d(TAG, "Write failed!");
        }
    }

    public void stopStreaming() {
        if (controlChar != null) {
            byte value[] = new byte[1];
            value[0] = 0;
            controlChar.setValue(value);
            if (mGatt.writeCharacteristic(controlChar))
                Log.d(TAG, "Stop reading data.");
            else
                Log.d(TAG, "Write failed!");
        }
    }

    public void setDemoType(DEMO d) {
        switch (d) {
            case SOLAR:
                this.DEVICE_NAME= NAME_DEMO_SOLAR;
                break;
            case STRING:
                this.DEVICE_NAME= NAME_DEMO_STRING;
                break;
            case TEG:
                this.DEVICE_NAME= NAME_DEMO_TEG;
                break;
            default:
                this.DEVICE_NAME= "UNDEFINED";
        }
    }

}
