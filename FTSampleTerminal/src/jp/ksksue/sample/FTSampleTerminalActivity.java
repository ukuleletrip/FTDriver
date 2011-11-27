package jp.ksksue.sample;
/*
 * Copyright (C) 2011 @ksksue
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

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
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import jp.ksksue.driver.serial.*;
public class FTSampleTerminalActivity extends Activity {
	
	FTDriver mSerial;

	private TextView mTvSerial;
	private String mText;
	private boolean mStop = false;
	private boolean mStopped = true;
	private boolean mPortExists = false;
	
	private final int DEFAULT_BAUD = FTDriver.BAUD9600;
		
	String TAG = "FTSampleTerminal";
    
    Handler mHandler = new Handler();

    private Button btWrite;
    private EditText etWrite;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mTvSerial = (TextView) findViewById(R.id.tvSerial);
        btWrite = (Button) findViewById(R.id.btWrite);
        etWrite = (EditText) findViewById(R.id.etWrite);
        
        // get service
        mSerial = new FTDriver((UsbManager)getSystemService(Context.USB_SERVICE));
        if (mSerial.begin()) {
        	mPortExists = true;
        }
        btWrite.setEnabled(mPortExists);
          
        // listen for new devices
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        
        if(mPortExists && mSerial.open(DEFAULT_BAUD)) {
        	mainloop();
        }
        
        // ---------------------------------------------------------------------------------------
       // Write Button
        // ---------------------------------------------------------------------------------------
        btWrite.setOnClickListener(new View.OnClickListener() {
    		@Override
    		public void onClick(View v) {
    			String strWrite = etWrite.getText().toString() + "\n";;
    			mSerial.write(strWrite.getBytes(),strWrite.length());
    		}
        });
        // ----------------------------------------------------------------------------------------
        // Input Text
        // ----------------------------------------------------------------------------------------
//        etWrite.setOnKeyListener(new View.OnKeyListener() {
//			@Override
//			public boolean onKey(View v, int keyCode, KeyEvent event) {
//				if (event.getAction() == KeyEvent.ACTION_DOWN
//						&& keyCode == KeyEvent.KEYCODE_ENTER
//						&& mPortExists) {
//	    			String strWrite = etWrite.getText().toString() + "\n";
//	    			mSerial.write(strWrite.getBytes(),strWrite.length());
//					return true;
//				}
//				return false;
//			}
//		});
    }
    
    @Override
    public void onDestroy() {
		mSerial.end();
		mStop = true;
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
    }
        
	private void mainloop() {
		new Thread(mLoop).start();
	}
	
	private Runnable mLoop = new Runnable() {
		@Override
		public void run() {
			byte[] rbuf = new byte[100];
						
			for(;;){//this is the main loop for transferring
				
				//////////////////////////////////////////////////////////
				// Read and Display to Terminal
				//////////////////////////////////////////////////////////
				int len = mSerial.read(rbuf);
				if(len > 0) {
					Log.i(TAG,"Read  Length : "+len);
					mText = (String) mTvSerial.getText();
					for(int i=0; i<len; ++i) {
						Log.i(TAG,"Read  Data["+i+"] : "+rbuf[i]);
						
						// "\r":CR(0x0D) "\n":LF(0x0A)
						if(rbuf[i]==0x0D) {							
							mText = mText + "\r";
						} else if(rbuf[i]==0x0A) {
							mText = mText + "\n";
						} else {
							mText = mText + "" +(char)rbuf[i];
						}
					}
					// FIXME 
					mHandler.post(new Runnable() {
						public void run() {
							mTvSerial.setText(mText);
						}
					});
				}
				
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				if(mStop) {
					mStopped = true;
					return;
				}
			}
		}
	};
	
    // BroadcastReceiver when insert/remove the device USB plug into/from a USB port  
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
    		String action = intent.getAction();
    		if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
    			if (mPortExists) {
    				mSerial.end();
    			}
    			if (mSerial.usbAttached(intent)) {
    		        if(mPortExists && mSerial.open(DEFAULT_BAUD)) {
    		            btWrite.setEnabled(mPortExists);
    		        	mainloop();
    		        }
    			}
    		} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
    			mSerial.usbDetached(intent);
    			mPortExists = false;
    			mStop = true;
    	        btWrite.setEnabled(mPortExists);
    		}
        }
    };
}
