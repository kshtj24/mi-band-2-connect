package com.saxena.kshitij.miband2connect;

import android.Manifest;
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class GattClientMainActivity extends AppCompatActivity {


    private ArrayAdapter<?> genericListAdapter;
    private ArrayList<BluetoothDevice> deviceArrayList;
    private ListView deviceListView;
    private BluetoothGattCallback miBandGattCallBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialiseViews();
        getPermissions();
        enableBTAndDiscover();
    }

    private void initialiseViews() {
        deviceListView = (ListView) findViewById(R.id.deviceListView);
        miBandGattCallBack = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothGatt.STATE_DISCONNECTED:
                        Log.i("Info", "Device disconnected");

                        break;
                    case BluetoothGatt.STATE_CONNECTED: {
                        Log.i("Info", "Connected with device");
                        Log.i("Info", "Discovering services");
                        gatt.discoverServices();
                    }
                    break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                enableNotifications(gatt);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                //Toast.makeText(GattClientMainActivity.this, "Heart Rate " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1), Toast.LENGTH_LONG).show();
                byte[] value = characteristic.getValue();
                if (value[0] == 0x10 && value[1] == 0x01 && value[2] == 0x01) {
                    characteristic.setValue(new byte[]{0x02, 0x8});
                    gatt.writeCharacteristic(characteristic);
                } else if (value[0] == 0x10 && value[1] == 0x02 && value[2] == 0x01) {
                    try {
                        byte[] tmpValue = Arrays.copyOfRange(value, 3, 19);
                        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

                        SecretKeySpec key = new SecretKeySpec(new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45}, "AES");

                        cipher.init(Cipher.ENCRYPT_MODE, key);
                        byte[] bytes = cipher.doFinal(tmpValue);


                        byte[] rq = ArrayUtils.addAll(new byte[]{0x03, 0x8}, bytes);
                        characteristic.setValue(rq);
                        gatt.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.e("Descriptor", descriptor.getUuid().toString() + " Read");
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.e("Descriptor", descriptor.getUuid().toString() + " Written");
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                super.onReliableWriteCompleted(gatt, status);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                super.onReadRemoteRssi(gatt, rssi, status);
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                super.onMtuChanged(gatt, mtu, status);
            }
        };

        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                connectDevice((BluetoothDevice) parent.getItemAtPosition(position));
            }
        });
    }

    private void enableNotifications(BluetoothGatt bluetoothGatt) {
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb"));

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("00000009-0000-3512-2118-0009af100700"));
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
            if (descriptor.getUuid().equals(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))) {
                Log.i("INFO", "Found NOTIFICATION BluetoothGattDescriptor: " + descriptor.getUuid().toString());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }

        characteristic.setValue(new byte[]{0x01, 0x8, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45});
        bluetoothGatt.writeCharacteristic(characteristic);
    }

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

    private void connectDevice(BluetoothDevice miBand) {

        if (miBand.getBondState() == BluetoothDevice.BOND_NONE) {
            miBand.createBond();
            Log.e("Bond", "Created with Device");
        }

        miBand.connectGatt(getApplicationContext(), true, miBandGattCallBack);
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
                Log.e("TAG", "Device found" + " " + result.getDevice().getAddress() + " " + result.getDevice().getName());
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


}
