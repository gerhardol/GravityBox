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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.View;

public class GpsTile extends BasicTile {
    private static final String TAG = "GB:GpsTile";
    private static final boolean DEBUG = false;

    public static final String GPS_ENABLED_CHANGE_ACTION = "android.location.GPS_ENABLED_CHANGE";
    public static final String GPS_FIX_CHANGE_ACTION = "android.location.GPS_FIX_CHANGE";
    public static final String EXTRA_GPS_ENABLED = "enabled";

    private boolean mGpsEnabled;
    private boolean mGpsFixed;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private BroadcastReceiver mLocationManagerReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            final String action = intent.getAction();

            if (action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                mGpsEnabled = Settings.Secure.isLocationProviderEnabled(
                        mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
                mGpsFixed = false;
            } else if (action.equals(GPS_FIX_CHANGE_ACTION)) {
                mGpsFixed = intent.getBooleanExtra(EXTRA_GPS_ENABLED, false);
            } else if (action.equals(GPS_ENABLED_CHANGE_ACTION)) {
                mGpsFixed = false;
            }

            if (DEBUG) log("mGpsEnabled = " + mGpsEnabled + "; mGpsFixed = " + mGpsFixed);
            updateResources();
        }
    };

    public GpsTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mOnClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.Secure.setLocationProviderEnabled(
                        mContext.getContentResolver(), LocationManager.GPS_PROVIDER, !mGpsEnabled);
            }
        };

        mOnLongClick = new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };

        mGpsEnabled = Settings.Secure.isLocationProviderEnabled(
                mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
        mGpsFixed = false;
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_gps;
    }

    @Override
    protected void onTilePostCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        intentFilter.addAction(GPS_ENABLED_CHANGE_ACTION);
        intentFilter.addAction(GPS_FIX_CHANGE_ACTION);
        mContext.registerReceiver(mLocationManagerReceiver, intentFilter);

        super.onTilePostCreate();
    }

    @Override
    protected synchronized void updateTile() {
        if (mGpsEnabled) {
            mLabel = mGpsFixed ? mGbContext.getString(R.string.qs_tile_gps_locked) :
                    mGbContext.getString(R.string.qs_tile_gps_enabled);
            mDrawableId = mGpsFixed ? R.drawable.ic_qs_gps_locked :
                    R.drawable.ic_qs_gps_enable;
            mTileColor = KK_COLOR_ON;
        } else {
            mLabel = mGbContext.getString(R.string.qs_tile_gps_disabled);
            mDrawableId = R.drawable.ic_qs_gps_disable;
            mTileColor = KK_COLOR_OFF;
        }

        super.updateTile();
    }
}
