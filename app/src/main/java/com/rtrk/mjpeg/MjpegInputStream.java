
package com.rtrk.mjpeg;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class MjpegInputStream extends DataInputStream {

    /**
     * * Tag used for logging.
     */
    public static final String TAG = MjpegInputStream.class.getSimpleName();

    /**
     * Stream content constants
     */
    public static final byte[] SOI_MARKER = {
            (byte) 0xFF, (byte) 0xD8
    };
    public static final byte[] EOF_MARKER = {
            (byte) 0xFF, (byte) 0xD9
    };

    private static final String CONTENT_LENGTH = "Content-Length";
    private static final int HEADER_MAX_LENGTH = 100;
    private static final int FRAME_MAX_LENGTH = 40000 + HEADER_MAX_LENGTH;

    /**
     * Frame content length.
     */
    private int mContentLength = -1;

    /**
     * Options for creating bitmap.
     */
    private BitmapFactory.Options opts;

    /**
     * Constructor
     * 
     * @param in InputStream received from HTTP client.
     * @param downSample down sample rate. Used to make bitmap creation more
     *            memory effective.
     */
    @SuppressLint("NewApi")
    public MjpegInputStream(InputStream in, int downSample) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
        opts = new BitmapFactory.Options();
        opts.inMutable = true;
        opts.inSampleSize = downSample;
    }

    /**
     * Get the end of sequence offset.
     * 
     * @param in InputStream
     * @param sequence data
     * @return sequence offset
     * @throws IOException
     */
    private int getEndOfSeqeunce(DataInputStream in, byte[] sequence)
            throws IOException {
        int seqIndex = 0;
        byte c;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            c = (byte) in.readUnsignedByte();
            if (c == sequence[seqIndex]) {
                seqIndex++;
                if (seqIndex == sequence.length) {
                    return i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    /**
     * Gets the start of sequence offset.
     * 
     * @param in InputStream
     * @param sequence data
     * @return sequence offset
     * @throws IOException
     */
    private int getStartOfSequence(DataInputStream in, byte[] sequence)
            throws IOException {
        int end = getEndOfSeqeunce(in, sequence);
        return (end < 0) ? (-1) : (end - sequence.length);
    }

    /**
     * Parse content length.
     * 
     * @param headerBytes byte array for content header
     * @return content length
     * @throws IOException
     * @throws NumberFormatException
     */
    private int parseContentLength(byte[] headerBytes) throws IOException,
            NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);

        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }

    /**
     * Reads one Motion JPEG frame from input stream,
     * 
     * @return next frame ({@link Bitmap})
     * @throws IOException
     */
    public Bitmap readMjpegFrame() throws IOException {
        // Marks the current position in this input stream.
        // A subsequent call to the reset method repositions this stream at the
        // last marked position so that subsequent reads re-read the same bytes
        mark(FRAME_MAX_LENGTH);

        // Start of JPEG image
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();

        if (headerLen > 0) {
            byte[] header = new byte[headerLen];
            readFully(header);

            try {
                mContentLength = parseContentLength(header);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing content length" + e.toString());
                return null;
            }
        }
        else {
            int eof = getStartOfSequence(this, EOF_MARKER);

            mContentLength = eof + EOF_MARKER.length;
        }

        reset();
        byte[] frameData = new byte[mContentLength];
        skipBytes(headerLen);

        // Read entire JPEG image
        readFully(frameData);

        // Decode it into a bitmap
        return BitmapFactory.decodeByteArray(frameData, 0, frameData.length,
                opts);
    }
}
