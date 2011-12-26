package jp.ksksue.sample;
/*
 * Copyright (C) 2011 @ksksue
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import jp.ksksue.serial.R;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import jp.ksksue.driver.serial.*;

public class FTSampleTerminalActivity extends Activity {
    
    private FTDriver mSerial;
    private FTSerialPort mPort = null;
    private InputStream mIn;
    private OutputStream mOut;

    private TextView mTvSerial;
    private String mText;
    private Spinner mPortSelector;
    private boolean mStop = false;
    
    private final int DEFAULT_BAUD = FTDriver.BAUD9600;
    
    private static final String TAG = FTSampleTerminalActivity.class.getSimpleName();
    
    Handler mHandler = new Handler();

    private Button btWrite;
    private EditText etWrite;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTvSerial = (TextView) findViewById(R.id.tvSerial);
        mPortSelector = (Spinner) findViewById(R.id.portSelector);
        btWrite = (Button) findViewById(R.id.btWrite);
        etWrite = (EditText) findViewById(R.id.etWrite);
        
        // get service
        mSerial = new FTDriver((UsbManager)getSystemService(Context.USB_SERVICE));
        if (mSerial.begin()) {
            String[] portNames = mSerial.getPortNames();
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, portNames);
            mPortSelector.setAdapter(adapter);
            mPortSelector.setSelection(0);
            mPort = mSerial.getPort(0);
        }
        btWrite.setEnabled(mPort != null);
          
        // listen for new devices
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        
        startMainloop();
        
        // ---------------------------------------------------------------------------------------
       // Write Button
        // ---------------------------------------------------------------------------------------
        btWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String strWrite = etWrite.getText().toString() + "\n";
                try {
                    mOut.write(strWrite.getBytes());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        
        mPortSelector.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1,
                    int pos, long id) {
                stopMainloop();
                String portName = (String) arg0.getItemAtPosition(pos);
                mPort = mSerial.getPort(portName);
                startMainloop();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // TODO Auto-generated method stub
                
            }
            
        });
        // ----------------------------------------------------------------------------------------
        // Input Text
        // ----------------------------------------------------------------------------------------
//        etWrite.setOnKeyListener(new View.OnKeyListener() {
//          @Override
//          public boolean onKey(View v, int keyCode, KeyEvent event) {
//              if (event.getAction() == KeyEvent.ACTION_DOWN
//                      && keyCode == KeyEvent.KEYCODE_ENTER
//                      && mPortExists) {
//                  String strWrite = etWrite.getText().toString() + "\n";
//                  mSerial.write(strWrite.getBytes(),strWrite.length());
//                  return true;
//              }
//              return false;
//          }
//      });
    }
    
    @Override
    public void onDestroy() {
        mSerial.end();
        mStop = true;
        unregisterReceiver(mUsbReceiver);
        super.onDestroy();
    }

    private Thread mLoop;
    
    private void startMainloop() {
        if (mPort == null){
            return;
        }
        mPort.open(DEFAULT_BAUD);
        mIn = mPort.getInputStream();
        mOut = mPort.getOutputStream();
        mStop = false;
        mLoop = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] rbuf = new byte[100];
                
                while (true) {

                    //////////////////////////////////////////////////////////
                    // Read and Display to Terminal
                    //////////////////////////////////////////////////////////
                    int len = 0;
                    try {
                        len = mIn.read(rbuf);
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    if(len > 0) {
                        //Log.i(TAG,"Read  Length : "+len);
                        mText = "";
                        for(int i=0; i<len; ++i) {
                            //Log.i(TAG,"Read  Data["+i+"] : "+rbuf[i]);
                            
                            // "\r":CR(0x0D) "\n":LF(0x0A)
                            if (rbuf[i] == 0x0D) {
                                mText = mText + "\r";
                            } else if (rbuf[i] == 0x0A) {
                                mText = mText + "\n";
                            } else {
                                mText = mText + "" +(char)rbuf[i];
                            }
                        }
                        mHandler.post(new Runnable() {
                            public void run() {
                                mTvSerial.append(mText);
                            }
                        });
                    }
                    
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    if(mStop) {
                        return;
                    }
                }
            }
        });
        mLoop.start();
    }
    
    private void stopMainloop() {
        if (mPort == null) {
            return;
        }
        mStop = true;
        try {
            mLoop.join();
            mPort.close();
            Log.d(TAG, "Thread stopped.");
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    // BroadcastReceiver when insert/remove the device USB plug into/from a USB port  
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                stopMainloop();
                if (mSerial.usbAttached(intent)) {
                    mPort = mSerial.getPort();
                    btWrite.setEnabled(true);
                    startMainloop();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                stopMainloop();
                mSerial.usbDetached(intent);
                btWrite.setEnabled(false);
            }
        }
    };
}
