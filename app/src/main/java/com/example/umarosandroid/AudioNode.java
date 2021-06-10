package com.example.umarosandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.util.Preconditions;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.opencv.android.Utils;
import org.ros.concurrent.CancellableLoop;
import org.ros.internal.message.MessageBuffers;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.topic.Publisher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import std_msgs.UInt16MultiArray;
import std_msgs.UInt8;
import std_msgs.UInt8MultiArray;

public class AudioNode extends AbstractNodeMain {

    private String nodeName;
    private AudioManager audioManager;

    private Publisher<UInt16MultiArray> audioPubliser16;
    private Publisher<UInt8MultiArray> audioPubliser8;


    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    int minBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
    AudioRecord recorder;
    private boolean isRecording = false;

    public AudioNode(String nodeName, AudioManager audioManager) {
        this.nodeName = nodeName;
        this.audioManager = audioManager;
    }

    @Override
    public GraphName getDefaultNodeName()
    {
        return GraphName.of(nodeName+"/AudioNode");
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        audioPubliser16 = connectedNode.newPublisher(nodeName+"/audio16",UInt16MultiArray._TYPE);
        audioPubliser8 = connectedNode.newPublisher(nodeName+"/audio8",UInt8MultiArray._TYPE);

        UInt16MultiArray audioMsg16 = audioPubliser16.newMessage();
        UInt8MultiArray audioMsg8 = audioPubliser8.newMessage();




        connectedNode.executeCancellableLoop(new CancellableLoop() {
            short[] buffer = new short[minBufferSize];
            byte[] byteBuffer = new byte[minBufferSize];

            private final ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
            @Override
            protected void setup() {
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                        RECORDER_AUDIO_ENCODING, minBufferSize*2);

                recorder.startRecording();
                isRecording = true;
            }

            @Override
            protected void loop() throws InterruptedException {
                minBufferSize = recorder.read(buffer, 0, buffer.length);

                updateAudio8(buffer);
                updateAudio16(buffer);
                Thread.sleep((1/RECORDER_SAMPLERATE)*1000);
            }


            @SuppressLint("RestrictedApi")
            public void updateAudio8(short[] buffer) {
                Preconditions.checkNotNull(buffer);


                byteBuffer = ShortToByte(buffer);
                stream.buffer().writeBytes(byteBuffer);

                audioMsg8.setData(stream.buffer().copy());
                stream.buffer().clear();

                audioPubliser8.publish(audioMsg8);
                System.out.println("Audio 8 sent.");

            }

            @SuppressLint("RestrictedApi")
            public void updateAudio16(short[] buffer) {
                Preconditions.checkNotNull(buffer);

                audioMsg16.setData(buffer);

                audioPubliser16.publish(audioMsg16);
                System.out.println("Audio 16 sent.");

            }

            byte [] ShortToByte(short [] input) {
                int short_index, byte_index;
                int iterations = input.length;

                byte [] buffer = new byte[input.length * 2];

                short_index = byte_index = 0;

                for(/*NOP*/; short_index != iterations; /*NOP*/)
                {
                    buffer[byte_index]     = (byte) (input[short_index] & 0x00FF);
                    buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);

                    ++short_index; byte_index += 2;
                }

                return buffer;
            }
        });

    }

    @Override
    public void onShutdown(Node node) {
        stopRecording();
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }
}
