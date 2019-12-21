package com.softwarelogistics.deviceconfig;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class BTItemView extends LinearLayout {
    private TextView mBTName;
    private TextView mBTAddress;

    public BTItemView(Context context) {
        super(context);
        setupChildren();
    }

    public BTItemView(Context context, AttributeSet attrs) {
        super(context);
        setupChildren();
    }

    public BTItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        setupChildren();
    }

    private void setupChildren() {
        mBTName = findViewById(R.id.bt_name);
        mBTAddress = findViewById(R.id.bt_address);
        setOrientation(BTItemView.VERTICAL);
    }

    public static BTItemView inflate(ViewGroup parent) {
        BTItemView itemView = (BTItemView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.blue_tooth_device_row, parent, false);
        itemView.setupChildren();

        return itemView;
    }

    public void setItem(BluetoothDevice device) {
        mBTName.setText(device.getName());
        mBTAddress.setText(device.getAddress());
    }
}

