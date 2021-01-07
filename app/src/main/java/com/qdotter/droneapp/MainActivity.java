package com.qdotter.droneapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
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
import dji.sdk.sdkmanager.DJISDKInitEvent;
import dji.sdk.sdkmanager.DJISDKManager;

import com.squareup.otto.Subscribe;

class CurrentConnectedDevice
{
    public String modelString = "";
    public String name = "";
    public TreeMap<BaseProduct.ComponentKey, String> components = new TreeMap<BaseProduct.ComponentKey, String>();  // a map of components connected in the system
    public long databaseDownloadCurrent = 0;  // the current download status of the database
    public long databaseDownloadTotal = 0;  // the total download amount of the database
    public String databaseVersionString = "";
    public String sdkRegistrationString = "";
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
    private ProgressBar m_progressBar;
    private Button m_buttonOpenDroneTracking;
    private Button m_buttonOpenPhoneTracking;
    private BaseProduct m_product;
    private DJIKey m_firmwareKey;
    private TextView m_textViewStatus;
    private Handler m_handler;
    private KeyListener m_firmwareVersionUpdater;
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {

        @Override
        public void onConnectivityChange(boolean isConnected) {
            Log.d(TAG, "onComponentConnectivityChanged: " + isConnected);
            notifyStatusChange();
            updateUI();
        }
    };

    // connected device details
    CurrentConnectedDevice m_currentDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        m_handler = new Handler();
        m_currentDevice = new CurrentConnectedDevice();
        m_progressBar = (ProgressBar) findViewById(R.id.progressBar);
        m_textViewStatus = (TextView) findViewById(R.id.textViewStatus);
        m_buttonOpenPhoneTracking = (Button) findViewById(R.id.buttonPhoneCameraTracking);
        m_buttonOpenDroneTracking = (Button) findViewById(R.id.buttonOpenDroneCameraTracking);

