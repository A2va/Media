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

package com.example.media;

import androidx.appcompat.app.AppCompatActivity;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;


import java.util.concurrent.TimeUnit;

import com.github.a2va.media.IMediaPlayer;
import com.github.a2va.media.MediaPlayer;


public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";

    private MediaPlayer mediaPlayer = null;

    private SeekBar seekBar;
    private TextView duration;
    private TextView currentPosition;

    private Handler handler = new Handler();

    private boolean isPrepared = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        seekBar = findViewById(R.id.seekBar);
        duration = findViewById(R.id.duration);
        currentPosition = findViewById(R.id.currentPosition);


        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isPrepared = true;

                int duration_ms = mediaPlayer.getDuration();
                seekBar.setMax(duration_ms);
                seekBar.setProgress(0);

                duration.setText(getTimeString(duration_ms));

            }
        });


        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser && isPrepared) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    private String getTimeString(int millis) {
        return String.format("%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    public void setDataSource(View view) {
        // TODO Choose media file chooser

        /*String path = Environment.getExternalStorageDirectory() + "/Music/Arisu - Ice Sheet.ac3";
        try {
            mediaPlayer.setDataSource(path);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        FileChooserFragment dialogFragment = new FileChooserFragment();
        dialogFragment.setFileChoosedListener(new FileChooserFragment.FileChooserListener() {
            @Override
            public void onFileChoosed(String url, Uri uri) {
                // If the uri is null that a url
                if(uri == null) {
                    try {
                        mediaPlayer.setDataSource(url);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }

                try {
                    mediaPlayer.setDataSource(getApplicationContext(),uri);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });

        dialogFragment.show(getSupportFragmentManager(), "");

    }

    public void prepare(View view) {
        try {
            mediaPlayer.prepare();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    public void start(View view) {
        try{
            handler.postDelayed(updateSongTime, 100);
            mediaPlayer.start();
        } catch(Exception e) {
            e.printStackTrace();
        }

    }

    public void pause(View view) {
        try{
            mediaPlayer.pause();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void stop(View view) {
        try{
            mediaPlayer.stop();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void reset(View view) {
        try{
            mediaPlayer.reset();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void release(View view) {
        try{
            mediaPlayer.release();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private Runnable updateSongTime = new Runnable() {
        @Override
        public void run() {
            int timestamp = mediaPlayer.getCurrentPosition();
            currentPosition.setText(getTimeString(timestamp));
            seekBar.setProgress(timestamp);

            handler.postDelayed(this, 100);
        }
    };

}