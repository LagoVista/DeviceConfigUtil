package com.softwarelogistics.deviceconfig;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Created by da Ent on 1-11-2015.
 */
public class BluetoothService {

    private Handler myHandler;
    private Handler dfuHandler;
    private int state;

    private int mLastBlockReceived = -1;

    BluetoothDevice myDevice;

    SendBufferThread sendBufferThread = null;
    ConnectThread connectThread = null;
    ConnectedThread connectedThread = null;

    Semaphore pendingDFUResponse = new Semaphore(0);

    public BluetoothService(Handler handler, BluetoothDevice device) {
        state = Constants.STATE_NONE;
        myHandler = handler;
        myDevice = device;
        dfuHandler = new Handler();
    }

    public synchronized void connect() {
        Log.d(FullscreenActivity.TAG, "Connecting to: " + myDevice.getName() + " - " + myDevice.getAddress());
        // Start the thread to connect with the given device

        setState(Constants.STATE_CONNECTING);
        connectThread = new ConnectThread(myDevice);
        connectThread.start();
    }

    public synchronized void disconnect() {
        setState(Constants.STATE_DISCONNECTING);
        cancelConnectThread();
        cancelConnectedThread();
    }

    public synchronized  void cancelReadThread() {
        cancelConnectedThread();
    }

    private synchronized void setState(int state) {
        Log.d(FullscreenActivity.TAG, "setState() " + this.state + " -> " + state);
        switch(state){
            case Constants.STATE_CONNECTING: Log.d(FullscreenActivity.TAG, "setState() STATE_CONNECTING" ); break;
            case Constants.STATE_CONNECTED: Log.d(FullscreenActivity.TAG, "setState() Connected" ); break;
            case Constants.STATE_DISCONNECTING: Log.d(FullscreenActivity.TAG, "setState() STATE_DISCONNECTING" ); break;
            case Constants.STATE_DISCONNECTED: Log.d(FullscreenActivity.TAG, "setState() STATE_DISCONNECTED" ); break;
            case Constants.STATE_ERROR: Log.d(FullscreenActivity.TAG, "setState() STATE_ERROR" ); break;
            case Constants.STATE_NONE: Log.d(FullscreenActivity.TAG, "setState() STATE_NOTE" ); break;
        }
        this.state = state;
        // Give the new state to the Handler so the UI Activity can update
        myHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return state;
    }


    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(FullscreenActivity.TAG, "connected to: " + device.getName());

