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
import org.ros.concurrent.CancellableLoop;
import org.ros.internal.message.MessageBuffers;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.topic.Publisher;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import std_msgs.UInt8;
import std_msgs.UInt8MultiArray;

public class AudioNode extends AbstractNodeMain {

    private Context context;
    private String nodeName;
    private AudioManager audioManager;

    private Publisher<UInt8MultiArray> audioPubliser;

    public AudioNode(Context context, String nodeName, AudioManager audioManager) {
        this.context = context;
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
        audioPubliser = connectedNode.newPublisher(nodeName+"/audio",UInt8MultiArray._TYPE);
        UInt8MultiArray audioMsg = audioPubliser.newMessage();
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            AudioRecord mRecord;
            short[] buffer;
            int bufferSize;
            int samplingRate;
            byte[] buffer_bytearray = {};


            private final ChannelBufferOutputStream stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
            @Override
            protected void setup() {
                int audioSource = MediaRecorder.AudioSource.MIC;
                samplingRate = 11025;
                int channelConfig = AudioFormat.CHANNEL_IN_DEFAULT;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

                bufferSize = AudioRecord.getMinBufferSize(samplingRate,channelConfig,audioFormat);

                buffer = new short[bufferSize/4];
                mRecord = new AudioRecord(audioSource,samplingRate,channelConfig,audioFormat,bufferSize);

                mRecord.startRecording();

                int noAllRead = 0;
            }

            @Override
            protected void loop() throws InterruptedException {
                int bufferResults = mRecord.read(buffer,0,bufferSize/4);
                updateAudio(buffer);
                Thread.sleep(100);
            }

            @SuppressLint("RestrictedApi")
            public void updateAudio(short[] buffer) {
                Preconditions.checkNotNull(buffer);

                buffer_bytearray = ShortToByte(buffer);
                stream.buffer().writeBytes(buffer_bytearray);
                audioMsg.setData(stream.buffer().copy());
                stream.buffer().clear();

                audioPubliser.publish(audioMsg);
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
}
