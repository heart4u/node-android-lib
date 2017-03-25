node-android-lib
===============

Just make [node-android](https://github.com/InstantWebP2P/node-android) be able to be used as a library.

For more information, see the ogrginal repo [node-android](https://github.com/InstantWebP2P/node-android).

### How to use

To get a Git project into your build:
#### Step 1. Add the JitPack repository to your build file
Add it in your root build.gradle at the end of repositories:
```
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

#### Step 2. Add the dependency
```
	dependencies {
	        compile 'com.github.hyb1996:node-android-lib:1.0.13'
	}
```