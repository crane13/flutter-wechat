package com.onmr.flutter.wechat;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.EventChannel.StreamHandler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.Toast;

import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXImageObject;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXMiniProgramObject;
import com.tencent.mm.opensdk.modelmsg.WXMusicObject;
import com.tencent.mm.opensdk.modelmsg.WXTextObject;
import com.tencent.mm.opensdk.modelmsg.WXVideoObject;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.SoftReference;
import java.text.DecimalFormat;
import java.util.List;
import java.lang.OutOfMemoryError;

/** WechatPlugin */
public class WechatPlugin implements MethodCallHandler {
  public static final String TAG = "WechatPlugin";
  private static final int THUMB_SIZE_MINIPROGRAM = 350;
  private static int code;//返回错误吗
  private static String loginCode;//获取access_code
  private static IWXAPI api;
  private static Result result;
  private Context context;
  private String appid;
  private Bitmap bitmap;
  private WXMediaMessage message;
  private String kind = "session";
  private final PluginRegistry.Registrar registrar;
  private BroadcastReceiver sendRespReceiver;

  private WechatPlugin(Context ctx, Registrar registrar) {
    this.registrar = registrar;
    context = ctx;
  }

  public static int getCode() {
    return code;
  }

  public static void setCode(int value) {
    code = value;
  }

  public static String getLoginCode() {
    return loginCode;
  }

  public static void setLoginCode(String value) {
    loginCode = value;
  }

