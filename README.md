# OpenCV dependencies

`mkdir .\ObjectTracker\app\src\main\jniLibs`
Copy the contents of `.\opencv_android_build\OpenCV-android-sdk\sdk\native\libs` to `.\ObjectTracker\app\src\main\jniLibs`
You will also need to add the additional c++ libraries from `https://github.com/tintintin/android-opencv-example/tree/master/app/src/main/jniLibs` to the folders in `.\ObjectTracker\app\src\main\jniLibs`
If you don't do the latter step then when you run the application on android you will get a prompt to get the OpenCV Manager app, which apparently is deprecated.