package com.example.umarosandroid;

import android.Manifest;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;


import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.ros.address.InetAddressFactory;
import org.ros.android.AppCompatRosActivity;
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatRosActivity  implements LifecycleOwner {
    private static final String  TAG = "MainActivity";

    // Camera requests
    private static final String[] CAMERA_PERMISSION = new String[]{Manifest.permission.CAMERA};
    private static final int CAMERA_REQUEST_CODE = 10;

    // IMU and Camera instances and views
    private SensorManager sensorManager;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;

    // For custom LifecycleOwner
    private LifecycleRegistry lifecycleRegistry;

    public MainActivity() {
        super("Example","Example");
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        previewView = findViewById(R.id.previewView);

        if (!hasCameraPermission()) {
            requestPermission();
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    // Checks if camera permission is granted
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    // Requests camera permission
    private void requestPermission() {
        ActivityCompat.requestPermissions(
                this,
                CAMERA_PERMISSION,
                CAMERA_REQUEST_CODE
        );
    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        ImuNode imuNode = new ImuNode(sensorManager);
        CameraNode cameraNode = new CameraNode(this,cameraProviderFuture,previewView);

        //Network configuration with ROS master
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(
                InetAddressFactory.newNonLoopback().getHostAddress()
        );
        nodeConfiguration.setMasterUri(getMasterUri());

        // Run nodes
        nodeMainExecutor.execute(imuNode, nodeConfiguration);
        nodeMainExecutor.execute(cameraNode, nodeConfiguration);
    }
}