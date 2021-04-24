package com.example.umarosandroid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.tasks.OnSuccessListener;

import org.ros.concurrent.CancellableLoop;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import sensor_msgs.NavSatFix;

public class GPSNode extends AbstractNodeMain {
    private final Context context;
    private FusedLocationProviderClient fusedLocationClient;

    private Publisher<NavSatFix> GPSPublisher;

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("android/GPSNode");
    }

    public GPSNode(Context context, FusedLocationProviderClient fusedLocationClient) {
        this.context = context;
        this.fusedLocationClient = fusedLocationClient;
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        GPSPublisher = connectedNode.newPublisher("android/GPS",NavSatFix._TYPE);
        NavSatFix GPSmsg = GPSPublisher.newMessage();

        connectedNode.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void setup() {

            }

            @Override
            protected void loop() throws InterruptedException {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                fusedLocationClient.getLastLocation().addOnSuccessListener((AppCompatActivity) context, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if(location!=null) {
                            GPSmsg.setAltitude(location.getAltitude());
                            GPSmsg.setLongitude(location.getLongitude());
                            GPSmsg.setLatitude(location.getLatitude());
                            GPSPublisher.publish(GPSmsg);
                        }
                    }
                });
            }
        });
    }

}
