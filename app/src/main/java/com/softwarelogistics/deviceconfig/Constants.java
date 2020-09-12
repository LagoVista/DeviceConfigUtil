package com.softwarelogistics.deviceconfig;

import java.util.UUID;

public interface Constants {

    int REQUEST_ENABLE_BT = 1;

    // message types sent from the BluetoothChatService Handler
    int MESSAGE_STATE_CHANGE = 1;
    int MESSAGE_READ = 2;
    int MESSAGE_WRITE = 3;
    int MESSAGE_SNACKBAR = 4;
    int MESSAGE_PROPERTY = 5;
    int FULL_MESSAGE_CONTENT = 6;

    int FW_BLOCK_RECEIVED = 7;

    // Constants that indicate the current connection state
    int STATE_NONE = 0;       // we're doing nothing
    int STATE_ERROR = 1;
    int STATE_CONNECTING = 2; // now initiating an outgoing connection
    int STATE_CONNECTED = 3;  // now connected to a remote device
    int STATE_DISCONNECTING = 4;
    int STATE_DISCONNECTED = 5;

    UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    String SNACKBAR = "toast";


}