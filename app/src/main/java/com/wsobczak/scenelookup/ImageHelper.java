package com.wsobczak.scenelookup;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.widget.Toast;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageHelper {

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;

        if (width > reqWidth || height > reqHeight) {
            final int halfWidth = width / 2;
            final int halfHeight = height / 2;

            while ((halfWidth / inSampleSize) >= reqWidth && (halfHeight / inSampleSize) >= reqHeight) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromPath(String pathName, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();

        int orientation = -1;

        try {
            ExifInterface ei = new ExifInterface(pathName);
            orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        } catch (IOException e) {
            e.printStackTrace();
        }

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(pathName, options);
        Bitmap rotatedBitmap = null;

        switch (orientation) {

            case ExifInterface.ORIENTATION_ROTATE_90:
                rotatedBitmap = rotateImage(bitmap, 90);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                rotatedBitmap = rotateImage(bitmap, 180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                rotatedBitmap = rotateImage(bitmap, 270);
                break;

            case ExifInterface.ORIENTATION_NORMAL:
            default:
                rotatedBitmap = bitmap;
        }
        return rotatedBitmap;
    }

    public static Bitmap rotateImage(Bitmap source, int angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    public static Bitmap bitmapFromPath(String pathName, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(pathName, options);
    }

    public static float[] getLatLong(String filePath) {
        float[] latLong = new float[2];
        try {
            final ExifInterface exifInterface = new ExifInterface(filePath);
            if (!exifInterface.getLatLong(latLong)) {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            latLong = null;
        }
        return latLong;
    }

    public static float getDirection(String filePath) {
        float direction = -1;
        try {
            final ExifInterface exifInterface = new ExifInterface(filePath);

            if (exifInterface.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF) != null && exifInterface.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF).equals("T")) {
                direction = (float) exifInterface.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, -1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return direction;
    }

    public static float[] getYawPitchRoll(String filePath) {
        float[] yawPitchRoll = new float[3];
        yawPitchRoll[0] = 720;
        yawPitchRoll[1] = 720;
        yawPitchRoll[2] = 720;
        String temp="";
        String pitch = null, roll = null;
        try {
            final ExifInterface exifInterface = new ExifInterface(filePath);
            temp = exifInterface.getAttribute(ExifInterface.TAG_USER_COMMENT);
            if (exifInterface.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF) != null && exifInterface.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF).equals("T")) {
                yawPitchRoll[0] = (float) exifInterface.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, -1);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!temp.isEmpty()) {
            pitch = extractDataFromTags(temp, "pitch");
            roll = extractDataFromTags(temp, "roll");
        }
        if (pitch != null) {
            yawPitchRoll[1] = Float.valueOf(pitch);
        }
        if (roll != null) {
            yawPitchRoll[2] = Float.valueOf(roll);
        }
        return yawPitchRoll;
    }

    public static String extractDataFromTags(String input, String tag) {
        final Pattern pattern = Pattern.compile("<" + tag + ">(.+?)</" + tag + ">", Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(input);
        matcher.find();
        try {
            return matcher.group(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String createTaggedData(String data, String tag) {
        return "<" + tag + ">" + data + "</" + tag + ">";
    }
}
