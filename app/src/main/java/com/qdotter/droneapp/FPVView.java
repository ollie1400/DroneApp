package com.qdotter.droneapp;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import com.qdotter.droneapp.ToastUtils;
import java.nio.ByteBuffer;

import dji.common.product.Model;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

public class FPVView extends Activity implements TextureView.SurfaceTextureListener, DJICodecManager.YuvDataCallback
{

    private static final String TAG = MainActivity.class.getName();
    protected VideoFeeder.VideoDataListener m_receivedVideoDataListener = null;
    private TextureView m_videoSurface;
    private DJICodecManager m_codecManager;

    private void initUI() {
        // init mVideoSurface
        m_videoSurface = (TextureView)findViewById(R.id.video_previewer_surface);
        if (m_videoSurface != null) {
            m_videoSurface.setSurfaceTextureListener(this);
        }
    }

    @Override
    protected void onDestroy()
    {
        if (m_codecManager != null) {
            m_codecManager.cleanSurface();
            m_codecManager.destroyCodec();
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fpv);
        initUI();

        // The callback for receiving the raw H264 video data for camera live view
        m_receivedVideoDataListener = new VideoFeeder.VideoDataListener() {

            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (m_codecManager != null) {
                    m_codecManager.sendDataToDecoder(videoBuffer, size);
                }
            }
        };
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        onProductChange();

        if(m_videoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    protected void onProductChange() {
        initPreviewer();
    }

    private void initPreviewer()
    {
        BaseProduct product = DroneApplication.getProductInstance();

        if (product == null || !product.isConnected())
        {
            ToastUtils.showToast("Disconnected");
        }
        else
        {
            if (m_videoSurface != null)
            {
                m_videoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UNKNOWN_AIRCRAFT))
            {
                VideoFeeder.getInstance().getPrimaryVideoFeed().addVideoDataListener(m_receivedVideoDataListener);
            }
            else
            {
                ToastUtils.showToast("UNKNOWN_AIRCRAFT");
            }
        }
    }

    private void uninitPreviewer()
    {
        VideoFeeder.getInstance().getPrimaryVideoFeed().removeVideoDataListener(m_receivedVideoDataListener);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (m_codecManager == null)
        {
            m_codecManager = new DJICodecManager(this, surface, width, height);
            m_codecManager.setYuvDataCallback(this);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG,"onSurfaceTextureDestroyed");

        // onSurfaceTextureDestroyed is invoked when the SurfaceTexture is about to be destroyed by the system
        // so we need to clean up the codec manager before this happens
        if (m_codecManager != null)
        {
            m_codecManager.cleanSurface();
            m_codecManager = null;
        }
        // android SDK suggests we should call
        // surface.release(); here ?
        // (https://developer.android.com/reference/android/view/TextureView.SurfaceTextureListener#onSurfaceTextureDestroyed(android.graphics.SurfaceTexture))
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onYuvDataReceived(MediaFormat mediaFormat, ByteBuffer byteBuffer, int i, int i1, int i2)
    {
        Log.i(TAG, "Format: " + mediaFormat.toString());
        Log.i(TAG, "i: " + i);
    }
}
