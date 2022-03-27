/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.MediaDataSource;
import android.media.MediaFormat;
import android.media.MediaTimestamp;
import android.media.PlaybackParams;
import android.media.RingtoneManager;
import android.media.SyncParams;
import android.media.TimedMetaData;
import android.net.Uri;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;


import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.net.UriCompat;
import androidx.core.util.Preconditions;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.util.Vector;

/**
 * MediaPlayer class can be used to control playback
 * of audio/video files and streams. An example on how to use the methods in
 * this class can be found in {@link android.widget.VideoView}.
 *
 * <p>Topics covered here are:
 * <ol>
 * <li><a href="#StateDiagram">State Diagram</a>
 * <li><a href="#Valid_and_Invalid_States">Valid and Invalid States</a>
 * <li><a href="#Permissions">Permissions</a>
 * <li><a href="#Callbacks">Register informational and error callbacks</a>
 * </ol>
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about how to use MediaPlayer, read the
 * <a href="{@docRoot}guide/topics/media/mediaplayer.html">Media Playback</a> developer guide.</p>
 * </div>
 *
 * <a name="StateDiagram"></a>
 * <h3>State Diagram</h3>
 *
 * <p>Playback control of audio/video files and streams is managed as a state
 * machine. The following diagram shows the life cycle and the states of a
 * MediaPlayer object driven by the supported playback control operations.
 * The ovals represent the states a MediaPlayer object may reside
 * in. The arcs represent the playback control operations that drive the object
 * state transition. There are two types of arcs. The arcs with a single arrow
 * head represent synchronous method calls, while those with
 * a double arrow head represent asynchronous method calls.</p>
 *
 * <p><img src="../../../images/mediaplayer_state_diagram.gif"
 *         alt="MediaPlayer State diagram"
 *         border="0" /></p>
 *
 * <p>From this state diagram, one can see that a MediaPlayer object has the
 *    following states:</p>
 * <ul>
 *     <li>When a MediaPlayer object is just created using <code>new</code> or
 *         after {@link #reset()} is called, it is in the <em>Idle</em> state; and after
 *         {@link #release()} is called, it is in the <em>End</em> state. Between these
 *         two states is the life cycle of the MediaPlayer object.
 *         <ul>
 *         <li>There is a subtle but important difference between a newly constructed
 *         MediaPlayer object and the MediaPlayer object after {@link #reset()}
 *         is called. It is a programming error to invoke methods such
 *         as {@link #getCurrentPosition()},
 *         {@link #getDuration()}, {@link #getVideoHeight()},
 *         {@link #getVideoWidth()}, {@link #setAudioStreamType(int)},
 *         {@link #setLooping(boolean)},
 *         {@link #setVolume(float, float)}, {@link #pause()}, {@link #start()},
 *         {@link #stop()}, {@link #seekTo(int)}, {@link #prepare()} or
 *         {@link #prepareAsync()} in the <em>Idle</em> state for both cases. If any of these
 *         methods is called right after a MediaPlayer object is constructed,
 *         the user supplied callback method OnErrorListener.onError() won't be
 *         called by the internal player engine and the object state remains
 *         unchanged; but if these methods are called right after {@link #reset()},
 *         the user supplied callback method OnErrorListener.onError() will be
 *         invoked by the internal player engine and the object will be
 *         transfered to the <em>Error</em> state. </li>
 *         <li>It is also recommended that once
 *         a MediaPlayer object is no longer being used, call {@link #release()} immediately
 *         so that resources used by the internal player engine associated with the
 *         MediaPlayer object can be released immediately. Resource may include
 *         singleton resources such as hardware acceleration components and
 *         failure to call {@link #release()} may cause subsequent instances of
 *         MediaPlayer objects to fallback to software implementations or fail
 *         altogether. Once the MediaPlayer
 *         object is in the <em>End</em> state, it can no longer be used and
 *         there is no way to bring it back to any other state. </li>
 *         <li>Furthermore,
 *         the MediaPlayer objects created using <code>new</code> is in the
 *         <em>Idle</em> state, while those created with one
 *         of the overloaded convenient <code>create</code> methods are <em>NOT</em>
 *         in the <em>Idle</em> state. In fact, the objects are in the <em>Prepared</em>
 *         state if the creation using <code>create</code> method is successful.
 *         </li>
 *         </ul>
 *         </li>
 *     <li>In general, some playback control operation may fail due to various
 *         reasons, such as unsupported audio/video format, poorly interleaved
 *         audio/video, resolution too high, streaming timeout, and the like.
 *         Thus, error reporting and recovery is an important concern under
 *         these circumstances. Sometimes, due to programming errors, invoking a playback
 *         control operation in an invalid state may also occur. Under all these
 *         error conditions, the internal player engine invokes a user supplied
 *         OnErrorListener.onError() method if an OnErrorListener has been
 *         registered beforehand via
 *         {@link #setOnErrorListener(android.media.MediaPlayer.OnErrorListener)}.
 *         <ul>
 *         <li>It is important to note that once an error occurs, the
 *         MediaPlayer object enters the <em>Error</em> state (except as noted
 *         above), even if an error listener has not been registered by the application.</li>
 *         <li>In order to reuse a MediaPlayer object that is in the <em>
 *         Error</em> state and recover from the error,
 *         {@link #reset()} can be called to restore the object to its <em>Idle</em>
 *         state.</li>
 *         <li>It is good programming practice to have your application
 *         register a OnErrorListener to look out for error notifications from
 *         the internal player engine.</li>
 *         <li>IllegalStateException is
 *         thrown to prevent programming errors such as calling {@link #prepare()},
 *         {@link #prepareAsync()}, or one of the overloaded <code>setDataSource
 *         </code> methods in an invalid state. </li>
 *         </ul>
 *         </li>
 *     <li>Calling
 *         {@link #setDataSource(FileDescriptor)}, or
 *         {@link #setDataSource(String)}, or
 *         {@link #setDataSource(Context, Uri)}, or
 *         {@link #setDataSource(FileDescriptor, long, long)} transfers a
 *         MediaPlayer object in the <em>Idle</em> state to the
 *         <em>Initialized</em> state.
 *         <ul>
 *         <li>An IllegalStateException is thrown if
 *         setDataSource() is called in any other state.</li>
 *         <li>It is good programming
 *         practice to always look out for <code>IllegalArgumentException</code>
 *         and <code>IOException</code> that may be thrown from the overloaded
 *         <code>setDataSource</code> methods.</li>
 *         </ul>
 *         </li>
 *     <li>A MediaPlayer object must first enter the <em>Prepared</em> state
 *         before playback can be started.
 *         <ul>
 *         <li>There are two ways (synchronous vs.
 *         asynchronous) that the <em>Prepared</em> state can be reached:
 *         either a call to {@link #prepare()} (synchronous) which
 *         transfers the object to the <em>Prepared</em> state once the method call
 *         returns, or a call to {@link #prepareAsync()} (asynchronous) which
 *         first transfers the object to the <em>Preparing</em> state after the
 *         call returns (which occurs almost right way) while the internal
 *         player engine continues working on the rest of preparation work
 *         until the preparation work completes. When the preparation completes or when {@link #prepare()} call returns,
 *         the internal player engine then calls a user supplied callback method,
 *         onPrepared() of the OnPreparedListener interface, if an
 *         OnPreparedListener is registered beforehand via {@link
 *         #setOnPreparedListener(android.media.MediaPlayer.OnPreparedListener)}.</li>
 *         <li>It is important to note that
 *         the <em>Preparing</em> state is a transient state, and the behavior
 *         of calling any method with side effect while a MediaPlayer object is
 *         in the <em>Preparing</em> state is undefined.</li>
 *         <li>An IllegalStateException is
 *         thrown if {@link #prepare()} or {@link #prepareAsync()} is called in
 *         any other state.</li>
 *         <li>While in the <em>Prepared</em> state, properties
 *         such as audio/sound volume, screenOnWhilePlaying, looping can be
 *         adjusted by invoking the corresponding set methods.</li>
 *         </ul>
 *         </li>
 *     <li>To start the playback, {@link #start()} must be called. After
 *         {@link #start()} returns successfully, the MediaPlayer object is in the
 *         <em>Started</em> state. {@link #isPlaying()} can be called to test
 *         whether the MediaPlayer object is in the <em>Started</em> state.
 *         <ul>
 *         <li>While in the <em>Started</em> state, the internal player engine calls
 *         a user supplied OnBufferingUpdateListener.onBufferingUpdate() callback
 *         method if a OnBufferingUpdateListener has been registered beforehand
 *         via {@link #setOnBufferingUpdateListener(OnBufferingUpdateListener)}.
 *         This callback allows applications to keep track of the buffering status
 *         while streaming audio/video.</li>
 *         <li>Calling {@link #start()} has not effect
 *         on a MediaPlayer object that is already in the <em>Started</em> state.</li>
 *         </ul>
 *         </li>
 *     <li>Playback can be paused and stopped, and the current playback position
 *         can be adjusted. Playback can be paused via {@link #pause()}. When the call to
 *         {@link #pause()} returns, the MediaPlayer object enters the
 *         <em>Paused</em> state. Note that the transition from the <em>Started</em>
 *         state to the <em>Paused</em> state and vice versa happens
 *         asynchronously in the player engine. It may take some time before
 *         the state is updated in calls to {@link #isPlaying()}, and it can be
 *         a number of seconds in the case of streamed content.
 *         <ul>
 *         <li>Calling {@link #start()} to resume playback for a paused
 *         MediaPlayer object, and the resumed playback
 *         position is the same as where it was paused. When the call to
 *         {@link #start()} returns, the paused MediaPlayer object goes back to
 *         the <em>Started</em> state.</li>
 *         <li>Calling {@link #pause()} has no effect on
 *         a MediaPlayer object that is already in the <em>Paused</em> state.</li>
 *         </ul>
 *         </li>
 *     <li>Calling  {@link #stop()} stops playback and causes a
 *         MediaPlayer in the <em>Started</em>, <em>Paused</em>, <em>Prepared
 *         </em> or <em>PlaybackCompleted</em> state to enter the
 *         <em>Stopped</em> state.
 *         <ul>
 *         <li>Once in the <em>Stopped</em> state, playback cannot be started
 *         until {@link #prepare()} or {@link #prepareAsync()} are called to set
 *         the MediaPlayer object to the <em>Prepared</em> state again.</li>
 *         <li>Calling {@link #stop()} has no effect on a MediaPlayer
 *         object that is already in the <em>Stopped</em> state.</li>
 *         </ul>
 *         </li>
 *     <li>The playback position can be adjusted with a call to
 *         {@link #seekTo(int)}.
 *         <ul>
 *         <li>Although the asynchronuous {@link #seekTo(int)}
 *         call returns right way, the actual seek operation may take a while to
 *         finish, especially for audio/video being streamed. When the actual
 *         seek operation completes, the internal player engine calls a user
 *         supplied OnSeekComplete.onSeekComplete() if an OnSeekCompleteListener
 *         has been registered beforehand via
 *         {@link #setOnSeekCompleteListener(OnSeekCompleteListener)}.</li>
 *         <li>Please
 *         note that {@link #seekTo(int)} can also be called in the other states,
 *         such as <em>Prepared</em>, <em>Paused</em> and <em>PlaybackCompleted
 *         </em> state.</li>
 *         <li>Furthermore, the actual current playback position
 *         can be retrieved with a call to {@link #getCurrentPosition()}, which
 *         is helpful for applications such as a Music player that need to keep
 *         track of the playback progress.</li>
 *         </ul>
 *         </li>
 *     <li>When the playback reaches the end of stream, the playback completes.
 *         <ul>
 *         <li>If the looping mode was being set to <var>true</var>with
 *         {@link #setLooping(boolean)}, the MediaPlayer object shall remain in
 *         the <em>Started</em> state.</li>
 *         <li>If the looping mode was set to <var>false
 *         </var>, the player engine calls a user supplied callback method,
 *         OnCompletion.onCompletion(), if a OnCompletionListener is registered
 *         beforehand via {@link #setOnCompletionListener(OnCompletionListener)}.
 *         The invoke of the callback signals that the object is now in the <em>
 *         PlaybackCompleted</em> state.</li>
 *         <li>While in the <em>PlaybackCompleted</em>
 *         state, calling {@link #start()} can restart the playback from the
 *         beginning of the audio/video source.</li>
 * </ul>
 *
 *
 * <a name="Valid_and_Invalid_States"></a>
 * <h3>Valid and invalid states</h3>
 *
 * <table border="0" cellspacing="0" cellpadding="0">
 * <tr><td>Method Name </p></td>
 *     <td>Valid Sates </p></td>
 *     <td>Invalid States </p></td>
 *     <td>Comments </p></td></tr>
 * <tr><td>attachAuxEffect </p></td>
 *     <td>{Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted} </p></td>
 *     <td>{Idle, Error} </p></td>
 *     <td>This method must be called after setDataSource.
 *     Calling it does not change the object state. </p></td></tr>
 * <tr><td>getAudioSessionId </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>getCurrentPosition </p></td>
 *     <td>{Idle, Initialized, Prepared, Started, Paused, Stopped,
 *         PlaybackCompleted} </p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method in a valid state does not change the
 *         state. Calling this method in an invalid state transfers the object
 *         to the <em>Error</em> state. </p></td></tr>
 * <tr><td>getDuration </p></td>
 *     <td>{Prepared, Started, Paused, Stopped, PlaybackCompleted} </p></td>
 *     <td>{Idle, Initialized, Error} </p></td>
 *     <td>Successful invoke of this method in a valid state does not change the
 *         state. Calling this method in an invalid state transfers the object
 *         to the <em>Error</em> state. </p></td></tr>
 * <tr><td>getVideoHeight </p></td>
 *     <td>{Idle, Initialized, Prepared, Started, Paused, Stopped,
 *         PlaybackCompleted}</p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method in a valid state does not change the
 *         state. Calling this method in an invalid state transfers the object
 *         to the <em>Error</em> state.  </p></td></tr>
 * <tr><td>getVideoWidth </p></td>
 *     <td>{Idle, Initialized, Prepared, Started, Paused, Stopped,
 *         PlaybackCompleted}</p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method in a valid state does not change
 *         the state. Calling this method in an invalid state transfers the
 *         object to the <em>Error</em> state. </p></td></tr>
 * <tr><td>isPlaying </p></td>
 *     <td>{Idle, Initialized, Prepared, Started, Paused, Stopped,
 *          PlaybackCompleted}</p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method in a valid state does not change
 *         the state. Calling this method in an invalid state transfers the
 *         object to the <em>Error</em> state. </p></td></tr>
 * <tr><td>pause </p></td>
 *     <td>{Started, Paused}</p></td>
 *     <td>{Idle, Initialized, Prepared, Stopped, PlaybackCompleted, Error}</p></td>
 *     <td>Successful invoke of this method in a valid state transfers the
 *         object to the <em>Paused</em> state. Calling this method in an
 *         invalid state transfers the object to the <em>Error</em> state.</p></td></tr>
 * <tr><td>prepare </p></td>
 *     <td>{Initialized, Stopped} </p></td>
 *     <td>{Idle, Prepared, Started, Paused, PlaybackCompleted, Error} </p></td>
 *     <td>Successful invoke of this method in a valid state transfers the
 *         object to the <em>Prepared</em> state. Calling this method in an
 *         invalid state throws an IllegalStateException.</p></td></tr>
 * <tr><td>prepareAsync </p></td>
 *     <td>{Initialized, Stopped} </p></td>
 *     <td>{Idle, Prepared, Started, Paused, PlaybackCompleted, Error} </p></td>
 *     <td>Successful invoke of this method in a valid state transfers the
 *         object to the <em>Preparing</em> state. Calling this method in an
 *         invalid state throws an IllegalStateException.</p></td></tr>
 * <tr><td>release </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>After {@link #release()}, the object is no longer available. </p></td></tr>
 * <tr><td>reset </p></td>
 *     <td>{Idle, Initialized, Prepared, Started, Paused, Stopped,
 *         PlaybackCompleted, Error}</p></td>
 *     <td>{}</p></td>
 *     <td>After {@link #reset()}, the object is like being just created.</p></td></tr>
 * <tr><td>seekTo </p></td>
 *     <td>{Prepared, Started, Paused, PlaybackCompleted} </p></td>
 *     <td>{Idle, Initialized, Stopped, Error}</p></td>
 *     <td>Successful invoke of this method in a valid state does not change
 *         the state. Calling this method in an invalid state transfers the
 *         object to the <em>Error</em> state. </p></td></tr>
 * <tr><td>setAudioSessionId </p></td>
 *     <td>{Idle} </p></td>
 *     <td>{Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted,
 *          Error} </p></td>
 *     <td>This method must be called in idle state as the audio session ID must be known before
 *         calling setDataSource. Calling it does not change the object state. </p></td></tr>
 * <tr><td>setAudioStreamType </p></td>
 *     <td>{Idle, Initialized, Stopped, Prepared, Started, Paused,
 *          PlaybackCompleted}</p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method does not change the state. In order for the
 *         target audio stream type to become effective, this method must be called before
 *         prepare() or prepareAsync().</p></td></tr>
 * <tr><td>setAuxEffectSendLevel </p></td>
 *     <td>any</p></td>
 *     <td>{} </p></td>
 *     <td>Calling this method does not change the object state. </p></td></tr>
 * <tr><td>setDataSource </p></td>
 *     <td>{Idle} </p></td>
 *     <td>{Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted,
 *          Error} </p></td>
 *     <td>Successful invoke of this method in a valid state transfers the
 *         object to the <em>Initialized</em> state. Calling this method in an
 *         invalid state throws an IllegalStateException.</p></td></tr>
 * <tr><td>setDisplay </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setSurface </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setVideoScalingMode </p></td>
 *     <td>{Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted} </p></td>
 *     <td>{Idle, Error}</p></td>
 *     <td>Successful invoke of this method does not change the state.</p></td></tr>
 * <tr><td>setLooping </p></td>
 *     <td>{Idle, Initialized, Stopped, Prepared, Started, Paused,
 *         PlaybackCompleted}</p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method in a valid state does not change
 *         the state. Calling this method in an
 *         invalid state transfers the object to the <em>Error</em> state.</p></td></tr>
 * <tr><td>isLooping </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setOnBufferingUpdateListener </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setOnCompletionListener </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setOnErrorListener </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setOnPreparedListener </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setOnSeekCompleteListener </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state. </p></td></tr>
 * <tr><td>setScreenOnWhilePlaying</></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state.  </p></td></tr>
 * <tr><td>setVolume </p></td>
 *     <td>{Idle, Initialized, Stopped, Prepared, Started, Paused,
 *          PlaybackCompleted}</p></td>
 *     <td>{Error}</p></td>
 *     <td>Successful invoke of this method does not change the state.
 * <tr><td>setWakeMode </p></td>
 *     <td>any </p></td>
 *     <td>{} </p></td>
 *     <td>This method can be called in any state and calling it does not change
 *         the object state.</p></td></tr>
 * <tr><td>start </p></td>
 *     <td>{Prepared, Started, Paused, PlaybackCompleted}</p></td>
 *     <td>{Idle, Initialized, Stopped, Error}</p></td>
 *     <td>Successful invoke of this method in a valid state transfers the
 *         object to the <em>Started</em> state. Calling this method in an
 *         invalid state transfers the object to the <em>Error</em> state.</p></td></tr>
 * <tr><td>stop </p></td>
 *     <td>{Prepared, Started, Stopped, Paused, PlaybackCompleted}</p></td>
 *     <td>{Idle, Initialized, Error}</p></td>
 *     <td>Successful invoke of this method in a valid state transfers the
 *         object to the <em>Stopped</em> state. Calling this method in an
 *         invalid state transfers the object to the <em>Error</em> state.</p></td></tr>
 * <tr><td>getTrackInfo </p></td>
 *     <td>{Prepared, Started, Stopped, Paused, PlaybackCompleted}</p></td>
 *     <td>{Idle, Initialized, Error}</p></td>
 *     <td>Successful invoke of this method does not change the state.</p></td></tr>
 * <tr><td>addTimedTextSource </p></td>
 *     <td>{Prepared, Started, Stopped, Paused, PlaybackCompleted}</p></td>
 *     <td>{Idle, Initialized, Error}</p></td>
 *     <td>Successful invoke of this method does not change the state.</p></td></tr>
 * <tr><td>selectTrack </p></td>
 *     <td>{Prepared, Started, Stopped, Paused, PlaybackCompleted}</p></td>
 *     <td>{Idle, Initialized, Error}</p></td>
 *     <td>Successful invoke of this method does not change the state.</p></td></tr>
 * <tr><td>deselectTrack </p></td>
 *     <td>{Prepared, Started, Stopped, Paused, PlaybackCompleted}</p></td>
 *     <td>{Idle, Initialized, Error}</p></td>
 *     <td>Successful invoke of this method does not change the state.</p></td></tr>
 *
 * </table>
 *
 * <a name="Permissions"></a>
 * <h3>Permissions</h3>
 * <p>One may need to declare a corresponding WAKE_LOCK permission {@link
 * android.R.styleable#AndroidManifestUsesPermission &lt;uses-permission&gt;}
 * element.
 *
 * <p>This class requires the {@link android.Manifest.permission#INTERNET} permission
 * when used with network-based content.
 *
 * <a name="Callbacks"></a>
 * <h3>Callbacks</h3>
 * <p>Applications may want to register for informational and error
 * events in order to be informed of some internal state update and
 * possible runtime errors during playback or streaming. Registration for
 * these events is done by properly setting the appropriate listeners (via calls
 * to
 * {@link #setOnPreparedListener(OnPreparedListener)}setOnPreparedListener,
 * {@link #setOnVideoSizeChangedListener(OnVideoSizeChangedListener)}setOnVideoSizeChangedListener,
 * {@link #setOnSeekCompleteListener(OnSeekCompleteListener)}setOnSeekCompleteListener,
 * {@link #setOnCompletionListener(OnCompletionListener)}setOnCompletionListener,
 * {@link #setOnBufferingUpdateListener(OnBufferingUpdateListener)}setOnBufferingUpdateListener,
 * {@link #setOnInfoListener(OnInfoListener)}setOnInfoListener,
 * {@link #setOnErrorListener(OnErrorListener)}setOnErrorListener, etc).
 * In order to receive the respective callback
 * associated with these listeners, applications are required to create
 * MediaPlayer objects on a thread with its own Looper running (main UI
 * thread by default has a Looper running).
 *
 */
