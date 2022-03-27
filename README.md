# MediaPlayer

A MediaPlayer like ([MediaPlayer SDK](https://developer.android.com/reference/android/media/MediaPlayer)) but with [ffmpeg](https://ffmpeg.org/) for decoding file and [oboe](https://github.com/google/oboe) for playback. 

## Usage

### Documentation 
You can use [Android Documentation](https://developer.android.com/reference/android/media/MediaPlayer) as reference. 
All methods in MediaPlayer is not ready to use but you can look at IMediaPlayer for available working function.

Limitations compare with Android MediaPlayer:
* No DRM
* No Volume Shaper
* No auxiliary effect (but it's still possible to use one effect)

### Gradle

Add maven repository to your Gradle file.
```
repositories {
mavenCentral()
maven { url 'https://packagecloud.io/A2va/android/maven2'}
}
```

Add implementation dependency with a tag release or latest for the latest development build (not updated regularly). 
You can get the commit hash of the publish with BuildConfig or with pom file.
```
implementation 'com.github.a2va:media:x.y.z@aar'
implementation 'com.github.a2va:media:latest@aar'
```

### Example

For example you can use interface to choose between the two players.
```java
import com.github.a2va.media.IMediaPlayer;
import com.github.a2va.media.MediaPlayer;
import com.github.a2va.media.AndroidMediaPlayer;

boolean isAndroid = false;
IMediaPlayer mediaPlayer;
if(isAndroid) {
    mediaPlayer = new android.media.MediaPlayer();
} else {
    mediaPlayer = new MediaPlayer();
}
```

## Build

Install:
* Android SDK
* Android NDK
* Docker

You can use Android Studio to install SDK and NDK.
If your are on Windows, you can use Docker to build ffmpeg. But WSL (Windows 10) is needed for that.
https://github.com/Javernaut/ffmpeg-android-maker/wiki/Docker-support

```
// Clone repo
cd /home/username
git clone https://github.com/A2va/MediaPlayer.git
cd MediaPlayer
// Build ffmpeg
docker pull javernaut/ffmpeg-android-maker
docker run --rm -v /home/username/MediaPlayer/ffmpeg-android-maker:/mnt/ffmpeg-android-maker javernaut/ffmpeg-android-maker

// Build demo app and media library
gradlew build
```
