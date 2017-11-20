package com.saxena.kshitij.miband2connect;

import android.*;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by kshitij.saxena on 20-11-2017.
 */

public class GattServerMainActivity extends AppCompatActivity {
    private ArrayAdapter<?> genericListAdapter;
    private ArrayList<BluetoothDevice> deviceArrayList;
    private ListView deviceListView;
    private BluetoothGattServer bluetoothGattServer;
    private BluetoothManager bluetoothManager;
    private BluetoothGattServerCallback bluetoothGattServerCallback;
    private BluetoothDevice miBand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialiseViews();
        getPermissions();
        enableBTAndDiscover();
    }

    private void initialiseViews() {
        if (bluetoothManager == null)
            bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);

        bluetoothGattServerCallback = new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                switch (newState) {
                    case BluetoothGattServer.STATE_CONNECTED:
                        Log.i("Device", "Connected");
                        addServices();
                        break;
                    case BluetoothGattServer.STATE_DISCONNECTED:
                        Log.e("Device", "Disconnected");
                        break;
                }
            }

            @Override
            public void onServiceAdded(int status, BluetoothGattService service) {
                for (BluetoothGattCharacteristic characteristic: service.getCharacteristics()) {
                    bluetoothGattServer.notifyCharacteristicChanged(miBand,characteristic,false);
                }
            }

            @Override
            public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
                //super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            }

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                Log.e("Characteristic", "Write request received");
            }

            @Override
            public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
                super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            }

            @Override
            public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            }

            @Override
            public void onNotificationSent(BluetoothDevice device, int status) {
                super.onNotificationSent(device, status);
            }
        };
        bluetoothGattServer = bluetoothManager.openGattServer(GattServerMainActivity.this, bluetoothGattServerCallback);
        deviceListView = (ListView) findViewById(R.id.deviceListView);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                miBand = (BluetoothDevice) parent.getItemAtPosition(position);
                connectDevice();
            }
        });
    }

    private void addServices() {
        bluetoothGattServer.addService(new BluetoothGattService(UUIDs.HEART_RATE_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY));
        bluetoothGattServer.addService(new BluetoothGattService(UUIDs.CUSTOM_BATTERY_SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY));
    }

    private void getPermissions() {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_DENIED) {
                shouldShowRequestPermissionRationale("Please grant this app the following permissions to make it work properly");
                requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_ADMIN, android.Manifest.permission.BLUETOOTH}, 1);
            }
            if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission_group.LOCATION) == PackageManager.PERMISSION_DENIED) {
                shouldShowRequestPermissionRationale("Please grand Location permission for scanning devices nearby");
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }

        } else {
            Toast.makeText(GattServerMainActivity.this, "This device doesn't support Bluetooth LE", Toast.LENGTH_LONG).show();
        }
    }

    private void connectDevice() {

        if (miBand.getBondState() == BluetoothDevice.BOND_NONE) {
            miBand.createBond();
            Log.e("Bond", "Created with Device");
        }
        bluetoothGattServer.connect(miBand, true);
    }

    private void enableBTAndDiscover() {
        final BluetoothAdapter bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();

        final ProgressDialog searchProgress = new ProgressDialog(GattServerMainActivity.this);
        searchProgress.setIndeterminate(true);
        searchProgress.setTitle("BlueTooth LE Device");
        searchProgress.setMessage("Searching...");
        searchProgress.setCancelable(false);
        searchProgress.show();

        deviceArrayList = new ArrayList<BluetoothDevice>();
        genericListAdapter = new ArrayAdapter<>(GattServerMainActivity.this, android.R.layout.simple_list_item_1, deviceArrayList);
        deviceListView.setAdapter(genericListAdapter);


        if (bluetoothAdapter == null) {
            Toast.makeText(GattServerMainActivity.this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
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
