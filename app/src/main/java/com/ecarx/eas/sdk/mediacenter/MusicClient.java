package com.ecarx.eas.sdk.mediacenter;

import com.ecarx.eas.framework.sdk.common.annotation.KeepForSdk;
import java.util.List;

/* JADX INFO: loaded from: assets/ecarx-sdk.dex */
@KeepForSdk
public class MusicClient extends AbstractMusicClient {
    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public int ctrlCollect(int i, boolean z) {
        return 0;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public void ctrlCollect(int i, String str, boolean z) {
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean ctrlPauseMediaList(int i) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean ctrlPlayMediaList(int i) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public List<ContentInfo> getContentList() {
        return null;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public long getCurrentProgress() {
        return 0L;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public int getCurrentSourceType() {
        return 0;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public int[] getMediaSourceTypeList() {
        return new int[0];
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public MediaListsInfo getMultiMediaList(int[] iArr) {
        return null;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public MusicPlaybackInfo getMusicPlaybackInfo() {
        return null;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public List<MediaInfo> getPlaylist(int i) {
        return null;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onCancelRecommend(RecommendInfo recommendInfo) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onCollect(int i, boolean z) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onDownload(int i, boolean z) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onExit() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onForward() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onLoopModeChange(int i) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public void onMediaCenterFocusChanged(String str) {
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onMediaForward(boolean z) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onMediaQualityChange(int i) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onMediaRewind(boolean z) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onMediaSelected(int i, String str) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onMediaSelected(MediaInfo mediaInfo) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onNext() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onPause() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onPlay() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onPlayRecommend(RecommendInfo recommendInfo) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onPrevious() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onReplay() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onRewind() {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public void onSeek(long j) {
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onSourceChanged(int i, String str) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean onSourceSelected(int i) {
        return false;
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public void operationType(int i) {
    }

    @Override // com.ecarx.eas.sdk.mediacenter.AbstractMusicClient
    public boolean selectListMediaPlay(int i, int i2, String str) {
        return false;
    }
}
