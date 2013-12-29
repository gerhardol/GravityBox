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

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

public class StayAwakeTile extends BasicTile {
    private static final String TAG = "GB:StayAwakeTile";
    private static final boolean DEBUG = false;

    private static final int NEVER_SLEEP = Integer.MAX_VALUE;
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;
    public static final String SETTING_USER_TIMEOUT = "gb_stay_awake_tile_user_timeout";

    private SettingsObserver mSettingsObserver;
    private int mCurrentTimeout;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public StayAwakeTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStayAwake();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(android.provider.Settings.ACTION_DISPLAY_SETTINGS);
                return true;
            }
        };

        mCurrentTimeout = Settings.System.getInt(mContext.getContentResolver(), 
                Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
        if (DEBUG) log("mCurrentTimeout = " + mCurrentTimeout);
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_stay_awake;
    }

    @Override
    protected void onTilePostCreate() {
        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();

        super.onTilePostCreate();
    }

    @Override
    protected synchronized void updateTile() {
        if (mCurrentTimeout == NEVER_SLEEP) {
            mLabel = mGbContext.getString(R.string.quick_settings_stay_awake_on);
            mDrawableId = R.drawable.ic_qs_stayawake_on;
        } else {
            mLabel = mGbContext.getString(R.string.quick_settings_stay_awake_off);
            mDrawableId = R.drawable.ic_qs_stayawake_off;
        }

        if (mTileStyle == KITKAT) {
            mDrawable = mGbResources.getDrawable(mDrawableId).mutate();
            mDrawable.setColorFilter(mCurrentTimeout == NEVER_SLEEP ? 
                    KK_COLOR_ON : KK_COLOR_OFF, PorterDuff.Mode.SRC_ATOP);
        }

        super.updateTile();
    }

    private void toggleStayAwake() {
        ContentResolver cr = mContext.getContentResolver();
        if (mCurrentTimeout == NEVER_SLEEP) {
            if (DEBUG) log("disabling never sleep");
            Settings.System.putInt(cr,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    Settings.System.getInt(cr, SETTING_USER_TIMEOUT, 
                            FALLBACK_SCREEN_TIMEOUT_VALUE));
        } else {
            if (DEBUG) log("enabling never sleep");
            Settings.System.putInt(cr,
                    SETTING_USER_TIMEOUT, mCurrentTimeout);
            Settings.System.putInt(cr,
                    Settings.System.SCREEN_OFF_TIMEOUT, NEVER_SLEEP);
        }
    }

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @SuppressLint("NewApi")
        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT), 
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mCurrentTimeout = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, FALLBACK_SCREEN_TIMEOUT_VALUE);
            if (DEBUG) log("SettingsObserver onChange; mCurrentTimeout = " + mCurrentTimeout);
            updateResources();
        }
    }
}