/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.gm2.gravitybox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModSmartRadio {
    private static final String TAG = "GB:SmartRadio";
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static Context mContext;
    private static Handler mHandler;
    private static int mOnDataEnabledMode;
    private static int mOnDataDisabledMode;
    private static int mPreviousState;
    private static boolean mBlockStateChange;

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_SMART_RADIO_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_ON_DATA_ENABLED)) {
                    mOnDataEnabledMode = intent.getIntExtra(GravityBoxSettings.EXTRA_SR_ON_DATA_ENABLED, -1);
                    if (DEBUG) log("mOnDataEnabledMode = " + mOnDataEnabledMode);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_SR_ON_DATA_DISABLED)) {
                    mOnDataDisabledMode = intent.getIntExtra(GravityBoxSettings.EXTRA_SR_ON_DATA_DISABLED, -1);
                    if (DEBUG) log("mOnDataDisabledMode = " + mOnDataDisabledMode);
                }
            }
        }
    };

    private static Runnable mUnblockStateChange = new Runnable() {
        @Override
        public void run() {
            mBlockStateChange = false;
        }
    };

    private static PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            if (DEBUG) log("onDataConnectionStateChanged; state=" + state);

            if (state == mPreviousState) {
                if (DEBUG) log("state == previousState: ignoring");
                return;
            }

            if (mBlockStateChange) {
                if (DEBUG) log("state change blocked: ignoring");
                return;
            }

            Intent intent = null;
            if (state == TelephonyManager.DATA_CONNECTED && mOnDataEnabledMode != -1) {
                intent = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
                intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mOnDataEnabledMode);
            } else if (state == TelephonyManager.DATA_DISCONNECTED && mOnDataDisabledMode != -1) {
                intent = new Intent(PhoneWrapper.ACTION_CHANGE_NETWORK_TYPE);
                intent.putExtra(PhoneWrapper.EXTRA_NETWORK_TYPE, mOnDataDisabledMode);
            }
            if (intent != null) {
                mContext.sendBroadcast(intent);
            }

            mBlockStateChange = true;
            mHandler.postDelayed(mUnblockStateChange, 3000);
            mPreviousState = state;
        }
    };

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> classSystemUIService = XposedHelpers.findClass(
                    "com.android.systemui.SystemUIService", classLoader);

            mOnDataEnabledMode = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_ON_DATA_ENABLED, -1);
            mOnDataDisabledMode = prefs.getInt(GravityBoxSettings.PREF_KEY_SMART_RADIO_ON_DATA_DISABLED, -1);

            XposedHelpers.findAndHookMethod(classSystemUIService, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mContext = (Context) param.thisObject;
                    if (mContext != null) {
                        if (DEBUG) log("SystemUIService created. Registering phone state listener");
                        mHandler = new Handler();
                        TelephonyManager phone = 
                                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                        phone.listen(mPhoneStateListener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE);

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_SMART_RADIO_CHANGED);
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
