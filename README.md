# Example of bytecode patching in fat-aar plugin

This project showcases how bytecode patching can be used with [fat-aar](https://github.com/kezong/fat-aar-android) plugin.

## Usage

If you build and run the app, you can see it's crashing, because R file of :mylibrary cannot be found. Now, go to [fat-library/build.gradle](https://github.com/aasitnikov/fat-aar-bytecode-patching/blob/master/fat-library/build.gradle#L26), and uncomment `android.registerTransform(...)` lines, run `./gradlew :fat-library:publishAar`, and rerun the app. Now you can see, that app works fine and doesn't crash.

## Additional info

More info can be found in [this issue](https://github.com/kezong/fat-aar-android/issues/184). [Bytecode patcher](https://github.com/aasitnikov/fat-aar-bytecode-patching/blob/master/buildSrc/src/main/java/com/example/EmbedRClassesBytecodeTransformer.java) is copied from [this gist](https://gist.github.com/aasitnikov/1e1c8047566d2c3e9b416b5e15c7feaa).

