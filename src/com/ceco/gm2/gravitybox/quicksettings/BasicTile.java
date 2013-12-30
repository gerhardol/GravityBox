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

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.TextView;

// Abstract Basic Tile definition
// Tile layout should consist of only 1 View element: TextView
public abstract class BasicTile extends AQuickSettingsTile {

    protected TextView mTextView;
    protected int mDrawableId;
    protected Drawable mDrawable;
    protected String mLabel;
    protected int mTileColor;

    public BasicTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);
    }

    // each basic tile clone must provide its own layout identified by unique ID
    protected abstract int onGetLayoutId();

    // basic tile clone can override this to supply custom TextView ID
    protected int onGetTextViewId() {
        return R.id.text;
    }

    @Override
    protected void onTileCreate() {
        LayoutInflater inflater = LayoutInflater.from(mGbContext);
        inflater.inflate(onGetLayoutId(), mTile);
        mTextView = (TextView) mTile.findViewById(onGetTextViewId());
    }

    @Override
    protected void updateTile() {
        mTextView.setText(mLabel);
        if (mTileStyle == KITKAT) {
            mDrawable = mGbResources.getDrawable(mDrawableId).mutate();
            mDrawable.setColorFilter(mTileColor, PorterDuff.Mode.SRC_ATOP);
            mTextView.setCompoundDrawablesWithIntrinsicBounds(null, mDrawable, null, null);
        } else {
            mTextView.setCompoundDrawablesWithIntrinsicBounds(0, mDrawableId, 0, 0);
        }
    }
}