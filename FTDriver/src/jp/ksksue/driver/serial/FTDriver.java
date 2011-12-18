package jp.ksksue.driver.serial;
/*
 * FTDI Driver Class
 * 
 * Copyright (C) 2011 @ksksue
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * FT232RL
 * Baudrate : any
 * RX Data Size up to 60byte
 * TX Data Size up to 64byte
 */

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

class UsbId {
    int mVid;
    int mPid;
    UsbId(int vid, int pid) { mVid = vid; mPid = pid; }
}

public class FTDriver {
    private static final UsbId[] IDS = { 
        new UsbId(0x0403, 0x6001),
        new UsbId(0x0403, 0x6010),
    };
    public static final int BAUD9600 = 9600;
    public static final int BAUD14400 = 14400;
    public static final int BAUD19200 = 19200;
    public static final int BAUD38400 = 38400;
    public static final int BAUD57600 = 57600;
    public static final int BAUD115200 = 115200;
    public static final int BAUD230400 = 230400;

    private static final String TAG = FTDriver.class.getSimpleName();

    private UsbManager mManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mDeviceConnection;
    private List<FTSerialPort> mPorts;

    public FTDriver(UsbManager manager) {
        mManager = manager;
        mPorts = new ArrayList<FTSerialPort>();
    }
    
    public FTSerialPort getPort(int index) {
        return mPorts.get(index);
    }
    
    public FTSerialPort getPort() {
        return getPort(0);
    }

    // collect information of FTDI USB Device and create serial ports.
    public boolean begin() {
        for (UsbDevice device : mManager.getDeviceList().values()) {
            Log.i(TAG, "Devices : " + device.toString());
            if (beginDevice(device)) {
                // in current version , we use only first device.
                return true;
            }
        }
        return false;
    }

    // Close the device
    public void end() {
        if (mDevice != null) {
            endDevice(mDevice);
        }
    }

    // TODO Implement these methods
/*    public void available() {
    	
    }
    
    public void peek() {
    	
    }
    
    public void flush() {
    	
    }
    
    public void print() {
    	
    }
    
    public void println() {
    	
    }
    */

    private boolean beginDevice(UsbDevice device) {
    	List<FTSerialPort> newPorts = new ArrayList<FTSerialPort>();
        UsbDeviceConnection connection = mManager.openDevice(device);
        if (connection == null) {
        	// next device
        	return false;
        }
        Log.d(TAG, "check ids of device: " + device);
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            for (UsbId id : IDS) {
                if (device.getVendorId() == id.mVid && device.getProductId() == id.mPid) {
                    FTSerialPort port = createPort(connection, intf, i + ((count > 1)? 1 : 0));
                    if (port != null) {
                        newPorts.add(port);
                    }
                }
            }
        }
    	if (newPorts.size() > 0) {
    		// use this device.
    		// [TODO] in current version, it supports only one device
    		mPorts = newPorts;
    		mDevice = device;
    		mDeviceConnection = connection;
        	Log.i(TAG,"Device Serial : "+mDeviceConnection.getSerial());
        	return true;
    	}
    	connection.close();
    	return false;
    }
    private void endDevice(UsbDevice device) {
		String deviceName = device.getDeviceName();
		if (mDevice != null && mDevice.equals(deviceName)) {
			Log.d(TAG, "USB interface removed");
	        if (mDeviceConnection != null) {
	            for (FTSerialPort port : mPorts) {
	                port.close();
	            }
	        	mPorts.clear();
	            mDeviceConnection.close();
	            mDevice = null;
	            mDeviceConnection = null;
	        }
		}
    }
    
    // Sets the current USB device and interface
    private FTSerialPort createPort(UsbDeviceConnection connection, UsbInterface intf, int intfNo) {
    	try {
            // intf.getId() doesn't return value I expected.
            return new FTSerialPort(connection, intf, intfNo);
        } catch (Exception e) {
            Log.e(TAG, "error creating serial port", e);
        }
        return null;
    }

    // when insert the device USB plug into a USB port
    public boolean usbAttached(Intent intent) {
        Log.d(TAG, "USB device attached");
        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        return beginDevice(device);
    }
	
    // when remove the device USB plug from a USB port
    public void usbDetached(Intent intent) {
        Log.d(TAG, "USB device detached");
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        endDevice(device);
    }
}