        cancelConnectThread();
        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(Constants.STATE_CONNECTED);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        Log.e(FullscreenActivity.TAG, "Connection Failed");
        // Send a failure item_message back to the Activity
        Message msg = myHandler.obtainMessage(Constants.MESSAGE_SNACKBAR);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.SNACKBAR, "Unable to connect");
        msg.setData(bundle);
        myHandler.sendMessage(msg);
        if(state != Constants.STATE_DISCONNECTING &&
                state != Constants.STATE_DISCONNECTED &&
                state != Constants.STATE_NONE) {
            setState(Constants.STATE_ERROR);
        }

        cancelConnectThread();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        if(state != Constants.STATE_DISCONNECTING &&
            state != Constants.STATE_DISCONNECTED &&
            state != Constants.STATE_NONE) {
            Log.e(FullscreenActivity.TAG, "Connection Lost");
            // Send a failure item_message back to the Activity
            Message msg = myHandler.obtainMessage(Constants.MESSAGE_SNACKBAR);
            myHandler.sendMessage(msg);
            Bundle bundle = new Bundle();
            bundle.putString(Constants.SNACKBAR, "Cconnection was lost");
            msg.setData(bundle);

            setState(Constants.STATE_ERROR);
            setState(Constants.STATE_DISCONNECTED);
        }

        cancelConnectedThread();
    }

    private void cancelConnectThread() {
        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
    }

    private void cancelConnectedThread() {
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public void sendBuffer(byte[] buffer) {
        sendBufferThread = new SendBufferThread(connectedThread, buffer);
        sendBufferThread.start();
    }

    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (state != Constants.STATE_CONNECTED) {
                Log.e(FullscreenActivity.TAG, "Trying to send but not connected");
                return;
            }
            r = connectedThread;
        }

        Log.e(FullscreenActivity.TAG, "MsgSent");

        // Perform the write unsynchronized
        r.write(out);
    }

    private class SendBufferThread extends Thread {
        private final ConnectedThread mSocket;
        private final byte[] mBuffer;

        SendBufferThread(ConnectedThread socket, byte[] buffer) {
            mSocket = socket;
            mBuffer = buffer;
        }

        public void run() {
            mSocket.setFirmwareUpdateMode((true));

            try {
                mSocket.write("FIRMWARE\n".getBytes());

                int blockSize = 500;
                short blocks = (short) ((mBuffer.length / blockSize) + 1);

                ByteBuffer buffer = ByteBuffer.allocate(2);
                buffer.putShort(blocks);
                mSocket.write(buffer.array());
                pendingDFUResponse.acquire();

                for (int idx = 0; idx < blocks; ++idx) {
                    int start = idx * blockSize;
                    int len = mBuffer.length - start;

                    len = Math.min(blockSize, len);
                    byte[] sendBuffer = new byte[len + 3];
                    sendBuffer[0] = (byte)(len >> 8);
                    sendBuffer[1] = (byte)(len & 0xff);
                    // Send actual buffer
                    System.arraycopy(mBuffer, start, sendBuffer, 2, len);
                    // Send check sum
                    byte checkSum = 0;
                    for (int ch = 2; ch < len; ch++) {
                        checkSum += sendBuffer[ch];
                    }

                    sendBuffer[len + 2] = checkSum;

                    mSocket.write(sendBuffer);
                    Log.d(FullscreenActivity.TAG, String.format("Send %d %d %d %d %d %d (%d/%d) - %d [%d,%d,%d]", sendBuffer[0], sendBuffer[1], start, len, start + len, mBuffer.length, idx, blocks, checkSum, sendBuffer[2], sendBuffer[3], sendBuffer[4]));

                    pendingDFUResponse.acquire();
                }

                pendingDFUResponse.acquire();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            mSocket.setFirmwareUpdateMode((false));
        }

        public void cancel() {

        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID uuid = Constants.myUUID;
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(FullscreenActivity.TAG, "Create RFcomm socket failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.d(FullscreenActivity.TAG, "Unable to connect", connectException);
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(FullscreenActivity.TAG, "Unable to close() socket during connection failure", closeException);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Do work to manage the connection (in a separate thread)
            connected(mmSocket, mmDevice);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
                setState(Constants.STATE_DISCONNECTED);
            } catch (IOException e) {
                Log.e(FullscreenActivity.TAG, "Close() socket failed", e);
            }
        }
    }

    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private boolean m_fwUpdateModeEnabled;

        public void setFirmwareUpdateMode(boolean enabled) {
            if(enabled != m_fwUpdateModeEnabled) {
                m_fwUpdateModeEnabled = enabled;
                Log.d(FullscreenActivity.TAG, enabled ? "Switch to DFU mode" : "Switch from DFU mode");
            }
        }

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(FullscreenActivity.TAG, "Temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d(FullscreenActivity.TAG, "Begin connectedThread");
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            StringBuilder readMessage = new StringBuilder();

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);

                    String read = new String(buffer, 0, bytes);
                    readMessage.append(read);
                    Log.d(FullscreenActivity.TAG, read);

                    myHandler.obtainMessage(Constants.FULL_MESSAGE_CONTENT, bytes, -1, buffer).sendToTarget();

                    if (read.contains("\n")) {
                        if(m_fwUpdateModeEnabled) {
                            pendingDFUResponse.release();
                        }
                        else if (read.startsWith("OK")) {

                        } else if (read.startsWith("PROPERTIES")) {
                            String payload = read.substring(11, read.toString().length() - 3);
                            Log.d(FullscreenActivity.TAG, "PROPS AS FOUND: [" + payload + "]");
                            String[] parts = payload.trim().split(",");
                            for (String part : parts) {
                                String[] sections = part.split("=");
                                if (sections.length == 2) {
                                    String[] labelParts = sections[0].split("-");
                                    RemoteParameter remoteParameter = new RemoteParameter(labelParts[1].trim(), labelParts[0].trim(), sections[1].trim());
                                    myHandler.obtainMessage(Constants.MESSAGE_PROPERTY, bytes, -1, remoteParameter).sendToTarget();
                                }
                            }
                        } else {
                            String[] parts = readMessage.toString().trim().split(";");

                            for (String part : parts) {
                                String[] sectionParts = part.trim().split(",");
                                if (sectionParts.length == 2) {
                                    int index = Integer.parseInt(sectionParts[0]);
                                    String value = sectionParts[1];
                                    if (value.compareTo("?") != 0 && value.compareTo("ACK") != 0) {
                                        RemoteParameter remoteParameter = new RemoteParameter(index, value);
                                        myHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, remoteParameter).sendToTarget();
                                    }
                                }

                                Log.d(FullscreenActivity.TAG, part);
                            }
                        }
                        readMessage.setLength(0);
                    }
                } catch (IOException e) {

                    Log.e(FullscreenActivity.TAG, "Connection Lost", e);
                    connectionLost();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                myHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, bytes).sendToTarget();
            } catch (IOException e) {
                Log.e(FullscreenActivity.TAG, "Exception during write", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(FullscreenActivity.TAG, "close() of connect socket failed", e);}
        }
    }

}