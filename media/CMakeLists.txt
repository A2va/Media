project(Media)
cmake_minimum_required(VERSION 3.10.0)

## FFmpeg

set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg-android-maker/output)
set(ffmpeg_libs ${ffmpeg_dir}/lib/${ANDROID_ABI})

include_directories(${ffmpeg_dir}/include/${ANDROID_ABI})
set(
        # List variable name
        ffmpeg_libs_names
        # Values in the list
        avutil avformat avcodec swscale swresample)

foreach (ffmpeg_lib_name ${ffmpeg_libs_names})
    add_library(
            ${ffmpeg_lib_name}
            SHARED
            IMPORTED)
    set_target_properties(
            ${ffmpeg_lib_name}
            PROPERTIES
            IMPORTED_LOCATION
            ${ffmpeg_libs}/lib${ffmpeg_lib_name}.so)
endforeach ()


include_directories(src/main/cpp)
add_library(media SHARED

        # All cpp files to compile
        src/main/cpp/media-player.cpp

        src/main/cpp/FFmpegDecoder.cpp
        src/main/cpp/FFmpegDecoder.h

        src/main/cpp/MediaPlayer.cpp
        src/main/cpp/MediaPlayer.h

        src/main/cpp/utils.cpp
        src/main/cpp/utils.h

        src/main/cpp/callback/IRenderableAudio.h
        src/main/cpp/callback/DefaultDataCallback.h
        src/main/cpp/callback/FFmpegCallback.h

        # JNI utils
        src/main/cpp/jni/utils.cpp
        src/main/cpp/jni/utils.h
        src/main/cpp/jni/exception.cpp
        src/main/cpp/jni/exception.h

        # Oboe Fifo Buffer
        src/main/cpp/fifo/FifoBuffer.cpp
        src/main/cpp/fifo/FifoBuffer.h
        src/main/cpp/fifo/FifoController.cpp
        src/main/cpp/fifo/FifoController.h
        src/main/cpp/fifo/FifoControllerBase.cpp
        src/main/cpp/fifo/FifoControllerBase.h
        src/main/cpp/fifo/FifoControllerIndirect.cpp
        src/main/cpp/fifo/FifoControllerIndirect.h


        )


find_package (oboe REQUIRED CONFIG)
# Include libraries needed for hello-jni lib
target_link_libraries(media
                      log
                      oboe::oboe
        # FFmpeg libraries
        ${ffmpeg_libs_names})

#[[target_link_libraries(media
        android
        log
        oboe::oboe)]]
