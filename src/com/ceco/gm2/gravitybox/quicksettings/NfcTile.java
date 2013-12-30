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

package com.ceco.gm2.gravitybox.quicksettings;

import com.ceco.gm2.gravitybox.R;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

public class NfcTile extends BasicTile {
    private static final String TAG = "GB:NfcTile";
    private static final boolean DEBUG = false;

    private static NfcAdapter mNfcAdapter;
    private static final int NFC_ADAPTER_UNKNOWN = -100;
    private static final int STATE_OFF = 1;
    private static final int STATE_TURNING_ON = 2;
    private static final int STATE_ON = 3;
    private static final int STATE_TURNING_OFF = 4;
    private static final String ACTION_ADAPTER_STATE_CHANGED = 
            "android.nfc.action.ADAPTER_STATE_CHANGED";

    private int mNfcState = NFC_ADAPTER_UNKNOWN;
    private BroadcastReceiver mStateChangeReceiver;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public NfcTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                updateResources();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(Settings.ACTION_NFC_SETTINGS);
                return true;
            }
        };

        // get state with retry flag set as NFC Adapter needs some time for initialization
        getNfcState(true);
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_nfc;
    }

    @Override
    protected void onTilePostCreate() {
        mStateChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                getNfcState();
                updateResources();
            }
        };
        IntentFilter intentFilter = new IntentFilter(ACTION_ADAPTER_STATE_CHANGED);
        mContext.registerReceiver(mStateChangeReceiver, intentFilter);

        super.onTilePostCreate();
    }

    @Override
    protected synchronized void updateTile() {
        switch (mNfcState) {
            case STATE_TURNING_ON:
            case STATE_ON:
                mDrawableId = R.drawable.ic_qs_nfc_on;
                mLabel = mGbContext.getString(R.string.quick_settings_nfc_on);
                mTileColor = KK_COLOR_ON;
                break;
            case STATE_TURNING_OFF:
            case STATE_OFF:
            default:
                mDrawableId = R.drawable.ic_qs_nfc_off;
                mLabel = mGbContext.getString(R.string.quick_settings_nfc_off);
                mTileColor = KK_COLOR_OFF;
                break;
        }

        super.updateTile();
    }

    protected void toggleState() {
        getNfcState();
        switch (mNfcState) {
            case STATE_TURNING_ON:
            case STATE_ON:
                try {
                    XposedHelpers.callMethod(mNfcAdapter, "disable");
                } catch (Throwable t) {
                    log("Error calling disable() on NFC adapter: " + t.getMessage());
                }
                break;
            case STATE_TURNING_OFF:
            case STATE_OFF:
                try {
                    XposedHelpers.callMethod(mNfcAdapter, "enable");
                } catch (Throwable t) {
                    log("Error calling enable() on NFC adapter: " + t.getMessage());
                }
                break;
        }
    }

    private void getNfcState() {
        getNfcState(false);
    }

    private void getNfcState(boolean retry) {
        try {
            if (mNfcAdapter == null) {
                mNfcAdapter = (NfcAdapter) XposedHelpers.callStaticMethod(
                        NfcAdapter.class, "getNfcAdapter", mContext);
            }
            mNfcState = (Integer) XposedHelpers.callMethod(mNfcAdapter, "getAdapterState");
        } catch (Throwable t) {
            mNfcState = NFC_ADAPTER_UNKNOWN;
            // if attempt failed and retry flag set, we will ask again after 6 seconds
            if (retry) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (DEBUG) log("Retrying to get NfcAdapter state");
                        getNfcState();
                        updateResources();
                    }
                }, 6000);
            } else {
                log("Error getting state of NfcAdapter: " + t.getMessage());
            }
        }
        if (DEBUG) log("getNfcState: mNfcState = " + mNfcState);
    }
}
