package cn.reactnative.modules.weibo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.internal.Preconditions;
import com.facebook.common.references.CloseableReference;
import com.facebook.common.util.UriUtil;
import com.facebook.datasource.BaseDataSubscriber;
import com.facebook.datasource.DataSource;
import com.facebook.datasource.DataSubscriber;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.drawable.OrientedDrawable;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.image.CloseableStaticBitmap;
import com.facebook.imagepipeline.image.EncodedImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.sina.weibo.sdk.api.ImageObject;
import com.sina.weibo.sdk.api.TextObject;
import com.sina.weibo.sdk.api.VideoSourceObject;
import com.sina.weibo.sdk.api.WebpageObject;
import com.sina.weibo.sdk.api.WeiboMultiMessage;
import com.sina.weibo.sdk.auth.WbAuthListener;
import com.sina.weibo.sdk.auth.AuthInfo;
import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.auth.WbConnectErrorMessage;
import com.sina.weibo.sdk.share.WbShareCallback;

import com.sina.weibo.sdk.WbSdk;
import com.sina.weibo.sdk.share.WbShareHandler;

import java.util.Date;
import javax.annotation.Nullable;

/**
 * Created by lvbingru on 12/22/15.
 */
public class WeiboModule extends ReactContextBaseJavaModule implements ActivityEventListener, WbShareCallback  {

    public WeiboModule(ReactApplicationContext reactContext) {
        super(reactContext);
        ApplicationInfo appInfo = null;
        try {
            appInfo = reactContext.getPackageManager().getApplicationInfo(reactContext.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            throw new Error(e);
        }
        if (!appInfo.metaData.containsKey("WB_APPID")){
            throw new Error("meta-data WB_APPID not found in AndroidManifest.xml");
        }
        this.appId = appInfo.metaData.get("WB_APPID").toString();
        this.appId = this.appId.substring(2);
    }

    private static final String RCTWBEventName = "Weibo_Resp";

    private SsoHandler mSsoHandler;
    private String appId;
    private boolean wbSdkInstalled = false;

    private WbShareHandler shareHandler;

    private static final String RCTWBShareTypeNews = "news";
    private static final String RCTWBShareTypeImage = "image";
    private static final String RCTWBShareTypeText = "text";
    private static final String RCTWBShareTypeVideo = "video";

    private static final String RCTWBShareType = "type";
    private static final String RCTWBShareText = "text";
    private static final String RCTWBShareTitle = "title";
    private static final String RCTWBShareDescription = "description";
    private static final String RCTWBShareWebpageUrl = "webpageUrl";
    private static final String RCTWBSharePropVideoPath = "videoPath";
    private static final String RCTWBShareImageUrl = "imageUrl";
    private static final String RCTWBShareAccessToken = "accessToken";

    private static WeiboModule gModule = null;

    @Override
    public void initialize() {
        super.initialize();
        gModule = this;
        getReactApplicationContext().addActivityEventListener(this);
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        gModule = null;
        getReactApplicationContext().removeActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "RCTWeiboAPI";
    }

    private WbShareHandler registerShare() {
        if (shareHandler == null) {
            shareHandler = new WbShareHandler(getCurrentActivity());
            shareHandler.registerApp();
        }
        return shareHandler;
    }

    private void _installWbSdk(final ReadableMap config) {
        if (!wbSdkInstalled) {
            AuthInfo sinaAuthInfo = this._genAuthInfo(config);
            WbSdk.install(getCurrentActivity(), sinaAuthInfo);
            wbSdkInstalled = true;
        }
    }


    @ReactMethod
    public void login(final ReadableMap config, final Callback callback){

        this._installWbSdk(config);

        if (mSsoHandler == null) {
            mSsoHandler = new SsoHandler(getCurrentActivity());
            mSsoHandler.authorize(this.genWeiboAuthListener());
        }

        callback.invoke();
    }

    @ReactMethod
    public void shareToWeibo(final ReadableMap data, Callback callback){

        this._installWbSdk(data);

        if (data.hasKey(RCTWBShareImageUrl)) {
            String imageUrl = data.getString(RCTWBShareImageUrl);
            DataSubscriber<CloseableReference<CloseableImage>> dataSubscriber =
                    new BaseDataSubscriber<CloseableReference<CloseableImage>>() {
                        @Override
                        public void onNewResultImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                            // isFinished must be obtained before image, otherwise we might set intermediate result
                            // as final image.
                            boolean isFinished = dataSource.isFinished();
//                        float progress = dataSource.getProgress();
                            CloseableReference<CloseableImage> image = dataSource.getResult();
                            if (image != null) {
                                Drawable drawable = _createDrawable(image);
                                Bitmap bitmap = _drawable2Bitmap(drawable);
                                _share(data, bitmap);
                            } else if (isFinished) {
                                _share(data, null);
                            }
                            dataSource.close();
                        }
                        @Override
                        public void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                            dataSource.close();
                            _share(data, null);
                        }

                        @Override
                        public void onProgressUpdate(DataSource<CloseableReference<CloseableImage>> dataSource) {
                        }
                    };
            ResizeOptions resizeOptions = null;
            if (!data.hasKey(RCTWBShareType) || !data.getString(RCTWBShareType).equals(RCTWBShareTypeImage)) {
                resizeOptions = new ResizeOptions(80, 80);
            }

            this._downloadImage(imageUrl, resizeOptions, dataSubscriber);
        }
        else {
            this._share(data, null);
        }

