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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {


    private ArrayAdapter<?> genericListAdapter;
    private ArrayList<BluetoothDevice> deviceArrayList;
    private ArrayList<BluetoothGattCharacteristic> gattCharacList;
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
        gattCharacList = new ArrayList<BluetoothGattCharacteristic>();
        genericListAdapter = new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_list_item_1,gattCharacList);

        miBandGattCallBack = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (status) {
                    case BluetoothGatt.GATT_FAILURE:
                        Log.i("Info", "Device disconnected");
                        break;
                    case BluetoothGatt.GATT_SUCCESS: {
                        Log.i("Info", "Connected with device");
                        Log.i("Info", "Discovering services");
                        gatt.discoverServices();
                    }
                    break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    gatt.
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
                super.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                super.onDescriptorWrite(gatt, descriptor, status);
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
            Toast.makeText(MainActivity.this, "This device doesn't support Bluetooth LE", Toast.LENGTH_LONG).show();
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

        final ProgressDialog searchProgress = new ProgressDialog(MainActivity.this);
        searchProgress.setIndeterminate(true);
        searchProgress.setTitle("BlueTooth LE Device");
        searchProgress.setMessage("Searching...");
        searchProgress.setCancelable(false);
        searchProgress.show();

        deviceArrayList = new ArrayList<BluetoothDevice>();
        genericListAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1,  deviceArrayList);
        deviceListView.setAdapter(genericListAdapter);


        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
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
