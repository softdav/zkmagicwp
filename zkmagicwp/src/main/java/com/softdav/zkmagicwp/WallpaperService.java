package com.softdav.zkmagicwp;

import static com.softdav.zkmagicwp.MainActivity.ACTION_UPDATE_UI;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.app.WallpaperManager;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MAGIC WALLPAPER CHANGER
 * <p>
 * Background service that handles images switching
 *
 * @version 1.02
 * @author Daveri Nico
 * @since 05/01/2025
 */
public class WallpaperService extends Service {
    public static final String ACTION_CHANGE_WP = "com.softdav.zkmagicwp.change_wp";
    public static final String ACTION_REQUEST_UPDATE_UI = "com.softdav.zkmagicwp.request_update_ui";
    public static final int CHANGE_LOCKSCREEN=1;
    public static final int CHANGE_WALLPAPER=2;
    public static final int CHANGE_BOTH=3;
    private static final String CHANNEL_ID = "ZkScreenRotationChannel";
    private static final String CONFIG_NAME = "ZkMagicWPConf";
    private static final String DEFAULT_FOLDER_PATH=Environment.getExternalStorageDirectory().getAbsolutePath()+"/MyMedia";
    private static final int DEFAULT_INTERVAL_SECONDS=300;

    private final List<String> images=new ArrayList<>();    // List of file names to display
    private int currentIndex=0;
    private String currentFolderPath="";
    private String folderPath="";
    private int intervalSeconds=0;
    private int changeMode=0;
    private int wpCenterMode=0;

    private Handler handler;
    private Runnable wallpaperChanger;
    private static boolean isRunning=false;
    private boolean isChanging=false;

