/*
* Copyright (C) 2013 Peter Gregus for GravityBox Project (C3C076@xda)
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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ConnectivityServiceWrapper {
    private static final String TAG = "GB:ConnectivityServiceWrapper";
    private static final boolean DEBUG = false;

    private static final String CLASS_CONNECTIVITY_SERVICE = "com.android.server.ConnectivityService";

    public static final String ACTION_SET_MOBILE_DATA_ENABLED = 
            "gravitybox.intent.action.SET_MOBILE_DATA_ENABLED";
    public static final String ACTION_XPERIA_MOBILE_DATA_TOGGLE =
            "com.android.phone.intent.ACTION_DATA_TRAFFIC_SWITCH";
    public static final String ACTION_TOGGLE_MOBILE_DATA = 
            "gravitybox.intent.action.TOGGLE_MOBILE_DATA";
    public static final String ACTION_TOGGLE_WIFI = "gravitybox.intent.action.TOGGLE_WIFI";
    public static final String ACTION_TOGGLE_BLUETOOTH = "gravitybox.intent.action.TOGGLE_BLUETOOTH";
    public static final String ACTION_TOGGLE_WIFI_AP = "gravitybox.intent.action.TOGGLE_WIFI_AP";
    public static final String ACTION_TOGGLE_NFC = "gravitybox.intent.action.TOGGLE_NFC";
    public static final String ACTION_TOGGLE_GPS = "gravitybox.intent.action.TOGGLE_GPS";
    public static final String EXTRA_ENABLED = "enabled";

    private static final int NFC_STATE_OFF = 1;
    private static final int NFC_STATE_TURNING_ON = 2;
    private static final int NFC_STATE_ON = 3;
    private static final int NFC_STATE_TURNING_OFF = 4;

    private static Context mContext;
    private static Object mConnectivityService;
    private static WifiManagerWrapper mWifiManager;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());

            if (intent.getAction().equals(ACTION_SET_MOBILE_DATA_ENABLED)) {
                final boolean enabled = intent.getBooleanExtra(EXTRA_ENABLED, false);
                setMobileDataEnabled(enabled);
            } else if (intent.getAction().equals(ACTION_TOGGLE_MOBILE_DATA)) {
                toggleMobileData();
            } else if (intent.getAction().equals(ACTION_TOGGLE_WIFI)) {
                toggleWiFi();
            } else if (intent.getAction().equals(ACTION_TOGGLE_BLUETOOTH)) {
                toggleBluetooth();
            } else if (intent.getAction().equals(ACTION_TOGGLE_WIFI_AP)) {
                toggleWiFiAp();
            } else if (intent.getAction().equals(ACTION_TOGGLE_NFC)) {
                toggleNfc();
            } else if (intent.getAction().equals(ACTION_TOGGLE_GPS)) {
                toggleGps();
            }
        }
    };

    public static void initZygote() {
        try {
            final Class<?> connServiceClass = 
                    XposedHelpers.findClass(CLASS_CONNECTIVITY_SERVICE, null);

            XposedBridge.hookAllConstructors(connServiceClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (DEBUG) log("ConnectivityService constructed.");
                    mConnectivityService = param.thisObject;

                    Context context = (Context) XposedHelpers.getObjectField(
                            param.thisObject, "mContext");
                    if (context == null && param.args.length != 0) {
                        context = (Context) param.args[0];
                    }
                    
                    if (context != null) {
                        mContext = context;
                        mWifiManager = new WifiManagerWrapper(context);

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(ACTION_SET_MOBILE_DATA_ENABLED);
                        intentFilter.addAction(ACTION_TOGGLE_MOBILE_DATA);
                        intentFilter.addAction(ACTION_TOGGLE_WIFI);
                        intentFilter.addAction(ACTION_TOGGLE_BLUETOOTH);
                        intentFilter.addAction(ACTION_TOGGLE_WIFI_AP);
                        intentFilter.addAction(ACTION_TOGGLE_NFC);
                        intentFilter.addAction(ACTION_TOGGLE_GPS);
                        context.registerReceiver(mBroadcastReceiver, intentFilter);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setMobileDataEnabled(boolean enabled) {
        if (mConnectivityService == null) return;
        try {
            XposedHelpers.callMethod(mConnectivityService, "setMobileDataEnabled", enabled);
            if (DEBUG) log("setMobileDataEnabled called");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleMobileData() {
        if (mConnectivityService == null) return;
        try {
            final boolean enabled = 
                    (Boolean) XposedHelpers.callMethod(mConnectivityService, "getMobileDataEnabled");
            setMobileDataEnabled(!enabled);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleWiFi() {
        if (mWifiManager == null) return;
        try {
            mWifiManager.toggleWifiEnabled();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleWiFiAp() {
        if (mWifiManager == null) return;
        try {
            mWifiManager.toggleWifiApEnabled();
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleBluetooth() {
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            if (btAdapter.isEnabled()) {
                btAdapter.disable();
            } else {
                btAdapter.enable();
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleNfc() {
        if (mContext == null) return;
        try {
            NfcAdapter adapter = (NfcAdapter) XposedHelpers.callStaticMethod(
                    NfcAdapter.class, "getNfcAdapter", mContext);
            if (adapter != null) {
                int nfcState = (Integer) XposedHelpers.callMethod(adapter, "getAdapterState");
                switch (nfcState) {
                    case NFC_STATE_TURNING_ON:
                    case NFC_STATE_ON:
                        XposedHelpers.callMethod(adapter, "disable");
                        break;
                    case NFC_STATE_TURNING_OFF:
                    case NFC_STATE_OFF:
                        XposedHelpers.callMethod(adapter, "enable");
                        break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void toggleGps() {
        if (mContext == null) return;
        try {
            boolean mGpsEnabled = Settings.Secure.isLocationProviderEnabled(
                    mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
            Settings.Secure.setLocationProviderEnabled(
                    mContext.getContentResolver(), LocationManager.GPS_PROVIDER, !mGpsEnabled);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
