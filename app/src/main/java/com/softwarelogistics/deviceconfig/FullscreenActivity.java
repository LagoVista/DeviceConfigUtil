package com.softwarelogistics.deviceconfig;

import android.Manifest;
import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {

    public static String TAG = "devicediag.";

    BluetoothService mBluetoothService;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDeviceAdapter bluetoothDevicesAdapter;

    Button mSearchNow;
    ListView mDeviceList;
    LinearLayout mDeviceEditor;
    LinearLayout mDeviceSearchView;

    boolean mHasBluetoothPermissions;
    boolean mHasBluetoothAdminPermissions;

    private static final int REQUEST_PERMISSIONS = 1;
    private static String[] APP_PERMISSIONS = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_ADMIN
    };

    public void verifyAppPermissions() {
        // Check if we have write permission
        boolean hasAll = true;
        mHasBluetoothPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
        mHasBluetoothAdminPermissions = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        hasAll &= ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        hasAll &= ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasAll) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    APP_PERMISSIONS,
                    REQUEST_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSIONS: {
                for(int idx = 0; idx < permissions.length; ++idx) {
                    //YUCK THERE HAS TO BE A BETTER WAY!
                    if(permissions[idx].contentEquals(Manifest.permission.BLUETOOTH) &&
                            grantResults[idx] == PackageManager.PERMISSION_GRANTED) {
                        mHasBluetoothPermissions = true;
                    }

                    if(permissions[idx].contentEquals(Manifest.permission.BLUETOOTH_ADMIN) &&
                            grantResults[idx] == PackageManager.PERMISSION_GRANTED) {
                        mHasBluetoothAdminPermissions = true;
                    }
                }
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mDeviceSearchView = findViewById(R.id.device_search_view);
        mDeviceEditor = findViewById(R.id.device_editor);
        mDeviceList = findViewById(R.id.device_list);
        mSearchNow = findViewById(R.id.search_now);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.search_now).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSearching();
            }
        });

        findViewById(R.id.done_editing).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mDeviceSearchView.setVisibility(View.VISIBLE);
                mDeviceEditor.setVisibility(View.GONE);
            }
        });

        findViewById(R.id.send_now).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String msg = "0,hvacmon001\n";
                mBluetoothService.write(msg.getBytes());

                msg = "1,www.foo.com\n";
                mBluetoothService.write(msg.getBytes());

                msg = "2,false\n";
                mBluetoothService.write(msg.getBytes());

                msg = "3,kevinw\n";
                mBluetoothService.write(msg.getBytes());

                msg = "4,Test1234\n";
                mBluetoothService.write(msg.getBytes());
            }
        });

        bluetoothDevicesAdapter = new BluetoothDeviceAdapter(this);
        mDeviceList.setAdapter(bluetoothDevicesAdapter);


        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = bluetoothDevicesAdapter.getItem(position);

                mBluetoothMessageHandler = new BluetoothMessageHandler(FullscreenActivity.this);
                mBluetoothService = new BluetoothService(mBluetoothMessageHandler, device);
                mBluetoothService.connect();

                mDeviceSearchView.setVisibility(View.GONE);
                mDeviceEditor.setVisibility(View.VISIBLE);
            }
        });

        mDeviceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = bluetoothDevicesAdapter.getItem(position);

                mBluetoothMessageHandler = new BluetoothMessageHandler(FullscreenActivity.this);
                mBluetoothService = new BluetoothService(mBluetoothMessageHandler, device);
                mBluetoothService.connect();

                mDeviceSearchView.setVisibility(View.GONE);
                mDeviceEditor.setVisibility(View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    @Override protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        this.verifyAppPermissions();
    }

    @Override protected void onStop() {
        super.onStop();
        Log.d(FullscreenActivity.TAG, "Receiver unregistered");
        unregisterReceiver(mReceiver);
    }

    private void enableBluetooth(){
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                device.fetchUuidsWithSdp();

                if(device.getName() != null &&
                        device.getName().toLowerCase().startsWith("nuviot") &&
                        mBluetoothService == null) {

                    bluetoothDevicesAdapter.add(device);
                    bluetoothDevicesAdapter.notifyDataSetChanged();

                    Log.d(FullscreenActivity.TAG, String.format("Found device %s", device.getName()));
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(FullscreenActivity.TAG, "Finished searching");
                mSearchNow.setEnabled(true);
                Toast.makeText(FullscreenActivity.this, "Finished searching", Toast.LENGTH_SHORT).show();
            } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Toast.makeText(FullscreenActivity.this, "Bluetooth turned off", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
    };

    private void startSearching(){
        enableBluetooth();

        bluetoothDevicesAdapter.clear();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter.isEnabled()) {
            if(bluetoothAdapter == null) {
                Toast.makeText(this, "No bluetooth adapter found", Toast.LENGTH_SHORT).show();
            }
            else {
                if(!bluetoothAdapter.startDiscovery()) {
                    Toast.makeText(this, "Failed to start searching", Toast.LENGTH_SHORT).show();
                }
                else {
                    Toast.makeText(this, "Searching for NuvIoT devices.", Toast.LENGTH_SHORT).show();
                    mSearchNow.setEnabled(false);
                }
            }
        }
        else {
            enableBluetooth();
            Toast.makeText(this, "Enabling Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    BluetoothMessageHandler mBluetoothMessageHandler;

    private class BluetoothMessageHandler extends Handler {

        private final WeakReference<FullscreenActivity> mActivity;

        public BluetoothMessageHandler(FullscreenActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case Constants.STATE_CONNECTED:
                            Toast.makeText(FullscreenActivity.this, "Bluetooth connected", Toast.LENGTH_SHORT).show();
                            break;
                        case Constants.STATE_CONNECTING:
                            Toast.makeText(FullscreenActivity.this, "Bluetooth connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case Constants.STATE_NONE:
                            break;
                        case Constants.STATE_ERROR:
                            Toast.makeText(FullscreenActivity.this, "Error Connecting", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    /* Not currently sending anything to BT */
                    break;
                case Constants.MESSAGE_READ:

                    break;

                case Constants.MESSAGE_SNACKBAR:


                    break;
            }
        }
    }
}
