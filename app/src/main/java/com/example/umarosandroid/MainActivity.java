package com.example.umarosandroid;

import android.Manifest;

import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.google.common.util.concurrent.ListenableFuture;

import org.ros.address.InetAddressFactory;
import org.ros.android.RosActivity;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

public class MainActivity extends RosActivity  {

    private static final int CAMERA_PERMISSION_CODE = 100;
    //private SensorManager sensorManager;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    public MainActivity() {
        super("Example","Example");
    }

    @Override
    protected void onPause()
    {
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        //sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        //Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        previewView = findViewById(R.id.preview_view);

        checkPermission(Manifest.permission.CAMERA,CAMERA_PERMISSION_CODE);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
    }

   // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission)
                == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[] { permission },
                    requestCode);
        }
        else {
            Toast.makeText(MainActivity.this,
                    "Permission already granted",
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    // This function is called when the user accepts or decline the permission.
    // Request Code is used to check which permission called this function.
    // This request code is provided when the user is prompt for permission.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super
                .onRequestPermissionsResult(requestCode,
                        permissions,
                        grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this,
                        "Camera Permission Granted",
                        Toast.LENGTH_SHORT)
                        .show();
            }
            else {
                Toast.makeText(MainActivity.this,
                        "Camera Permission Denied",
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }

    }

    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        // ROS Nodes
        //ImuNode imuNode = new ImuNode(sensorManager);
        CameraNode cameraNode = new CameraNode(this, cameraProviderFuture, previewView);

        //Network configuration with ROS master
        NodeConfiguration nodeConfiguration = NodeConfiguration.newPublic(
                InetAddressFactory.newNonLoopback().getHostAddress()
        );
        nodeConfiguration.setMasterUri(getMasterUri());

        // Run node
        //nodeMainExecutor.execute(imuNode, nodeConfiguration);
        nodeMainExecutor.execute(cameraNode, nodeConfiguration);
    }
}