        if (m_buttonOpenDroneTracking != null)
        {
            m_buttonOpenDroneTracking.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //DroneApplication.getEventBus().post(componentList);
                    Intent intent = new Intent(MainActivity.this, FPVView.class);
                    startActivity(intent);
                }
            });
        }

        if (m_buttonOpenPhoneTracking != null)
        {
            m_buttonOpenPhoneTracking.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MainActivity.this,  CameraTrackingActivity.class);
                    startActivity(intent);
                }
            });
        }

        checkAndRequestPermissions();
        DroneApplication.getEventBus().register(this);
    }

    public static class ConnectivityChangeEvent {
    }

    private void notifyStatusChange() {
        DroneApplication.getEventBus().post(new ConnectivityChangeEvent());
    }

    private void getDBVersion(){
        m_handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                DJISDKManager.getInstance().getFlyZoneManager().getPreciseDatabaseVersion(new CommonCallbacks.CompletionCallbackWith<String>() {
                    @Override
                    public void onSuccess(String s) {
                        m_currentDevice.databaseVersionString = s;
                        invokeUpdateUI();
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        m_currentDevice.databaseVersionString = djiError.getDescription();
                        invokeUpdateUI();
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
                    m_currentDevice.sdkRegistrationString = "Registering to SDK...";
                    invokeUpdateUI();
                    DJISDKManager.getInstance().registerApp(MainActivity.this.getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS)
                            {
                                m_currentDevice.sdkRegistrationString = DJISDKError.REGISTRATION_SUCCESS.getDescription();
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                ToastUtils.setResultToToast(MainActivity.this.getString(R.string.sdk_registration_success_message));
                                getDBVersion();
                            } else {
                                m_currentDevice.sdkRegistrationString = MainActivity.this.getString(R.string.sdk_registration_message) + djiError.getDescription();
                            }
                            invokeUpdateUI();
                        }
                        @Override
                        public void onProductDisconnect() {
                            Log.d(TAG, "onProductDisconnect");
                            m_currentDevice.name = "";
                            m_currentDevice.modelString = "";
                            invokeUpdateUI();
                        }
                        @Override
                        public void onProductConnect(BaseProduct baseProduct)
                        {
                            // this fires after we have connected to the product
                            if (baseProduct == null || baseProduct.getModel() == null)
                            {
                                return;
                            }

                            runOnUiThread(()->{
                                m_currentDevice.modelString = baseProduct.getModel().getDisplayName();
                                m_currentDevice.name = "Retrieving...";
                                updateUI();
                            });
                            baseProduct.getName(new CommonCallbacks.CompletionCallbackWith<String>() {
                                @Override
                                public void onSuccess(String s)
                                {
                                    m_currentDevice.name = s;
                                    invokeUpdateUI();
                                }

                                @Override
                                public void onFailure(DJIError djiError)
                                {
                                    m_currentDevice.name = "Failed to get name: " + djiError.toString();
                                    invokeUpdateUI();
                                }
                            });
                        }

                        @Override
                        public void onProductChanged(BaseProduct baseProduct) {
                            if (baseProduct.getModel() == null)
                            {
                                return;
                            }

                            runOnUiThread(()->{
                                m_currentDevice.modelString = baseProduct.getModel().getDisplayName();
                                m_currentDevice.name = "Retrieving...";
                                updateUI();
                            });
                            baseProduct.getName(new CommonCallbacks.CompletionCallbackWith<String>() {
                                @Override
                                public void onSuccess(String s)
                                {
                                    m_currentDevice.name = s;
                                    invokeUpdateUI();
                                }

                                @Override
                                public void onFailure(DJIError djiError)
                                {
                                    m_currentDevice.name = "Failed to get name: " + djiError.toString();
                                    invokeUpdateUI();
                                }
                            });
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

                            // put into the dictionary for viewing
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (newComponent != null)
                                    {
                                        runOnUiThread(() -> {
                                            String str = "Obtaining serial number...";
                                            m_currentDevice.components.put(componentKey, str);
                                            updateUI();

                                            newComponent.getSerialNumber(new CommonCallbacks.CompletionCallbackWith<String>() {
                                                @Override
                                                public void onSuccess(String s) {
                                                  runOnUiThread(()->{
                                                      m_currentDevice.components.put(componentKey, s);
                                                      updateUI();
                                                  });
                                                }

                                                @Override
                                                public void onFailure(DJIError djiError) {
                                                    runOnUiThread(()->{
                                                        m_currentDevice.components.put(componentKey, "Error obtaining serial number: " + djiError.getDescription());
                                                        updateUI();
                                                    });
                                                }
                                            });
                                        });
                                    }
                                    else
                                    {
                                        m_currentDevice.components.remove(componentKey);
                                    }
                                }
                            });

                            if (newComponent != null)
                            {
                                newComponent.setComponentListener(mDJIComponentListener);
                            }
                            Log.d(TAG,
                                    String.format("onComponentChange key:%s, oldComponent:%s, newComponent:%s",
                                            componentKey,
                                            oldComponent,
                                            newComponent));;
                        }

                        @Override
                        public void onInitProcess(DJISDKInitEvent djisdkInitEvent, int i) {
                            invokeUpdateUI();
                        }

                        @Override
                        public void onDatabaseDownloadProgress(long current, long total)
                        {
                            if (current == m_currentDevice.databaseDownloadCurrent)
                            {
                                return;
                            }
                            m_currentDevice.databaseDownloadCurrent = current;
                            m_currentDevice.databaseDownloadTotal = total;
                            invokeUpdateUI();
                        }
                    });
                }
            });
        }
    }

    private void invokeUpdateUI()
    {
        m_handler.post(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        });
    }

    private void hideProcess(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void showProgress(final int process){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                m_progressBar.setVisibility(View.VISIBLE);
                m_progressBar.setProgress(process);
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
        if (requestCode == REQUEST_PERMISSION_CODE)
        {
            for (int i = grantResults.length - 1; i >= 0; i--)
            {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty())
        {
            startSDKRegistration();
        }
        else
        {
            Toast.makeText(getApplicationContext(), "Missing permissions!!!", Toast.LENGTH_LONG).show();
        }
    }

    private void tryUpdateFirmwareVersionWithListener() {
        if (m_firmwareVersionUpdater == null)
        {
            m_firmwareVersionUpdater = new KeyListener() {
                @Override
                public void onValueChange(final Object o, final Object o1)
                {
                    invokeUpdateUI();
                }
            };
            m_firmwareKey = ProductKey.create(ProductKey.FIRMWARE_PACKAGE_VERSION);
            if (KeyManager.getInstance() != null)
            {
                KeyManager.getInstance().addListener(m_firmwareKey, new KeyListener()
                {
                    @Override
                    public void onValueChange(Object o, Object o1) {
                        return;
                    }
                });
            }
        }
    }

    private void updateUI()
    {
        m_product = DroneApplication.getProductInstance();
        Log.d(TAG, "mProduct: " + (m_product == null ? "null" : "unnull"));
        if (null != m_product ) {
            if (m_product.isConnected()) {
                tryUpdateFirmwareVersionWithListener();
                if (m_product instanceof Aircraft) {
                    m_buttonOpenDroneTracking.setEnabled(true);
                }
            }
            else if (m_product instanceof Aircraft)
            {
                Aircraft aircraft = (Aircraft) m_product;
                if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected())
                {
                    m_buttonOpenDroneTracking.setEnabled(false);
                }
            }
        }
        else
        {
            m_buttonOpenDroneTracking.setEnabled(false);
        }

        // downloading database?
        if (m_currentDevice.databaseDownloadTotal != 0)
        {
            int percent = (int) ((100 * m_currentDevice.databaseDownloadCurrent) / m_currentDevice.databaseDownloadTotal);
            showProgress(percent);
        }
        else
        {
            hideProcess();
        }

        // update status UI
        if (m_textViewStatus != null)
        {
            String statusText = "";
            statusText += getResources().getString(R.string.sdk_version,
                    DJISDKManager.getInstance().getSDKVersion()
                            +
                            " "
                            +
                            DJISDKManager.getInstance().getSdkBetaVersion()
                            + " Debug:"
                            + GlobalConfig.DEBUG + "\n");
            statusText += "SDK Registration: " + m_currentDevice.sdkRegistrationString + "\n";
            statusText += "Database version: " + m_currentDevice.databaseVersionString + "\n";
            statusText += "Device name: " + m_currentDevice.name + "\n";
            statusText += "Model name: " + m_currentDevice.modelString + "\n";
            if (m_currentDevice.databaseDownloadTotal != 0)
            {
                double percentage = (100.0 * m_currentDevice.databaseDownloadCurrent) / m_currentDevice.databaseDownloadTotal;
                statusText += "Database update : " + m_currentDevice.databaseDownloadCurrent + "/" + m_currentDevice.databaseDownloadTotal + "(" + percentage + ")\n";
            }

            statusText += "Components:\n";
            if (m_currentDevice.components.isEmpty())
            {
                statusText += "(None connected)";
            }
            else
            {
                for (Map.Entry<BaseProduct.ComponentKey, String> componentAndName : m_currentDevice.components.entrySet())
                {
                    statusText += componentAndName.getKey().toString() + ": " + componentAndName.getValue() + "\n";
                }
            }

            m_textViewStatus.setText(statusText);
        }


    }

    @Override
    protected void onDestroy()
    {
        DroneApplication.getEventBus().unregister(this);
        super.onDestroy();
    }

    @Subscribe
    public void answerAvailable(ConnectivityChangeEvent event) {
        invokeUpdateUI();
    }
}