public class MediaPlayer implements IMediaPlayer
{
    /**
     Constant to retrieve only the new metadata since the last
     call.
     // FIXME: unhide.
     // FIXME: add link to getMetadata(boolean, boolean)
     {@hide}
     */
    public static final boolean METADATA_UPDATE_ONLY = true;
    /**
     Constant to retrieve all the metadata.
     // FIXME: unhide.
     // FIXME: add link to getMetadata(boolean, boolean)
     {@hide}
     */
    public static final boolean METADATA_ALL = false;
    /**
     Constant to enable the metadata filter during retrieval.
     // FIXME: unhide.
     // FIXME: add link to getMetadata(boolean, boolean)
     {@hide}
     */
    public static final boolean APPLY_METADATA_FILTER = true;
    /**
     Constant to disable the metadata filter during retrieval.
     // FIXME: unhide.
     // FIXME: add link to getMetadata(boolean, boolean)
     {@hide}
     */
    public static final boolean BYPASS_METADATA_FILTER = false;
    static {
        System.loadLibrary("avutil");
        System.loadLibrary("avcodec");
        System.loadLibrary("avformat");
        System.loadLibrary("swscale");
        System.loadLibrary("swresample");
        System.loadLibrary("media");

        native_init();
    }
    private final static String TAG = "MediaPlayer";
    // Name of the remote interface for the media player. Must be kept
    // in sync with the 2nd parameter of the IMPLEMENT_META_INTERFACE
    // macro invocation in IMediaPlayer.cpp
    private final static String IMEDIA_PLAYER = "android.media.IMediaPlayer";
    private long mNativeContext; // accessed by native methods
    private int mNativeSurfaceTexture;  // accessed by native methods
    private int mListenerContext; // accessed by native methods
    private SurfaceHolder mSurfaceHolder;
    private EventHandler mEventHandler;
    private PowerManager.WakeLock mWakeLock = null;
    private boolean mScreenOnWhilePlaying;
    private boolean mStayAwake;
    private int mStreamType = AudioManager.USE_DEFAULT_STREAM_TYPE;
    private int mUsage = -1;
    /**
     * Default constructor. Consider using one of the create() methods for
     * synchronously instantiating a MediaPlayer from a Uri or resource.
     * <p>When done with the MediaPlayer, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPlayer instances may
     * result in an exception.</p>
     */
    public MediaPlayer() {
        Looper looper;
        if ((looper = Looper.myLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            mEventHandler = new EventHandler(this, looper);
        } else {
            mEventHandler = null;
        }
        /* Native setup requires a weak reference to our object.
         * It's easier to create it here than in C++.
         */

        long result = native_setup(new WeakReference<MediaPlayer>(this));
        if(result == -1) {
            throw new IllegalStateException("Unable to intanciate native code");
        }
        mNativeContext = result;
    }
    /*
     * Update the MediaPlayer SurfaceTexture.
     * Call after setting a new display surface.
     */
    private native void _setVideoSurface(Surface surface);
    /* Do not change these values (starting with INVOKE_ID) without updating
     * their counterparts in include/media/mediaplayer.h!
     */
    private static final int INVOKE_ID_GET_TRACK_INFO = 1;
    private static final int INVOKE_ID_ADD_EXTERNAL_SOURCE = 2;
    private static final int INVOKE_ID_ADD_EXTERNAL_SOURCE_FD = 3;
    private static final int INVOKE_ID_SELECT_TRACK = 4;
    private static final int INVOKE_ID_DESELECT_TRACK = 5;
    private static final int INVOKE_ID_SET_VIDEO_SCALE_MODE = 6;
    private static final int INVOKE_ID_GET_SELECTED_TRACK = 7;
    /**
     * Create a request parcel which can be routed to the native media
     * player using {@link #invoke(Parcel, Parcel)}. The Parcel
     * returned has the proper InterfaceToken set. The caller should
     * not overwrite that token, i.e it can only append data to the
     * Parcel.
     *
     * @return A parcel suitable to hold a request for the native
     * player.
     * {@hide}
     */
    // TODO Remove this method (it seems to be useless)
    public Parcel newRequest() {
        Parcel parcel = Parcel.obtain();
        parcel.writeInterfaceToken(IMEDIA_PLAYER);
        return parcel;
    }
    /**
     * Invoke a generic method on the native player using opaque
     * parcels for the request and reply. Both payloads' format is a
     * convention between the java caller and the native player.
     * Must be called after setDataSource to make sure a native player
     * exists. On failure, a RuntimeException is thrown.
     *
     * @param request Parcel with the data for the extension. The
     * caller must use {@link #newRequest()} to get one.
     *
     * @param reply Output parcel with the data returned by the
     * native player.
     *
     * {@hide}
     */
    // TODO Remove this method (it seems to be useless)
    public void invoke(Parcel request, Parcel reply) {
        int retcode = native_invoke(request, reply);
        reply.setDataPosition(0);
        if (retcode != 0) {
            throw new RuntimeException("failure code: " + retcode);
        }
    }
    /**
     * Sets the {@link SurfaceHolder} to use for displaying the video
     * portion of the media.
     *
     * Either a surface holder or surface must be set if a display or video sink
     * is needed.  Not calling this method or {@link #setSurface(Surface)}
     * when playing back a video will result in only the audio track being played.
     * A null surface holder or surface will result in only the audio track being
     * played.
     *
     * @param sh the SurfaceHolder to use for video display
     */
    public void setDisplay(SurfaceHolder sh) {
        mSurfaceHolder = sh;
        Surface surface;
        if (sh != null) {
            surface = sh.getSurface();
        } else {
            surface = null;
        }
        _setVideoSurface(surface);
        updateSurfaceScreenOn();
    }
    /**
     * Sets the {@link Surface} to be used as the sink for the video portion of
     * the media. This is similar to {@link #setDisplay(SurfaceHolder)}, but
     * does not support {@link #setScreenOnWhilePlaying(boolean)}.  Setting a
     * Surface will un-set any Surface or SurfaceHolder that was previously set.
     * A null surface will result in only the audio track being played.
     *
     * If the Surface sends frames to a {@link SurfaceTexture}, the timestamps
     * returned from {@link SurfaceTexture#getTimestamp()} will have an
     * unspecified zero point.  These timestamps cannot be directly compared
     * between different media sources, different instances of the same media
     * source, or multiple runs of the same program.  The timestamp is normally
     * monotonically increasing and is unaffected by time-of-day adjustments,
     * but it is reset when the position is set.
     *
     * @param surface The {@link Surface} to be used for the video portion of
     * the media.
     */
    public void setSurface(Surface surface) {
        if (mScreenOnWhilePlaying && surface != null) {
            Log.w(TAG, "setScreenOnWhilePlaying(true) is ineffective for Surface");
        }
        mSurfaceHolder = null;
        _setVideoSurface(surface);
        updateSurfaceScreenOn();
    }
    /* Do not change these video scaling mode values below without updating
     * their counterparts in system/window.h! Please do not forget to update
     * {@link #isVideoScalingModeSupported} when new video scaling modes
     * are added.
     */
    /**
     * Specifies a video scaling mode. The content is stretched to the
     * surface rendering area. When the surface has the same aspect ratio
     * as the content, the aspect ratio of the content is maintained;
     * otherwise, the aspect ratio of the content is not maintained when video
     * is being rendered. Unlike {@link #VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING},
     * there is no content cropping with this video scaling mode.
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT = 1;
    /**
     * Specifies a video scaling mode. The content is scaled, maintaining
     * its aspect ratio. The whole surface area is always used. When the
     * aspect ratio of the content is the same as the surface, no content
     * is cropped; otherwise, content is cropped to fit the surface.
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING = 2;
    /**
     * Sets video scaling mode. To make the target video scaling mode
     * effective during playback, this method must be called after
     * data source is set. If not called, the default video
     * scaling mode is {@link #VIDEO_SCALING_MODE_SCALE_TO_FIT}.
     *
     * <p> The supported video scaling modes are:
     * <ul>
     * <li> {@link #VIDEO_SCALING_MODE_SCALE_TO_FIT}
     * <li> {@link #VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING}
     * </ul>
     *
     * @param mode target video scaling mode. Most be one of the supported
     * video scaling modes; otherwise, IllegalArgumentException will be thrown.
     *
     * @see MediaPlayer#VIDEO_SCALING_MODE_SCALE_TO_FIT
     * @see MediaPlayer#VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
     */
    public void setVideoScalingMode(int mode) {
        if (!isVideoScalingModeSupported(mode)) {
            final String msg = "Scaling mode " + mode + " is not supported";
            throw new IllegalArgumentException(msg);
        }
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(IMEDIA_PLAYER);
            request.writeInt(INVOKE_ID_SET_VIDEO_SCALE_MODE);
            request.writeInt(mode);
            invoke(request, reply);
        } finally {
            request.recycle();
            reply.recycle();
        }
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
     * @return a MediaPlayer object, or null if creation failed
     */
    public static MediaPlayer create(Context context, Uri uri) {
        return create (context, uri, null);
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
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        int s = audioManager.generateAudioSessionId();
        return create(context, uri, holder, null, s > 0 ? s : 0);
    }
    // Note no convenience method to create a MediaPlayer with SurfaceTexture sink.
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
        try {
            MediaPlayer mp = new MediaPlayer();
            final AudioAttributes aa = audioAttributes != null ? audioAttributes :
                    new AudioAttributes.Builder().build();
            // mp.setAudioAttributes(aa); FIXME
            mp.setAudioSessionId(audioSessionId);
            mp.setDataSource(context, uri);
            if (holder != null) {
                mp.setDisplay(holder);
            }
            mp.prepare();
            return mp;
        } catch (IOException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (SecurityException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        }
        return null;
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
        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        int s = audioManager.generateAudioSessionId();
        return create(context, resid, null, s > 0 ? s : 0);
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
        try {
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(resid);
            if (afd == null) return null;
            MediaPlayer mp = new MediaPlayer();
            final AudioAttributes aa = audioAttributes != null ? audioAttributes :
                    new AudioAttributes.Builder().build();
            // mp.setAudioAttributes(aa); FIXME
            mp.setAudioSessionId(audioSessionId);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            return mp;
        } catch (IOException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        } catch (SecurityException ex) {
            Log.d(TAG, "create failed:", ex);
            // fall through
        }
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
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(context, uri, null);
    }
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
            throws IOException {
        if (context == null) {
            throw new NullPointerException("context param can not be null.");
        }
        if (uri == null) {
            throw new NullPointerException("uri param can not be null.");
        }
        if (cookies != null) {
            CookieHandler cookieHandler = CookieHandler.getDefault();
            if (cookieHandler != null && !(cookieHandler instanceof CookieManager)) {
                throw new IllegalArgumentException("The cookie handler has to be of CookieManager "
                        + "type when cookies are provided.");
            }
        }
        // The context and URI usually belong to the calling user. Get a resolver for that user
        // and strip out the userId from the URI if present.
        final ContentResolver resolver = context.getContentResolver();
        final String scheme = uri.getScheme();
        final String authority = MediaPlayer.getAuthorityWithoutUserId(uri.getAuthority());
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            setDataSource(uri.getPath());
            return;
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)
                && Settings.AUTHORITY.equals(authority)) {
            // Try cached ringtone first since the actual provider may not be
            // encryption aware, or it may be stored on CE media storage
            final int type = RingtoneManager.getDefaultType(uri);
            //final Uri cacheUri = RingtoneManager.getCacheForType(type, context.getUserId());
            final Uri actualUri = RingtoneManager.getActualDefaultRingtoneUri(context, type);
            if(attemptDataSource(resolver, actualUri)) {
                return;
            } else {
                setDataSource(uri.toString(), headers, cookies);
            }
            /*if (attemptDataSource(resolver, cacheUri)) {
                return;
            } else if (attemptDataSource(resolver, actualUri)) {
                return;
            } else {
                setDataSource(uri.toString(), headers, cookies);
            }*/
        } else {
            // Try requested Uri locally first, or fallback to media server
            if (attemptDataSource(resolver, uri)) {
                return;
            } else {
                setDataSource(uri.toString(), headers, cookies);
            }
        }
    }
    private static String getAuthorityWithoutUserId(String auth) {
        if (auth == null) return null;
        int end = auth.lastIndexOf('@');
        return auth.substring(end+1);
    }
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
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(context, uri, headers, null);
    }
    private boolean attemptDataSource(ContentResolver resolver, Uri uri) {
        try (AssetFileDescriptor afd = resolver.openAssetFileDescriptor(uri, "r")) {
            setDataSource(afd);
            return true;
        } catch (NullPointerException | SecurityException | IOException ex) {
            Log.w(TAG, "Couldn't open " + (uri == null ? "null uri" : UriCompat.toSafeString(uri)), ex);
            return false;
        }
    }
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
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(path, null, null);
    }
    /**
     * Sets the data source (file-path or http/rtsp URL) to use.
     *
     * @param path the path of the file, or the http/rtsp URL of the stream you want to play
     * @param headers the headers associated with the http request for the stream you want to play
     * @throws IllegalStateException if it is called in an invalid state
     * @hide pending API council
     */
    public void setDataSource(String path, Map<String, String> headers)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        setDataSource(path, headers, null);
    }
    private void setDataSource(String path, Map<String, String> headers, List<HttpCookie> cookies)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException
    {
        String[] keys = null;
        String[] values = null;
        if (headers != null) {
            keys = new String[headers.size()];
            values = new String[headers.size()];
            int i = 0;
            for (Map.Entry<String, String> entry: headers.entrySet()) {
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                ++i;
            }
        }
        setDataSource(path, keys, values, cookies);
    }
    private void setDataSource(String path, String[] keys, String[] values,
                               List<HttpCookie> cookies)
            throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
        final Uri uri = Uri.parse(path);
        final String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            path = uri.getPath();
        } else if (scheme != null) {
            // handle non-file sources
            nativeSetDataSource(path, keys, values);
            return;
        }
        nativeSetDataSource(path, keys, values);
        // TODO File desriptor break getDuration.
        /*final File file = new File(path);
        try (FileInputStream is = new FileInputStream(file)) {
            setDataSource(is.getFD());
        }*/
    }
    private native void nativeSetDataSource(String path, String[] keys, String[] values)
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
            throws IOException, IllegalArgumentException, IllegalStateException {
        Preconditions.checkNotNull(afd);
        // Note: using getDeclaredLength so that our behavior is the same
        // as previous versions when the content provider is returning
        // a full file.
        if (afd.getDeclaredLength() < 0) {
            setDataSource(afd.getFileDescriptor());
        } else {
            setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getDeclaredLength());
        }
    }
    /**
     * Sets the data source (FileDescriptor) to use. It is the caller's responsibility
     * to close the file descriptor. It is safe to do so as soon as this call returns.
     *
     * @param fd the FileDescriptor for the file you want to play
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void setDataSource(FileDescriptor fd)
            throws IOException, IllegalArgumentException, IllegalStateException {
        // intentionally less than LONG_MAX
        setDataSource(fd, 0, 0x7ffffffffffffffL);
    }
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
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException {
        _setDataSource(fd, offset, length);
    }
    private native void _setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException;
    /**
     * Sets the data source (MediaDataSource) to use.
     *
     * @param dataSource the MediaDataSource for the media you want to play
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if dataSource is not a valid MediaDataSource
     */
    // TODO
    public void setDataSource(MediaDataSource dataSource)
            throws IllegalArgumentException, IllegalStateException {
        _setDataSource(dataSource);
    }
    private native void _setDataSource(MediaDataSource dataSource)
            throws IllegalArgumentException, IllegalStateException;

    /**
     * Prepares the player for playback, synchronously.
     *
     * After setting the datasource and the display surface, you need to either
     * call prepare() or prepareAsync(). For files, it is OK to call prepare(),
     * which blocks until MediaPlayer is ready for playback.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void prepare() throws IOException, IllegalStateException {
        _prepare();
    }
    private native void _prepare() throws IOException, IllegalStateException;
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
    public native void prepareAsync() throws IllegalStateException;
    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * been stopped, or never started before, playback will start at the
     * beginning.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void start() throws IllegalStateException {
        try {
            startImpl();
        } catch (IllegalStateException e) {
            // fail silently for a state exception when it is happening after
            // a delayed start, as the player state could have changed between the
            // call to start() and the execution of startImpl()
        }
    }
    private void startImpl() {
        stayAwake(true);
        _start();
    }
    private native void _start() throws IllegalStateException;

    private int getAudioStreamType() {
        if (mStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
            mStreamType = _getAudioStreamType();
        }
        return mStreamType;
    }
    private native int _getAudioStreamType() throws IllegalStateException;
    /**
     * Stops playback after playback has been stopped or paused.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    public void stop() throws IllegalStateException {
        stayAwake(false);
        _stop();
    }
    private native void _stop() throws IllegalStateException;
    /**
     * Pauses playback. Call start() to resume.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    public void pause() throws IllegalStateException {
        stayAwake(false);
        _pause();
    }
    private native void _pause() throws IllegalStateException;

    private AudioDeviceInfo mPreferredDevice = null;
    /**
     * Specifies an audio device (via an {@link AudioDeviceInfo} object) to route
     * the output from this MediaPlayer.
     * @param deviceInfo The {@link AudioDeviceInfo} specifying the audio sink or source.
     *  If deviceInfo is null, default routing is restored.
     * @return true if succesful, false if the specified {@link AudioDeviceInfo} is non-null and
     * does not correspond to a valid audio device.
     */
    public boolean setPreferredDevice(AudioDeviceInfo deviceInfo) {

        // isSink and getId require API 23
        if (deviceInfo != null && !deviceInfo.isSink()) {
            return false;
        }
        int preferredDeviceId = deviceInfo != null ? deviceInfo.getId() : 0;
        boolean status = native_setOutputDevice(preferredDeviceId);
        if (status == true) {
            synchronized (this) {
                mPreferredDevice = deviceInfo;
            }
        }
        return status;
    }
    /**
     * Returns the selected output specified by {@link #setPreferredDevice}. Note that this
     * is not guaranteed to correspond to the actual device being used for playback.
     */
    public AudioDeviceInfo getPreferredDevice() {
        synchronized (this) {
            return mPreferredDevice;
        }
    }
    /**
     * Returns an {@link AudioDeviceInfo} identifying the current routing of this MediaPlayer
     * Note: The query is only valid if the MediaPlayer is currently playing.
     * If the player is not playing, the returned device can be null or correspond to previously
     * selected device when the player was last active.
     */
    // TODO
    public AudioDeviceInfo getRoutedDevice() {
        int deviceId = native_getRoutedDeviceId();
        if (deviceId == 0) {
            return null;
        }
        //AudioDeviceInfo[] devices = AudioManager.getDevicesStatic(AudioManager.GET_DEVICES_OUTPUTS);
        AudioDeviceInfo[] devices = native_getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if(devices == null) {
            return null;
        }
        for (int i = 0; i < devices.length; i++) {
            if (devices[i].getId() == deviceId) {
                return devices[i];
            }
        }
        return null;
    }

    private static native final AudioDeviceInfo[] native_getDevices(int flags);

    private native final boolean native_setOutputDevice(int deviceId);
    private native final int native_getRoutedDeviceId();
    private native final void native_enableDeviceCallback(boolean enabled);
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
    public void setWakeMode(Context context, int mode) {
        boolean washeld = false;
        /* Disable persistant wakelocks in media player based on property */
        /*if (SystemPropertiesProxy.getBoolean(context,"audio.offload.ignore_setawake", false) == true) {
            Log.w(TAG, "IGNORING setWakeMode " + mode);
            return;
        }*/
        if (mWakeLock != null) {
            if (mWakeLock.isHeld()) {
                washeld = true;
                mWakeLock.release();
            }
            mWakeLock = null;
        }
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode|PowerManager.ON_AFTER_RELEASE, MediaPlayer.class.getName());
        mWakeLock.setReferenceCounted(false);
        if (washeld) {
            mWakeLock.acquire();
        }
    }
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
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (mScreenOnWhilePlaying != screenOn) {
            if (screenOn && mSurfaceHolder == null) {
                Log.w(TAG, "setScreenOnWhilePlaying(true) is ineffective without a SurfaceHolder");
            }
            mScreenOnWhilePlaying = screenOn;
            updateSurfaceScreenOn();
        }
    }
    private void stayAwake(boolean awake) {
        if (mWakeLock != null) {
            if (awake && !mWakeLock.isHeld()) {
                mWakeLock.acquire();
            } else if (!awake && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        mStayAwake = awake;
        updateSurfaceScreenOn();
    }
    private void updateSurfaceScreenOn() {
        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(mScreenOnWhilePlaying && mStayAwake);
        }
    }
    /**
     * Returns the width of the video.
     *
     * @return the width of the video, or 0 if there is no video,
     * no display surface was set, or the width has not been determined
     * yet. The OnVideoSizeChangedListener can be registered via
     * {@link #setOnVideoSizeChangedListener(OnVideoSizeChangedListener)}
     * to provide a notification when the width is available.
     */
    public native int getVideoWidth();
    /**
     * Returns the height of the video.
     *
     * @return the height of the video, or 0 if there is no video,
     * no display surface was set, or the height has not been determined
     * yet. The OnVideoSizeChangedListener can be registered via
     * {@link #setOnVideoSizeChangedListener(OnVideoSizeChangedListener)}
     * to provide a notification when the height is available.
     */
    public native int getVideoHeight();
    /**
     * Return Metrics data about the current player.
     *
     * @return a {@link PersistableBundle} containing the set of attributes and values
     * available for the media being handled by this instance of MediaPlayer
     * The attributes are descibed in {@link MetricsConstants}.
     *
     *  Additional vendor-specific fields may also be present in
     *  the return value.
     */
    public PersistableBundle getMetrics() {
        PersistableBundle bundle = native_getMetrics();
        return bundle;
    }
    private native PersistableBundle native_getMetrics();
    /**
     * Checks whether the MediaPlayer is playing.
     *
     * @return true if currently playing, false otherwise
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     */
    public native boolean isPlaying();
    /**
     * Change playback speed of audio by resampling the audio.
     * <p>
     * Specifies resampling as audio mode for variable rate playback, i.e.,
     * resample the waveform based on the requested playback rate to get
     * a new waveform, and play back the new waveform at the original sampling
     * frequency.
     * When rate is larger than 1.0, pitch becomes higher.
     * When rate is smaller than 1.0, pitch becomes lower.
     *
     * @hide
     */
    public static final int PLAYBACK_RATE_AUDIO_MODE_RESAMPLE = 2;
    /**
     * Change playback speed of audio without changing its pitch.
     * <p>
     * Specifies time stretching as audio mode for variable rate playback.
     * Time stretching changes the duration of the audio samples without
     * affecting its pitch.
     * <p>
     * This mode is only supported for a limited range of playback speed factors,
     * e.g. between 1/2x and 2x.
     *
     * @hide
     */
    public static final int PLAYBACK_RATE_AUDIO_MODE_STRETCH = 1;
    /**
     * Change playback speed of audio without changing its pitch, and
     * possibly mute audio if time stretching is not supported for the playback
     * speed.
     * <p>
     * Try to keep audio pitch when changing the playback rate, but allow the
     * system to determine how to change audio playback if the rate is out
     * of range.
     *
     * @hide
     */
    public static final int PLAYBACK_RATE_AUDIO_MODE_DEFAULT = 0;
    /** @hide */
    @IntDef(
            value = {
                    PLAYBACK_RATE_AUDIO_MODE_DEFAULT,
                    PLAYBACK_RATE_AUDIO_MODE_STRETCH,
                    PLAYBACK_RATE_AUDIO_MODE_RESAMPLE,
            })
    public @interface PlaybackRateAudioMode {}
    /**
     * Sets playback rate and audio mode.
     *
     * @param rate the ratio between desired playback rate and normal one.
     * @param audioMode audio playback mode. Must be one of the supported
     * audio modes.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     * @throws IllegalArgumentException if audioMode is not supported.
     *
     * @hide
     */
    @NonNull
    public PlaybackParams easyPlaybackParams(float rate, @PlaybackRateAudioMode int audioMode) {
        PlaybackParams params = new PlaybackParams();
        params.allowDefaults();
        switch (audioMode) {
            case PLAYBACK_RATE_AUDIO_MODE_DEFAULT:
                params.setSpeed(rate).setPitch(1.0f);
                break;
            case PLAYBACK_RATE_AUDIO_MODE_STRETCH:
                params.setSpeed(rate).setPitch(1.0f)
                        .setAudioFallbackMode(params.AUDIO_FALLBACK_MODE_FAIL);
                break;
            case PLAYBACK_RATE_AUDIO_MODE_RESAMPLE:
                params.setSpeed(rate).setPitch(rate);
                break;
            default:
                final String msg = "Audio playback mode " + audioMode + " is not supported";
                throw new IllegalArgumentException(msg);
        }
        return params;
    }
    /**
     * Sets playback rate using {@link PlaybackParams}. The object sets its internal
     * PlaybackParams to the input, except that the object remembers previous speed
     * when input speed is zero. This allows the object to resume at previous speed
     * when start() is called. Calling it before the object is prepared does not change
     * the object state. After the object is prepared, calling it with zero speed is
     * equivalent to calling pause(). After the object is prepared, calling it with
     * non-zero speed is equivalent to calling start().
     *
     * @param params the playback params.
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     * @throws IllegalArgumentException if params is not supported.
     */
    public native void setPlaybackParams(@NonNull PlaybackParams params);
    /**
     * Gets the playback params, containing the current playback rate.
     *
     * @return the playback params.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    @NonNull
    public native PlaybackParams getPlaybackParams();
    /**
     * Sets A/V sync mode.
     *
     * @param params the A/V sync params to apply
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     * @throws IllegalArgumentException if params are not supported.
     */
    public native void setSyncParams(@NonNull SyncParams params);
    /**
     * Gets the A/V sync mode.
     *
     * @return the A/V sync params
     *
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    @NonNull
    public native SyncParams getSyncParams();
    /**
     * Seek modes used in method seekTo(long, int) to move media position
     * to a specified location.
     *
     * Do not change these mode values without updating their counterparts
     * in include/media/IMediaSource.h!
     */
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a sync (or key) frame associated with a data source that is located
     * right before or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_PREVIOUS_SYNC    = 0x00;
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a sync (or key) frame associated with a data source that is located
     * right after or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_NEXT_SYNC        = 0x01;
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a sync (or key) frame associated with a data source that is located
     * closest to (in time) or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_CLOSEST_SYNC     = 0x02;
    /**
     * This mode is used with {@link #seekTo(long, int)} to move media position to
     * a frame (not necessarily a key frame) associated with a data source that
     * is located closest to or at the given time.
     *
     * @see #seekTo(long, int)
     */
    public static final int SEEK_CLOSEST          = 0x03;
    /** @hide */
    @IntDef(
            value = {
                    SEEK_PREVIOUS_SYNC,
                    SEEK_NEXT_SYNC,
                    SEEK_CLOSEST_SYNC,
                    SEEK_CLOSEST,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SeekMode {}
    private native final void _seekTo(long msec, int mode);
    /**
     * Moves the media to specified time position by considering the given mode.
     * <p>
     * When seekTo is finished, the user will be notified via OnSeekComplete supplied by the user.
     * There is at most one active seekTo processed at any time. If there is a to-be-completed
     * seekTo, new seekTo requests will be queued in such a way that only the last request
     * is kept. When current seekTo is completed, the queued request will be processed if
     * that request is different from just-finished seekTo operation, i.e., the requested
     * position or mode is different.
     *
     * @param msec the offset in milliseconds from the start to seek to.
     * When seeking to the given time position, there is no guarantee that the data source
     * has a frame located at the position. When this happens, a frame nearby will be rendered.
     * If msec is negative, time position zero will be used.
     * If msec is larger than duration, duration will be used.
     * @param mode the mode indicating where exactly to seek to.
     * Use {@link #SEEK_PREVIOUS_SYNC} if one wants to seek to a sync frame
     * that has a timestamp earlier than or the same as msec. Use
     * {@link #SEEK_NEXT_SYNC} if one wants to seek to a sync frame
     * that has a timestamp later than or the same as msec. Use
     * {@link #SEEK_CLOSEST_SYNC} if one wants to seek to a sync frame
     * that has a timestamp closest to or the same as msec. Use
     * {@link #SEEK_CLOSEST} if one wants to seek to a frame that may
     * or may not be a sync frame but is closest to or the same as msec.
     * {@link #SEEK_CLOSEST} often has larger performance overhead compared
     * to the other options if there is no sync frame located at msec.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized
     * @throws IllegalArgumentException if the mode is invalid.
     */
    public void seekTo(long msec, @SeekMode int mode) {
        if (mode < SEEK_PREVIOUS_SYNC || mode > SEEK_CLOSEST) {
            final String msg = "Illegal seek mode: " + mode;
            throw new IllegalArgumentException(msg);
        }
        // TODO: pass long to native, instead of truncating here.
        if (msec > Integer.MAX_VALUE) {
            Log.w(TAG, "seekTo offset " + msec + " is too large, cap to " + Integer.MAX_VALUE);
            msec = Integer.MAX_VALUE;
        } else if (msec < Integer.MIN_VALUE) {
            Log.w(TAG, "seekTo offset " + msec + " is too small, cap to " + Integer.MIN_VALUE);
            msec = Integer.MIN_VALUE;
        }
        _seekTo(msec, mode);
    }
    /**
     * Seeks to specified time position.
     * Same as {@link #seekTo(long, int)} with {@code mode = SEEK_PREVIOUS_SYNC}.
     *
     * @param msec the offset in milliseconds from the start to seek to
     * @throws IllegalStateException if the internal player engine has not been
     * initialized
     */
    public void seekTo(int msec) throws IllegalStateException {
        seekTo(msec, SEEK_PREVIOUS_SYNC /* mode */);
    }
    /**
     * Get current playback position as a {@link MediaTimestamp}.
     * <p>
     * The MediaTimestamp represents how the media time correlates to the system time in
     * a linear fashion using an anchor and a clock rate. During regular playback, the media
     * time moves fairly constantly (though the anchor frame may be rebased to a current
     * system time, the linear correlation stays steady). Therefore, this method does not
     * need to be called often.
     * <p>
     * To help users get current playback position, this method always anchors the timestamp
     * to the current {@link System#nanoTime system time}, so
     * {@link MediaTimestamp#getAnchorMediaTimeUs} can be used as current playback position.
     *
     * @return a MediaTimestamp object if a timestamp is available, or {@code null} if no timestamp
     *         is available, e.g. because the media player has not been initialized.
     *
     * @see MediaTimestamp
     */
    @Nullable
    public MediaTimestamp getTimestamp() {
        try {
            // TODO: get the timestamp from native side
            return new MediaTimestamp(
                    getCurrentPosition() * 1000L,
                    System.nanoTime(),
                    isPlaying() ? getPlaybackParams().getSpeed() : 0.f);
        } catch (IllegalStateException e) {
            return null;
        }
    }
    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public native int getCurrentPosition();
    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    public native int getDuration();
    /**
     * Set a filter for the metadata update notification and update
     * retrieval. The caller provides 2 set of metadata keys, allowed
     * and blocked. The blocked set always takes precedence over the
     * allowed one.
     * Metadata.MATCH_ALL and Metadata.MATCH_NONE are 2 sets available as
     * shorthands to allow/block all or no metadata.
     *
     * By default, there is no filter set.
     *
     * @param allow Is the set of metadata the client is interested
     *              in receiving new notifications for.
     * @param block Is the set of metadata the client is not interested
     *              in receiving new notifications for.
     * @return The call status code.
     *
    // FIXME: unhide.
     * {@hide}
     */
    // TODO Remove this method (it seems to be useless,  actually, maybe another library ?)
    public int setMetadataFilter(Set<Integer> allow, Set<Integer> block) {
        // Do our serialization manually instead of calling
        // Parcel.writeArray since the sets are made of the same type
        // we avoid paying the price of calling writeValue (used by
        // writeArray) which burns an extra int per element to encode
        // the type.
        Parcel request =  newRequest();
        // The parcel starts already with an interface token. There
        // are 2 filters. Each one starts with a 4bytes number to
        // store the len followed by a number of int (4 bytes as well)
        // representing the metadata type.
        int capacity = request.dataSize() + 4 * (1 + allow.size() + 1 + block.size());
        if (request.dataCapacity() < capacity) {
            request.setDataCapacity(capacity);
        }
        request.writeInt(allow.size());
        for(Integer t: allow) {
            request.writeInt(t);
        }
        request.writeInt(block.size());
        for(Integer t: block) {
            request.writeInt(t);
        }
        return native_setMetadataFilter(request);
    }
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
    public native void setNextMediaPlayer(MediaPlayer next);
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
    public void release() {
        stayAwake(false);
        updateSurfaceScreenOn();
        mOnPreparedListener = null;
        mOnBufferingUpdateListener = null;
        mOnCompletionListener = null;
        mOnSeekCompleteListener = null;
        mOnErrorListener = null;
        mOnInfoListener = null;
        mOnVideoSizeChangedListener = null;
        synchronized (mTimeProviderLock) {
            if (mTimeProvider != null) {
                mTimeProvider.close();
                mTimeProvider = null;
            }
        }
        synchronized(this) {
            mOnMediaTimeDiscontinuityListener = null;
            mOnMediaTimeDiscontinuityHandler = null;
        }

        _release();
    }
    private native void _release();
    /**
     * Resets the MediaPlayer to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * data source and calling prepare().
     */
    public void reset() {
        synchronized (mTimeProviderLock) {
            if (mTimeProvider != null) {
                mTimeProvider.close();
                mTimeProvider = null;
            }
        }
        stayAwake(false);
        _reset();
        // make sure none of the listeners get called anymore
        if (mEventHandler != null) {
            mEventHandler.removeCallbacksAndMessages(null);
        }
    }
    private native void _reset();
    /**
     * Set up a timer for {@link #TimeProvider}. {@link #TimeProvider} will be
     * notified when the presentation time reaches (becomes greater than or equal to)
     * the value specified.
     *
     * @param mediaTimeUs presentation time to get timed event callback at
     * @hide
     */
    public void notifyAt(long mediaTimeUs) {
        _notifyAt(mediaTimeUs);
    }
    private native void _notifyAt(long mediaTimeUs);
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
    public void setAudioStreamType(int streamtype) {
        _setAudioStreamType(streamtype);
        mStreamType = streamtype;
    }
    private native void _setAudioStreamType(int streamtype);
    // Keep KEY_PARAMETER_* in sync with include/media/mediaplayer.h
    private final static int KEY_PARAMETER_AUDIO_ATTRIBUTES = 1400;
    /**
     * Sets the parameter indicated by key.
     * @param key key indicates the parameter to be set.
     * @param value value of the parameter to be set.
     * @return true if the parameter is set successfully, false otherwise
     * {@hide}
     */
    private native boolean setParameter(int key, Parcel value);
    /**
     * Sets the audio attributes for this MediaPlayer.
     * See {@link AudioAttributes} for how to build and configure an instance of this class.
     * You must call this method before {@link #prepare()} or {@link #prepareAsync()} in order
     * for the audio attributes to become effective thereafter.
     * @param attributes a non-null set of audio attributes
     */
    public void setAudioAttributes(AudioAttributes attributes) throws IllegalArgumentException {
        if (attributes == null) {
            final String msg = "Cannot set AudioAttributes to null";
            throw new IllegalArgumentException(msg);
        }
        mUsage = attributes.getUsage();
        Parcel pattributes = Parcel.obtain();
        // AudioAttributes.FLATTEN_TAGS = 0x1;
        attributes.writeToParcel(pattributes, 0x1);
        setParameter(KEY_PARAMETER_AUDIO_ATTRIBUTES, pattributes);
        pattributes.recycle();
    }
    /**
     * Sets the player to be looping or non-looping.
     *
     * @param looping whether to loop or not
     */
    public native void setLooping(boolean looping);
    /**
     * Checks whether the MediaPlayer is looping or non-looping.
     *
     * @return true if the MediaPlayer is currently looping, false otherwise
     */
    public native boolean isLooping();
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
    public void setVolume(float leftVolume, float rightVolume) {
        _setVolume(leftVolume, rightVolume);
    }
    private native void _setVolume(float leftVolume, float rightVolume);
    /**
     * Similar, excepts sets volume of all channels to same value.
     * @hide
     */
    public void setVolume(float volume) {
        setVolume(volume, volume);
    }
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
    public native void setAudioSessionId(int sessionId)  throws IllegalArgumentException, IllegalStateException;
    /**
     * Returns the audio session ID.
     *
     * @return the audio session ID. {@see #setAudioSessionId(int)}
     * Note that the audio session ID is 0 only if a problem occured when the MediaPlayer was contructed.
     */
    public native int getAudioSessionId();
    /*
     * @param request Parcel destinated to the media player. The
     *                Interface token must be set to the IMediaPlayer
     *                one to be routed correctly through the system.
     * @param reply[out] Parcel that will contain the reply.
     * @return The status code.
     */
    private native final int native_invoke(Parcel request, Parcel reply);
    /*
     * @param update_only If true fetch only the set of metadata that have
     *                    changed since the last invocation of getMetadata.
     *                    The set is built using the unfiltered
     *                    notifications the native player sent to the
     *                    MediaPlayerService during that period of
     *                    time. If false, all the metadatas are considered.
     * @param apply_filter  If true, once the metadata set has been built based on
     *                     the value update_only, the current filter is applied.
     * @param reply[out] On return contains the serialized
     *                   metadata. Valid only if the call was successful.
     * @return The status code.
     */
    // TODO Remove this method (it seems to be useless,  actually, maybe another library ?)
    private native final boolean native_getMetadata(boolean update_only,
                                                    boolean apply_filter,
                                                    Parcel reply);
    /*
     * @param request Parcel with the 2 serialized lists of allowed
     *                metadata types followed by the one to be
     *                dropped. Each list starts with an integer
     *                indicating the number of metadata type elements.
     * @return The status code.
     */
    private native final int native_setMetadataFilter(Parcel request);

    private static native final void native_init();
    private native final long native_setup(Object mediaplayer_this);
    private native final void native_finalize();
    /**
     * Class for MediaPlayer to return each audio/video/subtitle track's metadata.
     *
     * @see android.media.MediaPlayer#getTrackInfo
     */
    static public class TrackInfo implements Parcelable {
        /**
         * Gets the track type.
         * @return TrackType which indicates if the track is video, audio, timed text.
         */
        public @TrackType int getTrackType() {
            return mTrackType;
        }
        /**
         * Gets the language code of the track.
         * @return a language code in either way of ISO-639-1 or ISO-639-2.
         * When the language is unknown or could not be determined,
         * ISO-639-2 language code, "und", is returned.
         */
        public String getLanguage() {
            String language = mFormat.getString(MediaFormat.KEY_LANGUAGE);
            return language == null ? "und" : language;
        }
        /**
         * Gets the {@link MediaFormat} of the track.  If the format is
         * unknown or could not be determined, null is returned.
         */
        public MediaFormat getFormat() {
            if (mTrackType == MEDIA_TRACK_TYPE_TIMEDTEXT
                    || mTrackType == MEDIA_TRACK_TYPE_SUBTITLE) {
                return mFormat;
            }
            return null;
        }
        public static final int MEDIA_TRACK_TYPE_UNKNOWN = 0;
        public static final int MEDIA_TRACK_TYPE_VIDEO = 1;
        public static final int MEDIA_TRACK_TYPE_AUDIO = 2;
        public static final int MEDIA_TRACK_TYPE_TIMEDTEXT = 3;
        public static final int MEDIA_TRACK_TYPE_SUBTITLE = 4;
        public static final int MEDIA_TRACK_TYPE_METADATA = 5;
        /** @hide */
        @IntDef(flag = false, value = {
                MEDIA_TRACK_TYPE_UNKNOWN,
                MEDIA_TRACK_TYPE_VIDEO,
                MEDIA_TRACK_TYPE_AUDIO,
                MEDIA_TRACK_TYPE_TIMEDTEXT,
                MEDIA_TRACK_TYPE_SUBTITLE,
                MEDIA_TRACK_TYPE_METADATA }
        )
        @Retention(RetentionPolicy.SOURCE)
        public @interface TrackType {}
        final int mTrackType;
        final MediaFormat mFormat;
        TrackInfo(Parcel in) {
            mTrackType = in.readInt();
            // TODO: parcel in the full MediaFormat; currently we are using createSubtitleFormat
            // even for audio/video tracks, meaning we only set the mime and language.
            String mime = in.readString();
            String language = in.readString();
            mFormat = MediaFormat.createSubtitleFormat(mime, language);
            if (mTrackType == MEDIA_TRACK_TYPE_SUBTITLE) {
                mFormat.setInteger(MediaFormat.KEY_IS_AUTOSELECT, in.readInt());
                mFormat.setInteger(MediaFormat.KEY_IS_DEFAULT, in.readInt());
                mFormat.setInteger(MediaFormat.KEY_IS_FORCED_SUBTITLE, in.readInt());
            }
        }
        /** @hide */
        TrackInfo(int type, MediaFormat format) {
            mTrackType = type;
            mFormat = format;
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public int describeContents() {
            return 0;
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mTrackType);
            dest.writeString(mFormat.getString(MediaFormat.KEY_MIME));
            dest.writeString(getLanguage());
            if (mTrackType == MEDIA_TRACK_TYPE_SUBTITLE) {
                dest.writeInt(mFormat.getInteger(MediaFormat.KEY_IS_AUTOSELECT));
                dest.writeInt(mFormat.getInteger(MediaFormat.KEY_IS_DEFAULT));
                dest.writeInt(mFormat.getInteger(MediaFormat.KEY_IS_FORCED_SUBTITLE));
            }
        }
        @Override
        public String toString() {
            StringBuilder out = new StringBuilder(128);
            out.append(getClass().getName());
            out.append('{');
            switch (mTrackType) {
                case MEDIA_TRACK_TYPE_VIDEO:
                    out.append("VIDEO");
                    break;
                case MEDIA_TRACK_TYPE_AUDIO:
                    out.append("AUDIO");
                    break;
                case MEDIA_TRACK_TYPE_TIMEDTEXT:
                    out.append("TIMEDTEXT");
                    break;
                case MEDIA_TRACK_TYPE_SUBTITLE:
                    out.append("SUBTITLE");
                    break;
                default:
                    out.append("UNKNOWN");
                    break;
            }
            out.append(", " + mFormat.toString());
            out.append("}");
            return out.toString();
        }
        /**
         * Used to read a TrackInfo from a Parcel.
         */
        static final @androidx.annotation.NonNull Parcelable.Creator<TrackInfo> CREATOR
                = new Parcelable.Creator<TrackInfo>() {
            @Override
            public TrackInfo createFromParcel(Parcel in) {
                return new TrackInfo(in);
            }
            @Override
            public TrackInfo[] newArray(int size) {
                return new TrackInfo[size];
            }
        };
    };
    // We would like domain specific classes with more informative names than the `first` and `second`
    // in generic Pair, but we would also like to avoid creating new/trivial classes. As a compromise
    // we document the meanings of `first` and `second` here:
    //
    // Pair.first - inband track index; non-null iff representing an inband track.
    // Pair.second - a SubtitleTrack registered with mSubtitleController; non-null iff representing
    //               an inband subtitle track or any out-of-band track (subtitle or timedtext).
    private BitSet mInbandTrackIndices = new BitSet();
    /**
     * Returns an array of track information.
     *
     * @return Array of track info. The total number of tracks is the array length.
     * Must be called again if an external timed text source has been added after any of the
     * addTimedTextSource methods are called.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public TrackInfo[] getTrackInfo() throws IllegalStateException {
        TrackInfo trackInfo[] = getInbandTrackInfo();
        return trackInfo;
    }
    private TrackInfo[] getInbandTrackInfo() throws IllegalStateException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(IMEDIA_PLAYER);
            request.writeInt(INVOKE_ID_GET_TRACK_INFO);
            invoke(request, reply);
            TrackInfo trackInfo[] = reply.createTypedArray(TrackInfo.CREATOR);
            return trackInfo;
        } finally {
            request.recycle();
            reply.recycle();
        }
    }
    /* Do not change these values without updating their counterparts
     * in include/media/stagefright/MediaDefs.h and media/libstagefright/MediaDefs.cpp!
     */
    /**
     * MIME type for SubRip (SRT) container. Used in addTimedTextSource APIs.
     * @deprecated use {@link MediaFormat#MIMETYPE_TEXT_SUBRIP}
     */
    public static final String MEDIA_MIMETYPE_TEXT_SUBRIP = MediaFormat.MIMETYPE_TEXT_SUBRIP;
    /**
     * MIME type for WebVTT subtitle data.
     * @hide
     * @deprecated
     */
    public static final String MEDIA_MIMETYPE_TEXT_VTT = MediaFormat.MIMETYPE_TEXT_VTT;
    /**
     * MIME type for CEA-608 closed caption data.
     * @hide
     * @deprecated
     */
    public static final String MEDIA_MIMETYPE_TEXT_CEA_608 = MediaFormat.MIMETYPE_TEXT_CEA_608;
    /**
     * MIME type for CEA-708 closed caption data.
     * @hide
     * @deprecated
     */
    public static final String MEDIA_MIMETYPE_TEXT_CEA_708 = MediaFormat.MIMETYPE_TEXT_CEA_708;
    /* TODO: Limit the total number of external timed text source to a reasonable number.
     */

    /**
     * Returns the index of the audio, video, or subtitle track currently selected for playback,
     * The return value is an index into the array returned by {@link #getTrackInfo()}, and can
     * be used in calls to {@link #selectTrack(int)} or {@link #deselectTrack(int)}.
     *
     * @param trackType should be one of {@link TrackInfo#MEDIA_TRACK_TYPE_VIDEO},
     * {@link TrackInfo#MEDIA_TRACK_TYPE_AUDIO}, or
     * {@link TrackInfo#MEDIA_TRACK_TYPE_SUBTITLE}
     * @return index of the audio, video, or subtitle track currently selected for playback;
     * a negative integer is returned when there is no selected track for {@code trackType} or
     * when {@code trackType} is not one of audio, video, or subtitle.
     * @throws IllegalStateException if called after {@link #release()}
     *
     * @see #getTrackInfo()
     * @see #selectTrack(int)
     * @see #deselectTrack(int)
     */
    public int getSelectedTrack(int trackType) throws IllegalStateException {
        /*if (mSubtitleController != null
                && (trackType == TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE
                || trackType == TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT)) {
            SubtitleTrack subtitleTrack = mSubtitleController.getSelectedTrack();
            if (subtitleTrack != null) {
                synchronized (mIndexTrackPairs) {
                    for (int i = 0; i < mIndexTrackPairs.size(); i++) {
                        Pair<Integer, SubtitleTrack> p = mIndexTrackPairs.get(i);
                        if (p.second == subtitleTrack && subtitleTrack.getTrackType() == trackType) {
                            return i;
                        }
                    }
                }
            }
        }*/
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(IMEDIA_PLAYER);
            request.writeInt(INVOKE_ID_GET_SELECTED_TRACK);
            request.writeInt(trackType);
            invoke(request, reply);
            int inbandTrackIndex = reply.readInt();
            return -1;
        } finally {
            request.recycle();
            reply.recycle();
        }
    }
    /**
     * Deselect a track.
     * <p>
     * Currently, the track must be a timed text track and no audio or video tracks can be
     * deselected. If the timed text track identified by index has not been
     * selected before, it throws an exception.
     * </p>
     * @param index the index of the track to be deselected. The valid range of the index
     * is 0..total number of tracks - 1. The total number of tracks as well as the type of
     * each individual track can be found by calling {@link #getTrackInfo()} method.
     * @throws IllegalStateException if called in an invalid state.
     *
     * @see android.media.MediaPlayer#getTrackInfo
     */
    public void deselectTrack(int index) throws IllegalStateException {
        selectOrDeselectTrack(index, false /* select */);
    }
    private void selectOrDeselectTrack(int index, boolean select)
            throws IllegalStateException {
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(IMEDIA_PLAYER);
            request.writeInt(select? INVOKE_ID_SELECT_TRACK: INVOKE_ID_DESELECT_TRACK);
            request.writeInt(index);
            invoke(request, reply);
        } finally {
            request.recycle();
            reply.recycle();
        }
    }
    /**
     * @param reply Parcel with audio/video duration info for battery
    tracking usage
     * @return The status code.
     * {@hide}
     */
    // TODO Remove this method (it seems to be useless)
    public native static int native_pullBatteryData(Parcel reply);
    /**
     * Sets the target UDP re-transmit endpoint for the low level player.
     * Generally, the address portion of the endpoint is an IP multicast
     * address, although a unicast address would be equally valid.  When a valid
     * retransmit endpoint has been set, the media player will not decode and
     * render the media presentation locally.  Instead, the player will attempt
     * to re-multiplex its media data using the Android@Home RTP profile and
     * re-transmit to the target endpoint.  Receiver devices (which may be
     * either the same as the transmitting device or different devices) may
     * instantiate, prepare, and start a receiver player using a setDataSource
     * URL of the form...
     *
     * aahRX://&lt;multicastIP&gt;:&lt;port&gt;
     *
     * to receive, decode and render the re-transmitted content.
     *
     * setRetransmitEndpoint may only be called before setDataSource has been
     * called; while the player is in the Idle state.
     *
     * @param endpoint the address and UDP port of the re-transmission target or
     * null if no re-transmission is to be performed.
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if the retransmit endpoint is supplied,
     * but invalid.
     *
     * {@hide} pending API council
     */
    // TODO Remove this method (it seems to be useless)
    public void setRetransmitEndpoint(InetSocketAddress endpoint)
            throws IllegalStateException, IllegalArgumentException
    {
        String addrString = null;
        int port = 0;
        if (null != endpoint) {
            addrString = endpoint.getAddress().getHostAddress();
            port = endpoint.getPort();
        }
        int ret = native_setRetransmitEndpoint(addrString, port);
        if (ret != 0) {
            throw new IllegalArgumentException("Illegal re-transmit endpoint; native ret " + ret);
        }
    }
    private native final int native_setRetransmitEndpoint(String addrString, int port);
    @Override
    protected void finalize() {
        native_finalize();
    }
    /* Do not change these values without updating their counterparts
     * in include/media/mediaplayer.h!
     */
    private static final int MEDIA_NOP = 0; // interface test message
    private static final int MEDIA_PREPARED = 1;
    private static final int MEDIA_PLAYBACK_COMPLETE = 2;
    private static final int MEDIA_BUFFERING_UPDATE = 3;
    private static final int MEDIA_SEEK_COMPLETE = 4;
    private static final int MEDIA_SET_VIDEO_SIZE = 5;
    private static final int MEDIA_STARTED = 6;
    private static final int MEDIA_PAUSED = 7;
    private static final int MEDIA_STOPPED = 8;
    private static final int MEDIA_SKIPPED = 9;
    private static final int MEDIA_NOTIFY_TIME = 98;
    private static final int MEDIA_TIMED_TEXT = 99;
    private static final int MEDIA_ERROR = 100;
    private static final int MEDIA_INFO = 200;
    private static final int MEDIA_SUBTITLE_DATA = 201;
    private static final int MEDIA_META_DATA = 202;
    private static final int MEDIA_DRM_INFO = 210;
    private static final int MEDIA_TIME_DISCONTINUITY = 211;
    private static final int MEDIA_AUDIO_ROUTING_CHANGED = 10000;
    private TimeProvider mTimeProvider;
    private final Object mTimeProviderLock = new Object();
    private class EventHandler extends Handler
    {
        private MediaPlayer mMediaPlayer;
        public EventHandler(MediaPlayer mp, Looper looper) {
            super(looper);
            mMediaPlayer = mp;
        }
        @Override
        public void handleMessage(Message msg) {
            if (mMediaPlayer.mNativeContext == 0) {
                Log.w(TAG, "mediaplayer went away with unhandled events");
                return;
            }
            switch(msg.what) {
                case MEDIA_PREPARED:
                    try {
                        //scanInternalSubtitleTracks();
                    } catch (RuntimeException e) {
                        // send error message instead of crashing;
                        // send error message instead of inlining a call to onError
                        // to avoid code duplication.
                        Message msg2 = obtainMessage(
                                MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_UNSUPPORTED, null);
                        sendMessage(msg2);
                    }
                    OnPreparedListener onPreparedListener = mOnPreparedListener;
                    if (onPreparedListener != null)
                        onPreparedListener.onPrepared(mMediaPlayer);
                    return;
                case MEDIA_PLAYBACK_COMPLETE:
                {
                    mOnCompletionInternalListener.onCompletion(mMediaPlayer);
                    OnCompletionListener onCompletionListener = mOnCompletionListener;
                    if (onCompletionListener != null)
                        onCompletionListener.onCompletion(mMediaPlayer);
                }
                stayAwake(false);
                return;
                case MEDIA_STOPPED:
                {
                    TimeProvider timeProvider = mTimeProvider;
                    if (timeProvider != null) {
                        timeProvider.onStopped();
                    }
                }
                break;
                case MEDIA_STARTED:
                case MEDIA_PAUSED:
                {
                    TimeProvider timeProvider = mTimeProvider;
                    if (timeProvider != null) {
                        timeProvider.onPaused(msg.what == MEDIA_PAUSED);
                    }
                }
                break;
                case MEDIA_BUFFERING_UPDATE:
                    OnBufferingUpdateListener onBufferingUpdateListener = mOnBufferingUpdateListener;
                    if (onBufferingUpdateListener != null)
                        onBufferingUpdateListener.onBufferingUpdate(mMediaPlayer, msg.arg1);
                    return;
                case MEDIA_SEEK_COMPLETE:
                    OnSeekCompleteListener onSeekCompleteListener = mOnSeekCompleteListener;
                    if (onSeekCompleteListener != null) {
                        onSeekCompleteListener.onSeekComplete(mMediaPlayer);
                    }
                    // fall through
                case MEDIA_SKIPPED:
                {
                    TimeProvider timeProvider = mTimeProvider;
                    if (timeProvider != null) {
                        timeProvider.onSeekComplete(mMediaPlayer);
                    }
                }
                return;
                case MEDIA_SET_VIDEO_SIZE:
                    OnVideoSizeChangedListener onVideoSizeChangedListener = mOnVideoSizeChangedListener;
                    if (onVideoSizeChangedListener != null) {
                        onVideoSizeChangedListener.onVideoSizeChanged(
                                mMediaPlayer, msg.arg1, msg.arg2);
                    }
                    return;
                case MEDIA_ERROR:
                    Log.e(TAG, "Error (" + msg.arg1 + "," + msg.arg2 + ")");
                    boolean error_was_handled = false;
                    OnErrorListener onErrorListener = mOnErrorListener;
                    if (onErrorListener != null) {
                        error_was_handled = onErrorListener.onError(mMediaPlayer, msg.arg1, msg.arg2);
                    }
                {
                    mOnCompletionInternalListener.onCompletion(mMediaPlayer);
                    OnCompletionListener onCompletionListener = mOnCompletionListener;
                    if (onCompletionListener != null && ! error_was_handled) {
                        onCompletionListener.onCompletion(mMediaPlayer);
                    }
                }
                stayAwake(false);
                return;
                case MEDIA_INFO:
                    switch (msg.arg1) {
                        case MEDIA_INFO_VIDEO_TRACK_LAGGING:
                            Log.i(TAG, "Info (" + msg.arg1 + "," + msg.arg2 + ")");
                            break;
                        case MEDIA_INFO_METADATA_UPDATE:
                            try {
                                //scanInternalSubtitleTracks();
                            } catch (RuntimeException e) {
                                Message msg2 = obtainMessage(
                                        MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_UNSUPPORTED, null);
                                sendMessage(msg2);
                            }
                            // fall through
                        case MEDIA_INFO_EXTERNAL_METADATA_UPDATE:
                            msg.arg1 = MEDIA_INFO_METADATA_UPDATE;
                            // update default track selection
                            break;
                        case MEDIA_INFO_BUFFERING_START:
                        case MEDIA_INFO_BUFFERING_END:
                            TimeProvider timeProvider = mTimeProvider;
                            if (timeProvider != null) {
                                timeProvider.onBuffering(msg.arg1 == MEDIA_INFO_BUFFERING_START);
                            }
                            break;
                    }
                    OnInfoListener onInfoListener = mOnInfoListener;
                    if (onInfoListener != null) {
                        onInfoListener.onInfo(mMediaPlayer, msg.arg1, msg.arg2);
                    }
                    // No real default action so far.
                    return;
                case MEDIA_NOTIFY_TIME:
                    TimeProvider timeProvider = mTimeProvider;
                    if (timeProvider != null) {
                        timeProvider.onNotifyTime();
                    }
                    return;
                case MEDIA_TIMED_TEXT:
                    return;
                case MEDIA_SUBTITLE_DATA:
                    return;
                case MEDIA_META_DATA:
                    OnTimedMetaDataAvailableListener onTimedMetaDataAvailableListener =
                            mOnTimedMetaDataAvailableListener;
                    if (onTimedMetaDataAvailableListener == null) {
                        return;
                    }
                    if (msg.obj instanceof Parcel) {
                        Parcel parcel = (Parcel) msg.obj;
                        //TimedMetaData data = TimedMetaData.createTimedMetaDataFromParcel(parcel);
                        TimedMetaData data = null; // FIXME
                        parcel.recycle();
                        onTimedMetaDataAvailableListener.onTimedMetaDataAvailable(mMediaPlayer, data);
                    }
                    return;
                case MEDIA_NOP: // interface test message - ignore
                    break;
                case MEDIA_AUDIO_ROUTING_CHANGED:

                    return;
                case MEDIA_TIME_DISCONTINUITY:
                    final OnMediaTimeDiscontinuityListener mediaTimeListener;
                    final Handler mediaTimeHandler;
                    synchronized(this) {
                        mediaTimeListener = mOnMediaTimeDiscontinuityListener;
                        mediaTimeHandler = mOnMediaTimeDiscontinuityHandler;
                    }
                    if (mediaTimeListener == null) {
                        return;
                    }
                    if (msg.obj instanceof Parcel) {
                        Parcel parcel = (Parcel) msg.obj;
                        parcel.setDataPosition(0);
                        long anchorMediaUs = parcel.readLong();
                        long anchorRealUs = parcel.readLong();
                        float playbackRate = parcel.readFloat();
                        parcel.recycle();
                        final MediaTimestamp timestamp;
                        if (anchorMediaUs != -1 && anchorRealUs != -1) {
                            timestamp = new MediaTimestamp(
                                    anchorMediaUs /*Us*/, anchorRealUs * 1000 /*Ns*/, playbackRate);
                        } else {
                            timestamp = MediaTimestamp.TIMESTAMP_UNKNOWN;
                        }
                        if (mediaTimeHandler == null) {
                            mediaTimeListener.onMediaTimeDiscontinuity(mMediaPlayer, timestamp);
                        } else {
                            mediaTimeHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mediaTimeListener.onMediaTimeDiscontinuity(mMediaPlayer, timestamp);
                                }
                            });
                        }
                    }
                    return;
                default:
                    Log.e(TAG, "Unknown message type " + msg.what);
                    return;
            }
        }
    }
    /*
     * Called from native code when an interesting event happens.  This method
     * just uses the EventHandler system to post the event back to the main app thread.
     * We use a weak reference to the original MediaPlayer object so that the native
     * code is safe from the object disappearing from underneath it.  (This is
     * the cookie passed to native_setup().)
     */
    private static void postEventFromNative(Object mediaplayer_ref,
                                            int what, int arg1, int arg2, Object obj)
    {
        MediaPlayer mp = (MediaPlayer)((WeakReference)mediaplayer_ref).get();
        if (mp == null) {
            return;
        }
        switch (what) {
            case MEDIA_INFO:
                if (arg1 == MEDIA_INFO_STARTED_AS_NEXT) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            // this acquires the wakelock if needed, and sets the client side state
                            mp.start();
                        }
                    }).start();
                    Thread.yield();
                }
                break;
            case MEDIA_DRM_INFO:
                // We need to derive mDrmInfo before prepare() returns so processing it here
                // before the notification is sent to EventHandler below. EventHandler runs in the
                // notification looper so its handleMessage might process the event after prepare()
                // has returned.
                Log.v(TAG, "postEventFromNative MEDIA_DRM_INFO");
                if (obj instanceof Parcel) {
                    Parcel parcel = (Parcel)obj;

                } else {
                    Log.w(TAG, "MEDIA_DRM_INFO msg.obj of unexpected type " + obj);
                }
                break;
            case MEDIA_PREPARED:
                // By this time, we've learned about DrmInfo's presence or absence. This is meant
                // mainly for prepareAsync() use case. For prepare(), this still can run to a race
                // condition b/c MediaPlayerNative releases the prepare() lock before calling notify
                // so we also set mDrmInfoResolved in prepare().
                break;
        }
        if (mp.mEventHandler != null) {
            Message m = mp.mEventHandler.obtainMessage(what, arg1, arg2, obj);
            mp.mEventHandler.sendMessage(m);
        }
    }
    /**
     * Interface definition for a callback to be invoked when the media
     * source is ready for playback.
     */
    public interface OnPreparedListener
    {
        /**
         * Called when the media file is ready for playback.
         *
         * @param mp the MediaPlayer that is ready for playback
         */
        void onPrepared(MediaPlayer mp);
    }
    /**
     * Register a callback to be invoked when the media source is ready
     * for playback.
     *
     * @param listener the callback that will be run
     */
    public void setOnPreparedListener(OnPreparedListener listener)
    {
        mOnPreparedListener = listener;
    }
    private OnPreparedListener mOnPreparedListener;
    /**
     * Interface definition for a callback to be invoked when playback of
     * a media source has completed.
     */
    public interface OnCompletionListener
    {
        /**
         * Called when the end of a media source is reached during playback.
         *
         * @param mp the MediaPlayer that reached the end of the file
         */
        void onCompletion(MediaPlayer mp);
    }
    /**
     * Register a callback to be invoked when the end of a media source
     * has been reached during playback.
     *
     * @param listener the callback that will be run
     */
    public void setOnCompletionListener(OnCompletionListener listener)
    {
        mOnCompletionListener = listener;
    }
    private OnCompletionListener mOnCompletionListener;
    /**
     * @hide
     * Internal completion listener to update PlayerBase of the play state. Always "registered".
     */
    private final OnCompletionListener mOnCompletionInternalListener = new OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
        }
    };
    /**
     * Interface definition of a callback to be invoked indicating buffering
     * status of a media resource being streamed over the network.
     */
    public interface OnBufferingUpdateListener
    {
        /**
         * Called to update status in buffering a media stream received through
         * progressive HTTP download. The received buffering percentage
         * indicates how much of the content has been buffered or played.
         * For example a buffering update of 80 percent when half the content
         * has already been played indicates that the next 30 percent of the
         * content to play has been buffered.
         *
         * @param mp      the MediaPlayer the update pertains to
         * @param percent the percentage (0-100) of the content
         *                that has been buffered or played thus far
         */
        void onBufferingUpdate(MediaPlayer mp, int percent);
    }
    /**
     * Register a callback to be invoked when the status of a network
     * stream's buffer has changed.
     *
     * @param listener the callback that will be run.
     */
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener)
    {
        mOnBufferingUpdateListener = listener;
    }
    private OnBufferingUpdateListener mOnBufferingUpdateListener;
    /**
     * Interface definition of a callback to be invoked indicating
     * the completion of a seek operation.
     */
    public interface OnSeekCompleteListener
    {
        /**
         * Called to indicate the completion of a seek operation.
         *
         * @param mp the MediaPlayer that issued the seek operation
         */
        public void onSeekComplete(MediaPlayer mp);
    }
    /**
     * Register a callback to be invoked when a seek operation has been
     * completed.
     *
     * @param listener the callback that will be run
     */
    public void setOnSeekCompleteListener(OnSeekCompleteListener listener)
    {
        mOnSeekCompleteListener = listener;
    }
    private OnSeekCompleteListener mOnSeekCompleteListener;
    /**
     * Interface definition of a callback to be invoked when the
     * video size is first known or updated
     */
    public interface OnVideoSizeChangedListener
    {
        /**
         * Called to indicate the video size
         *
         * The video size (width and height) could be 0 if there was no video,
         * no display surface was set, or the value was not determined yet.
         *
         * @param mp        the MediaPlayer associated with this callback
         * @param width     the width of the video
         * @param height    the height of the video
         */
        public void onVideoSizeChanged(MediaPlayer mp, int width, int height);
    }
    /**
     * Register a callback to be invoked when the video size is
     * known or updated.
     *
     * @param listener the callback that will be run
     */
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener)
    {
        mOnVideoSizeChangedListener = listener;
    }
    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;
    /**
     * Interface definition of a callback to be invoked when discontinuity in the normal progression
     * of the media time is detected.
     * The "normal progression" of media time is defined as the expected increase of the playback
     * position when playing media, relative to the playback speed (for instance every second, media
     * time increases by two seconds when playing at 2x).<br>
     * Discontinuities are encountered in the following cases:
     * <ul>
     * <li>when the player is starved for data and cannot play anymore</li>
     * <li>when the player encounters a playback error</li>
     * <li>when the a seek operation starts, and when it's completed</li>
     * <li>when the playback speed changes</li>
     * <li>when the playback state changes</li>
     * <li>when the player is reset</li>
     * </ul>
     * See the
     * {@link MediaPlayer#setOnMediaTimeDiscontinuityListener(OnMediaTimeDiscontinuityListener, Handler)}
     * method to set a listener for these events.
     */
    public interface OnMediaTimeDiscontinuityListener {
        /**
         * Called to indicate a time discontinuity has occured.
         * @param mp the MediaPlayer for which the discontinuity has occured.
         * @param mts the timestamp that correlates media time, system time and clock rate,
         *     or {@link MediaTimestamp#TIMESTAMP_UNKNOWN} in an error case.
         */
        public void onMediaTimeDiscontinuity(@NonNull MediaPlayer mp, @NonNull MediaTimestamp mts);
    }
    /**
     * Sets the listener to be invoked when a media time discontinuity is encountered.
     * @param listener the listener called after a discontinuity
     * @param handler the {@link Handler} that receives the listener events
     */
    public void setOnMediaTimeDiscontinuityListener(
            @NonNull OnMediaTimeDiscontinuityListener listener, @NonNull Handler handler) {
        if (listener == null) {
            throw new IllegalArgumentException("Illegal null listener");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Illegal null handler");
        }
        setOnMediaTimeDiscontinuityListenerInt(listener, handler);
    }
    /**
     * Sets the listener to be invoked when a media time discontinuity is encountered.
     * The listener will be called on the same thread as the one in which the MediaPlayer was
     * created.
     * @param listener the listener called after a discontinuity
     */
    public void setOnMediaTimeDiscontinuityListener(
            @NonNull OnMediaTimeDiscontinuityListener listener)
    {
        if (listener == null) {
            throw new IllegalArgumentException("Illegal null listener");
        }
        setOnMediaTimeDiscontinuityListenerInt(listener, null);
    }
    /**
     * Clears the listener previously set with
     * {@link #setOnMediaTimeDiscontinuityListener(OnMediaTimeDiscontinuityListener)}
     * or {@link #setOnMediaTimeDiscontinuityListener(OnMediaTimeDiscontinuityListener, Handler)}
     */
    public void clearOnMediaTimeDiscontinuityListener() {
        setOnMediaTimeDiscontinuityListenerInt(null, null);
    }
    private void setOnMediaTimeDiscontinuityListenerInt(
            @Nullable OnMediaTimeDiscontinuityListener listener, @Nullable Handler handler) {
        synchronized (this) {
            mOnMediaTimeDiscontinuityListener = listener;
            mOnMediaTimeDiscontinuityHandler = handler;
        }
    }
    private OnMediaTimeDiscontinuityListener mOnMediaTimeDiscontinuityListener;
    private Handler mOnMediaTimeDiscontinuityHandler;
    /**
     * Interface definition of a callback to be invoked when a
     * track has timed metadata available.
     *
     * @see MediaPlayer#setOnTimedMetaDataAvailableListener(OnTimedMetaDataAvailableListener)
     */
    public interface OnTimedMetaDataAvailableListener
    {
        /**
         * Called to indicate avaliable timed metadata
         * <p>
         * This method will be called as timed metadata is extracted from the media,
         * in the same order as it occurs in the media. The timing of this event is
         * not controlled by the associated timestamp.
         *
         * @param mp             the MediaPlayer associated with this callback
         * @param data           the timed metadata sample associated with this event
         */
        public void onTimedMetaDataAvailable(MediaPlayer mp, TimedMetaData data);
    }
    /**
     * Register a callback to be invoked when a selected track has timed metadata available.
     * <p>
     * Currently only HTTP live streaming data URI's embedded with timed ID3 tags generates
     * {@link TimedMetaData}.
     *
     * @see MediaPlayer#selectTrack(int)
     * @see MediaPlayer.OnTimedMetaDataAvailableListener
     * @see TimedMetaData
     *
     * @param listener the callback that will be run
     */
    public void setOnTimedMetaDataAvailableListener(OnTimedMetaDataAvailableListener listener)
    {
        mOnTimedMetaDataAvailableListener = listener;
    }
    private OnTimedMetaDataAvailableListener mOnTimedMetaDataAvailableListener;
    /* Do not change these values without updating their counterparts
     * in include/media/mediaplayer.h!
     */
    /** Unspecified media player error.
     * @see android.media.MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_UNKNOWN = 1;
    /** Media server died. In this case, the application must release the
     * MediaPlayer object and instantiate a new one.
     * @see android.media.MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_SERVER_DIED = 100;
    /** The video is streamed and its container is not valid for progressive
     * playback i.e the video's index (e.g moov atom) is not at the start of the
     * file.
     * @see android.media.MediaPlayer.OnErrorListener
     */
    public static final int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;
    /** File or network related operation errors. */
    public static final int MEDIA_ERROR_IO = -1004;
    /** Bitstream is not conforming to the related coding standard or file spec. */
    public static final int MEDIA_ERROR_MALFORMED = -1007;
    /** Bitstream is conforming to the related coding standard or file spec, but
     * the media framework does not support the feature. */
    public static final int MEDIA_ERROR_UNSUPPORTED = -1010;
    /** Some operation takes too long to complete, usually more than 3-5 seconds. */
    public static final int MEDIA_ERROR_TIMED_OUT = -110;
    /** Unspecified low-level system error. This value originated from UNKNOWN_ERROR in
     * system/core/include/utils/Errors.h
     * @see android.media.MediaPlayer.OnErrorListener
     * @hide
     */
    public static final int MEDIA_ERROR_SYSTEM = -2147483648;
    /**
     * Interface definition of a callback to be invoked when there
     * has been an error during an asynchronous operation (other errors
     * will throw exceptions at method call time).
     */
    public interface OnErrorListener
    {
        /**
         * Called to indicate an error.
         *
         * @param mp      the MediaPlayer the error pertains to
         * @param what    the type of error that has occurred:
         * <ul>
         * <li>{@link #MEDIA_ERROR_UNKNOWN}
         * <li>{@link #MEDIA_ERROR_SERVER_DIED}
         * </ul>
         * @param extra an extra code, specific to the error. Typically
         * implementation dependent.
         * <ul>
         * <li>{@link #MEDIA_ERROR_IO}
         * <li>{@link #MEDIA_ERROR_MALFORMED}
         * <li>{@link #MEDIA_ERROR_UNSUPPORTED}
         * <li>{@link #MEDIA_ERROR_TIMED_OUT}
         * </ul>
         * @return True if the method handled the error, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the OnCompletionListener to be called.
         */
        boolean onError(MediaPlayer mp, int what, int extra);
    }
    /**
     * Register a callback to be invoked when an error has happened
     * during an asynchronous operation.
     *
     * @param listener the callback that will be run
     */
    public void setOnErrorListener(OnErrorListener listener)
    {
        mOnErrorListener = listener;
    }
    private OnErrorListener mOnErrorListener;
    /* Do not change these values without updating their counterparts
     * in include/media/mediaplayer.h!
     */
    /** Unspecified media player info.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_UNKNOWN = 1;
    /** The player was started because it was used as the next player for another
     * player, which just completed playback.
     * @see android.media.MediaPlayer.OnInfoListener
     * @hide
     */
    public static final int MEDIA_INFO_STARTED_AS_NEXT = 2;
    /** The player just pushed the very first video frame for rendering.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;
    /** The video is too complex for the decoder: it can't decode frames fast
     *  enough. Possibly only the audio plays fine at this stage.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_TRACK_LAGGING = 700;
    /** MediaPlayer is temporarily pausing playback internally in order to
     * buffer more data.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BUFFERING_START = 701;
    /** MediaPlayer is resuming playback after filling buffers.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BUFFERING_END = 702;
    /** Estimated network bandwidth information (kbps) is available; currently this event fires
     * simultaneously as {@link #MEDIA_INFO_BUFFERING_START} and {@link #MEDIA_INFO_BUFFERING_END}
     * when playing network files.
     * @see android.media.MediaPlayer.OnInfoListener
     * @hide
     */
    public static final int MEDIA_INFO_NETWORK_BANDWIDTH = 703;
    /** Bad interleaving means that a media has been improperly interleaved or
     * not interleaved at all, e.g has all the video samples first then all the
     * audio ones. Video is playing but a lot of disk seeks may be happening.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_BAD_INTERLEAVING = 800;
    /** The media cannot be seeked (e.g live stream)
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_NOT_SEEKABLE = 801;
    /** A new set of metadata is available.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_METADATA_UPDATE = 802;
    /** A new set of external-only metadata is available.  Used by
     *  JAVA framework to avoid triggering track scanning.
     * @hide
     */
    public static final int MEDIA_INFO_EXTERNAL_METADATA_UPDATE = 803;
    /** Informs that audio is not playing. Note that playback of the video
     * is not interrupted.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_AUDIO_NOT_PLAYING = 804;
    /** Informs that video is not playing. Note that playback of the audio
     * is not interrupted.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_VIDEO_NOT_PLAYING = 805;
    /** Failed to handle timed text track properly.
     * @see android.media.MediaPlayer.OnInfoListener
     *
     * {@hide}
     */
    public static final int MEDIA_INFO_TIMED_TEXT_ERROR = 900;
    /** Subtitle track was not supported by the media framework.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_UNSUPPORTED_SUBTITLE = 901;
    /** Reading the subtitle track takes too long.
     * @see android.media.MediaPlayer.OnInfoListener
     */
    public static final int MEDIA_INFO_SUBTITLE_TIMED_OUT = 902;
    /**
     * Interface definition of a callback to be invoked to communicate some
     * info and/or warning about the media or its playback.
     */
    public interface OnInfoListener
    {
        /**
         * Called to indicate an info or a warning.
         *
         * @param mp      the MediaPlayer the info pertains to.
         * @param what    the type of info or warning.
         * <ul>
         * <li>{@link #MEDIA_INFO_UNKNOWN}
         * <li>{@link #MEDIA_INFO_VIDEO_TRACK_LAGGING}
         * <li>{@link #MEDIA_INFO_VIDEO_RENDERING_START}
         * <li>{@link #MEDIA_INFO_BUFFERING_START}
         * <li>{@link #MEDIA_INFO_BUFFERING_END}
         * <li>{@link #MEDIA_INFO_BAD_INTERLEAVING}
         * <li>{@link #MEDIA_INFO_NOT_SEEKABLE}
         * <li>{@link #MEDIA_INFO_METADATA_UPDATE}
         * </ul>
         * @param extra an extra code, specific to the info. Typically
         * implementation dependent.
         * @return True if the method handled the info, false if it didn't.
         * Returning false, or not having an OnErrorListener at all, will
         * cause the info to be discarded.
         */
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }
    /**
     * Register a callback to be invoked when an info/warning is available.
     *
     * @param listener the callback that will be run
     */
    public void setOnInfoListener(OnInfoListener listener)
    {
        mOnInfoListener = listener;
    }
    private OnInfoListener mOnInfoListener;
    /*
     * Test whether a given video scaling mode is supported.
     */
    private boolean isVideoScalingModeSupported(int mode) {
        return (mode == VIDEO_SCALING_MODE_SCALE_TO_FIT ||
                mode == VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
    }
    /** @hide */
    static class TimeProvider implements MediaPlayer.OnSeekCompleteListener,
            MediaTimeProvider {
        private static final String TAG = "MTP";
        private static final long MAX_NS_WITHOUT_POSITION_CHECK = 5000000000L;
        private static final long MAX_EARLY_CALLBACK_US = 1000;
        private static final long TIME_ADJUSTMENT_RATE = 2;  /* meaning 1/2 */
        private long mLastTimeUs = 0;
        private MediaPlayer mPlayer;
        private boolean mPaused = true;
        private boolean mStopped = true;
        private boolean mBuffering;
        private long mLastReportedTime;
        // since we are expecting only a handful listeners per stream, there is
        // no need for log(N) search performance
        private MediaTimeProvider.OnMediaTimeListener mListeners[];
        private long mTimes[];
        private Handler mEventHandler;
        private boolean mRefresh = false;
        private boolean mPausing = false;
        private boolean mSeeking = false;
        private static final int NOTIFY = 1;
        private static final int NOTIFY_TIME = 0;
        private static final int NOTIFY_STOP = 2;
        private static final int NOTIFY_SEEK = 3;
        private static final int NOTIFY_TRACK_DATA = 4;
        private HandlerThread mHandlerThread;
        /** @hide */
        public boolean DEBUG = false;
        public TimeProvider(MediaPlayer mp) {
            mPlayer = mp;
            try {
                getCurrentTimeUs(true, false);
            } catch (IllegalStateException e) {
                // we assume starting position
                mRefresh = true;
            }
            Looper looper;
            if ((looper = Looper.myLooper()) == null &&
                    (looper = Looper.getMainLooper()) == null) {
                // Create our own looper here in case MP was created without one
                mHandlerThread = new HandlerThread("MediaPlayerMTPEventThread",
                        Process.THREAD_PRIORITY_FOREGROUND);
                mHandlerThread.start();
                looper = mHandlerThread.getLooper();
            }
            mEventHandler = new EventHandler(looper);
            //mListeners = new MediaTimeProvider.OnMediaTimeListener[0];
            mListeners = null; // FIXME
            mTimes = new long[0];
            mLastTimeUs = 0;
        }
        private void scheduleNotification(int type, long delayUs) {
            // ignore time notifications until seek is handled
            if (mSeeking && type == NOTIFY_TIME) {
                return;
            }
            if (DEBUG) Log.v(TAG, "scheduleNotification " + type + " in " + delayUs);
            mEventHandler.removeMessages(NOTIFY);
            Message msg = mEventHandler.obtainMessage(NOTIFY, type, 0);
            mEventHandler.sendMessageDelayed(msg, (int) (delayUs / 1000));
        }
        /** @hide */
        public void close() {
            mEventHandler.removeMessages(NOTIFY);
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
                mHandlerThread = null;
            }
        }
        /** @hide */
        protected void finalize() {
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
            }
        }
        /** @hide */
        public void onNotifyTime() {
            synchronized (this) {
                if (DEBUG) Log.d(TAG, "onNotifyTime: ");
                scheduleNotification(NOTIFY_TIME, 0 /* delay */);
            }
        }
        /** @hide */
        public void onPaused(boolean paused) {
            synchronized(this) {
                if (DEBUG) Log.d(TAG, "onPaused: " + paused);
                if (mStopped) { // handle as seek if we were stopped
                    mStopped = false;
                    mSeeking = true;
                    scheduleNotification(NOTIFY_SEEK, 0 /* delay */);
                } else {
                    mPausing = paused;  // special handling if player disappeared
                    mSeeking = false;
                    scheduleNotification(NOTIFY_TIME, 0 /* delay */);
                }
            }
        }
        /** @hide */
        public void onBuffering(boolean buffering) {
            synchronized (this) {
                if (DEBUG) Log.d(TAG, "onBuffering: " + buffering);
                mBuffering = buffering;
                scheduleNotification(NOTIFY_TIME, 0 /* delay */);
            }
        }
        /** @hide */
        public void onStopped() {
            synchronized(this) {
                if (DEBUG) Log.d(TAG, "onStopped");
                mPaused = true;
                mStopped = true;
                mSeeking = false;
                mBuffering = false;
                scheduleNotification(NOTIFY_STOP, 0 /* delay */);
            }
        }
        /** @hide */
        @Override
        public void onSeekComplete(MediaPlayer mp) {
            synchronized(this) {
                mStopped = false;
                mSeeking = true;
                scheduleNotification(NOTIFY_SEEK, 0 /* delay */);
            }
        }
        /** @hide */
        public void onNewPlayer() {
            if (mRefresh) {
                synchronized(this) {
                    mStopped = false;
                    mSeeking = true;
                    mBuffering = false;
                    scheduleNotification(NOTIFY_SEEK, 0 /* delay */);
                }
            }
        }
        private synchronized void notifySeek() {
            mSeeking = false;
            try {
                long timeUs = getCurrentTimeUs(true, false);
                if (DEBUG) Log.d(TAG, "onSeekComplete at " + timeUs);
                for (MediaTimeProvider.OnMediaTimeListener listener: mListeners) {
                    if (listener == null) {
                        break;
                    }
                    listener.onSeek(timeUs);
                }
            } catch (IllegalStateException e) {
                // we should not be there, but at least signal pause
                if (DEBUG) Log.d(TAG, "onSeekComplete but no player");
                mPausing = true;  // special handling if player disappeared
                notifyTimedEvent(false /* refreshTime */);
            }
        }
        private synchronized void notifyStop() {
            for (MediaTimeProvider.OnMediaTimeListener listener: mListeners) {
                if (listener == null) {
                    break;
                }
                listener.onStop();
            }
        }
        private int registerListener(MediaTimeProvider.OnMediaTimeListener listener) {
            int i = 0;
            for (; i < mListeners.length; i++) {
                if (mListeners[i] == listener || mListeners[i] == null) {
                    break;
                }
            }
            // new listener
            if (i >= mListeners.length) {
                MediaTimeProvider.OnMediaTimeListener[] newListeners =
                        new MediaTimeProvider.OnMediaTimeListener[i + 1];
                long[] newTimes = new long[i + 1];
                System.arraycopy(mListeners, 0, newListeners, 0, mListeners.length);
                System.arraycopy(mTimes, 0, newTimes, 0, mTimes.length);
                mListeners = newListeners;
                mTimes = newTimes;
            }
            if (mListeners[i] == null) {
                mListeners[i] = listener;
                mTimes[i] = MediaTimeProvider.NO_TIME;
            }
            return i;
        }
        public void notifyAt(
                long timeUs, MediaTimeProvider.OnMediaTimeListener listener) {
            synchronized(this) {
                if (DEBUG) Log.d(TAG, "notifyAt " + timeUs);
                mTimes[registerListener(listener)] = timeUs;
                scheduleNotification(NOTIFY_TIME, 0 /* delay */);
            }
        }
        public void scheduleUpdate(MediaTimeProvider.OnMediaTimeListener listener) {
            synchronized(this) {
                if (DEBUG) Log.d(TAG, "scheduleUpdate");
                int i = registerListener(listener);
                if (!mStopped) {
                    mTimes[i] = 0;
                    scheduleNotification(NOTIFY_TIME, 0 /* delay */);
                }
            }
        }
        public void cancelNotifications(
                MediaTimeProvider.OnMediaTimeListener listener) {
            synchronized(this) {
                int i = 0;
                for (; i < mListeners.length; i++) {
                    if (mListeners[i] == listener) {
                        System.arraycopy(mListeners, i + 1,
                                mListeners, i, mListeners.length - i - 1);
                        System.arraycopy(mTimes, i + 1,
                                mTimes, i, mTimes.length - i - 1);
                        mListeners[mListeners.length - 1] = null;
                        mTimes[mTimes.length - 1] = NO_TIME;
                        break;
                    } else if (mListeners[i] == null) {
                        break;
                    }
                }
                scheduleNotification(NOTIFY_TIME, 0 /* delay */);
            }
        }
        private synchronized void notifyTimedEvent(boolean refreshTime) {
            // figure out next callback
            long nowUs;
            try {
                nowUs = getCurrentTimeUs(refreshTime, true);
            } catch (IllegalStateException e) {
                // assume we paused until new player arrives
                mRefresh = true;
                mPausing = true; // this ensures that call succeeds
                nowUs = getCurrentTimeUs(refreshTime, true);
            }
            long nextTimeUs = nowUs;
            if (mSeeking) {
                // skip timed-event notifications until seek is complete
                return;
            }
            if (DEBUG) {
                StringBuilder sb = new StringBuilder();
                sb.append("notifyTimedEvent(").append(mLastTimeUs).append(" -> ")
                        .append(nowUs).append(") from {");
                boolean first = true;
                for (long time: mTimes) {
                    if (time == NO_TIME) {
                        continue;
                    }
                    if (!first) sb.append(", ");
                    sb.append(time);
                    first = false;
                }
                sb.append("}");
                Log.d(TAG, sb.toString());
            }
            Vector<OnMediaTimeListener> activatedListeners;
            activatedListeners = new Vector<MediaTimeProvider.OnMediaTimeListener>();
            for (int ix = 0; ix < mTimes.length; ix++) {
                if (mListeners[ix] == null) {
                    break;
                }
                if (mTimes[ix] <= NO_TIME) {
                    // ignore, unless we were stopped
                } else if (mTimes[ix] <= nowUs + MAX_EARLY_CALLBACK_US) {
                    activatedListeners.add(mListeners[ix]);
                    if (DEBUG) Log.d(TAG, "removed");
                    mTimes[ix] = NO_TIME;
                } else if (nextTimeUs == nowUs || mTimes[ix] < nextTimeUs) {
                    nextTimeUs = mTimes[ix];
                }
            }
            if (nextTimeUs > nowUs && !mPaused) {
                // schedule callback at nextTimeUs
                if (DEBUG) Log.d(TAG, "scheduling for " + nextTimeUs + " and " + nowUs);
                mPlayer.notifyAt(nextTimeUs);
            } else {
                mEventHandler.removeMessages(NOTIFY);
                // no more callbacks
            }
            for (MediaTimeProvider.OnMediaTimeListener listener: activatedListeners) {
                listener.onTimedEvent(nowUs);
            }
        }
        public long getCurrentTimeUs(boolean refreshTime, boolean monotonic)
                throws IllegalStateException {
            synchronized (this) {
                // we always refresh the time when the paused-state changes, because
                // we expect to have received the pause-change event delayed.
                if (mPaused && !refreshTime) {
                    return mLastReportedTime;
                }
                try {
                    mLastTimeUs = mPlayer.getCurrentPosition() * 1000L;
                    mPaused = !mPlayer.isPlaying() || mBuffering;
                    if (DEBUG) Log.v(TAG, (mPaused ? "paused" : "playing") + " at " + mLastTimeUs);
                } catch (IllegalStateException e) {
                    if (mPausing) {
                        // if we were pausing, get last estimated timestamp
                        mPausing = false;
                        if (!monotonic || mLastReportedTime < mLastTimeUs) {
                            mLastReportedTime = mLastTimeUs;
                        }
                        mPaused = true;
                        if (DEBUG) Log.d(TAG, "illegal state, but pausing: estimating at " + mLastReportedTime);
                        return mLastReportedTime;
                    }
                    // TODO get time when prepared
                    throw e;
                }
                if (monotonic && mLastTimeUs < mLastReportedTime) {
                    /* have to adjust time */
                    if (mLastReportedTime - mLastTimeUs > 1000000) {
                        // schedule seeked event if time jumped significantly
                        // TODO: do this properly by introducing an exception
                        mStopped = false;
                        mSeeking = true;
                        scheduleNotification(NOTIFY_SEEK, 0 /* delay */);
                    }
                } else {
                    mLastReportedTime = mLastTimeUs;
                }
                return mLastReportedTime;
            }
        }
        private class EventHandler extends Handler {
            public EventHandler(Looper looper) {
                super(looper);
            }
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == NOTIFY) {
                    switch (msg.arg1) {
                        case NOTIFY_TIME:
                            notifyTimedEvent(true /* refreshTime */);
                            break;
                        case NOTIFY_STOP:
                            notifyStop();
                            break;
                        case NOTIFY_SEEK:
                            notifySeek();
                            break;
                        case NOTIFY_TRACK_DATA:
                            //notifyTrackData((Pair<SubtitleTrack, byte[]>)msg.obj);
                            break;
                    }
                }
            }
        }
    }
    public final static class MetricsConstants
    {
        private MetricsConstants() {}
        /**
         * Key to extract the MIME type of the video track
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is a String.
         */
        public static final String MIME_TYPE_VIDEO = "android.media.mediaplayer.video.mime";
        /**
         * Key to extract the codec being used to decode the video track
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is a String.
         */
        public static final String CODEC_VIDEO = "android.media.mediaplayer.video.codec";
        /**
         * Key to extract the width (in pixels) of the video track
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is an integer.
         */
        public static final String WIDTH = "android.media.mediaplayer.width";
        /**
         * Key to extract the height (in pixels) of the video track
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is an integer.
         */
        public static final String HEIGHT = "android.media.mediaplayer.height";
        /**
         * Key to extract the count of video frames played
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is an integer.
         */
        public static final String FRAMES = "android.media.mediaplayer.frames";
        /**
         * Key to extract the count of video frames dropped
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is an integer.
         */
        public static final String FRAMES_DROPPED = "android.media.mediaplayer.dropped";
        /**
         * Key to extract the MIME type of the audio track
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is a String.
         */
        public static final String MIME_TYPE_AUDIO = "android.media.mediaplayer.audio.mime";
        /**
         * Key to extract the codec being used to decode the audio track
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is a String.
         */
        public static final String CODEC_AUDIO = "android.media.mediaplayer.audio.codec";
        /**
         * Key to extract the duration (in milliseconds) of the
         * media being played
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is a long.
         */
        public static final String DURATION = "android.media.mediaplayer.durationMs";
        /**
         * Key to extract the playing time (in milliseconds) of the
         * media being played
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is a long.
         */
        public static final String PLAYING = "android.media.mediaplayer.playingMs";
        /**
         * Key to extract the count of errors encountered while
         * playing the media
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is an integer.
         */
        public static final String ERRORS = "android.media.mediaplayer.err";
        /**
         * Key to extract an (optional) error code detected while
         * playing the media
         * from the {@link MediaPlayer#getMetrics} return value.
         * The value is an integer.
         */
        public static final String ERROR_CODE = "android.media.mediaplayer.errcode";
    }
}