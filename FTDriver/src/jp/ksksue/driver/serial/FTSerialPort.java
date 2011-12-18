/*
 * Copyright (C) 2011 @ksksue
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * 2011-12-12 modified by Ukuleletrip (@darkukll)
 */
package jp.ksksue.driver.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

class FTSerialInput extends InputStream {
    private UsbDeviceConnection mDeviceConnection;
    private UsbEndpoint mIn;
    private int mMaxPacketSize;
    private static final String TAG = FTSerialInput.class.getSimpleName();
    
    public FTSerialInput(UsbDeviceConnection conn, UsbEndpoint ep) {
        mDeviceConnection = conn;
        mIn = ep;
        mMaxPacketSize = ep.getMaxPacketSize();
        if (mMaxPacketSize == 0) {
            mMaxPacketSize = 64;
        }
    }

    @Override
    public int read() throws IOException {
        byte[] c = new byte[1];
        if (read(c, 0, 1) == 1) {
            return (int)c[0];
        }
        return -1;
    }
    
    @Override
    public int read (byte[] buffer, int offset, int length) {
        byte[] rbuf = new byte[mMaxPacketSize];
        int readLen = 0;

        while (readLen < length) {
            // we will get error (-1) if we call bulkTransfer with readLen 
            // more than maxPacketSize and data in serial buffer more than it. 
            int len = mDeviceConnection.bulkTransfer(mIn, rbuf,
                    Math.min(mMaxPacketSize, length-readLen+2), 0);
            if (len <= 2) {
                Log.d(TAG, "read received " + len + " bytes.");
                break;
            }
            // 1st and 2nd bytes are status bytes, they should be skipped.
            // FIXME shift rbuf's pointer 2 to 0. (I don't know how to do.)
            String dump = String.format("%02X %02X ", rbuf[0], rbuf[1]);
            for (int i = 0; i < len-2; ++i) {
                buffer[offset + readLen + i] = rbuf[i + 2];
                dump += String.format("%02X ", rbuf[i+2]);
            }
            Log.d(TAG, "read " + len + " bytes:" + dump);
            readLen += (len - 2);
        }
        return readLen;
    }
}

class FTSerialOutput extends OutputStream {
    private UsbDeviceConnection mDeviceConnection;
    private UsbEndpoint mOut;
    private int mMaxPacketSize;
    private static final String TAG = FTSerialInput.class.getSimpleName();
    
    public FTSerialOutput(UsbDeviceConnection conn, UsbEndpoint ep) {
        mDeviceConnection = conn;
        mOut = ep;
        mMaxPacketSize = ep.getMaxPacketSize();
        if (mMaxPacketSize == 0) {
            mMaxPacketSize = 64;
        }
    }

    @Override
    public void write(int arg0) throws IOException {
        byte[] c = new byte[1];
        c[0] = (byte)arg0;
        write(c, 0, 1);
    }
    
    @Override
    public void write (byte[] buffer, int offset, int count) throws IOException {
        int writtenLen = 0;
        byte[] writeBuffer = new byte[mMaxPacketSize];
        while (writtenLen < count) {
            int writeLen = Math.min(count - writtenLen, writeBuffer.length);
            String dump = "";
            for (int i=0; i < writeLen; i++) {
                writeBuffer[i] = buffer[offset + writtenLen + i];
                dump += String.format("%02X ", writeBuffer[i]);
            }
            Log.d(TAG, "write: " + dump);
            int len;
            if ((len = mDeviceConnection.bulkTransfer(mOut, writeBuffer, writeLen, 0)) < 0) {
                throw new IOException("bulkTransfer returned " + len);
            }
            writtenLen += len;
        }
    }
}

public class FTSerialPort {
    private static final String TAG = FTSerialPort.class.getSimpleName();
    private UsbDeviceConnection mDeviceConnection;
    private UsbInterface mInterface;
    private FTSerialInput mInputStream;
    private FTSerialOutput mOutputStream;
    private int mInterfaceNo;
    private boolean mIsOpened;
    
    public FTSerialPort(UsbDeviceConnection conn, UsbInterface intf, int intfNo) {
        mDeviceConnection = conn;
        mInterface = intf;
        mInterfaceNo = intfNo;
        mIsOpened = false;
        if (mInterface.getEndpointCount() < 2) {
            throw new IllegalArgumentException(
                    "interface " + mInterface.toString() + " does not have 2 endpoints");
        }
        mInputStream = new FTSerialInput(mDeviceConnection, mInterface.getEndpoint(0));
        mOutputStream = new FTSerialOutput(mDeviceConnection, mInterface.getEndpoint(1)); 
    }
    
    public void open(int baudrate) {
        if (mIsOpened) {
            return;
        }
        if (!mDeviceConnection.claimInterface(mInterface, false)) {
            throw new IllegalArgumentException(
                    "interface " + mInterface.toString() + " failed to claim");
        }
        mIsOpened = true;
        initFTDIChip(baudrate, mInterfaceNo);
    }
    
    public void close() {
        if (mIsOpened) {
            mDeviceConnection.releaseInterface(mInterface);
            mIsOpened = false;
        }
    }
    
    public InputStream getInputStream() {
        return mInputStream;
    }
    
    public OutputStream getOutputStream() {
        return mOutputStream;
    }
    
    // Initial control transfer
    private void initFTDIChip(int baudrate, int intfNo) {
        int baud = calcFTDIBaudrate(baudrate);
        mDeviceConnection.controlTransfer(0x40, 0, 0, intfNo, null, 0, 0);           //reset
        mDeviceConnection.controlTransfer(0x40, 0, 1, intfNo, null, 0, 0);           //clear Rx
        mDeviceConnection.controlTransfer(0x40, 0, 2, intfNo, null, 0, 0);           //clear Tx
        mDeviceConnection.controlTransfer(0x40, 0x02, 0x0000, intfNo, null, 0, 0);   //flow control none
        mDeviceConnection.controlTransfer(0x40, 0x03, baud, intfNo, null, 0, 0);     //set baudrate
        mDeviceConnection.controlTransfer(0x40, 0x04, 0x0008, intfNo, null, 0, 0);   //data bit 8, parity none, stop bit 1, tx off
    }
    
    /* Calculate a Divisor at 48MHz
     * 9600 : 0x4138
     * 11400    : 0xc107
     * 19200    : 0x809c
     * 38400    : 0xc04e
     * 57600    : 0x0034
     * 115200   : 0x001a
     * 230400   : 0x000d
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
        divisor = (base / 16 / baud) | (((base / 2 / baud) & 4) != 0 ? 0x4000 // 0.5
                : ((base / 2 / baud) & 2) != 0 ? 0x8000 // 0.25
                        : ((base / 2 / baud) & 1) != 0 ? 0xc000 // 0.125
                                : 0);
        return divisor;
    }
}
