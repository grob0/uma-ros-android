package com.example.umarosandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
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
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import sensor_msgs.CameraInfo;
import sensor_msgs.CompressedImage;
import sensor_msgs.Image;

import org.opencv.core.Mat;
import static org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_NV21;
import static org.opencv.imgproc.Imgproc.cvtColor;

/**
 * @author germanruizmudarra@gmail.com (Germán Ruiz-Mudarra)
 */

public class CameraNode extends AbstractNodeMain {
    private final Context context;
    private final ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private final int HEIGHT = 1080;
    private final int WIDTH = 1920;
    private final PreviewView previewView;

    private Time currentTime;
    private final String frameId = "camera";
    private Publisher<CameraInfo> cameraInfoPublisher;
    private Publisher<Image> rawImagePublisher;
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
        cameraInfoPublisher = connectedNode.newPublisher("/android/camera/camera_info", CameraInfo._TYPE);
        rawImagePublisher = connectedNode.newPublisher("/android/camera/image_raw", Image._TYPE);
        compressedImagePublisher = connectedNode.newPublisher("/android/camera/compressed", CompressedImage._TYPE);
        CameraInfo cameraInfo = cameraInfoPublisher.newMessage();
        Image rawImage = rawImagePublisher.newMessage();
        CompressedImage compressedImage = compressedImagePublisher.newMessage();


        connectedNode.executeCancellableLoop(new CancellableLoop() {
            private final ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
            private final Mat yuvMat = new Mat();
            private final Mat rgbMat = new Mat();
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
                                .setTargetResolution(new Size(WIDTH, HEIGHT))
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
                        YuvImage yuvImage = imageProxyToYuvImage(imageProxy);
                        updateCameraInfo(yuvImage);
                        updateCompressedImage(yuvImage);
                        imageProxy.close();
                    }
                });
                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, imageAnalysis,preview);
            }
            private YuvImage imageProxyToYuvImage(ImageProxy image)
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

                return new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            }

            public void updateCameraInfo(YuvImage yuvImage) {
                currentTime = connectedNode.getCurrentTime();
                cameraInfo.getHeader().setStamp(currentTime);
                cameraInfo.getHeader().setFrameId(frameId);

                cameraInfo.setWidth(yuvImage.getWidth());
                cameraInfo.setHeight(yuvImage.getHeight());

                cameraInfoPublisher.publish(cameraInfo);
            }

            @SuppressLint("RestrictedApi")
            public void updateCompressedImage(YuvImage yuvImage) {
                Preconditions.checkNotNull(yuvImage.getYuvData());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 25, out);
                compressedImage.setFormat("jpeg");

                stream.buffer().writeBytes(out.toByteArray());
                compressedImage.setData(stream.buffer().copy());
                stream.buffer().clear();

                currentTime = connectedNode.getCurrentTime();
                compressedImage.getHeader().setStamp(currentTime);
                compressedImage.getHeader().setFrameId(frameId);

                compressedImagePublisher.publish(compressedImage);
            }


            @SuppressLint("RestrictedApi")
            public void updateRawImage(YuvImage yuvImage){
                Preconditions.checkNotNull(yuvImage.getYuvData());

                yuvMat.put(0,0,yuvImage.getYuvData());
                cvtColor(yuvMat,rgbMat,COLOR_YUV2RGB_NV21);

                rawImage.setEncoding("rgb8");
                rawImage.setWidth(WIDTH);
                rawImage.setHeight(HEIGHT);
                rawImage.setStep(WIDTH*3);

                byte[] rgbData = new byte[rgbMat.height()*rgbMat.width()*3];
                rgbMat.get(0,0,rgbData);
                rgbMat.release();
                stream.buffer().writeBytes(rgbData);
                rawImage.setData(stream.buffer().copy());

                currentTime = connectedNode.getCurrentTime();
                rawImage.getHeader().setStamp(currentTime);
                rawImage.getHeader().setFrameId(frameId);

                rawImagePublisher.publish(rawImage);

                stream.buffer().clear();

                yuvMat.release();
            }
        });
    }
}