package com.onmr.flutter.wechat;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ImageUtils {
    public static final int MAX_SIZE = 1024;
    public static String fileName;
    static DisplayMetrics displayMetrics = new DisplayMetrics();
    public static Bitmap getPhoto(Activity context, Uri uri) {
        context.getWindowManager().getDefaultDisplay()
                .getMetrics(displayMetrics);
        Bitmap bitmap = null;
        if (uri != null) {
            // 通过URI获取原始图片path
            fileName = getRealPath(context, uri);

            bitmap = decodeSampledBitmapFromResource(fileName,
                    displayMetrics.widthPixels, displayMetrics.heightPixels);
            // 是否旋转图片
            int degree = getPictureDegree(fileName);
            if (degree > 0) {
                bitmap = rotaingImage(degree, bitmap);
            }
            Bitmap bit = compressImage(bitmap, MAX_SIZE);

            if (bitmap != null) {
                bitmap.recycle();
            }
            return bit;
        }

        return null;
    }

    /*
     * uri 转 filepath
     */
    public static String getRealPath(Context context, Uri fileUrl) {
        String fileName = null;
        Uri filePathUri = fileUrl;
        if (fileUrl != null && fileUrl.getScheme() != null) {
            if (fileUrl.getScheme().toString().compareTo("content") == 0) {
                // content://开头的uri
                Cursor cursor = context.getContentResolver().query(fileUrl,
                        null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    fileName = cursor.getString(column_index); // 取出文件路径
                    cursor.close();
                }
            } else if (fileUrl.getScheme().compareTo("file") == 0) {
                // file:///开头的uri
                fileName = filePathUri.toString();
                fileName = filePathUri.toString().replace("file://", "");// 替换file://
            }
        }
        return fileName;
    }

    /*
     * 按比例缩放图片 根据路径获取图片
     */
    public static Bitmap decodeSampledBitmapFromResource(String path,
                                                         int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth,
                reqHeight);

        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(path, options);
    }

    /*
     * 计算缩放比例
     */
    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
//        reqWidth = Math.min(480, reqWidth);
////        reqWidth = Math.min(800, reqHeight);
//        // height/width of image
        int height = options.outHeight;
        int width = options.outWidth;
//
        int inSampleSize = 1;
//
//        if (height > reqHeight || width > reqWidth)
//        {
//            final int halfHeight = height;
//            final int halfWidth = width;
//
//            while ((halfWidth / inSampleSize) > reqWidth
//                    && (halfHeight / inSampleSize) > reqHeight)
//            {
//                inSampleSize *= 2;
//            }
//        }


        // 想要缩放的目标尺寸
        float hh = 1280;
        float ww = 960;
        int be = 1;//be=1表示不缩放
        if (width > height && width > ww) {//如果宽度大的话根据宽度固定大小缩放
            be = (int) (width / ww);
        } else if (width < height && height > hh) {//如果高度高的话根据宽度固定大小缩放
            be = (int) (height / hh);
        }
        if (be <= 0) be = 1;
        inSampleSize = be;//设置缩放比例

        return inSampleSize;

    }

    /**
     * 获取图片的旋转角度 path
     */
    public static int getPictureDegree(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
                default:
                    break;
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
        return degree;
    }

    /**
     * 旋转图片
     */
    public static Bitmap rotaingImage(int degree, Bitmap bitmap) {
        // 旋转图片 动作
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        // 创建新的图片
        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return newBitmap;
    }

    /*
     * 按质量缩放图片到指定大小
     */
    public static Bitmap compressImage(Bitmap bitmap, int maxSize) {
        if (bitmap == null)
            return null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);// 质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        int options = 100;
//      Log.i(test,原始大小 + baos.toByteArray().length);
        while (baos.toByteArray().length / 1024 > maxSize) { // 循环判断如果压缩后图片是否大于(maxkb)50kb,大于继续压缩
//          Log.i(test,压缩一次!);
            baos.reset();// 重置baos即清空baos
            options -= 10;// 每次都减少10
            bitmap.compress(Bitmap.CompressFormat.JPEG, options, baos);// 这里压缩options%，把压缩后的数据存放到baos中
        }
//      Log.i(test,压缩后大小 + baos.toByteArray().length);
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());// 把压缩后的数据baos存放到ByteArrayInputStream中
        return BitmapFactory.decodeStream(isBm, null, null);// 把ByteArrayInputStream数据生成图片;
    }
}
