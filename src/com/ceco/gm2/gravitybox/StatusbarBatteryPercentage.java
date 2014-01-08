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

import com.ceco.gm2.gravitybox.BatteryInfoManager.BatteryData;
import com.ceco.gm2.gravitybox.BatteryInfoManager.BatteryStatusListener;
import com.ceco.gm2.gravitybox.StatusBarIconManager.ColorInfo;
import com.ceco.gm2.gravitybox.StatusBarIconManager.IconManagerListener;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.TextView;

public class StatusbarBatteryPercentage implements IconManagerListener, BatteryStatusListener {
    private TextView mPercentage;
    private int mDefaultColor;
    private int mIconColor;
    private String mPercentSign;
    private BatteryData mBatteryData;
    private ValueAnimator mChargeAnim;
    private boolean mChargeAnimEnabled;

    public StatusbarBatteryPercentage(TextView clockView) {
        mPercentage = clockView;
        mDefaultColor = mIconColor = mPercentage.getCurrentTextColor();
        mPercentSign = "";
    }

    private boolean startChargingAnimation() {
        if (mChargeAnim == null || !mChargeAnim.isRunning()) {
            mChargeAnim = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mIconColor, Color.GREEN);

            mChargeAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    mPercentage.setTextColor((Integer)va.getAnimatedValue());
                }
            });
            mChargeAnim.addListener(new AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mPercentage.setTextColor(mIconColor);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mPercentage.setTextColor(mIconColor);
                }

                @Override
                public void onAnimationRepeat(Animator animation) { }

                @Override
                public void onAnimationStart(Animator animation) { }
            });

            mChargeAnim.setDuration(1000);
            mChargeAnim.setRepeatMode(ValueAnimator.REVERSE);
            mChargeAnim.setRepeatCount(ValueAnimator.INFINITE);
            mChargeAnim.start();
            return true;
        }
        return false;
    }

    private boolean stopChargingAnimation() {
        if (mChargeAnim != null && mChargeAnim.isRunning()) {
            mChargeAnim.end();
            mChargeAnim.removeAllUpdateListeners();
            mChargeAnim.removeAllListeners();
            mChargeAnim = null;
            return true;
        }
        return false;
    }

    public TextView getView() {
        return mPercentage;
    }

    public void setTextColor(int color) {
        mIconColor = color;
        final boolean animWasRunning = stopChargingAnimation();
        mPercentage.setTextColor(mIconColor);
        if (animWasRunning) {
            startChargingAnimation();
        }
    }

    public void setTextSize(int size) {
        mPercentage.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    }

    public void setPercentSign(String percentSign) {
        mPercentSign = percentSign;
        update();
    }

    public void setChargeAnimEnabled(boolean enabled) {
        mChargeAnimEnabled = enabled;
        update();
    }

    public void update() {
        if (mBatteryData == null) return;

        mPercentage.setText(mBatteryData.level + mPercentSign);

        if (mChargeAnimEnabled && mBatteryData.charging && mBatteryData.level < 100) {
            startChargingAnimation();
        } else {
            stopChargingAnimation();
        }
    }

    public void setVisibility(int visibility) {
        mPercentage.setVisibility(visibility);
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            if (colorInfo.coloringEnabled) {
                setTextColor(colorInfo.iconColor[0]);
            } else {
                if (colorInfo.followStockBatteryColor && colorInfo.stockBatteryColor != null) {
                    setTextColor(colorInfo.stockBatteryColor);
                } else {
                    setTextColor(mDefaultColor);
                }
            }
        } else if ((flags & StatusBarIconManager.FLAG_LOW_PROFILE_CHANGED) != 0) {
            mPercentage.setAlpha(colorInfo.lowProfile ? 0.5f : 1);
        }
    }

    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        mBatteryData = batteryData;
        update();
    }
}
