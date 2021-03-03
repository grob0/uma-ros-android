package com.example.cameraxros;

import android.graphics.Bitmap;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

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

import sensor_msgs.CompressedImage;

public class CameraNode extends AbstractNodeMain {
    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("/android_camera");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        //final Publisher<CompressedImage> publisher =
        //        connectedNode.newPublisher("/android_camera/compressed", CompressedImage._TYPE);

        connectedNode.executeCancellableLoop(new CancellableLoop() {
            //private ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
            //private ByteArrayOutputStream baos = new ByteArrayOutputStream();
            private PreviewView previewView;
            private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
            @Override
            protected void setup() {
                setContentView(R.layout.activity_main);
                previewView = findViewById(R.id.previewView);
                cameraProviderFuture = ProcessCameraProvider.getInstance(this);

            }

            @Override
            protected void loop() throws InterruptedException {

            }

            /*
            public void updateCompressedImage() {
                Time currentTime = connectedNode.getCurrentTime();
                String frameId = "android_camera";

                CompressedImage compressedImage = publisher.newMessage();
                compressedImage.setFormat("jpeg");
                compressedImage.getHeader().setStamp(currentTime);
                compressedImage.getHeader().setFrameId(frameId);

                //bmp.compress(Bitmap.CompressFormat.JPEG, 10, baos);

                stream.buffer().writeBytes(baos.toByteArray());
                compressedImage.setData(stream.buffer().copy());
                baos.reset();
                stream.buffer().clear();

                publisher.publish(compressedImage);
            }*/
        });
    }
}
