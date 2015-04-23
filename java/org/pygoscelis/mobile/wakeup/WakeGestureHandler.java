/*
 * Copyright (C) 2015 Michael Serpieri (mickybart@xda)
 * Copyright (C) 2014 Peter Gregus (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pygoscelis.mobile.wakeup;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

import org.pygoscelis.mobile.wakeup.preference.AppPickerPreference;

public class WakeGestureHandler implements IWakeGestureListener {
    private static final String TAG = "WakeGestureHandler";

    private Context mContext;
    private SharedPreferences mPrefs;
    private WakeGestureProcessor mWgp;
    private Map<WakeGesture, Intent> mWakeGestures;
    private PowerManager mPm;
    private WakeLock mWakeLock;
    private SensorManager mSensorManager;
    private Sensor mProxSensor;

    public WakeGestureHandler(Context context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        initWakeGestures();
        initWakeGestureProcessor();
    }

    private void initWakeGestureProcessor() {
        mWgp = WakeGestureProcessor.getInstance();
        mWgp.registerWakeGestureListener(this);
        mWgp.startProcessing();
    }

    private void initWakeGestures() {
        mWakeGestures = new HashMap<WakeGesture, Intent>(5);
        mWakeGestures.put(WakeGesture.SWEEP_RIGHT, intentFromUri(mPrefs.getString(
                WakeGestureSettings.PREF_KEY_WG_SWEEP_RIGHT, null)));
        mWakeGestures.put(WakeGesture.SWEEP_LEFT, intentFromUri(mPrefs.getString(
                WakeGestureSettings.PREF_KEY_WG_SWEEP_LEFT, null)));
        mWakeGestures.put(WakeGesture.SWEEP_UP, intentFromUri(mPrefs.getString(
                WakeGestureSettings.PREF_KEY_WG_SWEEP_UP, null)));
        mWakeGestures.put(WakeGesture.SWEEP_DOWN, intentFromUri(mPrefs.getString(
                WakeGestureSettings.PREF_KEY_WG_SWEEP_DOWN, null)));
        mWakeGestures.put(WakeGesture.DOUBLETAP, intentFromUri(mPrefs.getString(
                WakeGestureSettings.PREF_KEY_WG_DOUBLETAP, null)));

        setPocketModeEnabled(mPrefs.getBoolean(WakeGestureSettings.PREF_KEY_POCKET_MODE, false));

        IntentFilter intentFilter = new IntentFilter(WakeGestureSettings.ACTION_WAKE_GESTURE_CHANGED);
        intentFilter.addAction(WakeGestureSettings.ACTION_SETTINGS_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    private void setPocketModeEnabled(boolean enabled) {
        if (enabled) {
            mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        } else {
            mProxSensor = null;
            mSensorManager = null;
        }
    }

    private Intent intentFromUri(String uri) {
        if (uri == null) return null;

        try {
            Intent intent = Intent.parseUri(uri, 0);
            return intent;
        } catch (URISyntaxException e) {
            Log.d(TAG,"Error parsing uri: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onWakeGesture(final WakeGesture gesture) {

        if (mSensorManager != null && mProxSensor != null) {
            mSensorManager.registerListener(new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    try {
                        final boolean screenCovered = 
                                event.values[0] < (mProxSensor.getMaximumRange() * 0.1f);
                        Log.d(TAG, "mProxSensorEventListener: " + event.values[0] +
                                "; screenCovered=" + screenCovered);
                        if (!screenCovered) {
                            processGesture(gesture);
                        }
                    } catch (Throwable t) {
                        Log.d(TAG, t.getMessage());
                    } finally {
                        try { 
                            mSensorManager.unregisterListener(this, mProxSensor); 
                        } catch (Throwable t) {
                            // should never happen
                        }
                    }
                }
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) { }
            }, mProxSensor, SensorManager.SENSOR_DELAY_FASTEST);
        } else {
            processGesture(gesture);
        }
    }

    private void processGesture(WakeGesture gesture) {
        handleIntent(mWakeGestures.get(gesture));
    }

    @Override
    public void onProcessingException(Exception e) {
        Log.d(TAG,"onProcessingException: " + e.getMessage());
    }

    @SuppressWarnings("deprecation")
    private void handleIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("mode")) return;

        boolean keepScreenOff = intent.getBooleanExtra(AppPickerPreference.EXTRA_KEEP_SCREEN_OFF, false);
        mWakeLock = mPm.newWakeLock(keepScreenOff ? PowerManager.PARTIAL_WAKE_LOCK : 
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                TAG);
        mWakeLock.acquire();

        int mode = intent.getIntExtra("mode", AppPickerPreference.MODE_APP);
        if (mode == AppPickerPreference.MODE_APP || mode == AppPickerPreference.MODE_SHORTCUT) {
            startActivity(intent);
        } else if (mode == AppPickerPreference.MODE_ACTION) {
            executeAction(intent);
        }

        mWakeLock.release();
        mWakeLock = null;
    }

    private void startActivity(Intent intent) {
        mContext.startActivity(intent);
    }

    private void executeAction(Intent intent) {
        String action = intent.getAction();
        if (action.equals(AppPickerPreference.ACTION_TOGGLE_TORCH)) {
            toggleTorch();
        } else if (action.equals(AppPickerPreference.ACTION_MEDIA_CONTROL)) {
            if (isMusicActive()) {
                sendMediaButtonEvent(intent.getIntExtra(AppPickerPreference.EXTRA_MC_KEYCODE, 0));
            }
        } else if (action.equals(AppPickerPreference.ACTION_SCREEN_ON)) {
            // do nothing as wake lock already did it for us
        }
    }

    private void toggleTorch() {
        try {
            Intent intent = new Intent(mContext, TorchService.class);
            intent.setAction(TorchService.ACTION_TOGGLE_TORCH);
            mContext.startService(intent);
        } catch (Throwable t) {
            Log.d(TAG,"Error toggling Torch: " + t.getMessage());
        }
    }

    private void sendMediaButtonEvent(int code) {
        Intent keyIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, code));
        mContext.sendOrderedBroadcast(keyIntent, null);

        keyIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, code));
        mContext.sendOrderedBroadcast(keyIntent, null);
    }

    private boolean isMusicActive() {
        final AudioManager am = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            Log.w(TAG, "isMusicActive: couldn't get AudioManager reference");
            return false;
        }
        return am.isMusicActive();
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WakeGestureSettings.ACTION_WAKE_GESTURE_CHANGED) &&
                    intent.hasExtra(WakeGestureSettings.EXTRA_WAKE_GESTURE)) {
                try {
                    WakeGesture wg = WakeGesture.valueOf(intent.getStringExtra(
                            WakeGestureSettings.EXTRA_WAKE_GESTURE));
                    if (wg != null) {
                        String uri = intent.getStringExtra(WakeGestureSettings.EXTRA_INTENT_URI);
                        mWakeGestures.put(wg, intentFromUri(uri));
                    }
                } catch (Exception e) { 
                    Log.d(TAG,"ACTION_WAKE_GESTURE_CHANGED error: " + e.getMessage());
                }
            } else if (action.equals(WakeGestureSettings.ACTION_SETTINGS_CHANGED)) {
                if (intent.hasExtra(WakeGestureSettings.EXTRA_POCKET_MODE)) {
                    setPocketModeEnabled(intent.getBooleanExtra(WakeGestureSettings.EXTRA_POCKET_MODE, false));
                }
            }
        }
    };
}
