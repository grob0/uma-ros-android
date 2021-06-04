package com.example.umarosandroid;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;

import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.regex.Pattern;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class SetupActivity extends AppCompatActivity {

    public static final String MASTER_URI = "com.example.umarosandroid.MASTER_URI";
    public static final String MASTER_PORT = "com.example.umarosandroid.MASTER_PORT";

    public static final String NODE_NAME = "com.example.umarosandroid.NODE_NAME";

    public static final String ENABLE_CAMERA = "com.example.umarosandroid.ENABLE_CAMERA";
    public static final String ENABLE_AUDIO = "com.example.umarosandroid.ENABLE_AUDIO";
    public static final String ENABLE_GPS = "com.example.umarosandroid.ENABLE_GPS";
    public static final String ENABLE_IMU = "com.example.umarosandroid.ENABLE_IMU";


    EditText masterURI;
    EditText masterPort;

    EditText nodeNameEdit;

    Switch cameraSwitch;
    Switch audioSwitch;
    Switch gpsSwitch;
    Switch imuSwitch;

    Button connectButton;


    private static final Pattern IP_ADDRESS
            = Pattern.compile(
            "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
                    + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
                    + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
                    + "|[1-9][0-9]|[0-9]))");

    String masterUri_s = "";
    String masterPort_s = "11311";

    String nodeName_s = "";

    boolean enableCamera;
    boolean enableAudio;
    boolean enableGps;
    boolean enableImu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        connectButton = (Button) findViewById(R.id.createorconnect);
        connectButton.setEnabled(false);
        masterURI = (EditText) findViewById(R.id.MasterUri);
        masterPort = (EditText) findViewById(R.id.MasterPort);

        nodeNameEdit = (EditText) findViewById(R.id.NodeName);

        cameraSwitch = (Switch) findViewById(R.id.CameraSwitch);
        audioSwitch = (Switch) findViewById(R.id.AudioSwitch);
        gpsSwitch = (Switch) findViewById(R.id.GPSSwitch);
        imuSwitch = (Switch) findViewById(R.id.IMUSwitch);


        masterURI.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String uri = s.toString();
                if((TextUtils.isEmpty(uri)) || !IP_ADDRESS.matcher(uri).matches()) {
                    masterURI.setError("Please enter valid IP address");
                    connectButton.setEnabled(false);
                }
                else {
                    masterURI.setError(null);
                    connectButton.setEnabled(checkPortValidOrNot(masterPort.getText().toString()));
                    masterUri_s = uri;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        masterPort.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String uri = s.toString();
                if((TextUtils.isEmpty(uri) || !checkPortValidOrNot(uri))) {
                    if(!TextUtils.isEmpty(uri))
                        masterPort.setError("Please enter valid port number");
                    connectButton.setEnabled(false);
                }
                else {
                    masterPort.setError(null);
                    connectButton.setEnabled(IP_ADDRESS.matcher(masterURI.getText().toString()).matches());
                    masterPort_s = uri;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        nodeNameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                final String name = s.toString();
                if(!TextUtils.isEmpty(name))  {
                    nodeNameEdit.setError(null);
                    nodeName_s = name;
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        cameraSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableCamera = isChecked;
                System.out.println("Camera toggled");

            }
        });
        audioSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableAudio = isChecked;
                System.out.println("Audio toggled");

            }
        });
        gpsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableGps = isChecked;
                System.out.println("GPS toggled");

            }
        });
        imuSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                enableImu = isChecked;
                System.out.println("IMU toggled");
            }
        });


        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent mIntent = new Intent(SetupActivity.this, MainActivity.class);
                mIntent.putExtra(MASTER_URI,masterUri_s);
                mIntent.putExtra(MASTER_PORT,masterPort_s);
                mIntent.putExtra(NODE_NAME,nodeName_s);
                mIntent.putExtra(ENABLE_CAMERA,enableCamera);
                mIntent.putExtra(ENABLE_AUDIO,enableAudio);
                mIntent.putExtra(ENABLE_GPS,enableGps);
                mIntent.putExtra(ENABLE_IMU,enableImu);
                startActivity(mIntent);
            }
        });
    }

    boolean checkPortValidOrNot(String port) {
        int result = Integer.parseInt(port);
        return ((result >1023) && (result< 65536));
    }
}
