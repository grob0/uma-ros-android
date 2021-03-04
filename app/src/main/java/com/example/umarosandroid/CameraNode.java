package com.example.umarosandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
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
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.concurrent.CancellableLoop;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import sensor_msgs.CompressedImage;

/**
 * @author germanruizmudarra@gmail.com (Germán Ruiz-Mudarra)
 */

public class CameraNode extends AbstractNodeMain {
    private Context context;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private Publisher<CompressedImage> compressedImagePublisher;

    public CameraNode(Context context, ListenableFuture<ProcessCameraProvider> cameraProviderFuture, PreviewView previewView) {
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
        compressedImagePublisher = connectedNode.newPublisher("/android/camera/compressed", CompressedImage._TYPE);

        connectedNode.executeCancellableLoop(new CancellableLoop() {
            private ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
            @Override
            protected void setup() {
                cameraProviderFuture.addListener(() -> {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        bindPreviewAndAnalysis(cameraProvider);
                    } catch (ExecutionException | InterruptedException e) {
                        // No errors need to be handled for this Future.
                        // This should never be reached.
                    }
                }, ContextCompat.getMainExecutor(context));
            }

            @Override
            protected void loop() throws InterruptedException {

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
                yBuffer.get(nv21,0,ySize);
                vBuffer.get(nv21,ySize,vSize);
                uBuffer.get(nv21,ySize+vSize,uSize);

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
    });
    }


}
