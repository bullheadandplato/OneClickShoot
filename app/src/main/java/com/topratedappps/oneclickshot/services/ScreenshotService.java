package com.topratedappps.oneclickshot.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.media.ToneGenerator;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.topratedappps.oneclickshot.R;
import com.topratedappps.oneclickshot.activities.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;


public class ScreenshotService extends Service {
    private NotificationManager notificationManager;
    private final static String CHANNEL_ID = "Main";
    private static final String EXIT_APP_FILTER = "exit_app";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_INTENT = "resultIntent";
    public static final String OUTPUT_PATH =
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/ocs/screenshots/";
    private ScreenshotBinder binder = new ScreenshotBinder();

    private WindowManager windowManager;
    private ImageView captureIcon;
    private WindowManager.LayoutParams globalParams;
    private int initX;
    private int initY;
    private float initTouchX;
    private float initTouchY;
    static final int VIRT_DISPLAY_FLAGS =
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private MediaProjection projection;
    private VirtualDisplay vdisplay;
    final private HandlerThread handlerThread =
            new HandlerThread(getClass().getSimpleName(),
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private Handler handler;
    private MediaProjectionManager mgr;
    private WindowManager wmgr;
    private ImageTransmogrifier it;
    private int resultCode = 210;
    private Intent resultData;
    final private ToneGenerator beeper =
            new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);

    public ScreenshotService() {
    }

    WindowManager getWindowManager() {
        return (wmgr);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        wmgr = (WindowManager) getSystemService(WINDOW_SERVICE);

        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class ScreenshotBinder extends Binder {
        public ScreenshotService getService() {
            return ScreenshotService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //check if already running
        if (windowManager != null) {
            windowManager.removeView(captureIcon);
        }
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        showRunningNotification();
        showScreenshotButton();
        resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 1337);
        resultData = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
        registerReceiver(exitReceiver, new IntentFilter(EXIT_APP_FILTER));
        return START_NOT_STICKY;
    }

    private void showScreenshotButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        captureIcon = new ImageView(this);
        captureIcon.setOnTouchListener(captureTouchListener);

        captureIcon.setImageResource(R.mipmap.ic_launcher_round);
        windowManager.addView(captureIcon, getParams());
    }

    private View.OnTouchListener captureTouchListener = new View.OnTouchListener() {
        boolean wasActionDown = false;

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {

            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX = globalParams.x;
                    initY = globalParams.y;
                    initTouchX = motionEvent.getRawX();
                    initTouchY = motionEvent.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    wasActionDown = true;
                    globalParams.x = initX + (int) (motionEvent.getRawX() - initTouchX);
                    globalParams.y = initY + (int) (motionEvent.getRawY() - initTouchY);
                    windowManager.updateViewLayout(captureIcon, globalParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (wasActionDown) {
                        wasActionDown = false;
                        return true;
                    } else {
                        takeScreenshot();
                    }
            }
            return false;
        }
    };

    private void takeScreenshot() {
        Toast.makeText(this, "Button taped.", Toast.LENGTH_SHORT).show();
        startCapture();
    }

    Handler getHandler() {
        return (handler);
    }


    void processImage(final byte[] png) {
        new Thread() {
            @Override
            public void run() {
                Date date = new Date();
                android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", date);
                File outputDir = new File(OUTPUT_PATH);
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                String filename = "screenshot" + date + ".png";
                File output = new File(outputDir, filename);
                try {
                    FileOutputStream fos = new FileOutputStream(output);

                    fos.write(png);
                    fos.flush();
                    fos.getFD().sync();
                    fos.close();

                    MediaScannerConnection.scanFile(ScreenshotService.this,
                            new String[]{output.getAbsolutePath()},
                            new String[]{"image/png"},
                            null);
                    showCapturedNotification(output);
                } catch (Exception e) {
                    Log.e(getClass().getSimpleName(), "Exception writing out screenshot", e);
                }
            }
        }.start();

        beeper.startTone(ToneGenerator.TONE_PROP_ACK);
        stopCapture();
    }

    private void showCapturedNotification(File output) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        PendingIntent sharePendingIntent = PendingIntent.getActivity(
                this, 12, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent launchAppIntent = new Intent(this, MainActivity.class);
        launchAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent launchPendingIntent =
                PendingIntent.getActivity(this, 2,
                        launchAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_screenshot)
                .setContentTitle("Captured")
                .setContentText("Screen shot captured and saved." + output.getName())
                .setLargeIcon(BitmapFactory.decodeFile(output.getAbsolutePath()))
                .addAction(R.drawable.ic_share_black_24dp, "Share", sharePendingIntent)
                .setContentIntent(launchPendingIntent)
                .setAutoCancel(true);
        notificationManager.notify(121, builder.build());

    }

    private void stopCapture() {
        if (projection != null) {
            projection.stop();
            vdisplay.release();
            projection = null;

        }
        captureIcon.post(new Runnable() {
            @Override
            public void run() {
                captureIcon.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startCapture() {
        try {
            captureIcon.setVisibility(View.GONE);
            projection = mgr.getMediaProjection(resultCode, resultData);
            it = new ImageTransmogrifier(this);

            MediaProjection.Callback cb = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    vdisplay.release();
                }
            };
            vdisplay = projection.createVirtualDisplay("andshooter",
                    it.getWidth(), it.getHeight(),
                    getResources().getDisplayMetrics().densityDpi,
                    VIRT_DISPLAY_FLAGS, it.getSurface(), null, handler);
            projection.registerCallback(cb, handler);
            //hide our view from screenshot

        } catch (IllegalStateException ex) {
            ex.printStackTrace();
            Toast.makeText(this, "A screenshot is already being captured!", Toast.LENGTH_SHORT).show();
        }
    }

    private WindowManager.LayoutParams getParams() {
        WindowManager.LayoutParams res = new WindowManager.LayoutParams();
        res.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        res.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            res.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        res.x = 10;
        res.y = 100;
        res.width = 100;
        res.height = 100;
        res.format = PixelFormat.TRANSLUCENT;
        res.gravity = Gravity.TOP | Gravity.LEFT;
        globalParams = res;
        return res;
    }

    private void showRunningNotification() {

        Intent exitIntent = new Intent(EXIT_APP_FILTER);
        PendingIntent exitPendingIntent =
                PendingIntent.getBroadcast(this, 1, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent launchAppIntent = new Intent(this, MainActivity.class);
        launchAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent launchPendingIntent =
                PendingIntent.getActivity(this, 2, launchAppIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_screenshot)
                        .setContentTitle("One Click Shot is running")
                        .addAction(R.drawable.ic_close_black_24dp, "Exit", exitPendingIntent)
                        .setContentIntent(launchPendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //create notification channel
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "App",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        startForeground(1, notificationBuilder.build());

    }

    private BroadcastReceiver exitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (windowManager != null) {
                windowManager.removeView(captureIcon);
            }
            stopForeground(true);
        }
    };

    @Override
    public void onDestroy() {
        try {
            if (captureIcon != null && windowManager != null) {
                windowManager.removeView(captureIcon);
            }
            unregisterReceiver(exitReceiver);
        } catch (Exception ex) {
            //ignore
        }
    }
}
