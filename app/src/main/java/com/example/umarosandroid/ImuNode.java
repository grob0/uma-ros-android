package com.example.umarosandroid;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;

import static androidx.core.content.ContextCompat.getSystemService;

public class ImuNode extends AbstractNodeMain {
    private final SensorManager sensorManager;

    public ImuNode(SensorManager manager)
    {
        this.sensorManager = manager;
    }

    public GraphName getDefaultNodeName()
    {
        return GraphName.of("android/ImuNode");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {

    }
}
