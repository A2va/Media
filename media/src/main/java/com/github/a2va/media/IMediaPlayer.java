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
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;

// TODO
// List of method that are compatible between android.media.MediaPlayer and
// com.example.MediaPlayer
// Listener at not usable with IMediaPlayer.

public interface IMediaPlayer {
    /**
     * Convenience method to create a MediaPlayer for a given Uri.
     * On success, {@link #prepare()} will already have been called and must not be called again.
     * <p>When done with the MediaPlayer, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPlayer instances will
     * result in an exception.</p>
     *
     * @param context the Context to use
     * @param uri     the Uri from which to get the datasource
     * @return a MediaPlayer object, or null if creation failed
     */
    public static MediaPlayer create(Context context, Uri uri) {
        return null;
    }
    /**
     * Convenience method to create a MediaPlayer for a given Uri.
     * On success, {@link #prepare()} will already have been called and must not be called again.
     * <p>When done with the MediaPlayer, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPlayer instances will
     * result in an exception.</p>
     *
     * @param context the Context to use
     * @param uri the Uri from which to get the datasource
     * @param holder the SurfaceHolder to use for displaying the video
     * @return a MediaPlayer object, or null if creation failed
     */
    public static MediaPlayer create(Context context, Uri uri, SurfaceHolder holder) {
        return null;
    }
    /**
     * Same factory method as {@link #create(Context, Uri, SurfaceHolder)} but that lets you specify
     * the audio attributes and session ID to be used by the new MediaPlayer instance.
     * @param context the Context to use
     * @param uri the Uri from which to get the datasource
     * @param holder the SurfaceHolder to use for displaying the video, may be null.
     * @param audioAttributes the {@link AudioAttributes} to be used by the media player.
     * @param audioSessionId the audio session ID to be used by the media player,
     *     see {@link AudioManager#generateAudioSessionId()} to obtain a new session.
     * @return a MediaPlayer object, or null if creation failed
     */
    public static MediaPlayer create(Context context, Uri uri, SurfaceHolder holder,
                                     AudioAttributes audioAttributes, int audioSessionId) {
        return  null;
    }
    /**
     * Convenience method to create a MediaPlayer for a given resource id.
     * On success, {@link #prepare()} will already have been called and must not be called again.
     * <p>When done with the MediaPlayer, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPlayer instances will
     * result in an exception.</p>
     * <p>Note that since {@link #prepare()} is called automatically in this method,
     * you cannot change the audio
     * session ID (see {@link #setAudioSessionId(int)}) or audio attributes
     * (see {@link #setAudioAttributes(AudioAttributes)} of the new MediaPlayer.</p>
     *
     * @param context the Context to use
     * @param resid the raw resource id (<var>R.raw.&lt;something></var>) for
     *              the resource to use as the datasource
     * @return a MediaPlayer object, or null if creation failed
     */
    public static MediaPlayer create(Context context, int resid) {
        return null;
    }
    /**
     * Same factory method as {@link #create(Context, int)} but that lets you specify the audio
     * attributes and session ID to be used by the new MediaPlayer instance.
     * @param context the Context to use
     * @param resid the raw resource id (<var>R.raw.&lt;something></var>) for
     *              the resource to use as the datasource
     * @param audioAttributes the {@link AudioAttributes} to be used by the media player.
     * @param audioSessionId the audio session ID to be used by the media player,
     *     see {@link AudioManager#generateAudioSessionId()} to obtain a new session.
     * @return a MediaPlayer object, or null if creation failed
     */
    public static MediaPlayer create(Context context, int resid,
                                     AudioAttributes audioAttributes, int audioSessionId) {
        return null;
    }
    /**
     * Sets the data source as a content Uri.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(Context context, Uri uri)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    /**
     * Sets the data source as a content Uri.
     *
     * To provide cookies for the subsequent HTTP requests, you can install your own default cookie
     * handler and use other variants of setDataSource APIs instead. Alternatively, you can use
     * this API to pass the cookies as a list of HttpCookie. If the app has not installed
     * a CookieHandler already, this API creates a CookieManager and populates its CookieStore with
     * the provided cookies. If the app has installed its own handler already, this API requires the
     * handler to be of CookieManager type such that the API can update the manager’s CookieStore.
     *
     * <p><strong>Note</strong> that the cross domain redirection is allowed by default,
     * but that can be changed with key/value pairs through the headers parameter with
     * "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value to
     * disallow or allow cross domain redirection.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @param headers the headers to be sent together with the request for the data
     *                The headers must not include cookies. Instead, use the cookies param.
     * @param cookies the cookies to be sent together with the request
     * @throws IllegalArgumentException if cookies are provided and the installed handler is not
     *                                  a CookieManager
     * @throws IllegalStateException    if it is called in an invalid state
     * @throws NullPointerException     if context or uri is null
     * @throws IOException              if uri has a file scheme and an I/O error occurs
     */
    public void setDataSource(@NonNull Context context, @NonNull Uri uri,
                              @Nullable Map<String, String> headers, @Nullable List<HttpCookie> cookies)
            throws IOException;
    /**
     * Sets the data source as a content Uri.
     *
     * <p><strong>Note</strong> that the cross domain redirection is allowed by default,
     * but that can be changed with key/value pairs through the headers parameter with
     * "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value to
     * disallow or allow cross domain redirection.
     *
     * @param context the Context to use when resolving the Uri
     * @param uri the Content URI of the data you want to play
     * @param headers the headers to be sent together with the request for the data
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(@NonNull Context context, @NonNull Uri uri,
                              @Nullable Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;
    /**
     * Sets the data source (file-path or http/rtsp URL) to use.
     *
     * @param path the path of the file, or the http/rtsp URL of the stream you want to play
     * @throws IllegalStateException if it is called in an invalid state
     *
     * <p>When <code>path</code> refers to a local file, the file may actually be opened by a
     * process other than the calling application.  This implies that the pathname
     * should be an absolute path (as any other process runs with unspecified current working
     * directory), and that the pathname should reference a world-readable file.
     * As an alternative, the application could first open the file for reading,
     * and then use the file descriptor form {@link #setDataSource(FileDescriptor)}.
     */
    public void setDataSource(String path)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException;

