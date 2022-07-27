package com.example.bt_event_davey;

import android.Manifest;
import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    Button scanButton;
    ListView scanList;
    TextView txtRSSI;
    ArrayList<String> arrayList = new ArrayList<>();
    private AudioManager mAudioManager;
    int headphoneState;
    Boolean firstRun=true;
    Boolean youkolMode=false;
    ToneGenerator beeper = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

    public static String TAG="PhoneStateReceiver";

    private SettingsContentObserver mSettingsContentObserver;

    //these are the devices allowed under youkol mode - hardcoded; when done they will come from the
    //admin flows & UI
    //true means that device x is okay to have audio io under the policy
    // BEGIN POLICY
    Boolean polBTHeadphones=false;
    Boolean polBTSpeaker=false;
    Boolean polBTCar=true;
    Boolean polBTOther=false;
    Boolean polUSBHeadphones=false;
    Boolean polUSBOther=false;
    Boolean polJackHeadphones=false;
    Boolean polEarpiece=false;
    Boolean polScreen=false;
    Boolean polMicrophone=false;
    Boolean polSpeakerphone=false;
    // END POLICY

    public static final int BLUETOOTH_PERMISSION_CODE = 100;
    public static final int LOCATION_PERMISSION_CODE = 101;
    public static final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scanButton = findViewById(R.id.scanButton);
        scanButton.setText("Force timer");
        txtRSSI = findViewById(R.id.txtRSSI);
        scanList = findViewById(R.id.scanList);
        scanButton.setOnClickListener(this::onClick);
        Context context = getApplicationContext();

        ActionBar toast = null;
        //Toast.makeText(context, "App Started!", Toast.LENGTH_SHORT).show();

        BluetoothAdapter mBluetoothAdapter;
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        this.registerReceiver(broadcastReceiver, filter);

        this.registerVolumeChangeListener();
        this.registerBroadcast();

        populateListItem("[System] Speakerphone");
        populateListItem("[System] Headset");
        populateListItem("[System] Microphone");
        startYoukolMode();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_PHONE_STATE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_PHONE_STATE},
                        MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);

                // MY_PERMISSIONS_REQUEST_READ_PHONE_STATE is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermission(Manifest.permission.BLUETOOTH, BLUETOOTH_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE);

    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        BluetoothDevice device;
        @Override
        public void onReceive(Context context, Intent intent) {
            checkPermission(Manifest.permission.BLUETOOTH, BLUETOOTH_PERMISSION_CODE);
            //Toast.makeText(getApplicationContext(), "On receive event",Toast.LENGTH_SHORT).show();
            String action = intent.getAction();
            String strBTDeviceClass = null;
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                //Toast.makeText(getApplicationContext(), device.getName() + " connected - stop Youkol mode",    Toast.LENGTH_SHORT).show();
                int i = device.getBluetoothClass().getDeviceClass();
                Toast.makeText(getApplicationContext(), String.valueOf(i),    Toast.LENGTH_SHORT).show();
                strBTDeviceClass = (String) convertBTtype(i);
                populateListItem("[" + strBTDeviceClass + "] " + device.getName());
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                txtRSSI.setText(rssi + " dBm");
                // muteMedia();
                if (i == 1028) {
                    //Toast.makeText(getApplicationContext(), device.getName() + i,    Toast.LENGTH_SHORT).show();
                    //checkAgainstPolicy(2);
                    stopYoukolMode();
                } else {
                    //Toast.makeText(getApplicationContext(), device.getName() + i,    Toast.LENGTH_SHORT).show();
                    //checkAgainstPolicy(4);
                    //muteMedia();
                    startYoukolMode();
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                int i = device.getBluetoothClass().getDeviceClass();
                if (i == 1028) {
                    strBTDeviceClass = (String) convertBTtype(i);
                    removeListItem("[" + strBTDeviceClass + "] " + device.getName());
                    startYoukolMode();
                }
            }
            if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
                headphoneState = intent.getIntExtra("state", -1);
                if (headphoneState == 1) {
                    //Toast.makeText(getApplicationContext(), "3.5mm headphones connected - stop Youkol mode", Toast.LENGTH_SHORT).show();
                    populateListItem("[Headphones] 3.5mm headphones");
                    muteMedia();
                    //stopYoukolMode();
                } else if (headphoneState == 0) {
                    if (!firstRun) {
                        //  Toast.makeText(getApplicationContext(), "3.5mm headphones disconnected - start Youkol mode", Toast.LENGTH_SHORT).show();
                        removeListItem("[Headphones] 3.5mm headphones");
                        startYoukolMode();
                    }
                }
            }
        }
    };

    private void registerBroadcast() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED);
        this.registerReceiver(audioStateChangeBroadcast, filter);
    }

    // Broadcasting changes in bluetooth headset Audio Output while in call
    private final BroadcastReceiver audioStateChangeBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device;
            checkPermission(Manifest.permission.BLUETOOTH, BLUETOOTH_PERMISSION_CODE);
            // Detect Changes in Bluetooth device audio
            if (intent.getAction().equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int deviceClass = device.getBluetoothClass().getDeviceClass();
                int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1);

                // New Bluetooth Device audio route appeared
                if ((state == BluetoothHeadset.STATE_AUDIO_CONNECTED) && (deviceClass == 1028)) {
                    Toast.makeText(context, "connected", Toast.LENGTH_SHORT).show();
                    mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    youkolMode = false;
                }

                // Bluetooth audio route disappeared
                else if ((state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) && (deviceClass == 1028)) {
                    Toast.makeText(context, "disconnected", Toast.LENGTH_SHORT).show();
                    mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    mAudioManager.setMicrophoneMute(true);
                    mAudioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                    mAudioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    youkolMode = true;;
                }
            } else if (intent.getAction().equals(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED)) {

                if (mAudioManager.isSpeakerphoneOn()) {
                    // Toast.makeText(context, "Speaker phone On", Toast.LENGTH_SHORT).show();
                } else {
                    // Toast.makeText(context, "Speaker phone Off", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    public void onClick(View v) {
        Context context = getApplicationContext();
        if (v == scanButton) {
            mAudioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            mAudioManager.setMicrophoneMute(true);
            mAudioManager.setSpeakerphoneOn(false);
            CallAudioState callAudioState = null;
            int i = 1; //callAudioState.getRoute();
            String str=Integer.toString(i);
            txtRSSI.setText(str);

            new CountDownTimer(60000, 1000) {
                public void onTick(long millisUntilFinished) {
                    scanButton.setText("seconds remaining: " + millisUntilFinished / 1000);
                }

                public void onFinish() {
                    scanButton.setText("done!");
                }
            }.start();
        } else {
            //
        }
    }

    private void populateListItem(String strR){
        Context context = getApplicationContext();
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayList);
        arrayList.add(strR);
        scanList.setAdapter(arrayAdapter);
    }

    private void removeListItem(String strR){
        Context context = getApplicationContext();
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, arrayList);
        arrayList.remove(strR);
        scanList.setAdapter(arrayAdapter);
    }

    private void startYoukolMode(){
        if (!youkolMode){
            youkolMode = true;
            TextView txtYoukolMode = findViewById(R.id.textView2);
            txtYoukolMode.setText("Youkol mode is on");
            hideNavBar();
            muteMedia();
        }
    }

    private void stopYoukolMode(){
        if (youkolMode){
            youkolMode = false;
            TextView txtYoukolMode = findViewById(R.id.textView2);
            txtYoukolMode.setText("Youkol mode is off");
            showNavBar();
            unmuteMedia();
        }
    }

    // OVERRIDE phone volume keys
    private void registerVolumeChangeListener() {
        mSettingsContentObserver = new SettingsContentObserver(this, new Handler());
        getApplicationContext().getContentResolver().registerContentObserver(android.provider.
                Settings.System.CONTENT_URI, true, mSettingsContentObserver);
    }

    private void unRegisterVolumeChangeListener() {
        getApplicationContext().getContentResolver().unregisterContentObserver(mSettingsContentObserver);
    }

    public class SettingsContentObserver  extends ContentObserver {
        Context context;

        public SettingsContentObserver(Context c, Handler handler) {
            super(handler);
            context=c;
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            if (youkolMode) {
                if (mAudioManager.getMode() == AudioManager.MODE_IN_CALL) {
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 14, AudioManager.FLAG_ALLOW_RINGER_MODES|AudioManager.FLAG_PLAY_SOUND);
                    mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                    mAudioManager.setStreamVolume(AudioManager.STREAM_DTMF, 0, AudioManager.ADJUST_RAISE);
                    mAudioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 14, AudioManager.ADJUST_RAISE);
                    mAudioManager.setMicrophoneMute(true);

                    beeper.startTone(ToneGenerator.TONE_SUP_INTERCEPT);

                } else {
                    mAudioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    beeper.stopTone();
                }
            } else {
                beeper.stopTone();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (youkolMode) {
            Toast.makeText(MainActivity.this, "Going back in not allowed in Youkol mode", Toast.LENGTH_SHORT).show();

        }else {
            super.onBackPressed();
        }
    }

    public void hideNavBar(){
        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

    public void showNavBar(){
        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH);
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
    }

    // func that OVERRIDE and block all user exit from app tries
    @Override
    protected void onUserLeaveHint() {

        if (youkolMode) {
            // Pop up message and not allowing the user to go back while in youkol mode
            Toast.makeText(MainActivity.this, "You can't exit app while in Youkol mode", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            super.onUserLeaveHint();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        //lastTouchX = e.getX();
        //lastTouchY = e.getY();
        //Toast.makeText(getApplicationContext(), "Touch event" + youkolMode.toString(), Toast.LENGTH_SHORT).show();
        if (youkolMode) {
            hideNavBar();
        }
        return super.onTouchEvent(e);
    }

    public CharSequence convertBTtype(Integer i){
        String strDeviceClass = null;
        switch (i) {
            //Reference: https://developer.android.com/reference/android/bluetooth/BluetoothClass.Device
            case 1028:
                strDeviceClass = "BT Headset";
                break;
            case 1056:
                strDeviceClass = "BT Car Audio";
                break;
            case 1032:
                strDeviceClass = "BT Car Handsfree";
                break;
            case 1048:
                strDeviceClass = "BT Headphones";
                break;
            case 1064:
                strDeviceClass = "BT HiFi Audio";
                break;
            case 1044:
                strDeviceClass = "BT Loudspeaker";
                break;
            case 1040:
                strDeviceClass = "BT Microphone";
                break;
            case 1052:
                strDeviceClass = "BT Portable Audio";
                break;
            case 1084:
                strDeviceClass = "BT Loudspeaker";
                break;
            default:
                strDeviceClass = "_";
                break;
        }
        return strDeviceClass;
    }

    public void getBTRSSI (BluetoothDevice btDevice){
        BluetoothDevice device;
        Intent intent=null;
        Context context;
        String action = intent.getAction();
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
            txtRSSI.setText("RSSI is");
            stopYoukolMode();
        }
    }

    public void muteMedia(){
        mAudioManager.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
    }

    public void unmuteMedia(){
        mAudioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
    }

    private void checkAgainstPolicy(Integer deviceType){
        String strDeviceType = null;
        switch (deviceType) {
            case 1:
                strDeviceType = "BT Headphones";
                if (!polBTHeadphones){
                    startYoukolMode();
                }
                break;
            case 2:
                strDeviceType = "BT Car Audio";
                if (!polBTCar){
                    startYoukolMode();
                }
                break;
            case 3:
                strDeviceType = "BT Speaker";
                if (!polBTSpeaker){
                    startYoukolMode();
                }
                break;
            case 4:
                strDeviceType = "BT Other";
                if (!polBTOther){
                    startYoukolMode();
                }
                break;
            case 5:
                strDeviceType = "USB Headset";
                if (!polUSBHeadphones){
                    startYoukolMode();
                }
                break;
            case 6:
                strDeviceType = "USB Other";
                if (!polUSBOther){
                    startYoukolMode();
                }
                break;
            case 7:
                strDeviceType = "3.5mm Headphones";
                if (!polJackHeadphones){
                    startYoukolMode();
                }
                break;
            case 8:
                strDeviceType = "Speakerphone";
                if (!polSpeakerphone){
                    startYoukolMode();
                }
                break;
            case 9:
                strDeviceType = "Earpiece";
                if (!polEarpiece){
                    startYoukolMode();
                }
                break;
            case 10:
                strDeviceType = "Microphone";
                if (!polMicrophone){
                    startYoukolMode();
                }
                break;
            case 11:
                strDeviceType = "Screen";
                if (!polScreen){
                    startYoukolMode();
                }
                break;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unRegisterVolumeChangeListener();
        unregisterReceiver(audioStateChangeBroadcast);
        unregisterReceiver(broadcastReceiver);
    }

    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(this, new String[] { permission }, requestCode);
        }
    }

    // This function is called when the user accepts or decline the permission.
    // Request Code is used to check which permission called this function.
    // This request code is provided when the user is prompt for permission.
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode,
                permissions,
                grantResults);

        if (requestCode == BLUETOOTH_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth Permission Granted", Toast.LENGTH_SHORT) .show();
            } else {
                Toast.makeText(this, "Bluetooth Permission Denied", Toast.LENGTH_SHORT) .show();
            }
        } else if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == MY_PERMISSIONS_REQUEST_READ_PHONE_STATE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                // permission was granted, yay!

            } else {

                // permission denied, boo! Disable the
                // functionality that depends on this permission.
            }
            return;
        }

        // other 'case' lines to check for other
        // permissions this app might request
    }

}