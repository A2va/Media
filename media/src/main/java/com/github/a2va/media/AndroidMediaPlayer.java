/*
 * Copyright 2022 A2va
 *
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

package com.github.a2va.media;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioDeviceInfo;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;

public class AndroidMediaPlayer extends android.media.MediaPlayer implements IMediaPlayer {
    public AndroidMediaPlayer() {
        super();
    }
    public void setDataSource(Context context, Uri uri)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException{
        super.setDataSource(context,uri);
    }
    public void setDataSource(@NonNull Context context, @NonNull Uri uri,
                              @Nullable Map<String, String> headers, @Nullable List<HttpCookie> cookies)
            throws IOException {
        super.setDataSource(context, uri, headers,cookies);
    }
    public void setDataSource(@NonNull Context context, @NonNull Uri uri,
                              @Nullable Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        super.setDataSource(context,uri, headers);
    }
    public void setDataSource(String path)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        super.setDataSource(path);
    }
    public void setDataSource(@NonNull AssetFileDescriptor afd)
            throws IOException, IllegalArgumentException, IllegalStateException {
        super.setDataSource(afd);
    }
    public void setDataSource(FileDescriptor fd) throws IOException, IllegalArgumentException, IllegalStateException {
        super.setDataSource(fd);
    }
    public void setDataSource(FileDescriptor fd, long offset, long length)  throws IOException, IllegalArgumentException, IllegalStateException {
        super.setDataSource(fd,offset,length);
    }
    public void prepare() throws IOException, IllegalStateException {
        super.prepare();
    }
    public void prepareAsync() throws IllegalStateException {
        super.prepareAsync();
    }
    public void start() throws IllegalStateException {
        super.start();
    }
    public void stop() throws IllegalStateException {
        super.stop();
    }
    public void pause() throws IllegalStateException {
        super.pause();
    }
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {
        return super.setPreferredDevice(deviceInfo);
    }
    public AudioDeviceInfo getPreferredDevice() {
        return super.getPreferredDevice();
    }
    public void setWakeMode(Context context, int mode) {
        super.setWakeMode(context,mode);
    }
    public void setScreenOnWhilePlaying(boolean screenOn) {
        super.setScreenOnWhilePlaying(screenOn);
    }
    public boolean isPlaying() {
        return super.isPlaying();
    }
    public void seekTo(int msec) throws IllegalStateException {
        super.seekTo(msec);
    }
    public int getCurrentPosition() {
        return super.getCurrentPosition();
    }
    public int getDuration() {
        return super.getDuration();
    }
    public void setNextMediaPlayer(Object next) {
        super.setNextMediaPlayer((android.media.MediaPlayer)next);
    }
    public void release() {
        super.release();
    }
    public void reset() {
        super.reset();
    }
    public void setAudioStreamType(int streamtype) {
        super.setAudioStreamType(streamtype);
    }
    public void setLooping(boolean looping) {
        super.setLooping(looping);
    }
    public boolean isLooping() {
        return super.isLooping();
    }
    public void setVolume(float leftVolume, float rightVolume) {
        super.setVolume(leftVolume,rightVolume);
    }
    public void setVolume(float volume) {
        setVolume(volume,volume);
    }
    public void setAudioSessionId(int sessionId)  throws IllegalArgumentException, IllegalStateException {
        setAudioSessionId(sessionId);
    }
    public int getAudioSessionId() {
        return super.getAudioSessionId();
    }

}
