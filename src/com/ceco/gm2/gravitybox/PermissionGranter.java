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

import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PermissionGranter {
    public static final String TAG = "GB:PermissionGranter";
    public static final boolean DEBUG = false;

    private static final String CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService";
    private static final String CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package";

    private static final String PERM_CAMERA = "android.permission.CAMERA";
    private static final String PERM_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE";
    private static final String PERM_BROADCAST_STICKY = "android.permission.BROADCAST_STICKY";

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote() {
        try {
            final Class<?> pmServiceClass = XposedHelpers.findClass(CLASS_PACKAGE_MANAGER_SERVICE, null);

            XposedHelpers.findAndHookMethod(pmServiceClass, "grantPermissionsLPw",
                    CLASS_PACKAGE_PARSER_PACKAGE, boolean.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final String pkgName = (String) XposedHelpers.getObjectField(param.args[0], "packageName");

                    // SystemUI
                    if (pkgName.equals("com.android.systemui")) {
                        final Object extras = XposedHelpers.getObjectField(param.args[0], "mExtras");
                        final HashSet<String> grantedPerms = 
                                (HashSet<String>) XposedHelpers.getObjectField(extras, "grantedPermissions");
                        final Object settings = XposedHelpers.getObjectField(param.thisObject, "mSettings");
                        final Object permissions = XposedHelpers.getObjectField(settings, "mPermissions");

                        // Add android.permission.CAMERA needed by camera tile
                        if (!grantedPerms.contains(PERM_CAMERA)) {
                            final Object pCamera = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CAMERA);
                            grantedPerms.add(PERM_CAMERA);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCamera, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCamera);
                        }

                        // Add android.permission.CHANGE_NETWORK_STATE and android.permission.BROADCAST_STICKY
                        // needed by Usb Tethering Tile
                        if (!grantedPerms.contains(PERM_CHANGE_NETWORK_STATE)) {
                            final Object pCns = XposedHelpers.callMethod(permissions, "get",
                                    PERM_CHANGE_NETWORK_STATE);
                            grantedPerms.add(PERM_CHANGE_NETWORK_STATE);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pCns, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pCns);
                        }
                        if (!grantedPerms.contains(PERM_BROADCAST_STICKY)) {
                            final Object pBs = XposedHelpers.callMethod(permissions, "get",
                                    PERM_BROADCAST_STICKY);
                            grantedPerms.add(PERM_BROADCAST_STICKY);
                            int[] gpGids = (int[]) XposedHelpers.getObjectField(extras, "gids");
                            int[] bpGids = (int[]) XposedHelpers.getObjectField(pBs, "gids");
                            gpGids = (int[]) XposedHelpers.callStaticMethod(param.thisObject.getClass(), 
                                    "appendInts", gpGids, bpGids);

                            if (DEBUG) log(pkgName + ": Permission added: " + pBs);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
