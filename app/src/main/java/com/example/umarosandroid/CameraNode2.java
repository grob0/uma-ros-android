package com.example.umarosandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Looper;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.util.Preconditions;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.concurrent.CancellableLoop;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.topic.Publisher;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import sensor_msgs.CompressedImage;
import sensor_msgs.Image;

/**
 * @author germanruizmudarra@gmail.com (Germán Ruiz-Mudarra)
 */

public class CameraNode2 extends AbstractNodeMain {
    private Context context;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private Publisher<CompressedImage> compressedImagePublisher;
    private Publisher<Image> rawImagePublisher;
    private CameraThread cameraThread;

    public CameraNode2(Context context, ListenableFuture<ProcessCameraProvider> cameraProviderFuture, PreviewView previewView) {
        this.context = context;
        this.cameraProviderFuture = cameraProviderFuture;
        this.previewView = previewView;
    }

    public GraphName getDefaultNodeName()
    {
        return GraphName.of("android/CameraNode");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        this.compressedImagePublisher = connectedNode.newPublisher("/android/camera/compressed", CompressedImage._TYPE);
        this.rawImagePublisher = connectedNode.newPublisher("/android/camera/image_raw", Image._TYPE);
        this.cameraThread = new CameraThread(context,cameraProviderFuture,connectedNode);
        this.cameraThread.start();
    }

    @Override
    public void onShutdown(Node node) {
        this.cameraThread.shutdown();
        try {
            this.cameraThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onShutdownComplete(Node node) {
    }

    public void onError(Node node, Throwable throwable) {
    }
    private class CameraThread extends Thread {
        private final ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
        private Looper threadLooper;
        private Context context;
        private ConnectedNode connectedNode;
        private ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());


        private CameraThread(Context context, ListenableFuture<ProcessCameraProvider> cameraProviderFuture, ConnectedNode connectedNode) {
            this.context = context;
            this.cameraProviderFuture = cameraProviderFuture;
            this.connectedNode = connectedNode;
        }

        public void run() {
            Looper.prepare();
            this.threadLooper = Looper.myLooper();

            // Listener
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreviewAndAnalysis(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }, ContextCompat.getMainExecutor(context));

            Looper.loop();
        }

        public void shutdown() {
            // Shutdown listener
            try {
                cameraProviderFuture.get().unbindAll();
            }catch (Exception e) {
                e.printStackTrace();
            }

            // Shutdown thread
            if(this.threadLooper != null)
            {
                this.threadLooper.quit();
            }
        }

        void bindPreviewAndAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
            Preview preview = new Preview.Builder()
                    .build();
            ImageAnalysis imageAnalysis =
                    new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(640, 480))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();


            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), new ImageAnalysis.Analyzer() {
                // analyze is called everytime a frame is captured so this is our main loop
                @Override
                public void analyze(@NonNull ImageProxy imageProxy) {
                    byte[] imageByteArray = imageProxyToByteArray(imageProxy);
                    updateCompressedImage(imageByteArray);
                    // updateImage(imageByteArray);
                    imageProxy.close();
                }
            });
            Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, imageAnalysis,preview);
        }

        private byte[] imageProxyToByteArray(ImageProxy image)
        {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[2].getBuffer();


            int ySize = yBuffer.remaining();
            int vSize = vBuffer.remaining();
            int uSize = uBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            // U a V channels are swapped
            yBuffer.get(nv21,0,ySize);
            uBuffer.get(nv21,ySize,uSize);
            vBuffer.get(nv21,ySize+uSize,vSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 10, out);

            return out.toByteArray();
        }

        @SuppressLint("RestrictedApi")
        public void updateCompressedImage(byte[] imageByteArray){
            Preconditions.checkNotNull(imageByteArray);
            Time currentTime = connectedNode.getCurrentTime();
            String frameId = "camera";

            CompressedImage compressedImage = compressedImagePublisher.newMessage();
            compressedImage.setFormat("jpeg");
            compressedImage.getHeader().setStamp(currentTime);
            compressedImage.getHeader().setFrameId(frameId);

            stream.buffer().writeBytes(imageByteArray);
            compressedImage.setData(stream.buffer().copy());
            stream.buffer().clear();

            compressedImagePublisher.publish(compressedImage);
        }

        // WIP
        @SuppressLint("RestrictedApi")
        public void updateImage(byte[] imageByteArray){
            Preconditions.checkNotNull(imageByteArray);
            Time currentTime = connectedNode.getCurrentTime();
            String frameId = "camera";

            Image rawImage = rawImagePublisher.newMessage();
            rawImage.getHeader().setStamp(currentTime);
            rawImage.getHeader().setFrameId(frameId);
            rawImage.setEncoding("bgr8");

            rawImage.setWidth(640);
            rawImage.setHeight(480);
            rawImage.setStep(640*3);
            rawImage.setIsBigendian((byte) 0);

            stream.buffer().writeBytes(imageByteArray);
            rawImage.setData(stream.buffer().copy());
            stream.buffer().clear();

            rawImagePublisher.publish(rawImage);
        }
    }
}