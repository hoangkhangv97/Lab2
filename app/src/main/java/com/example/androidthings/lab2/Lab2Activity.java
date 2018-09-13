/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.lab2;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;

/**
 * Example activity that provides a UART loopback on the
 * specified device. All data received at the specified
 * baud rate will be transferred back out the same UART.
 */
public class Lab2Activity extends Activity {
    private Button btn;
    private Pwm mPwm;
    private Gpio mLedGpioR;
    private Gpio mLedGpioG;
    private Gpio mLedGpioB;
    private static final String TAG = "Lab2Activity";
    private String temp = "F";
    private int mLedState = 0;
    private int mExercise = 0;
    private int pwmDuty = 100;
    private int pwmDutyR = 100;
    private int pwmDutyG = 100;
    private int pwmDutyB = 100;
    private int INTERVAL_BETWEEN_BLINKS_MS = 500;
    private Handler mHandler = new Handler();
    // UART Configuration Parameters
    private static final int BAUD_RATE = 115200;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;

    private static final int CHUNK_SIZE = 512;

    private HandlerThread mInputThread;
    private Handler mInputHandler;

    private UartDevice mLoopbackDevice;

    private Runnable mTransferUartRunnable = new Runnable() {
        @Override
        public void run() {
            transferUartData();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Loopback Created");
        setContentView(R.layout.activity_loopback);
        try {
            String PWM_NAME = "PWM1";
            String R = "BCM26";
            String G = "BCM16";
            String B = "BCM6";
            Log.i(TAG, "Registering button driver " + "BCM21");
            mPwm = PeripheralManager.getInstance().openPwm(PWM_NAME);
            mLedGpioR = PeripheralManager.getInstance().openGpio(R);
            mLedGpioR.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLedGpioG = PeripheralManager.getInstance().openGpio(G);
            mLedGpioG.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLedGpioB = PeripheralManager.getInstance().openGpio(B);
            mLedGpioB.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mHandler.post(mRGBRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }



        try {
            initializePwm(mPwm);
        }
        catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }



        // Create a background looper thread for I/O
        mInputThread = new HandlerThread("InputThread");
        mInputThread.start();
        mInputHandler = new Handler(mInputThread.getLooper());

        // Attempt to access the UART device
        try {
            openUart(BoardDefaults.getUartName(), BAUD_RATE);
            // Read any initially buffered data
            mInputHandler.post(mTransferUartRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Unable to open UART device", e);
        }

        btn = findViewById(R.id.button);


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mRGBRunnable);
        Log.d(TAG, "Loopback Destroyed");

        Log.i(TAG, "Closing LED GPIO pin");
        if (mPwm != null) {
            try {
                mPwm.close();
                mPwm = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close PWM", e);
            }
        }
        try {
            mLedGpioR.close();
            mLedGpioG.close();
            mLedGpioB.close();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        } finally {
            mLedGpioR = null;
            mLedGpioG = null;
            mLedGpioB = null;
        }


        // Terminate the worker thread
        if (mInputThread != null) {
            mInputThread.quitSafely();
        }

        // Attempt to close the UART device
        try {
            closeUart();
        } catch (IOException e) {
            Log.e(TAG, "Error closing UART device:", e);
        }
    }

    /**
     * Callback invoked when UART receives new incoming data.
     */
    private UartDeviceCallback mCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            // Queue up a data transfer
            transferUartData();
            //Continue listening for more interrupts
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    /* Private Helper Methods */

    /**
     * Access and configure the requested UART device for 8N1.
     *
     * @param name Name of the UART peripheral device to open.
     * @param baudRate Data transfer rate. Should be a standard UART baud,
     *                 such as 9600, 19200, 38400, 57600, 115200, etc.
     *
     * @throws IOException if an error occurs opening the UART port.
     */
    private void openUart(String name, int baudRate) throws IOException {
        mLoopbackDevice = PeripheralManager.getInstance().openUartDevice(name);
        // Configure the UART
        mLoopbackDevice.setBaudrate(baudRate);
        mLoopbackDevice.setDataSize(DATA_BITS);
        mLoopbackDevice.setParity(UartDevice.PARITY_NONE);
        mLoopbackDevice.setStopBits(STOP_BITS);

        mLoopbackDevice.registerUartDeviceCallback(mInputHandler, mCallback);
    }

    /**
     * Close the UART device connection, if it exists
     */
    private void closeUart() throws IOException {
        if (mLoopbackDevice != null) {
            mLoopbackDevice.unregisterUartDeviceCallback(mCallback);
            try {
                mLoopbackDevice.close();
            } finally {
                mLoopbackDevice = null;
            }
        }
    }

    /**
     * Loop over the contents of the UART RX buffer, transferring each
     * one back to the TX buffer to create a loopback service.
     *
     * Potentially long-running operation. Call from a worker thread.
     */
    private void transferUartData() {
        if (mLoopbackDevice != null) {
            // Loop until there is no more data in the RX buffer.
            try {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = mLoopbackDevice.read(buffer, buffer.length)) > 0) {
                    String s = new String(buffer,"UTF-8");
                    s = s.substring(0,1);
                    Log.d("Hello",s);
                    if (s.equals("o")&&temp.equals("F")){
                        temp = "O";
//
                    }
                    if (temp.equals("O")){
                        if (s.equals("1")){
                            mExercise=1;
                        }
                        if (s.equals("2")){
                            mExercise=2;
                        }
                        if (s.equals("3")){
                            mExercise=3;
                        }
                        if (s.equals("4")){
                            mExercise=4;
                        }
                        if (s.equals("5")){
                            mExercise=5;
                        }
                    }
                    if (s.equals("f")){
                        finish();
                    }

                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to transfer data over UART", e);
            }
        }
    }
    public void initializePwm(Pwm pwm) throws IOException {
        pwm.setPwmFrequencyHz(240);
        pwm.setPwmDutyCycle(100);

        // Enable the PWM signal
        pwm.setEnabled(true);
    }

