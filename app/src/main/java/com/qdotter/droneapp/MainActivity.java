package com.qdotter.droneapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.product.Model;
import dji.common.realname.AppActivationState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.DJIKey;
import dji.keysdk.KeyManager;
import dji.keysdk.ProductKey;
import dji.keysdk.callback.KeyListener;
import dji.log.DJILog;
import dji.log.GlobalConfig;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.realname.AppActivationManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

import com.qdotter.droneapp.CameraTrackingActivity;
import com.squareup.otto.Subscribe;

class CurrentConnectedDevice
{
    public Model model;
    public String name;
}

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String[] REQUIRED_PERMISSION_LIST = new String[] {
            Manifest.permission.VIBRATE, // Gimbal rotation
            Manifest.permission.INTERNET, // API requests
            Manifest.permission.ACCESS_WIFI_STATE, // WIFI connected products
            Manifest.permission.ACCESS_COARSE_LOCATION, // Maps
            Manifest.permission.ACCESS_NETWORK_STATE, // WIFI connected products
            Manifest.permission.ACCESS_FINE_LOCATION, // Maps
            Manifest.permission.CHANGE_WIFI_STATE, // Changing between WIFI and USB connection
            Manifest.permission.WRITE_EXTERNAL_STORAGE, // Log files
            Manifest.permission.BLUETOOTH, // Bluetooth connected products
            Manifest.permission.BLUETOOTH_ADMIN, // Bluetooth connected products
            Manifest.permission.READ_EXTERNAL_STORAGE, // Log files
            Manifest.permission.READ_PHONE_STATE, // Device UUID accessed upon registration
            Manifest.permission.RECORD_AUDIO // Speaker accessory
    };
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private FrameLayout contentFrameLayout;
    private ObjectAnimator pushInAnimator;
    private ObjectAnimator pushOutAnimator;
    private ObjectAnimator popInAnimator;
    private LayoutTransition popOutTransition;
    private ProgressBar progressBar;
    private TextView titleTextView;
    private TextView m_textConnectionStatus;
    private TextView m_textModelAvailable;
    private TextView m_textProduct;
    private Button m_buttonOpen;
    private Button m_buttonOpenDrone;
    private EditText m_bridgeModeEditText;
    private Button m_buttonBluetooth;
    private BaseProduct m_product;
    private DJIKey firmwareKey;
    private KeyListener firmwareVersionUpdater;
    private SearchView searchView;
    private MenuItem searchViewItem;
    private MenuItem hintItem;
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private int lastProcess = -1;
    private Handler mHander = new Handler();
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {

        @Override
        public void onConnectivityChange(boolean isConnected) {
            Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
            notifyStatusChange();
            refreshSDKRelativeUI();
        }
    };

    // connected device details
    CurrentConnectedDevice m_currentDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        DroneApplication.getEventBus().register(this);
        setContentView(R.layout.activity_main);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        m_textConnectionStatus = (TextView) findViewById(R.id.text_connection_status);
        m_textModelAvailable = (TextView) findViewById(R.id.text_model_available);
        m_textProduct = (TextView) findViewById(R.id.text_product_info);
        m_buttonOpen = (Button) findViewById(R.id.btn_open);
        m_buttonOpenDrone = (Button) findViewById(R.id.buttonOpenDrone);
        m_bridgeModeEditText = (EditText) findViewById(R.id.edittext_bridge_ip);
        m_buttonBluetooth = (Button) findViewById(R.id.btn_bluetooth);
        ((TextView) findViewById(R.id.text_version)).setText(getResources().getString(R.string.sdk_version,
                DJISDKManager.getInstance().getSDKVersion()
                        +
                        " "
                        +
                        DJISDKManager.getInstance().getSdkBetaVersion()
                        + " Debug:"
                        + GlobalConfig.DEBUG));

        if (m_buttonOpen != null)
        {
            m_buttonOpen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //DroneApplication.getEventBus().post(componentList);
                    Intent intent = new Intent(MainActivity.this, CameraTrackingActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (m_buttonOpenDrone != null)
        {
            m_buttonOpenDrone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this, FPVView.class);
                    startActivity(intent);
                }
            });
        }
    }

    public static class ConnectivityChangeEvent {
    }

    private void notifyStatusChange() {
        DroneApplication.getEventBus().post(new ConnectivityChangeEvent());
    }

    private void showDBVersion(){
        mHander.postDelayed(new Runnable() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getFlyZoneManager().getPreciseDatabaseVersion(new CommonCallbacks.CompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        ToastUtils.setResultToToast("db load success ! version : " + s);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        ToastUtils.setResultToToast("db load success ! get version error : " + djiError.getDescription());

                    }
                });
            }
        },1000);
    }

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_doing_message));
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                if (!DJISDKManager.getInstance().startConnectionToProduct())
                                {
                                    Log.e(TAG, "startConnectionToProduct failed");
                                    return;
                                }
                                ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_success_message));
                                showDBVersion();
                            } else {
                                ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_message) + djiError.getDescription());
                            }
                            Log.v(TAG, djiError.getDescription());
                            hideProcess();
                        }
                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            notifyStatusChange();
                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct) {
                            // this fires after we have connected to the product
                            Log.d(TAG, String.format("onProductConnect newProduct:%s", baseProduct));
                            notifyStatusChange();
                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {
                            notifyStatusChange();
                        }

                        @Override
                        public void onComponentChange(BaseProduct.ComponentKey componentKey,
                                                      BaseComponent oldComponent,
                                                      BaseComponent newComponent)
                        {
                            // this is called whenever new "components" are registered on the system
                            // e.g. the first call might be to say that the REMOTE_CONTROL has been registered
                            // once the aircraft is connected to via the remote control, this method is called
                            // for all of the components of the craft: the camera, the gimbal etc.
                            if (newComponent != null)
                            {
                                newComponent.setComponentListener(mDJIComponentListener);
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));

                            notifyStatusChange();
                        }

                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {

                        }

                        @Override
                        public void onDatabaseDownloadProgress(long current, long total) {
                            int process = (int) (100 * current / total);
                            if (process == lastProcess) {
                                return;
                            }
                            lastProcess = process;
                            showProgress(process);
                            if (process % 25 == 0){
                                ToastUtils.setResultToToast("DB load process : " + process);
                            }else if (process == 0){
                                ToastUtils.setResultToToast("DB load begin");
                            }
                        }
                    });
                }
            });
        }
    }

    private void hideProcess(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void showProgress(final int process){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(process);
            }
        });
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty())
        {
            startSDKRegistration();
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            Toast.makeText(getApplicationContext(), "Missing permissions!!!", Toast.LENGTH_LONG).show();
        }
    }

    private void tryUpdateFirmwareVersionWithListener() {
//        if (!hasStartedFirmVersionListener) {
//            firmwareVersionUpdater = new KeyListener() {
//                @Override
//                public void onValueChange(final Object o, final Object o1) {
//                    mHandlerUI.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            updateVersion();
//                        }
//                    });
//                }
//            };
//            firmwareKey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
//            if (KeyManager.getInstance() != null) {
//                KeyManager.getInstance().addListener(firmwareKey, firmwareVersionUpdater );
//            }
//            hasStartedFirmVersionListener = true;
//        }
//        updateVersion();
    }

    private void refreshSDKRelativeUI() {
        m_product = DroneApplication.getProductInstance();
        Log.d(TAG, "mProduct: " + (m_product == null ? "null" : "unnull"));
        if (null != m_product ) {
            if (m_product.isConnected()) {
                m_buttonOpen.setEnabled(true);
                String str = m_product instanceof Aircraft ? "DJIAircraft" : "DJIHandHeld";
                m_textConnectionStatus.setText("Status: " + str + " connected");
                tryUpdateFirmwareVersionWithListener();
//                if (m_product instanceof Aircraft) {
//                    addAppActivationListenerIfNeeded();
//                }

                if (null != m_product.getModel()) {
                    m_textProduct.setText("" + m_product.getModel().getDisplayName());
                } else {
                    m_textProduct.setText(R.string.product_information);
                }
            } else if (m_product instanceof Aircraft){
                Aircraft aircraft = (Aircraft) m_product;
                if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                    m_textConnectionStatus.setText(R.string.connection_only_rc);
                    m_textProduct.setText(R.string.product_information);
                    m_buttonOpen.setEnabled(false);
                    m_textModelAvailable.setText("Firmware version:N/A");
                }
            }
        } else {
            m_buttonOpen.setEnabled(false);
            m_textProduct.setText(R.string.product_information);
            m_textConnectionStatus.setText(R.string.connection_loose);
            m_textModelAvailable.setText("Firmware version:N/A");
        }
    }

    @Override
    protected void onDestroy() {
        DroneApplication.getEventBus().unregister(this);
        super.onDestroy();
    }

    @Subscribe
    public void answerAvailable(ConnectivityChangeEvent event) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshSDKRelativeUI();
            }
        });
    }
}