package com.softwarelogistics.deviceconfig;

import android.Manifest;
import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FullscreenActivity extends AppCompatActivity {
    public static String TAG = "devicediag.";

    BluetoothService mBluetoothService;
    BluetoothDeviceAdapter bluetoothDevicesAdapter;
    RemoteParameterAdapter propertiesAdapter;

    ArrayAdapter<String> mConsoleOutputAdapter;

    boolean mReady = false;

    Button mSearchNow;
    ListView mDeviceList;
    ListView mRemoteParameterValues;
    ListView mConsoleOutputList;
    LinearLayout mConsoleOutput;
    LinearLayout mDeviceEditor;
    LinearLayout mDeviceSearchView;
    LinearLayout mConnectingView;
    LinearLayout mCredentialsSection;
    EditText mDeviceId;
    EditText mHostName;
    EditText mUserName;
    EditText mPassword;
    EditText mWiFiSSID;
    EditText mWiFiPassword;

    TextView mFirmwareVersion;
    TextView mBluetoothAddress;
    TextView mDeviceType;
    TextView mFirmwareSKU;

    CheckBox mAnonymous;

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
    public boolean onPrepareOptionsMenu (Menu menu) {
        menu.findItem(R.id.action_open_profile).setEnabled(mReady);
        menu.findItem(R.id.action_show_console).setEnabled(mReady);
        menu.findItem(R.id.action_profile_save).setEnabled(mReady);
        menu.findItem(R.id.action_commission).setEnabled(mReady);
        menu.findItem(R.id.action_write_firmware).setEnabled(mReady);
        menu.findItem(R.id.action_restart).setEnabled(mReady);
        menu.findItem(R.id.action_read_properties).setEnabled(mReady);
        menu.findItem(R.id.action_write_profile_to_device).setEnabled(mReady);
        menu.findItem(R.id.action_done).setEnabled(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id)
        {
            case R.id.action_open_profile: selectProfile(); break;
            case R.id.action_read_properties: readProperties(); break;
            case R.id.action_write_firmware: writeFirmware(); break;
            case R.id.action_show_console: showConsole(); break;
            case R.id.action_profile_save: saveProfile(); break;
            case R.id.action_commission: mBluetoothService.write("COMMISSION\n".getBytes()); break;
            case R.id.action_restart: mBluetoothService.write("REBOOT\n".getBytes()); break;
            case R.id.action_write_profile_to_device: updateDevice(); break;
            case R.id.action_done: showSearchView(); break;
            default: super.onOptionsItemSelected(item);
        }

        return true;
    }

    void showConsole() {
        mBluetoothService.write("QUIT\n".getBytes());
        mDeviceEditor.setVisibility(View.GONE);
        mConsoleOutput.setVisibility(View.VISIBLE);
    }

    void openProfile(String name) {
        SharedPreferences sharedpreferences = getSharedPreferences("NUVIOT_CONFIG_PRFERENCES", Context.MODE_PRIVATE);

        Log.d(TAG, "Opening prefernences => " + name);

        Set<String> preferences = sharedpreferences.getStringSet(name, new HashSet<String>());
        for (String preference : preferences) {
            String[] parts = preference.split("=");
            if(parts.length == 2){
                if(parts[0].compareTo("ssid") == 0) mWiFiSSID.setText(parts[1]);
                if(parts[0].compareTo("wifipwd") == 0) mWiFiPassword.setText(parts[1]);
                if(parts[0].compareTo("host") == 0) mHostName.setText(parts[1]);
                if(parts[0].compareTo("anonymous") == 0){
                    mAnonymous.setChecked(parts[1].compareTo("true") == 0);
                }

                if(parts[0].compareTo("username") == 0) mUserName.setText(parts[1]);
                if(parts[0].compareTo("password") == 0) mPassword.setText(parts[1]);
            }

            Log.d(TAG, preference);
        }
    }

    void writeFirmware() {
        int len = 1024*1024 + 50 * 40 + 34;
        byte[] buffer = new byte[len];
        for(int idx = 0; idx < len; ++idx)
        {
            buffer[idx] = (byte)idx;
        }

        mBluetoothService.sendBuffer(buffer);
    }

    void readProperties() {
        mBluetoothService.write("PROPERTIES\n".getBytes());
    }

    void selectProfile(){
        SharedPreferences sharedpreferences = getSharedPreferences("NUVIOT_CONFIG_PRFERENCES", Context.MODE_PRIVATE);
        final Map<String, ?> allSettings = sharedpreferences.getAll();

        final Spinner preferenceSelectorSpinner = new Spinner(this);
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_spinner_item, allSettings.keySet().toArray());
        preferenceSelectorSpinner.setAdapter(arrayAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add a new task")
                .setMessage("What do you want to do next?")
                .setView(preferenceSelectorSpinner)
                .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String itemName = (String)preferenceSelectorSpinner.getSelectedItem();
                        openProfile(itemName);
                    }
                })
                .setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String itemName = (String)preferenceSelectorSpinner.getSelectedItem();
                        SharedPreferences sharedpreferences = getSharedPreferences("NUVIOT_CONFIG_PRFERENCES", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedpreferences.edit();
                        editor.remove(itemName);
                        editor.commit();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    void saveProfile() {
        final EditText taskEditText = new EditText(this);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add a new task")
                .setMessage("What do you want to do next?")
                .setView(taskEditText)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String settingName = String.valueOf(taskEditText.getText());

                        SharedPreferences sharedpreferences = getSharedPreferences("NUVIOT_CONFIG_PRFERENCES", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedpreferences.edit();
                        final Set<String> out = new LinkedHashSet<String>();
                        out.add("ssid=" + String.valueOf(mWiFiSSID.getText()));
                        out.add("wifipwd=" + String.valueOf(mWiFiPassword.getText() == null || mWiFiPassword.getText().length() == 0 ? "?" : mWiFiPassword.getText()));
                        out.add(String.valueOf("host=" + mHostName.getText()));

                        out.add(mAnonymous.isChecked() ? "anonymous=true" : "anonymous=false");
                        if(!mAnonymous.isChecked()){
                            out.add("username=" + String.valueOf(mUserName.getText()));
                            out.add("password=" + String.valueOf(mPassword.getText() == null || mPassword.getText().length() == 0 ? "?" : mPassword.getText()));
                        }

                        editor.putStringSet(settingName,out);
                        editor.commit();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    void showSearchView() {
        mBluetoothService.write("QUIT\n".getBytes());
        mDeviceSearchView.setVisibility(View.VISIBLE);
        mDeviceEditor.setVisibility(View.GONE);
        mConsoleOutput.setVisibility(View.GONE);
        mBluetoothService.disconnect();
        mBluetoothService = null;
        mReady = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mConsoleOutput = findViewById(R.id.console_output);
        mConsoleOutputList = findViewById(R.id.console_output_list);
        mWiFiPassword = findViewById(R.id.wifi_password);
        mWiFiSSID = findViewById(R.id.wifi_ssid);
        mDeviceId = findViewById(R.id.device_id);
        mHostName = findViewById(R.id.server_host_name);
        mUserName = findViewById(R.id.user_name);
        mPassword = findViewById(R.id.user_password);
        mFirmwareSKU = findViewById(R.id.firmware_sku);
        mFirmwareVersion = findViewById(R.id.firmware_veraion);
        mDeviceType = findViewById(R.id.device_type);
        mBluetoothAddress = findViewById(R.id.blue_tooth_address);
        mRemoteParameterValues = findViewById(R.id.remote_parameter_values);

        mDeviceSearchView = findViewById(R.id.device_search_view);
        mDeviceEditor = findViewById(R.id.device_editor);
        mDeviceList = findViewById(R.id.device_list);
        mSearchNow = findViewById(R.id.search_now);
        mConnectingView = findViewById(R.id.connecting_view);
        mCredentialsSection = findViewById(R.id.credentials_section);
        mAnonymous = findViewById(R.id.is_anonymous);
        mAnonymous.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mCredentialsSection.setVisibility( isChecked ? View.GONE : View.VISIBLE);
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.search_now).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSearching();
            }
        });

        List<String> listValues = new ArrayList<String>();

        mConsoleOutputAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1, listValues);
        mConsoleOutputList.setAdapter(mConsoleOutputAdapter);

        bluetoothDevicesAdapter = new BluetoothDeviceAdapter(this);
        mDeviceList.setAdapter(bluetoothDevicesAdapter);

        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectBlueToothItem(position);
            }
        });
    }

    void selectBlueToothItem(int idx) {
        BluetoothDevice device = bluetoothDevicesAdapter.getItem(idx);

        mBluetoothAddress.setText(device.getAddress());

        mBluetoothMessageHandler = new BluetoothMessageHandler(FullscreenActivity.this);
        mBluetoothService = new BluetoothService(mBluetoothMessageHandler, device);
        mBluetoothService.connect();

        propertiesAdapter = new RemoteParameterAdapter(this, mBluetoothService );
        mRemoteParameterValues.setAdapter(propertiesAdapter);

        mWiFiSSID.setText("");
        mWiFiPassword.setText("");
        mDeviceId.setText("");
        mDeviceType.setText("?");
        mDeviceId.setText("");
        mUserName.setText("");
        mPassword.setText("");
        mFirmwareVersion.setText("");
        mFirmwareSKU.setText("");
        mHostName.setText("");

        mDeviceSearchView.setVisibility(View.GONE);
        mDeviceEditor.setVisibility(View.VISIBLE);
    }

    void updateDevice() {
        String deviceId = String.valueOf(mDeviceId.getText());
        String hostName = String.valueOf(mHostName.getText());
        String userName = String.valueOf(mUserName.getText());
        String password = String.valueOf(mPassword.getText());
        String wifiSSID = String.valueOf(mWiFiSSID.getText());
        String wifiPassword = String.valueOf(mWiFiPassword.getText());

        if(deviceId.length() > 0) mBluetoothService.write(String.format("0,%s\n", deviceId).getBytes());
        if(hostName.length() > 0) mBluetoothService.write(String.format("1,%s\n", hostName).getBytes());
        mBluetoothService.write(String.format("2,%b\n", mAnonymous.isChecked()).getBytes());
        if(userName.length() > 0) mBluetoothService.write(String.format("3,%s\n", userName).getBytes());
        if(password.length() > 0) mBluetoothService.write(String.format("4,%s\n", password).getBytes());
        if(wifiSSID.length() > 0) mBluetoothService.write(String.format("6,%s\n", wifiSSID).getBytes());
        if(wifiPassword.length() > 0) mBluetoothService.write(String.format("7,%s\n", wifiPassword).getBytes());
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
        Log.d(TAG, "Enabling Bluetooth");

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

                Log.d(FullscreenActivity.TAG, String.format("Found device %s", device.getName()));

                if(device.getName() != null) {
                    Log.d(TAG, "Found device " + device.getName().toLowerCase());
                }
                else {
                    Log.d(TAG, "Found null name device ");
                }

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
        Log.d(TAG, "Start Searching");
        bluetoothDevicesAdapter.clear();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
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

    private void queryDevice() {
        mBluetoothService.write("HELLO\n".getBytes());
        mBluetoothService.write("QUERY\n".getBytes());
        mDeviceEditor.setVisibility(View.VISIBLE);
        propertiesAdapter.clear();
        mConnectingView.setVisibility(View.GONE);
    }

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
                            queryDevice();

                            break;
                        case Constants.STATE_CONNECTING:
                            Toast.makeText(FullscreenActivity.this, "Bluetooth connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case Constants.STATE_NONE:
                            Log.d(TAG, "No devices found.");
                            break;
                        case Constants.STATE_DISCONNECTED:
                            Toast.makeText(FullscreenActivity.this, "Bluetooth Disconnected", Toast.LENGTH_SHORT).show();
                            mDeviceEditor.setVisibility(View.GONE);
                            mDeviceSearchView.setVisibility(View.VISIBLE);
                            mBluetoothService = null;
                            startSearching();
                            break;
                        case Constants.STATE_ERROR:
                            Toast.makeText(FullscreenActivity.this, "Error Connecting", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case Constants.MESSAGE_PROPERTY:
                    RemoteParameter prop = (RemoteParameter)msg.obj;
                    Log.d(FullscreenActivity.TAG, prop.getKey() + " - " + prop.getValue());
                    propertiesAdapter.add(prop);
                    break;
                    case Constants.FULL_MESSAGE_CONTENT:
                        byte[] buffer = (byte[])msg.obj;
                        String read = new String(buffer, 0, msg.arg1);
                        mConsoleOutputAdapter.insert(read, 0);
                        break;
                case Constants.MESSAGE_WRITE:

                    /* Not currently sending anything to BT */
                    break;
                case Constants.MESSAGE_READ:

                    RemoteParameter prm = (RemoteParameter)msg.obj;
                    switch(prm.getIndex()){
                        case 0: mDeviceId.setText(prm.getValue());break;
                        case 1: mHostName.setText(prm.getValue());break;
                        case 2: mAnonymous.setChecked(prm.getValue().compareTo("true") == 0); break;
                        case 3: mUserName.setText(prm.getValue());break;
                        case 6: mWiFiSSID.setText(prm.getValue()); break;
                        case 100: mFirmwareVersion.setText(prm.getValue());break;
                        case 101: mFirmwareSKU.setText(prm.getValue());break;
                        case 102: mDeviceType.setText(prm.getValue()); break;
                    }

                    mReady = true;

                    Log.d(FullscreenActivity.TAG, String.format("%d=%s", prm.getIndex(), prm.getValue()));
                    break;

                case Constants.MESSAGE_SNACKBAR:


                    break;
            }
        }
    }
}
