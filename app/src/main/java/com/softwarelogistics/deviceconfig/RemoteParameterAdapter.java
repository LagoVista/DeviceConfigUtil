package com.softwarelogistics.deviceconfig;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

public class RemoteParameterAdapter extends ArrayAdapter<RemoteParameter> {
    BluetoothService mBtService;
    public RemoteParameterAdapter(Context context, BluetoothService btService) {
        super(context, 0);
        mBtService = btService;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        RemotePropertyView itemView = (RemotePropertyView)convertView;

        if (null == itemView)
            itemView = RemotePropertyView.inflate(parent);

        itemView.setItem(getItem(position), mBtService);

        return itemView;
    }

}
