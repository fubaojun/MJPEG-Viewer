
package com.rtrk.mjpeg;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class MjpegView extends ImageView {

    /**
     * MjpegView state enumerations
     */
    public enum State {
        DISCONNECTED, CONNECTING, BUFFERING, PLAYING
    };

    /**
     * MjpegView error enumerations
     */
    public enum Error {
        SERVER_UNREACHABLE, CONNECTION_REJECTED, CONNECTION_LOST
    };

    /**
     * Listener interface necessary for receiving view events.
     */
    public interface MJPEGViewListener {
        public abstract void onStateChanged(MjpegView view, State state);

        public abstract void onFrameRendered(MjpegView view);

        public abstract void onPlaybackError(MjpegView view, Error error);
    };

    /**
     * Tag used for logging.
     */
    public static final String TAG = MjpegView.class.getSimpleName();

    /**
     * Drawables for cross-fading two images.
     */
    private Drawable[] mDrawableLayers = new Drawable[2];

    /**
     * List of listeners that receives view updates.
     */
    private ArrayList<MJPEGViewListener> mListeners;

    /**
     * Thread used for receiving Motion JPEG stream.
     */
    private Thread mThread;

    /**
     * InputStream used for fetching JPEG images.
     */
    private MjpegInputStream mMjpegStream;

    private InputStream mInputStream;

    /**
     * Flag that indicates is MJPEG stream running.
     */
    private boolean mRunning;

    /**
     * MJPEG stream state.
     */
    private State mState;

    /**
     * Semaphore used for signaling when view has been refreshed.
     */
    private Semaphore mPaintSema;

    /**
     * Flag that indicates is image cross-fading enabled DEFAULT: false;
     */
    private boolean mIsCrossFadeEnabled;

    /**
     * Down-sample rate for bitmap resizing. DEFAULT:3
     */
    private int mBitmapDownsampleRate;

    /**
     * Default constructor needed for custom view XML inflating.
     * 
     * @param context
     * @param attrs
     * @param defStyle
     */
    public MjpegView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        constructorInitializer();
    }

    /**
     * Default constructor needed for custom view XML inflating.
     * 
     * @param context
     * @param attrs
     */
    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        constructorInitializer();
    }

    public MjpegView(Context context) {
        super(context);
        constructorInitializer();
    }

    /**
     * This method initializes class fields.
     */
    private void constructorInitializer() {
        mRunning = false;
        mPaintSema = new Semaphore(1);
        mThread = null;
        mInputStream = null;
        mListeners = new ArrayList<MJPEGViewListener>();
        mMjpegStream = null;
        mState = State.DISCONNECTED;
        mIsCrossFadeEnabled = false;
        mBitmapDownsampleRate = 3;

        setScaleType(ScaleType.FIT_XY);
        setBackgroundColor(Color.TRANSPARENT);
    }

    /**
     * Sets cross-fade effect between changing images.
     * 
     * @param enable true- Enable cross-fade, false- Disable cross-fade
     *            (DEFAULT)
     */
    public void setCrossFadeEffect(boolean enable) {
        mIsCrossFadeEnabled = enable;
    }

    /**
     * Down-sample rate for shrinking images.
     * 
     * @param rate how much times should the bitmap be smaller then in original
     *            stream.
     */
    public void setDownsampleRate(int rate) {
        mBitmapDownsampleRate = rate;
    }

    /**
     * Register listener to receive view events.
     * 
     * @param listener listener to register.
     */
    public void registerListener(MJPEGViewListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregister a listener on view events.
     * 
     * @param listener listener to unregister.
     */
    public void unregisterListener(MJPEGViewListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Remove all listeners on view events.
     */
    public void unregisterAllListeners() {
        mListeners.clear();
    }

    /**
     * Task that is assigned to receive Motion JPEG stream.
     */
    protected class MjpegViewTask implements Runnable {

        private TransitionDrawable transitionDrawable;

        private long timeCurrent, timePrevious;

        /**
         * Constructor
         */
        public MjpegViewTask() {
            timeCurrent = timePrevious = System.currentTimeMillis();
        }

        /**
         * Update view with new image.
         * 
         * @param newBitmap - image/s to set
         */
        private void updateView(final Bitmap newBitmap) {

            timeCurrent = System.currentTimeMillis();

            if (mIsCrossFadeEnabled) {
                if (timeCurrent - timePrevious < 0) {
                    return;
                }
                if (mDrawableLayers[0] == null && mDrawableLayers[1] == null) {
                    mDrawableLayers[0] = new BitmapDrawable(getResources(),
                            newBitmap);
                    mDrawableLayers[1] = new BitmapDrawable(getResources(),
                            newBitmap);
                } else if (newBitmap != null) {
                    mDrawableLayers[0] = mDrawableLayers[1];
                    mDrawableLayers[1] = new BitmapDrawable(getResources(),
                            newBitmap);

                    transitionDrawable = new TransitionDrawable(mDrawableLayers);
                    transitionDrawable.setCrossFadeEnabled(false);
                    transitionDrawable
                            .startTransition((int) (timeCurrent - timePrevious));
                }
            }

            /* Update view bitmap */
            ((Activity) MjpegView.this.getContext())
                    .runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            if (mIsCrossFadeEnabled) {
                                MjpegView.this
                                        .setImageDrawable(transitionDrawable);
                            } else {
                                MjpegView.this.setImageBitmap(newBitmap);
                            }
                            MjpegView.this.invalidate();
                            MjpegView.this.mPaintSema.release();

                        }
                    });
            timePrevious = timeCurrent;
        }

        /**
         * Motion JPEG main loop.
         */
        @Override
        public void run() {
            mPaintSema = new Semaphore(0);

            changeState(State.CONNECTING);

            mMjpegStream = new MjpegInputStream(mInputStream, mBitmapDownsampleRate);

            if (mInputStream == null) {
                Log.e(TAG, "Unable to get Input Stream from URL");
                playbackError(Error.SERVER_UNREACHABLE);
                return;
            }

            changeState(State.BUFFERING);

            while (mRunning) {
                // Read frame

                try {
                    updateView(mMjpegStream.readMjpegFrame());
                } catch (IOException e) {
                    if (mRunning) {
                        Log.e(TAG, "Error reading frame " + e);
                        playbackError(Error.CONNECTION_LOST);
                    }
                    return;
                }

                if (mState != State.PLAYING) {
                    changeState(State.PLAYING);
                }

                // Wait until bitmap has been painted
                try {
                    mPaintSema.acquire();
                } catch (InterruptedException e) {
                }

                frameRendered();
            }

            changeState(State.DISCONNECTED);
            Log.d(TAG, "Thread finished");
        }
    };

    /**
     * Get the state of views thread
     * 
     * @return state of view thread
     */
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * @param uri
     */
    public void play(InputStream inputStream) {
        Log.d(TAG, "Starting playback");

        if (mRunning) {
            stopPlayback();
        }

        mInputStream = inputStream;
        mRunning = true;
        mThread = new Thread(new MjpegViewTask());
        mThread.start();
    }

    /**
     * Stops Motion JPEG playback.
     */
    public void stopPlayback() {
        Log.d(TAG, "Stopping playback");

        mRunning = false;

        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
            }

            mInputStream = null;
        }

        mPaintSema.release();

        if (mThread != null) {
            try {
                mThread.join(250);
            } catch (InterruptedException e) {
                Log.d(TAG, e.toString());
            }
            mThread = null;
        }

        changeState(State.DISCONNECTED);
    }

    /**
     * Restarts the playback on error
     */
    private void restartPlaybackOnError() {
        stopPlayback();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Change state of view
     * 
     * @param state - new state
     */
    private void changeState(final State state) {
        Log.d(TAG, "State changed " + mState + " -> " + state);

        if (state != mState) {
            mState = state;
            notifyStateChange(state);
        }
    }

    /**
     * Reports that a frame has been render.
     * 
     * @param state - new state
     */
    private void frameRendered() {
        notifyFrameRender();
    }

    /**
     * Reports playback error.
     * 
     * @param state - new state
     */
    private void playbackError(final Error error) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                changeState(State.DISCONNECTED);
                notifyPlaybackError(error);
                restartPlaybackOnError();
            }
        }).start();
    }

    /**
     * Notifies the listeners that the views state has been changed.
     * 
     * @param state
     */
    private void notifyStateChange(State state) {
        for (MJPEGViewListener listener : mListeners) {
            listener.onStateChanged(MjpegView.this, state);
        }
    }

    /**
     * Notifies the listeners that an error has occurred.
     * 
     * @param error
     */
    private void notifyPlaybackError(Error error) {
        for (MJPEGViewListener listener : mListeners) {
            listener.onPlaybackError(MjpegView.this, error);
        }
    }

    /**
     * Notifies the listeners that a new frame has been rendered.
     */
    private void notifyFrameRender() {
        for (MJPEGViewListener listener : mListeners) {
            listener.onFrameRendered(MjpegView.this);
        }
    }

}
