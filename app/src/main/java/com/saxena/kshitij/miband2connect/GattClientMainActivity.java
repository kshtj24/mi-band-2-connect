package com.saxena.kshitij.miband2connect;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class GattClientMainActivity extends AppCompatActivity {


    private ArrayAdapter<?> genericListAdapter;
    private ArrayList<BluetoothDevice> deviceArrayList;
    private ListView deviceListView;
    private BluetoothGattCallback miBandGattCallBack;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService variableService;
    private final Object object = new Object();
    private SharedPreferences sharedPreferences;
    private Map<UUID, String> deviceInfoMap;
    private TextView heartRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deviceInfoMap = new HashMap<>();
        heartRate = (TextView) findViewById(R.id.heartRate);
        initialiseViewsAndComponents();
        getPermissions();
        enableBTAndDiscover();
    }

    @Override
    protected void onDestroy() {
        bluetoothGatt.disconnect();
        super.onDestroy();
    }


    /*------Methods to deal with the received data*/
    private void handleDeviceInfo(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        // Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
        deviceInfoMap.put(characteristic.getUuid(), value);
    }

    private void handleGenericAccess(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
    }

    private void handleGenericAttribute(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
    }

    private void handleAlertNotification(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
    }

    private void handleImmediateAlert(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
    }


    private void handleHeartRateData(final BluetoothGattCharacteristic characteristic) {

        Log.e("Heart",characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                heartRate.setText(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString());
            }
        });

    }


    /*------Methods to send requests to the device------*/
    private void authoriseMiBand() {
        BluetoothGattService service = bluetoothGatt.getService(UUIDs.CUSTOM_SERVICE_FEE1);

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUIDs.CUSTOM_SERVICE_AUTH_CHARACTERISTIC);
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
            if (descriptor.getUuid().equals(UUIDs.CUSTOM_SERVICE_AUTH_DESCRIPTOR)) {
                Log.d("INFO", "Found NOTIFICATION BluetoothGattDescriptor: " + descriptor.getUuid().toString());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }

        characteristic.setValue(new byte[]{0x01, 0x8, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45});
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private void executeAuthorisationSequence(BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        if (value[0] == 0x10 && value[1] == 0x01 && value[2] == 0x01) {
            characteristic.setValue(new byte[]{0x02, 0x8});
            bluetoothGatt.writeCharacteristic(characteristic);
        } else if (value[0] == 0x10 && value[1] == 0x02 && value[2] == 0x01) {
            try {
                byte[] tmpValue = Arrays.copyOfRange(value, 3, 19);
                Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

                SecretKeySpec key = new SecretKeySpec(new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45}, "AES");

                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] bytes = cipher.doFinal(tmpValue);


                byte[] rq = ArrayUtils.addAll(new byte[]{0x03, 0x8}, bytes);
                characteristic.setValue(rq);
                bluetoothGatt.writeCharacteristic(characteristic);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //getting the device details
    private void getDeviceInformation() {
        variableService = bluetoothGatt.getService(UUIDs.DEVICE_INFORMATION_SERVICE);

        try {
            for (BluetoothGattCharacteristic characteristic : variableService.getCharacteristics()) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                bluetoothGatt.readCharacteristic(characteristic);
                synchronized (object) {
                    object.wait(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getGenericAccessInfo() {
        variableService = bluetoothGatt.getService(UUIDs.GENERIC_ACCESS_SERVICE);
        try {
            for (BluetoothGattCharacteristic characteristic : variableService.getCharacteristics()) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                bluetoothGatt.readCharacteristic(characteristic);
                synchronized (object) {
                    object.wait(2000);
                }
                deviceInfoMap.put(characteristic.getUuid(), characteristic.getStringValue(0));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void genericAttribute() {
        variableService = bluetoothGatt.getService(UUIDs.GENERIC_ATTRIBUTE_SERVICE);
        try {
            for (BluetoothGattCharacteristic characteristic : variableService.getCharacteristics()) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                bluetoothGatt.readCharacteristic(characteristic);
                synchronized (object) {
                    object.wait(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void alertNotification() {
        variableService = bluetoothGatt.getService(UUIDs.ALERT_NOTIFICATION_SERVICE);
        try {
            for (BluetoothGattCharacteristic characteristic : variableService.getCharacteristics()) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                bluetoothGatt.readCharacteristic(characteristic);
                synchronized (object) {
                    object.wait(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void immediateAlert() {
        variableService = bluetoothGatt.getService(UUIDs.IMMEDIATE_ALERT_SERVICE);
        try {
            for (BluetoothGattCharacteristic characteristic : variableService.getCharacteristics()) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                bluetoothGatt.readCharacteristic(characteristic);
                synchronized (object) {
                    object.wait(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getHeartRate() {
        variableService = bluetoothGatt.getService(UUIDs.HEART_RATE_SERVICE);
        BluetoothGattCharacteristic heartRateCharacteristic = variableService.getCharacteristic(UUIDs.HEART_RATE_MEASUREMENT_CHARACTERISTIC);
        BluetoothGattDescriptor heartRateDescriptor = heartRateCharacteristic.getDescriptor(UUIDs.HEART_RATE_MEASURMENT_DESCRIPTOR);

        bluetoothGatt.setCharacteristicNotification(heartRateCharacteristic, true);
        heartRateDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(heartRateDescriptor);
    }


    /*--------Connection and other basic methods---------*/
    private void getPermissions() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_DENIED) {
                shouldShowRequestPermissionRationale("Please grant this app the following permissions to make it work properly");
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH}, 1);
            }
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission_group.LOCATION) == PackageManager.PERMISSION_DENIED) {
                shouldShowRequestPermissionRationale("Please grand Location permission for scanning devices nearby");
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }

        } else {
            Toast.makeText(GattClientMainActivity.this, "This device doesn't support Bluetooth LE", Toast.LENGTH_LONG).show();
        }
    }

    private void enableBTAndDiscover() {
        final BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();

        final ProgressDialog searchProgress = new ProgressDialog(GattClientMainActivity.this);
        searchProgress.setIndeterminate(true);
        searchProgress.setTitle("BlueTooth LE Device");
        searchProgress.setMessage("Searching...");
        searchProgress.setCancelable(false);
        searchProgress.show();

        deviceArrayList = new ArrayList<BluetoothDevice>();
        genericListAdapter = new ArrayAdapter<>(GattClientMainActivity.this, android.R.layout.simple_list_item_1, deviceArrayList);
        deviceListView.setAdapter(genericListAdapter);


        if (bluetoothAdapter == null) {
            Toast.makeText(GattClientMainActivity.this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);

        }
        final ScanCallback leDeviceScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d("TAG", "Device found" + " " + result.getDevice().getAddress() + " " + result.getDevice().getName());
                if (!deviceArrayList.contains(result.getDevice())) {
                    deviceArrayList.add(result.getDevice());
                    genericListAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
            }
        };


        bluetoothAdapter.getBluetoothLeScanner().startScan(leDeviceScanCallback);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter.getBluetoothLeScanner().stopScan(leDeviceScanCallback);
                searchProgress.dismiss();
            }
        }, 10000);
    }

    private void connectDevice(BluetoothDevice miBand) {

        if (miBand.getBondState() == BluetoothDevice.BOND_NONE) {
            miBand.createBond();
            Log.d("Bond", "Created with Device");
        }

        bluetoothGatt = miBand.connectGatt(getApplicationContext(), true, miBandGattCallBack);
    }

    private void initialiseViewsAndComponents() {
        deviceListView = (ListView) findViewById(R.id.deviceListView);
        Button getBandDetails = (Button) findViewById(R.id.getBandDetails);

        sharedPreferences = getSharedPreferences("MiBandConnectPreferences", Context.MODE_PRIVATE);
        miBandGattCallBack = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothGatt.STATE_DISCONNECTED:
                        Log.d("Info", "Device disconnected");

                        break;
                    case BluetoothGatt.STATE_CONNECTED: {
                        Log.d("Info", "Connected with device");
                        Log.d("Info", "Discovering services");
                        gatt.discoverServices();
                    }
                    break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {

                if (!sharedPreferences.getBoolean("isAuthenticated", false)) {
                    authoriseMiBand();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("isAuthenticated", true);
                    editor.apply();
                } else
                    Log.i("Device", "Already authenticated");
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

                switch (characteristic.getService().getUuid().toString()) {
                    case UUIDs.DEVICE_INFORMATION_SERVICE_STRING:
                        handleDeviceInfo(characteristic);
                        break;
                    case UUIDs.GENERIC_ACCESS_SERVICE_STRING:
                        handleGenericAccess(characteristic);
                        break;
                    case UUIDs.GENERIC_ATTRIBUTE_SERVICE_STRING:
                        handleGenericAttribute(characteristic);
                        break;
                    case UUIDs.ALERT_NOTIFICATION_SERVICE_STRING:
                        handleAlertNotification(characteristic);
                        break;
                    case UUIDs.IMMEDIATE_ALERT_SERVICE_STRING:
                        handleImmediateAlert(characteristic);
                        break;
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

                switch (characteristic.getUuid().toString()) {
                    case UUIDs.CUSTOM_SERVICE_AUTH_CHARACTERISTIC_STRING:
                        executeAuthorisationSequence(characteristic);
                        break;
                    case UUIDs.HEART_RATE_MEASUREMENT_CHARACTERISTIC_STRING:
                        handleHeartRateData(characteristic);
                        break;
                }
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.d("Descriptor", descriptor.getUuid().toString() + " Read");
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.d("Descriptor", descriptor.getUuid().toString() + " Written");
            }
        };

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                connectDevice((BluetoothDevice) parent.getItemAtPosition(position));
            }
        });

        getBandDetails.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView serialNo, hardwareRev, softwareRev, deviceName;
                Button cancel;

                getDeviceInformation();
                getGenericAccessInfo();
                //temporary calls
                getHeartRate();

                final Dialog deviceInfoDialog = new Dialog(GattClientMainActivity.this);
                deviceInfoDialog.setContentView(R.layout.device_info);
                deviceInfoDialog.setCancelable(false);
                deviceInfoDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        deviceInfoMap.clear();
                    }
                });

                deviceName = (TextView) deviceInfoDialog.findViewById(R.id.deviceName);
                serialNo = (TextView) deviceInfoDialog.findViewById(R.id.serialNumber);
                hardwareRev = (TextView) deviceInfoDialog.findViewById(R.id.hardwareRevision);
                softwareRev = (TextView) deviceInfoDialog.findViewById(R.id.softwareRevision);
                cancel = (Button) deviceInfoDialog.findViewById(R.id.cancel_action);

                deviceName.setText(deviceInfoMap.get(UUIDs.DEVICE_NAME_CHARACTERISTIC));
                serialNo.setText(deviceInfoMap.get(UUIDs.SERIAL_NUMBER));
                hardwareRev.setText(deviceInfoMap.get(UUIDs.HARDWARE_REVISION_STRING));
                softwareRev.setText(deviceInfoMap.get(UUIDs.SOFTWARE_REVISION_STRING));

                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        deviceInfoDialog.dismiss();
                    }
                });

                deviceInfoDialog.show();
            }
        });
    }
}
