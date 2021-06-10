package com.example.umarosandroid;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.util.Preconditions;

import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.concurrent.CancellableLoop;
import org.ros.internal.message.MessageBuffers;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.topic.Publisher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import std_msgs.UInt16MultiArray;
import std_msgs.UInt8MultiArray;

public class AudioNode2 extends AbstractNodeMain {

    private String nodeName;
    private AudioManager audioManager;

    private Publisher<UInt16MultiArray> audioPubliser16;
    private Publisher<UInt8MultiArray> audioPubliser8;

    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    public AudioNode2(String nodeName, AudioManager audioManager) {
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
        bufferSize = AudioRecord.getMinBufferSize(8000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        audioPubliser16 = connectedNode.newPublisher(nodeName+"/audio16",UInt16MultiArray._TYPE);
        audioPubliser8 = connectedNode.newPublisher(nodeName+"/audio8",UInt8MultiArray._TYPE);

        UInt16MultiArray audioMsg16 = audioPubliser16.newMessage();
        UInt8MultiArray audioMsg8 = audioPubliser8.newMessage();




        connectedNode.executeCancellableLoop(new CancellableLoop() {

            private final ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
            @Override
            protected void setup() {
                startRecording();
            }

            @Override
            protected void loop() throws InterruptedException {
                publishAudioData();
            }

            private void publishAudioData(){
                byte data[] = new byte[bufferSize];

                int read = 0;
                read = recorder.read(data, 0, bufferSize);

                if(AudioRecord.ERROR_INVALID_OPERATION != read){
                    stream.buffer().writeBytes(data);

                    audioMsg8.setData(stream.buffer().copy());
                    stream.buffer().clear();

                    audioPubliser8.publish(audioMsg8);
                    System.out.println("Audio 8 sent.");
                }
            }

        });

    }

    private void startRecording(){
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);

        int i = recorder.getState();
        if(i==1)
            recorder.startRecording();

        isRecording = true;

    }



    private void stopRecording(){
        if(null != recorder){
            isRecording = false;

            int i = recorder.getState();
            if(i==1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

    }

    @Override
    public void onShutdown(Node node) {
        stopRecording();
    }

}
