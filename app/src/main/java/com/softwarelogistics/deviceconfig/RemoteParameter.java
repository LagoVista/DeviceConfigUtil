package com.softwarelogistics.deviceconfig;

public class RemoteParameter {
    private int mIndex;
    private String mValue;

    public RemoteParameter(int index, String value){
        mIndex = index;
        mValue = value;
    }

    public int getIndex() {
        return mIndex;
    }

    public String getValue() {
        return mValue;
    }
}
