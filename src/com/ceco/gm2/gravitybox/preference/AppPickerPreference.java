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

package com.ceco.gm2.gravitybox.preference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.ceco.gm2.gravitybox.GravityBoxSettings.PrefsFragment;
import com.ceco.gm2.gravitybox.GravityBoxSettings.PrefsFragment.ShortcutHandler;
import com.ceco.gm2.gravitybox.R;
import com.ceco.gm2.gravitybox.Utils;
import com.ceco.gm2.gravitybox.adapters.IIconListAdapterItem;
import com.ceco.gm2.gravitybox.adapters.IconListAdapter;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

public class AppPickerPreference extends DialogPreference 
                                 implements OnItemClickListener, 
                                            OnItemSelectedListener {
    private static final String TAG = "GB:AppPickerPreference";
    public static final String SEPARATOR = "#C3C0#";

    public static final int MODE_APP = 0;
    public static final int MODE_SHORTCUT = 1;

    public static PrefsFragment sPrefsFragment;

    private Context mContext;
    private ListView mListView;
    private EditText mSearch;
    private ProgressBar mProgressBar;
    private AsyncTask<Void, Void, ArrayList<IIconListAdapterItem>> mAsyncTask;
    private String mDefaultSummaryText;
    private int mAppIconSizePx;
    private PackageManager mPackageManager;
    private Resources mResources;
    private int mMode;
    private Spinner mModeSpinner;

    private static LruCache<String, BitmapDrawable> sAppIconCache;
    static {
        final int cacheSize = Math.min((int)Runtime.getRuntime().maxMemory() / 6, 4194304);
        sAppIconCache = new LruCache<String, BitmapDrawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable d) {
                return d.getBitmap().getByteCount();
            }
        };
    }

    public AppPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mResources = mContext.getResources();
        mDefaultSummaryText = (String) getSummary();
        mAppIconSizePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, 
                mResources.getDisplayMetrics());
        mPackageManager = mContext.getPackageManager();
        mMode = MODE_APP;

        setDialogLayoutResource(R.layout.app_picker_preference);
        setPositiveButtonText(null);
    }

    @Override
    protected void onBindDialogView(View view) {
        mListView = (ListView) view.findViewById(R.id.icon_list);
        mListView.setOnItemClickListener(this);

        mSearch = (EditText) view.findViewById(R.id.input_search);
        mSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable arg0) { }

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1,
                    int arg2, int arg3) { }

            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                    int arg3) 
            {
                if(mListView.getAdapter() == null)
                    return;
                
                ((IconListAdapter)mListView.getAdapter()).getFilter().filter(arg0);
            }
        });

        mProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);

        mModeSpinner = (Spinner) view.findViewById(R.id.mode_spinner);
        ArrayAdapter<String> mModeSpinnerAdapter = new ArrayAdapter<String>(
                getContext(), android.R.layout.simple_spinner_item,
                        new ArrayList<String>(Arrays.asList(
                                mContext.getString(R.string.app_picker_applications), 
                                mContext.getString(R.string.app_picker_shortcuts))));
        mModeSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mModeSpinner.setAdapter(mModeSpinnerAdapter);
        mModeSpinner.setOnItemSelectedListener(this);
        mMode = mModeSpinner.getSelectedItemPosition();

        super.onBindView(view);

        setData();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mAsyncTask != null && mAsyncTask.getStatus() == AsyncTask.Status.RUNNING) {
            mAsyncTask.cancel(true);
            mAsyncTask = null;
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            String value = getPersistedString(null);
            if (value != null && value.contains(SEPARATOR)) {
                value = convertOldValueFormat(value);
            }
            String appName = getAppNameFromValue(value);
            setSummary(appName == null ? mDefaultSummaryText : appName);
        } else {
            setValue(null);
            setSummary(mDefaultSummaryText);
        }
    } 

    private String convertOldValueFormat(String oldValue) {
        try {
            String[] splitValue = oldValue.split(SEPARATOR);
            ComponentName cn = new ComponentName(splitValue[0], splitValue[1]);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(cn);
            intent.putExtra("mode", MODE_APP);
            String newValue = intent.toUri(0);
            setValue(newValue);
            Log.d(TAG, "Converted old AppPickerPreference value: " + newValue);
            return newValue;
        } catch(Exception e) {
            Log.e(TAG, "Error converting old AppPickerPreference value: " + e.getMessage());
            return null;
        }
    }

    public void setDefaultSummary(String summary) {
        mDefaultSummaryText = summary;
    }

    private void setData() {
        mAsyncTask = new AsyncTask<Void,Void,ArrayList<IIconListAdapterItem>>() {
            @Override
            protected void onPreExecute()
            {
                super.onPreExecute();

                mListView.setVisibility(View.INVISIBLE);
                mSearch.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected ArrayList<IIconListAdapterItem> doInBackground(Void... arg0) {
                ArrayList<IIconListAdapterItem> itemList = new ArrayList<IIconListAdapterItem>();
                List<ResolveInfo> appList = new ArrayList<ResolveInfo>();

                List<PackageInfo> packages = mPackageManager.getInstalledPackages(0);
                Intent mainIntent = new Intent();
                if (mMode == MODE_APP) {
                    mainIntent.setAction(Intent.ACTION_MAIN);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                } else if (mMode == MODE_SHORTCUT) {
                    mainIntent.setAction(Intent.ACTION_CREATE_SHORTCUT);
                }
                for(PackageInfo pi : packages) {
                    if (this.isCancelled()) break;
                    mainIntent.setPackage(pi.packageName);
                    List<ResolveInfo> activityList = mPackageManager.queryIntentActivities(mainIntent, 0);
                    for(ResolveInfo ri : activityList) {
                        appList.add(ri);
                    }
                }

                Collections.sort(appList, new ResolveInfo.DisplayNameComparator(mPackageManager));
                itemList.add(mMode == MODE_SHORTCUT ? 
                        new ShortcutItem(mContext.getString(R.string.app_picker_none), null) :
                        new AppItem(mContext.getString(R.string.app_picker_none), null));
                for (ResolveInfo ri : appList) {
                    if (this.isCancelled()) break;
                    String appName = ri.loadLabel(mPackageManager).toString();
                    IIconListAdapterItem ai = mMode == MODE_SHORTCUT ?
                            new ShortcutItem(appName, ri) : new AppItem(appName, ri);
                    itemList.add(ai);
                }

                return itemList;
            }

            @Override
            protected void onPostExecute(ArrayList<IIconListAdapterItem> result)
            {
                mProgressBar.setVisibility(View.GONE);
                mSearch.setVisibility(View.VISIBLE);
                mListView.setAdapter(new IconListAdapter(mContext, result));
                ((IconListAdapter)mListView.getAdapter()).notifyDataSetChanged();
                mListView.setVisibility(View.VISIBLE);
            }
        }.execute();
    }

    private void setValue(String value){
        persistString(value);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        IIconListAdapterItem item = (IIconListAdapterItem) parent.getItemAtPosition(position);
        if (mMode == MODE_APP) {
            AppItem ai = (AppItem) item;
            setValue(ai.getValue());
            setSummary(ai.getValue() == null ? mDefaultSummaryText : ai.getAppName());
            getDialog().dismiss();
        } else if (mMode == MODE_SHORTCUT) {
            ShortcutItem si = (ShortcutItem) item;
            if (si.getCreateShortcutIntent() == null) {
                setValue(null);
                setSummary(mDefaultSummaryText);
                getDialog().dismiss();
            } else {
                si.setShortcutCreatedListener(new ShortcutCreatedListener() {
                    @Override
                    public void onShortcutCreated(ShortcutItem sir) {
                        setValue(sir.getValue());
                        setSummary(sir.getValue() == null ? mDefaultSummaryText :
                            sir.getIntent().getStringExtra("prefLabel"));
                        // we have to call this explicitly for some yet unknown reason...
                        sPrefsFragment.onSharedPreferenceChanged(getSharedPreferences(), getKey());
                        getDialog().dismiss();
                    }
                });
                sPrefsFragment.obtainShortcut((ShortcutItem) item);
            }
        }
    }

    private String getAppNameFromValue(String value) {
        if (value == null) return null;

        try {
            Intent intent = Intent.parseUri(value, 0);
            if (intent.hasExtra("prefLabel")) {
                return intent.getStringExtra("prefLabel");
            } else {
                ComponentName cn = intent.getComponent();
                ActivityInfo ai = mPackageManager.getActivityInfo(cn, 0);
                return (ai.loadLabel(mPackageManager).toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mMode = position;
        setData();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) { }

    class AppItem implements IIconListAdapterItem {
        protected String mAppName;
        protected BitmapDrawable mAppIcon;
        protected ResolveInfo mResolveInfo;
        protected Intent mIntent;

        private AppItem() { }

        public AppItem(String appName, ResolveInfo ri) {
            mAppName = appName;
            mResolveInfo = ri;
            if (mResolveInfo != null) {
                mIntent = new Intent(Intent.ACTION_MAIN);
                mIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName cn = new ComponentName(mResolveInfo.activityInfo.packageName,
                        mResolveInfo.activityInfo.name);
                mIntent.setComponent(cn);
                mIntent.putExtra("mode", MODE_APP);
            }
        }

        public String getAppName() {
            return mAppName;
        }

        public String getValue() {
            return (mIntent == null ? null : mIntent.toUri(0));
        }

        public Intent getIntent() {
            return mIntent;
        }

        @Override
        public String getText() {
            return mAppName;
        }

        @Override
        public String getSubText() {
            return null;
        }

        protected String getKey() {
            return getValue();
        }

        @Override
        public Drawable getIconLeft() {
            if (mResolveInfo == null) return null;

            if (mAppIcon == null) {
                final String key = getKey();
                mAppIcon = sAppIconCache.get(key);
                if (mAppIcon == null) {
                    Bitmap bitmap = Utils.drawableToBitmap(mResolveInfo.loadIcon(mPackageManager));
                    bitmap = Bitmap.createScaledBitmap(bitmap, mAppIconSizePx, mAppIconSizePx, false);
                    mAppIcon = new BitmapDrawable(mResources, bitmap);
                    sAppIconCache.put(key, mAppIcon);
                }
            }
            return mAppIcon;
        }

        @Override
        public Drawable getIconRight() {
            return null;
        }
    }

    interface ShortcutCreatedListener {
        void onShortcutCreated(ShortcutItem item);
    }

    class ShortcutItem extends AppItem implements ShortcutHandler {
        private Intent mCreateShortcutIntent;
        private ShortcutCreatedListener mShortcutCreatedListener;

        public ShortcutItem(String appName, ResolveInfo ri) {
            mAppName = appName;
            mResolveInfo = ri;
            if (mResolveInfo != null) {
                mCreateShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                ComponentName cn = new ComponentName(mResolveInfo.activityInfo.packageName,
                        mResolveInfo.activityInfo.name);
                mCreateShortcutIntent.setComponent(cn);
                // mark intent so we can later identify it comes from GB
                mCreateShortcutIntent.putExtra("gravitybox", true);
            }
        }

        public void setShortcutCreatedListener(ShortcutCreatedListener listener) {
            mShortcutCreatedListener = listener;
        }

        @Override
        protected String getKey() {
            return mCreateShortcutIntent.toUri(0);
        }

        @Override
        public Intent getCreateShortcutIntent() {
            return mCreateShortcutIntent;
        }

        @Override
        public void onHandleShortcut(Intent intent, String name, Bitmap icon) {
            if (intent == null) {
                Toast.makeText(mContext, R.string.app_picker_shortcut_null_intent, Toast.LENGTH_LONG).show();
                return;
            }

            mIntent = intent;
            mIntent.putExtra("mode", MODE_SHORTCUT);

            // generate label
            if (name != null) {
                mAppName  += ": " + name;
                mIntent.putExtra("label", name);
                mIntent.putExtra("prefLabel", mAppName);
            } else {
                mIntent.putExtra("label", mAppName);
                mIntent.putExtra("prefLabel", mAppName);
            }

            // process icon
            if (icon != null || mAppIcon != null) {
                try {
                    final Context context = AppPickerPreference.this.mContext;
                    final String dir = context.getFilesDir() + "/app_picker";
                    final String fileName = dir + "/" + UUID.randomUUID().toString();
                    File d = new File(dir);
                    d.mkdirs();
                    d.setReadable(true, false);
                    d.setExecutable(true, false);
                    File f = new File(fileName);
                    FileOutputStream fos = new FileOutputStream(f);
                    final boolean iconSaved = icon == null ?
                            mAppIcon.getBitmap().compress(CompressFormat.PNG, 100, fos) :
                                icon.compress(CompressFormat.PNG, 100, fos);
                    if (iconSaved) {
                        mIntent.putExtra("icon", f.getAbsolutePath());
                        f.setReadable(true, false);
                    }
                    fos.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (icon != null) {
                        icon.recycle();
                    }
                }
            }

            // callback to shortcut created listener if set
            if (mShortcutCreatedListener != null) {
                mShortcutCreatedListener.onShortcutCreated(this);
            }
        }

        @Override
        public void onShortcutCancelled() {
            Toast.makeText(mContext, R.string.app_picker_shortcut_cancelled, Toast.LENGTH_SHORT).show();
        }
    }
}
