n-weibo
React Native's Sina Weibo login sharing plugin

This warehouse fork from [reactnativecn/react-native-weibo] (https://github.com/reactnativecn/react-native-weibo), but the source warehouse has not been maintained for a long time, so I made a new version of react-native and weiboSDK After the adaptation, npm released a new package `rn-weibo`.

And I forked from `rn-weibo` to fix error: "Unknown client id" from Android.

## Version Requirements

+ Adapted to react-native version 0.57.5
+ weiboSDK versions are ios 3.2.3 and Android 4.3.1 respectively

## how to install

### 1. First install the npm package

```bash
Npm install rn-weibo --save
```

### 2.link
#### Automatic link method

```bash
React-native link
```

#### Manual link~ (if you can't automatically link)
#####ios
a. Open the XCode's project, right click on the Libraries folder ➜ Add Files to <...>
b. Go to node_modules ➜ react-native-weibo ➜ ios ➜ select RCTWeiboAPI.xcodeproj
c. Add libRCTWeiboAPI.a to the project Build Phases ➜ Link Binary With Libraries

#####Android

```
// file: android/settings.gradle
...

Include ':react-native-weibo'
Project(':react-native-weibo').projectDir = new File(settingsDir, '../node_modules/react-native-weibo/android')
```

```
// file: android/app/build.gradle
...

Dependencies {
    ...
    Compile project(':react-native-weibo')
}
```

Add the following two lines to `android/app/src/main/java/<your package name>/MainApplication.java`:

```java
...
Import cn.reactnative.modules.weibo.WeiboPackage; // import before public class MainApplication

Public class MainApplication extends Application implements ReactApplication {

  Private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
    @Override
    Protected boolean getUseDeveloperSupport() {
      Return BuildConfig.DEBUG;
    }

    @Override
    Protected List<ReactPackage> getPackages() {
      Return Arrays.<ReactPackage>asList(
 New WeiboPackage(), // then add this line
          New MainReactPackage()
      );
    }
  };

  @Override
  Public ReactNativeHost getReactNativeHost() {
      Return mReactNativeHost;
  }
}
```

### 3.Project configuration
#### ios configuration
Add `node_modules/react-native-weibo/ios/libWeiboSDK/WeiboSDK.bundle` to the project (must be important, otherwise crash when logging in)

Add `libRCTWeiboAPI.a, libsqlite3.tbd, libz.tbd, ImageIO.framework, SystemConfiguration.framework, Security.framework, CoreTelephony.framework, CoreText.framework to `Build Phases->Link Binary with Libraries` in the project target.


Add the QQ scheme in `Info->URL Types`: `Identifier` is set to `sina`, `URL Schemes` is set to the APPID in your registered Weibo developer account, prefixed with `wb`, for example ` Wb1915346979`

Add the following code to your project's `AppDelegate.m` file:

```
#import "../Libraries/LinkingIOS/RCTLinkingManager.h"


- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url sourceApplication:(NSString *)sourceApplication annotation:(id)annotation {
  Return [RCTLinkingManager application:application openURL:url sourceApplication:sourceApplication annotation:annotation];
}

```

##### iOS9 adaptation problem

Since the release of iOS9 affects the integration of Weibo SDK and applications, in order to ensure a good application experience, we need to take the following measures:
##### a. Support for transmission security
On iOS9 systems, SSL is required for each network transfer by default. solve this problem:

- Set the NSAllowsArbitraryLoads property to YES and add it to your app's plist
-
<key>NSAppTransportSecurity</key>
<dict>
<key>NSAllowsArbitraryLoads</key>
</true>
</dict>

###### b. Support for application jumps
If you need to use the relevant features of Weibo, such as login, sharing, etc. And you need to implement the function of jumping to Weibo. In iOS9 system, you need to add the following key-value pairs to your app's plist. Otherwise, NO will be returned when the canOpenURL function is executed. For more information, please go to [https://developer.apple.com/videos/wwdc/2015/?id=703] (https://developer.apple.com/videos/wwdc/2015/?id=703)

-
<key>LSApplicationQueriesSchemes</key>
<array>
<string>sinaweibohd</string>
<string>sinaweibo</string>
<string>weibosdk</string>
<string>weibosdk2.5</string>
</array>


#### Android

In `android/app/build.gradle`, add the following code under the defaultConfig column:

```
manifestPlaceholders = [
    WB_APPID: "wb[APPID]" //Modify Weibo APPID here
]
```

If the react-native version is <0.18.0, make sure your MainActivity.java has an implementation of `onActivityResult`:

```java
@Override
Public void onActivityResult(int requestCode, int resultCode, Intent data){
    super.onActivityResult(requestCode, resultCode, data);
    getReactInstanceManager().onActivityResult(this, requestCode, resultCode, data);}
```

## how to use

### Introducing the package

```
Import * as WeiboAPI from 'react-native-weibo';
```

### API

#### WeiboAPI.login(config)

```javascript
// Login parameter
Config : {
Scope: permission settings, // default 'all'
redirectURI: redirect address, // default 'https://api.weibo.com/oauth2/default.html' (must be the same as the redirectURI setting in the application advanced settings in the sina microblogging open platform, otherwise login will fail)
}
```

Returns a `Promise` object. The callback on success is an object like this:

```javascript
{
"accessToken": "2.005e3HMBzh7eFCca6a3854060GQFJf",
"userID": "1098604232"
"expirationDate": "1452884401084.538"
"refreshToken": "2.005e3HMBzh8eFC3db19a18bb00pvbp"
}
```

#### WeiboAPI.share(data)

share to Weibo

```javascript
// share text
{
Type: 'text',
Text: text content,
}
```

```javascript
// share pictures
{
Type: 'image',
Text: text content,
imageUrl: image address
}
```
