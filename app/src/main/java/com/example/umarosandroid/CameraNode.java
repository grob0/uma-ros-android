package com.example.umarosandroid;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.ros.concurrent.CancellableLoop;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;

import java.util.concurrent.ExecutionException;

public class CameraNode extends AbstractNodeMain implements LifecycleOwner {
    private Context context;
    private LifecycleRegistry lifecycleRegistry;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    public CameraNode(Context context,ListenableFuture<ProcessCameraProvider> cameraProviderFuture,PreviewView previewView) {
        this.context = context;
        this.previewView = previewView;
        this.cameraProviderFuture = cameraProviderFuture;
    }

    public GraphName getDefaultNodeName()
    {
        return GraphName.of("android/CameraNode");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            @Override
            protected void setup() {
                setCameraProviderListener();
            }

            @Override
            protected void loop() throws InterruptedException {

            }
    });
    }
    private void setCameraProviderListener() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this.context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this.context));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview);
        //System.out.println("Hola");
        }


    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }
}
