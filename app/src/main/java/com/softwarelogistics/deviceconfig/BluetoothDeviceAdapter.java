package com.softwarelogistics.deviceconfig;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class BluetoothDeviceAdapter extends ArrayAdapter<BluetoothDevice> {
    public BluetoothDeviceAdapter(Context context) {
        super(context, 0);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        BTItemView itemView = (BTItemView)convertView;
        if (null == itemView)
            itemView = BTItemView.inflate(parent);

        itemView.setItem(getItem(position));

        return itemView;
    }
}