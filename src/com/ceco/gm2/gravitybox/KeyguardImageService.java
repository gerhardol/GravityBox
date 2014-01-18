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

import java.io.File;
import java.io.FileOutputStream;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class KeyguardImageService extends Service {
    public static final int MSG_BEGIN_OUTPUT = 1;
    public static final int MSG_WRITE_OUTPUT = 2;
    public static final int MSG_FINISH_OUTPUT = 3;
    public static final int MSG_GET_NEXT_CHUNK = 4;
    public static final int MSG_ERROR = -1;

    public static final String ACTION_KEYGUARD_IMAGE_UPDATED = "gravitybox.intent.action.KEYGUARD_IMAGE_UPDATED";

    private File mKisImageFile;
    private boolean mWriteInProgress;
    private FileOutputStream mFileOutputStream;

    final Messenger mMessenger = new Messenger(new ClientHandler());

    class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_BEGIN_OUTPUT:
                    if (mWriteInProgress) return;
                    try {
                        mFileOutputStream = new FileOutputStream(mKisImageFile);
                        mWriteInProgress = true;
                        msg.replyTo.send(Message.obtain(null, MSG_GET_NEXT_CHUNK));
                    } catch (Throwable t) {
                        try {
                            msg.replyTo.send(Message.obtain(null, MSG_ERROR));
                        } catch (RemoteException e1) { }
                        t.printStackTrace();
                    }
                    break;
                case MSG_WRITE_OUTPUT:
                    try {
                        byte[] data = msg.getData().getByteArray("data");
                        mFileOutputStream.write(data);
                        data = null;
                        msg.replyTo.send(Message.obtain(null, MSG_GET_NEXT_CHUNK));
                    } catch (Throwable t) {
                        try {
                            msg.replyTo.send(Message.obtain(null, MSG_ERROR));
                        } catch (RemoteException e1) { }
                        t.printStackTrace();
                    }
                    break;
                case MSG_FINISH_OUTPUT:
                    try {
                        mFileOutputStream.close();
                        mKisImageFile.setReadable(true, false);
                        Intent intent = new Intent(ACTION_KEYGUARD_IMAGE_UPDATED);
                        sendBroadcast(intent);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mKisImageFile = new File(getFilesDir() + "/kis_image.png");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }
}