    /**
     * Create boardcast receiver for wallpaper update request
     */
    private final BroadcastReceiver bcReceiverChangeWP=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            currentIndex=currentIndex+intent.getIntExtra("changeIndex", 0);
            changeWallpaper();
        }
    };

    /**
     * Create a broadcast receiver for ui data update requests
     */
    private final BroadcastReceiver bcReceiverRequestUpdateUI=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Send ui update signal
            Intent broadcastIntent=buildUpdateUI("updateUI");
            sendBroadcast(broadcastIntent);
        }
    };

    /**
     * Initialize the service
     */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        loadConfig();

        handler = new Handler();
        wallpaperChanger = new Runnable() {
            @Override
            public void run() {
                changeWallpaper();
                handler.postDelayed(this, intervalSeconds*1000L);
            }
        };

        handler.post(wallpaperChanger);

        // Register broadcast receiver for changing wallpaper
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_CHANGE_WP);
        registerReceiver(bcReceiverChangeWP, intentFilter, Context.RECEIVER_EXPORTED);

        // Register broadcast receiver for ui update request
        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_REQUEST_UPDATE_UI);
        registerReceiver(bcReceiverRequestUpdateUI, intentFilter, Context.RECEIVER_EXPORTED);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ZKMagic Wallpaper")
                .setContentText("Changing wallpaper periodically")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            if (intent!=null && intent.getExtras()!=null) {
                // Read configuration only if the service is not already started
                if (intent.getStringExtra("intervalSeconds") != null && !Objects.requireNonNull(intent.getStringExtra("intervalSeconds")).isEmpty())
                    intervalSeconds = Integer.parseInt(intent.getStringExtra("intervalSeconds"));
                if (intent.getStringExtra("folderImages") != null && !Objects.requireNonNull(intent.getStringExtra("folderImages")).isEmpty())
                    folderPath = intent.getStringExtra("folderImages");
                if (intent.getIntExtra("changeMode", 0) > 0)
                    changeMode = intent.getIntExtra("changeMode", 0);
                if (intent.getIntExtra("wpCenterMode", 0) > 0)
                    wpCenterMode = intent.getIntExtra("wpCenterMode", 0);
            }
            // Save new running configuration
            saveConfig();
            // Send ui update signal
            Intent broadcastIntent=buildUpdateUI("changeConf");
            sendBroadcast(broadcastIntent);
        }
        isRunning=true;
        return START_STICKY;    // The service is restarted if terminated by the system
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(wallpaperChanger);
        unregisterReceiver(bcReceiverChangeWP);
        unregisterReceiver(bcReceiverRequestUpdateUI);
        isRunning=false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Create update ui intent
     *
     * @return Update ui intent
     */
    private Intent buildUpdateUI(String lastAction) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_UPDATE_UI);
        if (!images.isEmpty()) {
            broadcastIntent.putExtra("currentImage", images.get(currentIndex - 1));
        }
        broadcastIntent.putExtra("intervalSeconds", String.valueOf(intervalSeconds));
        broadcastIntent.putExtra("changeMode", changeMode);
        broadcastIntent.putExtra("wpCenterMode", wpCenterMode);
        broadcastIntent.putExtra("folderImages", folderPath);
        broadcastIntent.putExtra("lastAction", lastAction);
        return broadcastIntent;
    }

    /**
     * Performs wallpaper change
     */
    private void changeWallpaper() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
        Configuration configuration = getResources().getConfiguration();
        if (isChanging) return;
        try {
            isChanging=true;
            // Check if folder path changed
            if (!currentFolderPath.equalsIgnoreCase(folderPath)) {
                // Reload images from new path
                currentFolderPath=folderPath;
                loadImagesFromFolder();
            }
            // Update index for next image
            currentIndex=getNextImageIndex();
            // Send ui update signal
            Intent broadcastIntent=buildUpdateUI("changeWP");
            sendBroadcast(broadcastIntent);

            if (images.isEmpty()) return;

            // Calculate vertical display size
            DisplayMetrics displayMetrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;
            if (configuration.orientation==Configuration.ORIENTATION_LANDSCAPE) {
                screenWidth = displayMetrics.heightPixels;
                screenHeight = displayMetrics.widthPixels;
            }

            //////////////////////////////
            // CHANGE LOCK SCREEN IMAGE //
            //////////////////////////////
            if (changeMode==CHANGE_LOCKSCREEN || changeMode==CHANGE_BOTH) {
                // Read image from disk
                Bitmap bitmap = BitmapFactory.decodeFile(images.get(currentIndex - 1));
                // Resize the image
                double newWidth = ((double) screenHeight / bitmap.getHeight()) * bitmap.getWidth();
                if (newWidth < screenWidth) newWidth = screenWidth;
                bitmap = resizeBitmap(bitmap, (int) newWidth, screenHeight);
                // Create a new centered image
                Bitmap finalBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(finalBitmap);
                int x = (screenWidth - bitmap.getWidth()) / 2;
                int y = 0;
                if (bitmap.getHeight() < screenHeight) {
                    y = (screenHeight - bitmap.getHeight()) / 2;
                }
                canvas.drawBitmap(bitmap, x, y, null);
                // Set new image
                wallpaperManager.setBitmap(finalBitmap, null, true, WallpaperManager.FLAG_LOCK);
            }

            ////////////////////////////
            // CHANGE WALLPAPER IMAGE //
            ////////////////////////////
            if (changeMode==CHANGE_WALLPAPER || changeMode==CHANGE_BOTH) {
                // Read image from disk
                int nextIndex=currentIndex;
                if (changeMode==CHANGE_BOTH) nextIndex=getNextImageIndex();
                Bitmap bitmap = BitmapFactory.decodeFile(images.get(nextIndex - 1));
                // Resize the image
                double newWidth = ((double) screenHeight / bitmap.getHeight()) * bitmap.getWidth();
                if (newWidth < screenWidth) newWidth = screenWidth;
                bitmap = resizeBitmap(bitmap, (int) newWidth, screenHeight);
                if (wpCenterMode>1) {
                    // Create a new centered image
                    Bitmap appBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(appBitmap);
                    int x = (screenWidth - bitmap.getWidth()) / wpCenterMode;
                    int y = 0;
                    if (bitmap.getHeight() < screenHeight) {
                        y = (screenHeight - bitmap.getHeight()) / 2;
                    }
                    canvas.drawBitmap(bitmap, x, y, null);
                    bitmap = appBitmap;
                }
                // Set new image
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
            }

        } catch (Exception e) {
            Log.e("Error", "Wallpaper change error: " + e.getMessage());
        } finally {
            isChanging=false;
        }
    }

    /**
     * Calculate next image index
     *
     * @return Next image index
     */
    private int getNextImageIndex() {
        int result = currentIndex + 1;
        if (result>images.size()){
            result=1;
        }  else if (result<=0) {
            result=1;
            if (!images.isEmpty()) result=images.size();
        }
        return result;
    }

    /**
     * Resize an image
     *
     * @param image Bitmap to resize
     * @param newWidth New width
     * @param newHeight New height
     * @return Resized bitmap
     */
    @NonNull
    private Bitmap resizeBitmap(@NonNull Bitmap image, int newWidth, int newHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // create a matrix for the manipulation
        Matrix matrix = new Matrix();
        // resize the bit map
        matrix.postScale(scaleWidth, scaleHeight);
        // recreate the new Bitmap
        return Bitmap.createBitmap(image, 0, 0, width, height, matrix, false);
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "ZkScreenRotation Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager=getSystemService(NotificationManager.class);
        if (manager!=null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    /**
     * Upload image file names
     */
    private void loadImagesFromFolder() {
        images.clear();
        try {
            File folder = new File(currentFolderPath);
            if (folder.exists()) {
                File[] files = folder.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().endsWith(".jpg")) {
                            images.add(file.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("Error", "Error reading files:" + e.getMessage());
        }
    }

    /**
     * Save current configuration
     */
    private void saveConfig() {
        SharedPreferences sharedConf = getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedConf.edit();
        editor.putString("currentFolderPath", folderPath);
        editor.putInt("intervalSeconds", intervalSeconds);
        editor.putInt("changeMode", changeMode);
        editor.putInt("wpCenterMode", wpCenterMode);
        editor.apply();
    }

    /**
     * Read configuration and if not present set defaults
     */
    private void loadConfig() {
        SharedPreferences sharedConf = getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE);
        folderPath=sharedConf.getString("currentFolderPath", DEFAULT_FOLDER_PATH);
        intervalSeconds=sharedConf.getInt("intervalSeconds", DEFAULT_INTERVAL_SECONDS);
        changeMode=sharedConf.getInt("changeMode", CHANGE_LOCKSCREEN);
        wpCenterMode=sharedConf.getInt("wpCenterMode", 2);
    }

}
