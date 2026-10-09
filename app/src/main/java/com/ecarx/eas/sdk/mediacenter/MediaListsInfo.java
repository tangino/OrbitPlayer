package com.ecarx.eas.sdk.mediacenter;

import com.ecarx.eas.framework.sdk.common.annotation.KeepForSdk;
import java.util.List;

/* JADX INFO: loaded from: assets/ecarx-sdk.dex */
@KeepForSdk
public class MediaListsInfo {
    private List<MediaListInfo> mediaListsInfo;

    public List<MediaListInfo> getMediaListsInfo() {
        return this.mediaListsInfo;
    }

    public void setMediaListsInfo(List<MediaListInfo> list) {
        this.mediaListsInfo = list;
    }
}
