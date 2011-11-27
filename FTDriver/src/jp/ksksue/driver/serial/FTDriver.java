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
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

class UsbId {
	int mVid;
	int mPid;
	UsbId(int vid, int pid) { mVid = vid; mPid = pid; }
}

class FTSerialPort {
	private final UsbInterface mInterface;
	private final UsbDeviceConnection mDeviceConnection;
	private final int mIntfNo;
	private final UsbEndpoint mIn;
	private final UsbEndpoint mOut;
	
    private static final String TAG = "FTSerialPort";

	FTSerialPort(UsbInterface intf, UsbDeviceConnection connection, int intfNo) {
    	if (!connection.claimInterface(intf, false)) {
			throw new IllegalArgumentException(
                	"interface " + intf.toString() + " failed to be claimed");
    	}
    	Log.d(TAG,"claim interface succeeded");
    	Log.d(TAG,"interface ID : "+intf.getId());
		mInterface = intf;
		mIntfNo = intfNo;
		mDeviceConnection = connection;
		if (intf.getEndpointCount() < 2) {
			throw new IllegalArgumentException(
                    	"interface " + intf.toString() + " does not have 2 endpoints");
		}
		mIn = intf.getEndpoint(0);
    	mOut = intf.getEndpoint(1);
	}
	public void end() {
        mDeviceConnection.releaseInterface(mInterface);
	}
	public boolean open(int baudrate) {
		initFTDIChip(baudrate, mIntfNo);
		return true;
	}
	public void close() {
		// do NOTHING.
	}
    // Read Binary Data
    public int read(byte[] buf, int length) {
    	byte[] rbuf = new byte[length+2];
    	
		int len = mDeviceConnection.bulkTransfer(mIn, rbuf, length+2, 0); // RX
		if (len < 2) {
			return -1;
		}
		// FIXME shift rbuf's pointer 2 to 0. (I don't know how to do.) 
		for(int i=0; i<len; ++i) {
			buf[i] = rbuf[i+2];
		}
		return (len-2);
    }
    // Write n byte Binary Data
    public int write(byte[] buf,int length) {
    	if(length > 64) {
    		// [TODO] it is needed ?
    		return -1;
    	}
		return mDeviceConnection.bulkTransfer(mOut, buf, length, 0); // TX    	
    }
    
    // Initial control transfer
	private void initFTDIChip(int baudrate, int interfaceNo) {
		int baud = calcFTDIBaudrate(baudrate);
		mDeviceConnection.controlTransfer(0x40, 0, 0, 0, null, 0, 0);				//reset
		mDeviceConnection.controlTransfer(0x40, 0, 1, 0, null, 0, 0);				//clear Rx
		mDeviceConnection.controlTransfer(0x40, 0, 2, 0, null, 0, 0);				//clear Tx
		mDeviceConnection.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 0);	//flow control none
		mDeviceConnection.controlTransfer(0x40, 0x03, baud, 0, null, 0, 0);		//set baudrate
		mDeviceConnection.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 0);	//data bit 8, parity none, stop bit 1, tx off
	}
	
	/* Calculate a Divisor at 48MHz
	 * 9600	: 0x4138
	 * 11400	: 0xc107
	 * 19200	: 0x809c
	 * 38400	: 0xc04e
	 * 57600	: 0x0034
	 * 115200	: 0x001a
	 * 230400	: 0x000d
	 */
	private static int calcFTDIBaudrate(int baud) {
		int divisor;
		if(baud <= 3000000) {
			divisor = calcFT232bmBaudBaseToDiv(baud, 48000000);
		} else {
			Log.e(TAG,"Cannot set baud rate : " + baud + ", because too high." );
			Log.e(TAG,"Set baud rate : 9600" );
			divisor = calcFT232bmBaudBaseToDiv(9600, 48000000);
		}
		return divisor;
	}

	// Calculate a divisor from baud rate and base clock for FT232BM, FT2232C and FT232LR
	// thanks to @titoi2
	private static int calcFT232bmBaudBaseToDiv(int baud, int base) {
		int divisor;
		divisor = (base / 16 / baud)
		| (((base / 2 / baud) & 4) != 0 ? 0x4000 // 0.5
				: ((base / 2 / baud) & 2) != 0 ? 0x8000 // 0.25
						: ((base / 2 / baud) & 1) != 0 ? 0xc000 // 0.125
								: 0);
		return divisor;
	}
}

