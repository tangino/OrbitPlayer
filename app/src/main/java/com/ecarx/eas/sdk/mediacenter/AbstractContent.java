package com.ecarx.eas.sdk.mediacenter;

import android.app.PendingIntent;
import android.net.Uri;
import com.ecarx.eas.framework.sdk.common.annotation.KeepForSdk;

/* JADX INFO: loaded from: assets/ecarx-sdk.dex */
@KeepForSdk
public abstract class AbstractContent {
    public abstract Uri getBackground();

    public abstract String getId();

    public abstract PendingIntent getPendingIntent();

    public abstract String getTitle();
}
