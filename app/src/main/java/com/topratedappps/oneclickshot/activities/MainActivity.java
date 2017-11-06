package com.topratedappps.oneclickshot.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.WindowManager;

import com.topratedappps.oneclickshot.R;
import com.topratedappps.oneclickshot.listview.HomeScreenAdapter;
import com.topratedappps.oneclickshot.model.Screenshot;
import com.topratedappps.oneclickshot.services.ScreenshotService;

import java.io.File;
import java.util.ArrayList;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class MainActivity extends AppCompatActivity {
    private static final int RC_WRITE_STORAGE = 100;
    private static final int RC_DRAW_APPS = 200;
    private static final int REQUEST_SCREENSHOT = 59706;
    private MediaProjectionManager mgr;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recent_screenshots_list);
        mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        //check draw over other apps permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                askDrawPermission();
            } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                checkStoragePermission();
            } else {
                startScreenshotService();
            }

        } else {
            startScreenshotService();
        }

    }

    private void checkStoragePermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_WRITE_STORAGE);
        } else {
            startScreenshotService();
        }
    }

    private void askDrawPermission() {
        SweetAlertDialog alertDialog = new SweetAlertDialog(this);
        alertDialog.setConfirmText("Ok");
        alertDialog.setContentText("I need draw over other apps permission to work. " +
                "Please grant that permission on next screen.");
        alertDialog.setTitleText("Need permission");
        alertDialog.setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
            @Override
            public void onClick(SweetAlertDialog sweetAlertDialog) {
                sweetAlertDialog.dismiss();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, RC_DRAW_APPS);
            }
        });
        alertDialog.show();
    }

    private void startScreenshotService() {
        setupRecyclerView();
        startActivityForResult(mgr.createScreenCaptureIntent(),
                REQUEST_SCREENSHOT);
    }

    private void setupRecyclerView() {
        new Thread(new Runnable() {
            ArrayList<Screenshot> screenshots = new ArrayList<>();
            File screenShotDir = new File(ScreenshotService.OUTPUT_PATH);

            @Override
            public void run() {
                if (screenShotDir.exists()) {
                    File[] screens = screenShotDir.listFiles();
                    if (screens != null && screens.length > 0) {
                        for (File temp : screens) {
                            Screenshot screenshot = new Screenshot();
                            if (temp.getName().contains("screenshot") && temp.getName().contains(".png")) {
                                String date = temp.getName().replace("screenshot", "");
                                date = date.replace(".png", "");
                                screenshot.setName(date);
                                screenshot.setFilepath(temp.getAbsolutePath());
                                screenshots.add(screenshot);
                            }
                        }
                    }
                }

                recyclerView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (screenshots.size() < 1) {
                            //show no files
                            noScreenshots();
                        } else {
                            HomeScreenAdapter adapter = new HomeScreenAdapter(MainActivity.this, screenshots);
                            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                            recyclerView.setAdapter(adapter);
                        }
                    }
                });
            }
        }).start();
    }

    private void noScreenshots() {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_DRAW_APPS) {
            //that was for asking draw permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this) || checkAndroidO()) {
                    //continue to other permissions.
                    checkStoragePermission();
                }
            }
        } else if (requestCode == REQUEST_SCREENSHOT && resultCode == RESULT_OK) {
            Intent intent = new Intent(this, ScreenshotService.class)
                    .putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(ScreenshotService.EXTRA_RESULT_INTENT, data);
            startService(intent);
        }
    }

    /**
     * This is known bug. System.canDrawOverlays(context) returns false on some devices even if
     * permission is granted.
     *
     * @return true if can add alert_view to system window manager else return false.
     * @see <a href="https://issuetracker.google.com/issues/37077274#c7"></a>
     */
    private boolean checkAndroidO() {

        View view = new View(this);
        view.setVisibility(View.GONE);
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        if (windowManager != null) {
            try {
                windowManager.addView(view, layoutParams);
                windowManager.removeView(view);
                return true;
            } catch (WindowManager.BadTokenException ex) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == RC_WRITE_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScreenshotService();
        } else {
            //show a beautiful dialog why we need permissions.
            showWhyNeedPermissionDialog();
        }
    }

    private void showWhyNeedPermissionDialog() {
        SweetAlertDialog sweetAlertDialog =
                new SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE);
        sweetAlertDialog.setTitleText("Need storage permission.");
        sweetAlertDialog.setContentText("For storing screenshots on device we need storage permission." +
                "Please grant storage permission.");
        sweetAlertDialog.setConfirmText("Ok");
        sweetAlertDialog.setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
            @Override
            public void onClick(SweetAlertDialog sweetAlertDialog) {
                sweetAlertDialog.dismissWithAnimation();
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_WRITE_STORAGE);
            }
        });
        sweetAlertDialog.show();
    }
}
