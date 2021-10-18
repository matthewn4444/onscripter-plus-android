This repository is not active anymore.

===========================
Onscripter Plus For Android
===========================
December 27, 2013

Fork of Onscripter for Android (http://onscripter.sourceforge.jp/android/android.html)
that is compiled on Windows including full source (with dependencies).

This Android application allows you to play visual novel games using the NScripter engine
on your Android device.


Steps to Compile
================
1. Install Java [https://www.java.com/en/] (match bit with Eclipse, probably get 32-bit)
2. Install Eclipse with Android SDK [https://developer.android.com/sdk/index.html] (32bit probably) 
3. Run the "Android SDK Manager" and install:
    - Tools/ - All
    - Android 2.2 (API 8) - All
    - Extras/ -> Support Repo, Support Library, Google USB Driver, Google Web Driver
    - [OPTIONAL] Android 4.0 (API 14) and/or anything newer
4. [WINDOWS] Install Cywin [http://cygwin.com/install.html]
    - When choosing what to install, only install:
        DLevel -> make
5. [WINDOWS] Set up the environment variable for cygwin in PATH (to the bin folder)
    C:\<path to cywin>/bin;
6. Install the NDK [http://developer.android.com/tools/sdk/ndk/index.html] (Doesn't matter the bit)
7. Setup NDK in Eclipse: 
    - Open Eclipse
    - Window -> Preference -> Android -> NDK -> [Browse the location of the NDK folder]
        C:\<folder to ndk>\android-ndk
8. Git clone this repo a folder
9. Open Eclipse and Import the project
    - Open Eclipse
    - File -> Import... -> Android -> Existing Android Code Into Workspace -> [Then find the repo]
10. Add Native Support to project:
    - Right click ONScripterPlus -> Android Tools -> Add Native Support (If no option, then you already have native support)
11. [WINDOWS] Open file "<project>/jni/application/Android.mk"
    - Change the part of APP_SUBDIR of "C:\Programming\cygwin64\bin\find" to cygwin "find"
11. [WINDOWS] Open file "<project>/jni/freetype/Android.mk"
    - Change the part of APP_SUBDIR of "C:\Programming\cygwin64\bin\find" to cygwin "find"
11. Build the project (ONScipter takes a while to build) and run on device


Change version of ONScripter
============================
1. Download new version of ONScripter source code based on your operating system from http://onscripter.sourceforge.jp/onscripter.html
2. Delete the folder "<project>/jni/application/onscripter-*" (e.g. /onscripter-20130202)
3. Extract the folder "onscripter-*" (e.g. onscripter-20130202) source into "<project>/jni/application"
4. Rebuild the code from Eclipse


Play Visual Novel
=================
1. Download a visual novel game that uses NScripter
2. Plug Android device in computer and navigate to the internal memory of the Android device
3. Make a new folder "ons"
4. Place the game folder into "ons" folder
5. [OPTIONAL] Place a font (Japanese preferably) into the game folder and name it "default.ttf"
    - if no font is provided, it will use a default font
6. You can delete the dll files
