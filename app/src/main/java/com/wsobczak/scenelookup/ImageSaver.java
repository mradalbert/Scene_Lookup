package com.wsobczak.scenelookup;

import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class ImageSaver implements Runnable {
    private final File mFile;
    private final Image mImage;
    private final Location mLocation;
    private float[] mOrientation;

    public ImageSaver(File file, Image image, Location location, float[] orientation) {
        mFile = file;
        mImage = image;
        mLocation = location;
        mOrientation = orientation;
    }

    @Override
    public void run() {
        ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);

        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(mFile);
            fileOutputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mImage.close();

            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        saveGPSExif(mFile, mLocation);
    }


    public void saveGPSExif(File imagePath, Location location) {
        if (imagePath == null || location == null) return;

        //Getting UTC time required by Exif
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss");
        time.setTimeZone(TimeZone.getTimeZone("GMT"));
        SimpleDateFormat date = new SimpleDateFormat("yyyy:MM:dd.");
        date.setTimeZone(TimeZone.getTimeZone("GMT"));


        try {
            ExifInterface exif = new ExifInterface(imagePath.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, decToDMS(location.getLatitude()));
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, location.getLatitude() < 0 ? "S" : "N");
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, decToDMS(location.getLongitude()));
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, location.getLongitude() < 0 ? "W" : "E");

            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, (int) (location.getAltitude()*1000) + "/1000");
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, location.getAltitude() < 0 ? "1" : "0");

            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, date.format(location.getTime()));
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, time.format(location.getTime()));
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, location.getProvider());
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF,"T");
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, (int) (100*yawToDirection(mOrientation[0])) + "/100");
            //exif.setAttribute(ExifInterface.TAG_USER_COMMENT,"CameraElevationAngle: " + mOrientation[1] + "\n" + "CameraRoll: " + mOrientation[2]);
            String elevationRoll = ImageHelper.createTaggedData(String.valueOf(mOrientation[1]), "pitch") + "\n" + ImageHelper.createTaggedData(String.valueOf(mOrientation[2]), "roll");
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, elevationRoll);
            String i = exif.getAttribute(ExifInterface.TAG_USER_COMMENT);
            exif.saveAttributes();

            ExifInterface exiff = new ExifInterface(imagePath.getAbsolutePath());
            String t = exiff.getAttribute(ExifInterface.TAG_USER_COMMENT);
            t = exiff.getAttribute(ExifInterface.TAG_USER_COMMENT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String decToDMS(double coord) {
        coord = coord > 0 ? coord : -coord;
        String sOut = (int) coord + "/1,";
        coord = (coord % 1) * 60;
        sOut = sOut + (int) coord + "/1,";
        coord = (coord % 1) * 60000;
        sOut = sOut + (int) coord + "/1000";
        return sOut;
    }

    public static float yawToDirection(float yaw) {
        if (yaw < 0) yaw = 360 + yaw;
        return yaw;
    }
}