  private Handler handler = new Handler(new Handler.Callback() {
    @Override
    public boolean handleMessage(Message osMessage) {
      SendMessageToWX.Req request = new SendMessageToWX.Req();
      request.scene = kind.equals("timeline")
        ? SendMessageToWX.Req.WXSceneTimeline
        : kind.equals("favorite")
          ? SendMessageToWX.Req.WXSceneFavorite
          : SendMessageToWX.Req.WXSceneSession;

      if (bitmap != null) {
        Bitmap thumbBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true);
        message.thumbData = convertBitmapToByteArray(thumbBitmap, true);
      }
      switch (osMessage.what) {
        case 0:
          request.transaction = String.valueOf(System.currentTimeMillis());
          request.message = message;
          api.sendReq(request);
          break;
        case 1:
          if (bitmap == null) {
            Toast.makeText(context, "图片路径错误", Toast.LENGTH_SHORT).show();
            break;
          }
          WXImageObject imageObject = new WXImageObject(bitmap);
          message.mediaObject = imageObject;
          request.transaction = String.valueOf(System.currentTimeMillis());
          request.message = message;
          api.sendReq(request);
          break;
        case 2:
          request.transaction = String.valueOf(System.currentTimeMillis());
          request.message = message;
          api.sendReq(request);
          break;
        case 3:
          request.transaction = String.valueOf(System.currentTimeMillis());
          request.message = message;
          api.sendReq(request);
          break;
        case 4:
          request.transaction = String.valueOf(System.currentTimeMillis());
          request.message = message;
          api.sendReq(request);
          break;
        default:
          break;
      }
      return false;
    }
  });

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "wechat");
    channel.setMethodCallHandler(new WechatPlugin(registrar.context(), registrar));
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction("sendResp");
    registrar.context().registerReceiver(createReceiver(), intentFilter);
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    }
    else if (call.method.equals("register")) {
      appid = call.argument("appid");
      api = WXAPIFactory.createWXAPI(context, appid, true);
      result.success(api.registerApp(appid));
    }
    // Check if wechat app installed
    else if (call.method.equals("isWechatInstalled")) {
      if (api == null) {
        result.success(false);
      } else {
        result.success(api.isWXAppInstalled());
      }
    }
    else if (call.method.equals("getApiVersion")) {
      result.success(api.getWXAppSupportAPI());
    }
    else if (call.method.equals("openWechat")) {
      result.success(api.openWXApp());
    }
    else if (call.method.equals("share")) {
      final String kind = call.argument("kind");
      final String to = call.argument("to");
      final String coverUrl = call.argument("coverUrl");
      SendMessageToWX.Req request = new SendMessageToWX.Req();
      message = new WXMediaMessage();
      request.scene = to.equals("timeline")
        ? SendMessageToWX.Req.WXSceneTimeline
        : to.equals("favorite")
          ? SendMessageToWX.Req.WXSceneFavorite
          : SendMessageToWX.Req.WXSceneSession;
      switch (kind) {
        case "text":
          WXTextObject textObject = new WXTextObject();
          final String text = call.argument("text");
          textObject.text = text;
          message.mediaObject = textObject;
          message.description = text;
          request.transaction = String.valueOf(System.currentTimeMillis());
          request.message = message;
          api.sendReq(request);
          break;
        case "music":
          WXMusicObject musicObject = new WXMusicObject();
          musicObject.musicUrl = call.argument("resourceUrl").toString();
          musicObject.musicDataUrl = call.argument("resourceUrl").toString();
          musicObject.musicLowBandDataUrl = call.argument("resourceUrl").toString();
          musicObject.musicLowBandUrl = call.argument("resourceUrl").toString();
          message = new WXMediaMessage();
          message.mediaObject = musicObject;
          message.title = call.argument("title").toString();
          message.description = call.argument("description").toString();
          //网络图片或者本地图片
          new Thread() {
            public void run() {
              Message osMessage = new Message();
              bitmap = GetBitmap(coverUrl);
              osMessage.what = 2;
              handler.sendMessage(osMessage);
            }
          }.start();
          break;
        case "video":
          WXVideoObject videoObject = new WXVideoObject();
          videoObject.videoUrl = call.argument("resourceUrl").toString();
          videoObject.videoLowBandUrl = call.argument("resourceUrl").toString();
          message = new WXMediaMessage();
          message.mediaObject = videoObject;
          message.title = call.argument("title").toString();
          message.description = call.argument("description").toString();
          //网络图片或者本地图片
          new Thread() {
            public void run() {
              Message osMessage = new Message();
              bitmap = GetBitmap(coverUrl);
              osMessage.what = 3;
              handler.sendMessage(osMessage);
            }
          }.start();
          break;
        case "image":
          message = new WXMediaMessage();
          message.title = call.argument("title").toString();
          message.description = call.argument("description").toString();
          final String imageResourceUrl = call.argument("resourceUrl");
          //网络图片或者本地图片
          new Thread() {
            public void run() {
              Message osMessage = new Message();
              if(URLUtil.isValidUrl(imageResourceUrl))
              {
                bitmap = GetBitmap(imageResourceUrl);
              }else {
                bitmap = GetLocalBitmap(context, imageResourceUrl);
              }

              osMessage.what = 1;
              handler.sendMessage(osMessage);
            }
          }.start();
          break;
        case "webpage":
          WXWebpageObject webpageObject = new WXWebpageObject();
          webpageObject.webpageUrl = call.argument("url").toString();
          message = new WXMediaMessage();
          message.mediaObject = webpageObject;
          message.title = call.argument("title").toString();
          message.description = call.argument("description").toString();
          //网络图片或者本地图片
          new Thread() {
            public void run() {
              Message osMessage = new Message();
              bitmap = GetBitmap(coverUrl);
              osMessage.what = 0;
              handler.sendMessage(osMessage);
            }
          }.start();
          break;
        case "miniprogram":
          WXMiniProgramObject miniProgramObj = new WXMiniProgramObject();
          miniProgramObj.webpageUrl = call.argument("url").toString(); // 兼容低版本的网页链接
          miniProgramObj.miniprogramType = WXMiniProgramObject.MINIPTOGRAM_TYPE_RELEASE;// 正式版:0，测试版:1，体验版:2
          miniProgramObj.userName = call.argument("mina_id").toString();     // 小程序原始id
          miniProgramObj.path = call.argument("mina_path").toString();            //小程序页面路径
          message = new WXMediaMessage(miniProgramObj);
          message.title = call.argument("title").toString();                    // 小程序消息title
          message.description = call.argument("description").toString();               // 小程序消息desc

          //网络图片或者本地图片
          new Thread() {
            public void run() {
              Message osMessage = new Message();
              bitmap = GetBitmap(coverUrl);
              bitmap = convertBitmapTo5x4(bitmap, THUMB_SIZE_MINIPROGRAM,
                      THUMB_SIZE_MINIPROGRAM * 4 / 5);
              osMessage.what = 4;
              handler.sendMessage(osMessage);
            }
          }.start();
          break;
        case "openminiprogram":

          WXLaunchMiniProgram.Req req = new WXLaunchMiniProgram.Req();
          req.userName = call.argument("mina_id").toString(); // 填小程序原始id
          req.path = call.argument("mina_path").toString();                  //拉起小程序页面的可带参路径，不填默认拉起小程序首页
          req.miniprogramType = WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE;// 可选打开 开发版，体验版和正式版
          api.sendReq(req);
          break;
      }
    }
    else if (call.method.equals("login")) {
      final String scope = call.argument("scope").toString();
      final String state = call.argument("state").toString();
      SendAuth.Req authRequest = new SendAuth.Req();
      authRequest.scope = scope;
      authRequest.state = state;
      api.sendReq(authRequest);
    }
    else if (call.method.equals("pay")) {
      final String appId = call.argument("appId").toString();
      final String partnerId = call.argument("partnerId").toString();
      final String prepayId = call.argument("prepayId").toString();
      final String nonceStr = call.argument("nonceStr").toString();
      final String timestamp = call.argument("timestamp").toString();
      final String sign = call.argument("sign").toString();
      final String packageValue = call.argument("package").toString();
      PayReq payRequest = new PayReq();
      payRequest.partnerId = partnerId;
      payRequest.prepayId = prepayId;
      payRequest.nonceStr = nonceStr;
      payRequest.timeStamp = timestamp;
      payRequest.sign = sign;
      payRequest.packageValue = packageValue;
      payRequest.appId = appId;
      api.sendReq(payRequest);
    }
    else {
      result.notImplemented();
    }
  }

  public Bitmap GetBitmap(String url) {
    Bitmap bitmap = null;
    InputStream in = null;
    BufferedOutputStream out = null;
    try {
      in = new BufferedInputStream(new URL(url).openStream(), 1024);
      final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
      out = new BufferedOutputStream(dataStream, 1024);
      copy(in, out);
      out.flush();
      byte[] data = dataStream.toByteArray();
      bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
      return bitmap;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public Bitmap GetLocalBitmap(Context context, String path)
  {
//    if(!TextUtils.isEmpty(path) && (context instanceof Activity))
//    {
//        return BitmapFactory.decodeFile(path);
////      return ImageUtils.getPhoto((Activity) context, Uri.parse(path));
//    }
//    return null;
        return BitmapFactory.decodeFile(path);
  }

  public byte[] convertBitmapToByteArray(final Bitmap bitmap, final boolean needRecycle) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
		bitmap.compress(CompressFormat.PNG, 100, output);
		if (needRecycle) {
			bitmap.recycle();
		}

		byte[] result = output.toByteArray();
		try {
			output.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] b = new byte[1024];
    int read;
    while ((read = in.read(b)) != -1) {
      out.write(b, 0, read);
    }
  }

  private static BroadcastReceiver createReceiver() {
    return new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        System.out.println(intent.getStringExtra("type"));
        if (intent.getStringExtra("type").equals("SendAuthResp")) {
          result.success(intent.getStringExtra("code"));
        }
        else if (intent.getStringExtra("type").equals("PayResp")) {
          result.success(intent.getStringExtra("code"));
        }
        else if (intent.getStringExtra("type").equals("ShareResp")) {
          System.out.println(intent.getStringExtra("code"));
          result.success(intent.getStringExtra("code"));
        }
      }
    };
  }

  // for share miniprogram
  public static Bitmap convertBitmapTo5x4(Bitmap bitmap,int w,int h) {
    try {
      Bitmap output = Bitmap.createBitmap(w,
              h, Config.ARGB_8888);
      Canvas canvas = new Canvas(output);

      Rect src = new Rect(0 , 0, (w * bitmap.getHeight() / h) ,
              bitmap.getHeight());
      Rect dst = new Rect(0, 0, w, h);

      if (w * bitmap.getHeight() >= h * bitmap.getWidth())
      {
        src = new Rect(0, (bitmap.getHeight() - bitmap.getWidth() * h / w) / 2,
                bitmap.getWidth(), (bitmap.getHeight() + bitmap.getWidth() * h / w) / 2);

//				dst = new Rect(0, 0, bitmap.getWidth(), w * bitmap.getHeight() / h);
      } else
      {
        src = new Rect((bitmap.getWidth() - bitmap.getHeight() * w / h) / 2, 0,
                (bitmap.getWidth() + bitmap.getHeight() * w / h) / 2, bitmap.getHeight());

//                dst = new Rect(0, 0, h * bitmap.getWidth() / w, bitmap.getHeight());
      }


      final int color = 0xff424242;
      final Paint paint = new Paint();
//			final Rect rect = new Rect(0, 0, bitmap.getWidth(),
//					bitmap.getHeight());
      final RectF rectF = new RectF(dst);
//			final float roundPx = Math.min(w ,h)/ 10;
      final float roundPx = 4 * 0;

      paint.setAntiAlias(true);
//            canvas.drawARGB(0, 0, 0, 0);
//            paint.setColor(color);

//            canvas.drawRoundRect(rectF, roundPx, roundPx, paint);


//			RectF rectBottom = new RectF(0,rectF.top-20,rectF.right,rectF.bottom);
//			paint.setXfermode(new PorterDuffXfermode(Mode.SRC_OVER));
//			canvas.drawRect(rectBottom,paint);

      Log.v(TAG, "roundPx:" + roundPx);
//            paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));

      canvas.drawBitmap(bitmap, src, dst, paint);
//			canvas.drawBitmap(bitmap, rect, rect, paint);
      Log.v(TAG, "convertToRoundRect success");
//            bitmap.recycle();
      SoftReference<Bitmap> d = new SoftReference<Bitmap>(output);
      return d.get();
//            return output;
    } catch (Exception e)
    {
      Log.v(TAG, "convertToRoundRect fail :" + e.toString());
      e.printStackTrace();
    } catch (OutOfMemoryError e)
    {
      Log.v(TAG, "convertToRoundRect fail :" + e.toString());
      e.printStackTrace();
    }
    return bitmap;
  }

}
