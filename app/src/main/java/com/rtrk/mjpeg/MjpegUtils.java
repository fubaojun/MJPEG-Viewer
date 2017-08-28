
package com.rtrk.mjpeg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MjpegUtils {
    public static InputStream getFileInputStream(String path) {
        File file = new File(path);
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static InputStream getHTTPInputStream(String url) {
        try {
            URL urlObj = null;
            urlObj = new URL(url);

            HttpURLConnection urlConnection = null;

            urlConnection = (HttpURLConnection) urlObj.openConnection();

            urlConnection.connect();

            int response = urlConnection.getResponseCode();

            if (response != 200) {
                return null;
            }

            return urlConnection.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
