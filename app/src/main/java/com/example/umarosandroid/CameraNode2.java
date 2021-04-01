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
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import sensor_msgs.CameraInfo;
import sensor_msgs.CompressedImage;
import sensor_msgs.Image;

/**
 * @author germanruizmudarra@gmail.com (Germán Ruiz-Mudarra)
 */

public class CameraNode2 extends AbstractNodeMain {
    private final Context context;
    private final ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final PreviewView previewView;

    private final int HEIGHT = 640;
    private final int WIDTH = 480;


    private Publisher<CompressedImage> compressedImagePublisher;
    private Publisher<CameraInfo> cameraInfoPublisher;
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
        this.cameraInfoPublisher = connectedNode.newPublisher("/android/camera/camera_info", CameraInfo._TYPE);
        this.rawImagePublisher = connectedNode.newPublisher("/android/camera/image_raw", Image._TYPE);
        this.compressedImagePublisher = connectedNode.newPublisher("/android/camera/compressed", CompressedImage._TYPE);

        this.cameraThread = new CameraThread(context,cameraProviderFuture,connectedNode,WIDTH,HEIGHT);
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
        private int width;
        private int height;


        private CameraThread(Context context, ListenableFuture<ProcessCameraProvider> cameraProviderFuture, ConnectedNode connectedNode, int width, int height) {
            this.context = context;
            this.cameraProviderFuture = cameraProviderFuture;
            this.connectedNode = connectedNode;
            this.width = width;
            this.height = height;
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
                            .setTargetResolution(new Size(width, height))
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
                    updateCameraInfo();
                    //updateImage(yuvImage);
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

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            /*System.out.println(yuvImage.getWidth());
            System.out.println(yuvImage.getHeight());
            System.out.println(Arrays.toString(yuvImage.getStrides()));
            System.out.println(yuvImage.getYuvFormat());
            */

            //ByteArrayOutputStream out = new ByteArrayOutputStream();
            //yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 10, out);

            return yuvImage;
        }

        public void updateCameraInfo() {
            Time currentTime = connectedNode.getCurrentTime();
            String frameId = "camera";

            CameraInfo cameraInfo = cameraInfoPublisher.newMessage();
            cameraInfo.getHeader().setStamp(currentTime);
            cameraInfo.getHeader().setFrameId(frameId);

            cameraInfo.setWidth(width);
            cameraInfo.setHeight(height);

            cameraInfoPublisher.publish(cameraInfo);
        }

        @SuppressLint("RestrictedApi")
        public void updateCompressedImage(YuvImage yuvImage){
            Preconditions.checkNotNull(yuvImage.getYuvData());

            Time currentTime = connectedNode.getCurrentTime();
            String frameId = "camera";

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 10, out);
            CompressedImage compressedImage = compressedImagePublisher.newMessage();
            compressedImage.setFormat("jpeg");
            compressedImage.getHeader().setStamp(currentTime);
            compressedImage.getHeader().setFrameId(frameId);

            stream.buffer().writeBytes(out.toByteArray());
            compressedImage.setData(stream.buffer().copy());
            stream.buffer().clear();

            compressedImagePublisher.publish(compressedImage);
        }

        // WIP
        @SuppressLint("RestrictedApi")
        public void updateImage(YuvImage yuvImage){
            Preconditions.checkNotNull(yuvImage.getYuvData());

            Time currentTime = connectedNode.getCurrentTime();
            String frameId = "camera";

            Image rawImage = rawImagePublisher.newMessage();
            rawImage.getHeader().setStamp(currentTime);
            rawImage.getHeader().setFrameId(frameId);
            rawImage.setEncoding("rgb8");

            rawImage.setWidth(yuvImage.getWidth());
            rawImage.setHeight(yuvImage.getHeight());
            rawImage.setStep(yuvImage.getStrides()[0]+yuvImage.getStrides()[1]);
            //rawImage.setIsBigendian((byte) 0);

            stream.buffer().writeBytes(yuvImage.getYuvData());
            rawImage.setData(stream.buffer().copy());
            stream.buffer().clear();

            rawImagePublisher.publish(rawImage);
        }
    }
}