    /**
     * Sets the data source (AssetFileDescriptor) to use. It is the caller's
     * responsibility to close the file descriptor. It is safe to do so as soon
     * as this call returns.
     *
     * @param afd the AssetFileDescriptor for the file you want to play
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if afd is not a valid AssetFileDescriptor
     * @throws IOException if afd can not be read
     */
    public void setDataSource(@NonNull AssetFileDescriptor afd)
            throws IOException, IllegalArgumentException, IllegalStateException;
    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(FileDescriptor fd) throws IOException, IllegalArgumentException, IllegalStateException;
    /**
     * Sets the data source (FileDescriptor) to use.  The FileDescriptor must be
     * seekable (N.B. a LocalSocket is not seekable). It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to play
     * @param offset the offset into the file where the data to be played starts, in bytes
     * @param length the length in bytes of the data to be played
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if fd is not a valid FileDescriptor
     * @throws IOException if fd can not be read
     */
    public void setDataSource(FileDescriptor fd, long offset, long length)  throws IOException, IllegalArgumentException, IllegalStateException;
    /**
     * Prepares the player for playback, synchronously.
     *
     * After setting the datasource and the display surface, you need to either
     * call prepare() or prepareAsync(). For files, it is OK to call prepare(),
     * which blocks until MediaPlayer is ready for playback.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void prepare() throws IOException, IllegalStateException;
    /**
     * Prepares the player for playback, asynchronously.
     *
     * After setting the datasource and the display surface, you need to either
     * call prepare() or prepareAsync(). For streams, you should call prepareAsync(),
     * which returns immediately, rather than blocking until enough data has been
     * buffered.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void prepareAsync() throws IllegalStateException;
    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * been stopped, or never started before, playback will start at the
     * beginning.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void start() throws IllegalStateException;
    /**
     * Stops playback after playback has been stopped or paused.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    public void stop() throws IllegalStateException;
    /**
     * Pauses playback. Call start() to resume.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    public void pause() throws IllegalStateException;
    /**
     * Specifies an audio device (via an {@link AudioDeviceInfo} object) to route
     * the output from this MediaPlayer.
     * @param deviceInfo The {@link AudioDeviceInfo} specifying the audio sink or source.
     *  If deviceInfo is null, default routing is restored.
     * @return true if succesful, false if the specified {@link AudioDeviceInfo} is non-null and
     * does not correspond to a valid audio device.
     */
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo);
    /**
     * Returns the selected output specified by {@link #setPreferredDevice}. Note that this
     * is not guaranteed to correspond to the actual device being used for playback.
     */
    public AudioDeviceInfo getPreferredDevice();
    /**
     * Set the low-level power management behavior for this MediaPlayer.  This
     * can be used when the MediaPlayer is not playing through a SurfaceHolder
     * set with {@link #setDisplay(SurfaceHolder)} and thus can use the
     * high-level {@link #setScreenOnWhilePlaying(boolean)} feature.
     *
     * <p>This function has the MediaPlayer access the low-level power manager
     * service to control the device's power usage while playing is occurring.
     * The parameter is a combination of {@link android.os.PowerManager} wake flags.
     * Use of this method requires {@link android.Manifest.permission#WAKE_LOCK}
     * permission.
     * By default, no attempt is made to keep the device awake during playback.
     *
     * @param context the Context to use
     * @param mode    the power/wake mode to set
     * @see android.os.PowerManager
     */
    public void setWakeMode(Context context, int mode);
    /**
     * Control whether we should use the attached SurfaceHolder to keep the
     * screen on while video playback is occurring.  This is the preferred
     * method over {@link #setWakeMode} where possible, since it doesn't
     * require that the application have permission for low-level wake lock
     * access.
     *
     * @param screenOn Supply true to keep the screen on, false to allow it
     * to turn off.
     */
    public void setScreenOnWhilePlaying(boolean screenOn);
    /**
     * Checks whether the MediaPlayer is playing.
     *
     * @return true if currently playing, false otherwise
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     */
    public boolean isPlaying();
    /**
     * Seeks to specified time position.
     * Same as {@link #seekTo(long, int)} with {@code mode = SEEK_PREVIOUS_SYNC}.
     *
     * @param msec the offset in milliseconds from the start to seek to
     * @throws IllegalStateException if the internal player engine has not been
     * initialized
     */
    public void seekTo(int msec) throws IllegalStateException;
    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public int getCurrentPosition();
    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    public int getDuration();
    /**
     * Set the MediaPlayer to start when this MediaPlayer finishes playback
     * (i.e. reaches the end of the stream).
     * The media framework will attempt to transition from this player to
     * the next as seamlessly as possible. The next player can be set at
     * any time before completion. The next player must be prepared by the
     * app, and the application should not call start() on it.
     * The next MediaPlayer must be different from 'this'. An exception
     * will be thrown if next == this.
     * The application may call setNextMediaPlayer(null) to indicate no
     * next player should be started at the end of playback.
     * If the current player is looping, it will keep looping and the next
     * player will not be started.
     *
     * @param next the player to start after this one completes playback.
     *
     */
    //public void setNextMediaPlayer(Object next);
    /**
     * Releases resources associated with this MediaPlayer object.
     * It is considered good practice to call this method when you're
     * done using the MediaPlayer. In particular, whenever an Activity
     * of an application is paused (its onPause() method is called),
     * or stopped (its onStop() method is called), this method should be
     * invoked to release the MediaPlayer object, unless the application
     * has a special need to keep the object around. In addition to
     * unnecessary resources (such as memory and instances of codecs)
     * being held, failure to call this method immediately if a
     * MediaPlayer object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and playback
     * failure for other applications if no multiple instances of the
     * same codec are supported on a device. Even if multiple instances
     * of the same codec are supported, some performance degradation
     * may be expected when unnecessary multiple instances are used
     * at the same time.
     */
    public void release();
    /**
     * Resets the MediaPlayer to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * data source and calling prepare().
     */
    public void reset();
    /**
     * Sets the audio stream type for this MediaPlayer. See {@link AudioManager}
     * for a list of stream types. Must call this method before prepare() or
     * prepareAsync() in order for the target stream type to become effective
     * thereafter.
     *
     * @param streamtype the audio stream type
     * @deprecated use {@link #setAudioAttributes(AudioAttributes)}
     * @see android.media.AudioManager
     */
    public void setAudioStreamType(int streamtype);
    /**
     * Sets the player to be looping or non-looping.
     *
     * @param looping whether to loop or not
     */
    public void setLooping(boolean looping);
    /**
     * Checks whether the MediaPlayer is looping or non-looping.
     *
     * @return true if the MediaPlayer is currently looping, false otherwise
     */
    public boolean isLooping();
    /**
     * Sets the volume on this player.
     * This API is recommended for balancing the output of audio streams
     * within an application. Unless you are writing an application to
     * control user settings, this API should be used in preference to
     * {@link AudioManager#setStreamVolume(int, int, int)} which sets the volume of ALL streams of
     * a particular type. Note that the passed volume values are raw scalars in range 0.0 to 1.0.
     * UI controls should be scaled logarithmically.
     *
     * @param leftVolume left volume scalar
     * @param rightVolume right volume scalar
     */
    /*
     * FIXME: Merge this into javadoc comment above when setVolume(float) is not @hide.
     * The single parameter form below is preferred if the channel volumes don't need
     * to be set independently.
     */
    public void setVolume(float leftVolume, float rightVolume);
    /**
     * Similar, excepts sets volume of all channels to same value.
     * @hide
     */
    public void setVolume(float volume);
    /**
     * Sets the audio session ID.
     *
     * @param sessionId the audio session ID.
     * The audio session ID is a system wide unique identifier for the audio stream played by
     * this MediaPlayer instance.
     * The primary use of the audio session ID  is to associate audio effects to a particular
     * instance of MediaPlayer: if an audio session ID is provided when creating an audio effect,
     * this effect will be applied only to the audio content of media players within the same
     * audio session and not to the output mix.
     * When created, a MediaPlayer instance automatically generates its own audio session ID.
     * However, it is possible to force this player to be part of an already existing audio session
     * by calling this method.
     * This method must be called before one of the overloaded <code> setDataSource </code> methods.
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setAudioSessionId(int sessionId)  throws IllegalArgumentException, IllegalStateException;
    /**
     * Returns the audio session ID.
     *
     * @return the audio session ID. {@see #setAudioSessionId(int)}
     * Note that the audio session ID is 0 only if a problem occured when the MediaPlayer was contructed.
     */
    public int getAudioSessionId();
}
