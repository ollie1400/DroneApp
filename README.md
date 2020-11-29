An Android app to explore some of the functionality of the DJI SDK using a Mavic Mini.

We try to emulate the "active tracking" feature of other drones by using OpenCV tracking on the live images from the drone's camera, tracking an object in the scene and moving the drone appropriately.

I wasn't able to find OpenCV Android binaries that had the tracking functionality already inside them, so we have to build it from source.

## Relevant literature

- https://www.pyimagesearch.com/2018/07/30/opencv-object-tracking/ A nice review of the available tracking algorithms available in OpenCV
- https://github.com/DJI-Mobile-SDK-Tutorials/Android-FPVDemo An example Android app from DJI about FPV
- https://futurestud.io/tutorials/how-to-debug-your-android-app-over-wifi-without-root How to do remote debugging of an Android app (over the local network)
- https://jbit.net/Android_Colors/ A lookup table for the ANdroid colour formats (for example, the value of the `color-format` field in the `MediaFormat` object return in the YUV data callback)
- https://android.googlesource.com/platform/hardware/qcom/msm8994/+/4d667ba/kernel-headers/media/msm_media_info.h#85 likely information about the `YCbCr_420_SP_VENUS_UBWC` format

# Compiling OpenCV with Tracking functionality, and using it in an Android project

## Compiling OpenCV

Clone opencv from `git@github.com:opencv/opencv.git`

Clone opencv_contrib from `git@github.com:opencv/opencv_contrib.git`

Configure CMake following `https://perso.uclouvain.be/allan.barrea/opencv/cmake_config.html`

Generate

Build solution

ensure `ANDROID_SDK` and `ANDROID_NDK` environment variables are defined that point to the android sdk and ndk location respectively.

Install `ninja` from `https://github.com/ninja-build/ninja/releases` (I used 1.10.0), and ensure it is on the path.  (`build_sdk` is supposed to use an android SDK cmake but that isn't apparently available)

```
mkdir opencv_android_build
cd opencv_android_build
python ..\opencv\platforms\android\build_sdk.py --extra_modules_path="..\opencv_contrib\modules"
```

If you get an error like:
```
Starting process 'Gradle build daemon'. Working directory: C:\Users\ONIDemoUnit2\.gradle\daemon\4.6 Command: C:\Program Files (x86)\Java\jdk1.8.0_181\bin\java.exe -Xmx2g -Dfile.encoding=UTF-8 -Duser.country=GB -Duser.language=en -Duser.variant -cp C:\Users\ONIDemoUnit2\.gradle\wrapper\dists\gradle-4.6-all\bcst21l2brirad8k2ben1letg\gradle-4.6\lib\gradle-launcher-4.6.jar org.gradle.launcher.daemon.bootstrap.GradleDaemon 4.6
Successfully started process 'Gradle build daemon'
An attempt to start the daemon took 0.099 secs.

FAILURE: Build failed with an exception.

* What went wrong:
Unable to start the daemon process.
This problem might be caused by incorrect configuration of the daemon.
For example, an unrecognized jvm option is used.
Please refer to the user guide chapter on the daemon at https://docs.gradle.org/4.6/userguide/gradle_daemon.html
Please read the following process output to find out more:
-----------------------
```
... you need to make sure that when cmake for opencv is configured at the start it is pointing to a 64bit version of java.  Check the values in the `JAVA` group in the opencv cmake configuration.


If you get an error like...
```
Executing: ['ninja', 'opencv_modules']
Executing: ninja opencv_modules
[292/292] Linking CXX static library lib\armeabi-v7a\libopencv_tracking.a
Executing: ['ninja', '-j3', 'install/strip']
Executing: ninja -j3 install/strip
ninja: error: 'samples/android/15-puzzle/opencv_java_android', needed by 'CMakeFiles/dephelper/android_sample_15-puzzle', missing and no known rule to make it
```
... then it is probably because some required modules are not being installed.  Make sure that these are not being excluded by the white-list (by adding some things to the `--modules_list` option but not others)


## Adding to android studio project

Go to `File --> Import Module` and find the directory `opencv_android_build\OpenCV-android-sdk\sdk\java`.  Next, Next and Finish.

In the `build.gradle` file for the opencv project, comment out the `applicationId` setting, e.g.:
```
//    defaultConfig {
//        applicationId "org.opencv"
//    }
```

`mkdir .\app\src\main\jniLibs`
Copy the contents of `.\opencv_android_build\OpenCV-android-sdk\sdk\native\libs` to `.\ObjectTracker\app\src\main\jniLibs`
You will also need to add the additional c++ libraries from `https://github.com/tintintin/android-opencv-example/tree/master/app/src/main/jniLibs` to the folders in `.\app\src\main\jniLibs`
If you don't do the latter step then when you run the application on android you will get a prompt to get the OpenCV Manager app, which apparently is deprecated.

Inside `app/build.gradle` I changed
```
    implementation fileTree(dir: "libs", include: ["*.jar"])
```
to
```
    implementation fileTree(dir: "libs", include: ["*.jar", "*.so"])
```
so make sure that the opencv objects were being compiled.  This may or may not be important...