    private void redColor(){
        if (mLedGpioR == null) {
            return;
        }
        try {
            mLedGpioR.setValue(false);
            mLedGpioG.setValue(true);
            mLedGpioB.setValue(true);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }

    private void blueColor(){
        if (mLedGpioR == null) {
            return;
        }
        try {
            mLedGpioB.setValue(false);
            mLedGpioG.setValue(true);
            mLedGpioR.setValue(true);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }

    private void greenColor(){
        if (mLedGpioR == null) {
            return;
        }
        try {
            mLedGpioG.setValue(false);
            mLedGpioR.setValue(true);
            mLedGpioB.setValue(true);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }

    private void rgColor(){
        if (mLedGpioR == null || mLedGpioG == null) {
            return;
        }
        try {
            mLedGpioR.setValue(false);
            mLedGpioG.setValue(false);
            mLedGpioB.setValue(true);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }

    private void rbColor(){
        if (mLedGpioR == null || mLedGpioB == null) {
            return;
        }
        try {
            mLedGpioR.setValue(false);
            mLedGpioB.setValue(false);
            mLedGpioG.setValue(true);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }

    private void gbColor(){
        if (mLedGpioG == null || mLedGpioB == null) {
            return;
        }
        try {
            mLedGpioB.setValue(false);
            mLedGpioG.setValue(false);
            mLedGpioR.setValue(true);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }

    private void rgbColor(){
        if (mLedGpioR == null || mLedGpioG == null || mLedGpioB == null) {
            return;
        }
        try {
            mLedGpioR.setValue(false);
            mLedGpioG.setValue(false);
            mLedGpioB.setValue(false);
        } catch (IOException e) {
            Log.e("Error", "Error on PeripheralIO API", e);
        }
    }



    private Runnable mRGBRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit Runnable if the GPIO is already closed
            if (mLedGpioR == null || mLedGpioG == null || mLedGpioB == null) {
                return;
            }
            try {
                if (mExercise == 1) {
                    mPwm.setPwmDutyCycle(100);
                    switch (mLedState) {
                        case 0: {
                            redColor();
                            mLedState++;
                            break;
                        }
                        case 1: {
                            blueColor();
                            mLedState++;
                            break;
                        }
                        case 2: {
                            greenColor();
                            mLedState++;
                            break;
                        }
                        case 3: {
                            rgColor();
                            mLedState++;
                            break;
                        }
                        case 4: {
                            gbColor();
                            mLedState++;
                            break;
                        }
                        case 5: {
                            rbColor();
                            mLedState++;
                            break;
                        }
                        case 6: {
                            rgbColor();
                            mLedState = 0;
                            break;
                        }
                    }
                }else if (mExercise == 2){
                    mExercise = 1;
                    INTERVAL_BETWEEN_BLINKS_MS = 2000;
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            switch (INTERVAL_BETWEEN_BLINKS_MS){
                                case 2000:{
                                    INTERVAL_BETWEEN_BLINKS_MS = 1000;
                                    break;
                                }
                                case 1000:{
                                    INTERVAL_BETWEEN_BLINKS_MS = 500;
                                    break;
                                }
                                case 500:{
                                    INTERVAL_BETWEEN_BLINKS_MS = 100;
                                    break;
                                }
                                case 100:{
                                    INTERVAL_BETWEEN_BLINKS_MS = 2000;
                                    break;
                                }
                            }
                            Log.d(TAG, String.valueOf(INTERVAL_BETWEEN_BLINKS_MS));
                        }
                    });
                }
                else if(mExercise == 3)
                {
                    switch (pwmDuty)
                    {
                        case 100:
                        {
                            if(mLedState == 0)
                            {
                                redColor();
                            }
                            else if(mLedState == 1)
                            {
                                greenColor();
                            }
                            else if(mLedState == 2)
                            {
                                blueColor();
                            }
                            else if(mLedState == 3)
                            {
                                rgbColor();
                            }
                            pwmDuty -= 20;
                            mPwm.setPwmDutyCycle(pwmDuty);
                            break;
                        }
                        case 80:
                        {
                            if(mLedState == 0)
                            {
                                redColor();
                            }
                            else if(mLedState == 1)
                            {
                                greenColor();
                            }
                            else if(mLedState == 2)
                            {
                                blueColor();
                            }
                            else if(mLedState == 3)
                            {
                                rgbColor();
                            }
                            pwmDuty -= 20;
                            mPwm.setPwmDutyCycle(pwmDuty);
                            break;
                        }
                        case 60:
                        {
                            if(mLedState == 0)
                            {
                                redColor();
                            }
                            else if(mLedState == 1)
                            {
                                greenColor();
                            }
                            else if(mLedState == 2)
                            {
                                blueColor();
                            }
                            else if(mLedState == 3)
                            {
                                rgbColor();
                            }
                            pwmDuty -= 20;
                            mPwm.setPwmDutyCycle(pwmDuty);
                            break;
                        }
                        case 40:
                        {
                            if(mLedState == 0)
                            {
                                redColor();
                            }
                            else if(mLedState == 1)
                            {
                                greenColor();
                            }
                            else if(mLedState == 2)
                            {
                                blueColor();
                            }
                            else if(mLedState == 3)
                            {
                                rgbColor();
                            }
                            pwmDuty -= 20;
                            mPwm.setPwmDutyCycle(pwmDuty);
                            break;
                        }
                        case 20:
                        {
                            if(mLedState == 0)
                            {
                                redColor();
                            }
                            else if(mLedState == 1)
                            {
                                greenColor();
                            }
                            else if(mLedState == 2)
                            {
                                blueColor();
                            }
                            else if(mLedState == 3)
                            {
                                rgbColor();
                            }
                            pwmDuty -= 20;
                            mPwm.setPwmDutyCycle(pwmDuty);
                            pwmDuty = 100;
                            if(mLedState < 3)
                            {
                                mLedState++;
                            }
                            else
                            {
                                mLedState = 0;
                            }
                            break;
                        }
                    }
                }else if (mExercise == 4) {
                    INTERVAL_BETWEEN_BLINKS_MS = 3000;
                    mLedState = 0;
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (pwmDutyR == 70){
                                pwmDutyG = 70;
                                pwmDutyR = 100;
                                pwmDutyB = 100;
                            }
                            if (pwmDutyG == 70){
                                pwmDutyB = 70;
                                pwmDutyR = 100;
                                pwmDutyG = 100;
                            }
                            if (pwmDutyB == 70){
                                pwmDutyR = 70;
                                pwmDutyG = 100;
                                pwmDutyB = 100;
                            }
                        }
                    });
                    switch (mLedState) {
                        case 0: {
                            mPwm.setPwmDutyCycle(pwmDutyR);
                            redColor();
                            mLedState++;
                            break;
                        }
                        case 1: {
                            mPwm.setPwmDutyCycle(pwmDutyG);
                            greenColor();
                            mLedState++;
                            break;
                        }
                        case 2: {
                            mPwm.setPwmDutyCycle(pwmDutyB);
                            blueColor();
                            mLedState=0;
                            break;
                        }
                    }
                }
                else if(mExercise == 5)
                {

                    switch (INTERVAL_BETWEEN_BLINKS_MS)
                    {
                        case 500:
                        {
                            mLedGpioR.setValue(false);
                            redColor();
                            mLedGpioR.setValue(true);
                            INTERVAL_BETWEEN_BLINKS_MS = 2000;
                            break;

                        }
                        case 2000:
                        {
                            mLedGpioG.setValue(false);
                            greenColor();
                            mLedGpioG.setValue(true);
                            INTERVAL_BETWEEN_BLINKS_MS = 3000;
                            break;
                        }
                        case 3000:
                        {
                            mLedGpioB.setValue(false);
                            blueColor();
                            mLedGpioB.setValue(true);
                            INTERVAL_BETWEEN_BLINKS_MS = 500;
                            break;
                        }
                    }
                }
                mHandler.postDelayed(mRGBRunnable, INTERVAL_BETWEEN_BLINKS_MS);
            } catch (Exception e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };
}
