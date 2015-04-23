/*
 * Copyright (C) 2015 Michael Serpieri (mickybart@xda)
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

import java.io.File;

/**
 * Enum representing all available wake gestures
 */
public enum WakeGesture {
    SWEEP_RIGHT(1),
    SWEEP_LEFT(2),
    SWEEP_UP(4),
    SWEEP_DOWN(8),
    DOUBLETAP(16),
    UNKNOWN(0);

    private int mBitValue;

    private static final String CONFIG_PATH_WG = "/sys/android_touch/wake_gestures";
    private static final String CONFIG_PATH_SWEEP = "/sys/android_touch/sweep2wake";
    private static final String CONFIG_PATH_DT = "/sys/android_touch/doubletap2wake";

    WakeGesture(int bitValue) {
        mBitValue = bitValue;
    }

    public static WakeGesture createFromId(int id) {
        switch (id) {
            case 1: return SWEEP_RIGHT;
            case 2: return SWEEP_LEFT;
            case 3: return SWEEP_UP;
            case 4: return SWEEP_DOWN;
            case 5: return DOUBLETAP;
            default: return UNKNOWN;
        }
    }

    public boolean isEnabled() {
        if (mBitValue == 0) return false;

        try {
            File f = new File(mBitValue == 16 ? CONFIG_PATH_DT : CONFIG_PATH_SWEEP);
            if (!f.exists()) return false;
            int value = Utils.readFileSingleLineAsInt(f);
            return (mBitValue == 16 ? value != 0 :
                    (value & mBitValue) == mBitValue);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Checks if device supports wake gestures
     * @return true if device supports wake gestures
     */
    public static boolean supportsWakeGestures() {
        try {
            File f = new File(CONFIG_PATH_WG);
            if (!f.exists()) return false;
            int value = Utils.readFileSingleLineAsInt(f);
            return (value == 1);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
};
