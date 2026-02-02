package com.example.tonetrainer.util;

import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AssetUtils {
    private AssetUtils() {
    }

    public static void copyAssetFolder(AssetManager assetManager, String assetPath, File destination)
            throws IOException {
        String[] assets = assetManager.list(assetPath);
        if (assets == null || assets.length == 0) {
            copyAssetFile(assetManager, assetPath, destination);
            return;
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Unable to create directory: " + destination.getAbsolutePath());
        }
        for (String asset : assets) {
            String childPath = assetPath.isEmpty() ? asset : assetPath + "/" + asset;
            File childDestination = new File(destination, asset);
            copyAssetFolder(assetManager, childPath, childDestination);
        }
    }

    private static void copyAssetFile(AssetManager assetManager, String assetPath, File destination)
            throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory: " + parent.getAbsolutePath());
        }
        if (destination.exists()) {
            return;
        }
        try (InputStream inputStream = assetManager.open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(destination)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
    }
}
