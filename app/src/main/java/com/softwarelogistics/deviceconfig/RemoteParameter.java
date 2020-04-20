package com.softwarelogistics.deviceconfig;

public class RemoteParameter {
    private int mIndex;
    private String mKey;
    private String mType;
    private String mValue;

    public RemoteParameter(int index, String value){
        mIndex = index;
        mValue = value;
    }

    public RemoteParameter(String key, String type, String value){
        mKey     = key;
        mType = type;
        mValue = value;
    }

    public int getIndex() {
        return mIndex;
    }
    public String getKey() {
        return mKey;
    }
    public String getFieldType() {
        return mType;
    }
    public String getValue() {
        return mValue;
    }
}
