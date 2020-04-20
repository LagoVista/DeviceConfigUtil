package com.softwarelogistics.deviceconfig;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RemotePropertyView  extends LinearLayout {
    private TextView mPropertyName;
    private EditText mPropertyValue;
    private Button mRemotePropertyTx;
    private BluetoothService mBtService;
    RemoteParameter mRemoteParameter;

    public RemotePropertyView(Context context) {
        super(context);
        setupChildren();
    }

    public RemotePropertyView(Context context, AttributeSet attrs) {
        super(context);
        setupChildren();
    }

    public RemotePropertyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        setupChildren();
    }

    private void setupChildren() {
        mPropertyName = findViewById(R.id.remote_property_label);
        mPropertyValue = findViewById(R.id.remote_property_value);
        mRemotePropertyTx = findViewById(R.id.remote_property_send);
        if(mRemotePropertyTx != null) {
            mRemotePropertyTx.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(FullscreenActivity.TAG, mRemoteParameter.getKey());
                    byte[] setCommand = ("SET " + mRemoteParameter.getFieldType() + "-" + mRemoteParameter.getKey() + "=" + mPropertyValue.getText() + "\n").getBytes();
                    mBtService.write(setCommand);
                }
            });
        }

        setOrientation(BTItemView.VERTICAL);
    }

    public static RemotePropertyView inflate(ViewGroup parent) {
        RemotePropertyView itemView = (RemotePropertyView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.device_property_row, parent, false);
        itemView.setupChildren();

        return itemView;
    }

    public void setItem(RemoteParameter parameter, BluetoothService btService) {
        mRemoteParameter = parameter;
        mBtService = btService;
        mPropertyName.setText(parameter.getKey() + " (" + parameter.getFieldType() + ")");
        mPropertyValue.setText(parameter.getValue());
    }
}