public class FTDriver {
    private static final UsbId[] IDS = { new UsbId(0x0403, 0x6001), new UsbId(0x0403, 0x6010) };

    public static final int BAUD9600	= 9600;
    public static final int BAUD14400	= 14400;
    public static final int BAUD19200	= 19200;
    public static final int BAUD38400	= 38400;
    public static final int BAUD57600	= 57600;
    public static final int BAUD115200	= 115200;
    public static final int BAUD230400	= 230400;

    private static final String TAG = "FTDriver";

    private UsbManager mManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mDeviceConnection;
    private List<FTSerialPort> mPorts;

    public FTDriver(UsbManager manager) {
        mManager = manager;
        mPorts = new ArrayList<FTSerialPort>();
    }
    
    // collect information of FTDI USB Device and create serial ports.
    public boolean begin() {
    	for (UsbDevice device : mManager.getDeviceList().values()) {
      	  	Log.i(TAG,"Devices : "+device.toString());
      	  	if (beginDevice(device)) {
      	  		return true;
      	  	}
    	}
    	return false;
    }
    
    // Open an FTDI USB Device
    public boolean open(int portNo, int baudrate) {
    	if (mPorts.size() <= portNo) {
    		return false;
    	}
    	mPorts.get(portNo).open(baudrate);
        return true;
    }
    public boolean open(int baudrate) {
    	return open(0, baudrate);
    }
    
    public void close(int portNo) {
    	if (mPorts.size() <= portNo) {
    		return;
    	}
    	mPorts.get(portNo).close();
    }
    public void close() {
    	close(0);
    }

    // Close the device
    public void end() {
    	if (mDevice != null) {
    		endDevice(mDevice);
    	}
    }

    // Read Binary Data
    public int read(int portNo, byte[] buf, int length) {
    	if (mPorts.size() <= portNo) {
    		return -1;
    	}
    	return mPorts.get(portNo).read(buf, length);
    }
    public int read(byte[] buf) {
    	return read(0, buf, buf.length);
    }

    // Write n byte Binary Data
    public int write(int portNo, byte[] buf,int length) {
    	if (mPorts.size() <= portNo) {
    		return -1;
    	}
    	return mPorts.get(portNo).write(buf, length);
    }
    public int write(byte[] buf,int length) {
    	return write(0, buf, length);
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
  	  	List<UsbInterface> intfs = findUSBInterfaceByVIDPID(device, IDS);
        UsbDeviceConnection connection = mManager.openDevice(device);
        if (connection == null) {
        	// next device
        	return false;
        }
    	for (UsbInterface intf : intfs) {
    		FTSerialPort port = createPort(device, connection, intf);
    		if (port != null) {
    			newPorts.add(port);
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
	        		port.end();
	            }
	        	mPorts.clear();
	            mDeviceConnection.close();
	            mDevice = null;
	            mDeviceConnection = null;
	        }
		}
    }
    
    // Sets the current USB device and interface
    private FTSerialPort createPort(UsbDevice device, UsbDeviceConnection connection, UsbInterface intf) {
    	Log.d(TAG,"Vendor ID : "+device.getVendorId());
    	Log.d(TAG,"Product ID : "+device.getProductId());
    	try {
    		return new FTSerialPort(intf, connection, intf.getId());
        } catch (Exception e) {
            Log.e(TAG, "error creating serial port", e);
        }
        return null;
    }

    // searches for an interface on the given USB device by VID and PID
    private List<UsbInterface> findUSBInterfaceByVIDPID(UsbDevice device, UsbId[] ids) {
    	List<UsbInterface> intfs = new ArrayList<UsbInterface>();
        Log.d(TAG, "findUSBInterface " + device);
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            for (UsbId id : ids) {
            	if (device.getVendorId() == id.mVid && device.getProductId() == id.mPid) {
            		intfs.add(intf);
            	}
            }
        }
        return intfs;
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