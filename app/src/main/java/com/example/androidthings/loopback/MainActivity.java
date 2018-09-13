package com.example.androidthings.loopback;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Pwm;
import com.google.android.things.userdriver.pio.GpioDriver;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Pwm mPwm;
    private Gpio mLedGpioR;
    private Gpio mLedGpioG;
    private Gpio mLedGpioB;
    private Gpio mBttn;

    //    private Button btn1;
//    private Button btn2;
//    private Button btn3;
    private int mLedState = 0;
    private int mExercise = 0;
    private int pwmDuty = 100;
    private boolean mIniEx5 = true;
    private boolean mIniEx2 = true;
    private int INTERVAL_BETWEEN_BLINKS_MS = 500;
    private Handler mHandler = new Handler();
    private GpioCallback gpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            try{
                if (gpio.getValue()) {
                    Log.d(TAG,"hello");
                } else {
                    // Pin is LOW
                    Log.d(TAG,"hi");
                }
            }
            catch (IOException e){
                Log.d(TAG,"error");
            }
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.w(TAG, gpio + ": Error event " + error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting MainActivity");
        try {
            String PWM_NAME = "PWM1";
            String R = "BCM26";
            String G = "BCM16";
            String B = "BCM6";
            Log.i(TAG, "Registering button driver " + "BCM21");
            mPwm = PeripheralManager.getInstance().openPwm(PWM_NAME);

            mBttn = PeripheralManager.getInstance().openGpio("BCM21");
            mBttn.setDirection(Gpio.DIRECTION_IN);
            mBttn.setEdgeTriggerType(Gpio.EDGE_BOTH);
            mBttn.setActiveType(Gpio.ACTIVE_HIGH);
            mBttn.registerGpioCallback(gpioCallback);

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
//        btn1 = findViewById(R.id.button1);
//        btn2 = findViewById(R.id.button2);
//        btn3 = findViewById(R.id.button3);
//        btn1.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mIniEx5 = true;
//                mExercise = 0;
//                if(mIniEx2)
//                {
//                    INTERVAL_BETWEEN_BLINKS_MS = 2000;
//                    mIniEx2 = false;
//                }
//                switch (INTERVAL_BETWEEN_BLINKS_MS){
//                    case 2000:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 1000;
//                        break;
//                    }
//                    case 1000:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 500;
//                        break;
//                    }
//                    case 500:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 100;
//                        break;
//                    }
//                    case 100:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 2000;
//                        break;
//                    }
//                }
//                Log.d(TAG, String.valueOf(INTERVAL_BETWEEN_BLINKS_MS));
//            }
//        });
//        btn2.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mExercise = 1;
//                mIniEx5 = true;
//                mIniEx2 = true;
//            }
//        });
//        btn3.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                mIniEx2 = true;
//                mExercise = 2;
//                if(mIniEx5)
//                {
//                    INTERVAL_BETWEEN_BLINKS_MS = 3000;
//                    mIniEx5 = false;
//                }
//                switch (INTERVAL_BETWEEN_BLINKS_MS){
//                    case 3000:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 500;
//                        break;
//                    }
//                    case 500:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 2000;
//                        break;
//                    }
//                    case 2000:{
//                        INTERVAL_BETWEEN_BLINKS_MS = 3000;
//                        break;
//                    }
//                }
//                Log.d(TAG, String.valueOf(INTERVAL_BETWEEN_BLINKS_MS));
//            }
//        });
        try {
            initializePwm(mPwm);
        }
        catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove pending blink Runnable from the handler.
        mHandler.removeCallbacks(mRGBRunnable);
        if (mBttn != null) {
            mBttn.unregisterGpioCallback(gpioCallback);
            try {
                mBttn.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

        // Close the Gpio pin.
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
                if(mExercise == 0)
                {
                    switch (mLedState){
                        case 0:{
                            redColor();
                            mLedState ++;
                            break;
                        }
                        case 1:{
                            blueColor();
                            mLedState ++;
                            break;
                        }
                        case 2:{
                            greenColor();
                            mLedState ++;
                            break;
                        }
                        case 3:{
                            rgColor();
                            mLedState ++;
                            break;
                        }
                        case 4:{
                            gbColor();
                            mLedState ++;
                            break;
                        }
                        case 5:{
                            rbColor();
                            mLedState ++;
                            break;
                        }
                        case 6:{
                            rgbColor();
                            mLedState = 0;
                            break;
                        }
                    }
                }
                else if(mExercise == 1)
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
                }
                else if(mExercise == 2)
                {
                    switch (INTERVAL_BETWEEN_BLINKS_MS)
                    {
                        case 500:
                        {
                            mLedGpioR.setValue(false);
                            redColor();
                            mLedGpioR.setValue(true);
                            break;
                        }
                        case 2000:
                        {
                            mLedGpioG.setValue(false);
                            greenColor();
                            mLedGpioG.setValue(true);
                            break;
                        }
                        case 3000:
                        {
                            mLedGpioB.setValue(false);
                            blueColor();
                            mLedGpioB.setValue(true);
                            break;
                        }
                    }
                }
//                Log.e(TAG, "Run");
                mHandler.postDelayed(mRGBRunnable, INTERVAL_BETWEEN_BLINKS_MS);
            } catch (Exception e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };
}

