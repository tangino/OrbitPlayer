package com.ecarx.eas.sdk.mediacenter;

import android.net.Uri;
import com.ecarx.eas.framework.sdk.common.annotation.KeepForSdk;

/* JADX INFO: loaded from: assets/ecarx-sdk.dex */
@KeepForSdk
public abstract class AbstractRecommendInfo {
    public static final int RECOMMEND_TYPE_ALBUM = 1;
    public static final int RECOMMEND_TYPE_SINGER = 3;
    public static final int RECOMMEND_TYPE_SINGLE = 0;
    public static final int RECOMMEND_TYPE_SONG_LIST = 2;

    public abstract String getArtist();

    public abstract Uri getArtwork();

    public abstract String getId();

    public abstract int getRecommendType();

    public abstract String getTextDescription();

    public abstract String getTitle();
}
