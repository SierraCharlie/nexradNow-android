package com.nexradnow.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.nexradnow.android.model.AppMessage;
import com.nexradnow.android.model.NexradUpdate;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Helpers to read/write data in files
 * Created by martin.hobson on 7/5/16.
 */
public class NexradNowFileUtils {

    public static File writeObjectToCacheFile(Context ctx, String prefix, String extension, Serializable object)
    throws IOException {
        File outputDir = ctx.getCacheDir(); // context being the Activity pointer
        File outputFile = File.createTempFile(prefix, extension, outputDir);
        OutputStream fileStream = new FileOutputStream(outputFile);
        ObjectOutputStream oos = new ObjectOutputStream(fileStream);
        oos.writeObject(object);
        oos.close();
        fileStream.close();
        return outputFile;
    }

    public static Serializable readObjectFromFile(File file) throws IOException, ClassNotFoundException {
        InputStream inStream = new FileInputStream(file);
        ObjectInputStream objectInputStream = new ObjectInputStream(inStream);
        Serializable readObject = (Serializable)objectInputStream.readObject();
        objectInputStream.close();
        inStream.close();
        return readObject;
    }

    public static File writeBitmapToCacheFile(Context ctx, String prefix, String extension, Bitmap bitmap)
            throws IOException {
        File outputDir = ctx.getCacheDir(); // context being the Activity pointer
        File outputFile = File.createTempFile(prefix, extension, outputDir);
        OutputStream fileStream = new FileOutputStream(outputFile);
        bitmap.compress(Bitmap.CompressFormat.PNG,100,fileStream);
        fileStream.close();
        return outputFile;
    }

    public static void clearCacheFiles(Context ctx, String prefix, String extension) {
        File cacheDir = ctx.getCacheDir();
        for (File eachFile : cacheDir.listFiles()) {
            if (eachFile.getName().startsWith(prefix) && (eachFile.getName().endsWith("."+extension))) {
                FileUtils.deleteQuietly(eachFile);
            }
        }

    }

    public static void clearCacheDir(Context ctx) {
        File cacheDir = ctx.getCacheDir();
        for (File eachFile : cacheDir.listFiles()) {
            FileUtils.deleteQuietly(eachFile);
        }
    }

    public static Bitmap readBitmapFromFile(File file) throws IOException {
        InputStream inStream = new FileInputStream(file);
        Bitmap bitmap = BitmapFactory.decodeStream(inStream);
        inStream.close();
        return bitmap;
    }

}
