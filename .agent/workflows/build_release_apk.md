---
description: How to build a signed Release APK for distribution
---

# Build Release APK

To distribute your app to friends (or the Play Store), you need a **Signed APK**.

## 1. Generate a Keystore (One-Time Setup)
If you don't have a `release.jks` file yet, run this command in the terminal to generate one.
**Password**: `mesh123456` (Change this for production apps!)

```powershell
& "C:\Program Files\Java\jdk-1.8\bin\keytool.exe" -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias mesh_key -storepass mesh123456 -keypass mesh123456 -dname "CN=MeshUser, OU=Mesh, O=MeshNetwork, L=City, S=State, C=US"
```
// turbo
Move-Item -Path "release.jks" -Destination "app/release.jks" -Force

## 2. Create Keystore Properties
Create a file named `keystore.properties` in the `app/` directory (same place as `build.gradle.kts`) with your passwords.

**File:** `app/keystore.properties`
```properties
storePassword=mesh123456
keyPassword=mesh123456
keyAlias=mesh_key
storeFile=release.jks
```

## 3. Build the APK
Run the following Gradle command to build the release artifact:

```powershell
./gradlew assembleRelease
```

## 4. Locate the APK
Once the build finishes, your APK will be at:
`app/build/outputs/apk/release/app-release.apk`