        callback.invoke();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mSsoHandler != null) {
            mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
            mSsoHandler = null;
        }
    }

    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data){
        this.onActivityResult(requestCode, resultCode, data);
    }

    public void onNewIntent(Intent intent){

    }

    WbAuthListener genWeiboAuthListener() {
        return new WbAuthListener() {

            @Override
            public void onSuccess(final Oauth2AccessToken token) {
                WritableMap event = Arguments.createMap();

                if (token.isSessionValid()) {
                    event.putString("accessToken", token.getToken());
                    event.putDouble("expirationDate", token.getExpiresTime());
                    event.putString("userID", token.getUid());
                    event.putString("refreshToken", token.getRefreshToken());
                    event.putInt("errCode", 0);
                } else {
//                    String code = bundle.getString("code", "");
                    event.putInt("errCode", -1);
                    event.putString("errMsg", "token invalid");
                }
                event.putString("type", "WBAuthorizeResponse");
                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(RCTWBEventName, event);

                // WBAuthActivity.this.runOnUiThread(new Runnable() {
                //     @Override
                //     public void run() {
                //         mAccessToken = token;
                //         if (mAccessToken.isSessionValid()) {
                //             // 显示 Token
                //             updateTokenView(false);
                //             // 保存 Token 到 SharedPreferences
                //             AccessTokenKeeper.writeAccessToken(WBAuthActivity.this, mAccessToken);
                //             Toast.makeText(WBAuthActivity.this,
                //                     R.string.weibosdk_demo_toast_auth_success, Toast.LENGTH_SHORT).show();
                //         }
                //     }
                // });
            }


            @Override
            public void onFailure(WbConnectErrorMessage e) {
                WritableMap event = Arguments.createMap();
                event.putString("type", "WBAuthorizeResponse");
                event.putString("errMsg", e.getErrorMessage());
                event.putInt("errCode", -1);
                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(RCTWBEventName, event);
            }

            @Override
            public void cancel() {
                WritableMap event = Arguments.createMap();
                event.putString("type", "WBAuthorizeResponse");
                event.putString("errMsg", "Cancel");
                event.putInt("errCode", -1);
                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(RCTWBEventName, event);
            }
        };
    }

    private void _share(ReadableMap data, Bitmap bitmap) {

        this.registerShare();
        WeiboMultiMessage weiboMessage = new WeiboMultiMessage();//初始化微博的分享消息
        TextObject textObject = new TextObject();
        if (data.hasKey(RCTWBShareText)) {
            textObject.text = data.getString(RCTWBShareText);
        }
        weiboMessage.textObject = textObject;

        String type = RCTWBShareTypeNews;
        if (data.hasKey(RCTWBShareType)){
            type = data.getString(RCTWBShareType);
        }

        if (type.equals(RCTWBShareTypeText)) {
        }
        else if (type.equals(RCTWBShareTypeImage)) {
            ImageObject imageObject = new ImageObject();
            if (bitmap != null) {
                Log.e("share","hasBitmap");
                imageObject.setImageObject(bitmap);
            }
            weiboMessage.imageObject = imageObject;
        }
        else {
            if (type.equals(RCTWBShareTypeNews)) {
                WebpageObject webpageObject = new WebpageObject();
                if (data.hasKey(RCTWBShareWebpageUrl)) {
                    webpageObject.actionUrl = data.getString(RCTWBShareWebpageUrl);
                }
                weiboMessage.mediaObject = webpageObject;
            }
            else if (type.equals(RCTWBShareTypeVideo)) {
                VideoSourceObject videoObject = new VideoSourceObject();
                // if (data.hasKey(RCTWBShareWebpageUrl)) {
                //     videoObject.dataUrl = data.getString(RCTWBShareWebpageUrl);
                // }

                // videoObject.videoPath = data.getString(RCTWBSharePropVideoPath);
                weiboMessage.mediaObject = videoObject;
            }
            if (data.hasKey(RCTWBShareDescription)) {
                weiboMessage.mediaObject.description = data.getString(RCTWBShareDescription);
            }
            if (data.hasKey(RCTWBShareTitle)) {
                weiboMessage.mediaObject.title = data.getString(RCTWBShareTitle);
            }
            if (bitmap != null) {
                weiboMessage.mediaObject.setThumbImage(bitmap);
            }
            weiboMessage.mediaObject.identify = new Date().toString();
        }

        shareHandler.shareMessage(weiboMessage, false);
    }


    @Override
    public void onWbShareSuccess() {
        WritableMap event = Arguments.createMap();
        event.putString("type", "WBSendMessageToWeiboResponse");
        event.putInt("errCode", 0);

        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(RCTWBEventName, event);
    }

    @Override
    public void onWbShareFail() {
        WritableMap map = Arguments.createMap();
        map.putInt("errCode", -1);
        map.putString("errMsg", "分享失败");
        map.putString("type", "WBSendMessageToWeiboResponse");
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(RCTWBEventName, map);
    }

    @Override
    public void onWbShareCancel() {
        WritableMap map = Arguments.createMap();
        map.putInt("errCode", -1);
        map.putString("errMsg", "分享取消");
        map.putString("type", "WBSendMessageToWeiboResponse");
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(RCTWBEventName, map);
    }

    private AuthInfo _genAuthInfo(ReadableMap config) {
        String redirectURI = "";
        if (config.hasKey("redirectURI")) {
            redirectURI = config.getString("redirectURI");
        }
        String scope = "";
        if (config.hasKey("scope")) {
            scope = config.getString("scope");
        }

        final AuthInfo sinaAuthInfo = new AuthInfo(getReactApplicationContext(), this.appId, redirectURI, scope);
        return sinaAuthInfo;
    }

    private void  _downloadImage(String imageUrl, ResizeOptions resizeOptions,DataSubscriber<CloseableReference<CloseableImage>> dataSubscriber) {
        Uri uri = null;
        try {
            uri = Uri.parse(imageUrl);
            // Verify scheme is set, so that relative uri (used by static resources) are not handled.
            if (uri.getScheme() == null) {
                uri = null;
            }
        } catch (Exception e) {
            // ignore malformed uri, then attempt to extract resource ID.
        }
        if (uri == null) {
            uri = _getResourceDrawableUri(getReactApplicationContext(), imageUrl);
        } else {
        }

        ImageRequestBuilder builder = ImageRequestBuilder.newBuilderWithSource(uri);
        if (resizeOptions != null) {
            builder.setResizeOptions(resizeOptions);
        }
        ImageRequest imageRequest = builder.build();

        ImagePipeline imagePipeline = Fresco.getImagePipeline();
        DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);
        dataSource.subscribe(dataSubscriber, UiThreadImmediateExecutorService.getInstance());
    }

    private static @Nullable
    Uri _getResourceDrawableUri(Context context, @Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        name = name.toLowerCase().replace("-", "_");
        int resId = context.getResources().getIdentifier(
                name,
                "drawable",
                context.getPackageName());
        return new Uri.Builder()
                .scheme(UriUtil.LOCAL_RESOURCE_SCHEME)
                .path(String.valueOf(resId))
                .build();
    }

    private Drawable _createDrawable(CloseableReference<CloseableImage> image) {
        Preconditions.checkState(CloseableReference.isValid(image));
        CloseableImage closeableImage = image.get();
        if (closeableImage instanceof CloseableStaticBitmap) {
            CloseableStaticBitmap closeableStaticBitmap = (CloseableStaticBitmap) closeableImage;
            BitmapDrawable bitmapDrawable = new BitmapDrawable(
                    getReactApplicationContext().getResources(),
                    closeableStaticBitmap.getUnderlyingBitmap());
            if (closeableStaticBitmap.getRotationAngle() == 0 ||
                    closeableStaticBitmap.getRotationAngle() == EncodedImage.UNKNOWN_ROTATION_ANGLE) {
                return bitmapDrawable;
            } else {
                return new OrientedDrawable(bitmapDrawable, closeableStaticBitmap.getRotationAngle());
            }
        } else {
            throw new UnsupportedOperationException("Unrecognized image class: " + closeableImage);
        }
    }

    private Bitmap _drawable2Bitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        } else if (drawable instanceof NinePatchDrawable) {
            Bitmap bitmap = Bitmap
                    .createBitmap(
                            drawable.getIntrinsicWidth(),
                            drawable.getIntrinsicHeight(),
                            drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                                    : Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            return bitmap;
        } else {
            return null;
        }
    }
}
