# SmartVS Android SDK

This repository contains the **SmartVS SDK** for integrating [Wellvii](https://github.com/Wellvii)'s SmartVS features into your Android application.

## 📦 Installation via JitPack

### Step 1: Add JitPack to your project-level `settings.gradle`

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2: Add the SDK dependency to your app-level `build.gradle`

```groovy
dependencies {
    implementation 'com.github.Wellvii:SmartVS-App-Android-SDK-Public:Tag'
}
```

> 🔖 Replace `Tag` with the latest [GitHub Release Tag](https://jitpack.io/#Wellvii/SmartVS-App-Android-SDK-Public), such as `v1.0.0`.

---
