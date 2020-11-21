package com.qdotter.droneapp;

import android.app.Activity;
import android.media.MediaFormat;
import android.os.Bundle;

import java.nio.ByteBuffer;

import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

public class FPVView extends Activity implements DJICodecManager.YuvDataCallback {

    private static final String TAG = MainActivity.class.getName();
    protected VideoFeeder.VideoDataListener mReceivedVideoDataListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        initUi();
    }

    void initUi()
    {

    }

    @Override
    public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer byteBuffer, int dataSize, int width, int height) {

    }
}
