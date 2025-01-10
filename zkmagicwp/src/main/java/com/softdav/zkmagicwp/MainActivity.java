package com.softdav.zkmagicwp;
/// Copyright 2025 Daveri Nico
///
///     Licensed under the Apache License, Version 2.0 (the "License");
///     you may not use this application except in compliance with the License.
///     You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
///     Unless required by applicable law or agreed to in writing, software
///     distributed under the License is distributed on an "AS IS" BASIS,
///     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
///     See the License for the specific language governing permissions and
///     limitations under the License.

import static com.softdav.zkmagicwp.WallpaperService.ACTION_CHANGE_WP;
import static com.softdav.zkmagicwp.WallpaperService.ACTION_REQUEST_UPDATE_UI;
import static com.softdav.zkmagicwp.WallpaperService.CHANGE_BOTH;
import static com.softdav.zkmagicwp.WallpaperService.CHANGE_LOCKSCREEN;
import static com.softdav.zkmagicwp.WallpaperService.CHANGE_WALLPAPER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Objects;

/**
 * MAGIC WALLPAPER CHANGER
 * <p>
 * Main activity for controlling and configuring the background service of wallpaper presentation
 *
 * @version 1.03
 * @author Daveri Nico
 * @since 05/01/2025
 */
public class MainActivity extends AppCompatActivity {
    public static final String ACTION_UPDATE_UI = "com.softdav.zkmagicwp.update_ui";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Requests permissions if necessary
        if (!Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }

        // Button to stop and restart the service
        Button stopServiceButton=findViewById(R.id.btnStop);
        stopServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (stopServiceButton.getText().toString().equalsIgnoreCase(getResources().getString(R.string.stop_service))) {
                    stopService();
                } else {
                    startService(true);
                }
            }
        });

        // Button to immediately move to the next image
        Button nextImageButton=findViewById(R.id.btnNext);
        nextImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(ACTION_CHANGE_WP);
                broadcastIntent.putExtra("changeIndex", 0);
                sendBroadcast(broadcastIntent);
                flowButtons(false);
            }
        });

        // Button to return to the previous image
        Button prevImageButton=findViewById(R.id.btnPrev);
        prevImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction(ACTION_CHANGE_WP);
                broadcastIntent.putExtra("changeIndex", -2);
                sendBroadcast(broadcastIntent);
                flowButtons(false);
            }
        });

        // Button to select the image folder
        Button folderButton=findViewById(R.id.btnFolder);
        folderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                // startActivityForResult(intent, REQUEST_CODE);
                selectFolder.launch(intent);
            }
        });

        startService(false);
    }

    /**
     * Stop the service and refresh interface objects
     */
    private void stopService() {
        Button stopServiceButton=findViewById(R.id.btnStop);
        Intent stopIntent = new Intent(MainActivity.this, WallpaperService.class);
        stopService(stopIntent);
        stopServiceButton.setText(R.string.start_service);
        EditText txtNrSecondi=findViewById(R.id.txtNrSecondi);
        txtNrSecondi.setEnabled(true);
        Spinner spCenterWP=findViewById(R.id.spCenterWP);
        spCenterWP.setEnabled(true);
        configButtons(true);
        flowButtons(false);
    }

    /**
     * Start the service by passing configuration from the interface fields
     */
    private void startService(boolean initConf) {
        Button stopServiceButton=findViewById(R.id.btnStop);
        EditText txtNrSecondi=findViewById(R.id.txtNrSecondi);
        TextView folderImages = findViewById(R.id.txtFolderImages);
        RadioButton chkLockScreen = findViewById(R.id.chkLockScreen);
        RadioButton chkWallPaper = findViewById(R.id.chkWallPaper);
        RadioButton chkBoth = findViewById(R.id.chkBoth);
        Spinner spCenterWP=findViewById(R.id.spCenterWP);
        Intent startIntent = new Intent(MainActivity.this, WallpaperService.class);
        if (initConf) {
            // Initialize service configuration from ui
            startIntent.putExtra("intervalSeconds", txtNrSecondi.getText().toString());
            if (!folderImages.getText().toString().isEmpty()) {
                startIntent.putExtra("folderImages", folderImages.getText().toString());
            }
            int changeMode = 0;
            if (chkLockScreen.isChecked()) {
                changeMode = CHANGE_LOCKSCREEN;
            } else if (chkWallPaper.isChecked()) {
                changeMode = CHANGE_WALLPAPER;
            } else if (chkBoth.isChecked()) {
                changeMode = CHANGE_BOTH;
            }
            startIntent.putExtra("changeMode", changeMode);
            int wpCenterMode = 1;
            if (spCenterWP.getSelectedItem().toString().equalsIgnoreCase("Half")) {
                wpCenterMode = 2;
            } else if (spCenterWP.getSelectedItem().toString().equalsIgnoreCase("Third")) {
                wpCenterMode = 3;
            } else if (spCenterWP.getSelectedItem().toString().equalsIgnoreCase("Quarter")) {
                wpCenterMode = 4;
            }
            startIntent.putExtra("wpCenterMode", wpCenterMode);
        }
        startService(startIntent);
        stopServiceButton.setText(R.string.stop_service);
        txtNrSecondi.setEnabled(false);
        spCenterWP.setEnabled(false);
        configButtons(false);
        flowButtons(true);
    }

    /**
     * Manages enabling of buttons to scroll images
     *
     * @param isEnabled Enable or disable the buttons
     */
    private void flowButtons(boolean isEnabled) {
        Button nextImageButton=findViewById(R.id.btnNext);
        nextImageButton.setEnabled(isEnabled);
        Button prevImageButton=findViewById(R.id.btnPrev);
        prevImageButton.setEnabled(isEnabled);
    }

    /**
     * Manages enabling of configuration buttons
     *
     * @param isEnabled Enable or disable the buttons
     */
    private void configButtons(boolean isEnabled) {
        Button folderButton=findViewById(R.id.btnFolder);
        folderButton.setEnabled(isEnabled);
        RadioButton chkLockScreen = findViewById(R.id.chkLockScreen);
        chkLockScreen.setEnabled(isEnabled);
        RadioButton chkWallPaper = findViewById(R.id.chkWallPaper);
        chkWallPaper.setEnabled(isEnabled);
        RadioButton chkBoth = findViewById(R.id.chkBoth);
        chkBoth.setEnabled(isEnabled);
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onResume() {
        super.onResume();
        // Create and register ui update handler
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UPDATE_UI);
        registerReceiver(bcReceiverUpdateUI, intentFilter, Context.RECEIVER_EXPORTED);
        // Requires interface update
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_REQUEST_UPDATE_UI);
        sendBroadcast(broadcastIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister ui update manager
        unregisterReceiver(bcReceiverUpdateUI);
    }

    /**
     * Create broadcast receiver for ui update
     */
    private final BroadcastReceiver bcReceiverUpdateUI=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Update the UI with the received data
            ImageView imgCurrent=findViewById(R.id.imgCurrent);
            if (intent.getStringExtra("currentImage")!=null && !Objects.requireNonNull(intent.getStringExtra("currentImage")).isEmpty()) {
                Bitmap bitmap = BitmapFactory.decodeFile(intent.getStringExtra("currentImage"));
                imgCurrent.setImageBitmap(bitmap);
            } else {
                imgCurrent.setImageBitmap(null);
            }
            String lastAction=Objects.requireNonNull(intent.getStringExtra("lastAction"));
            if (lastAction.equalsIgnoreCase("updateUI") || lastAction.equalsIgnoreCase("changeConf")) {
                if (intent.getStringExtra("intervalSeconds")!=null && !Objects.requireNonNull(intent.getStringExtra("intervalSeconds")).isEmpty()) {
                    EditText txtNrSecondi=findViewById(R.id.txtNrSecondi);
                    txtNrSecondi.setText(intent.getStringExtra("intervalSeconds"));
                }
                if (intent.getStringExtra("folderImages")!=null && !Objects.requireNonNull(intent.getStringExtra("folderImages")).isEmpty()) {
                    TextView folderImages=findViewById(R.id.txtFolderImages);
                    folderImages.setText(intent.getStringExtra("folderImages"));
                }
                int changeMode=intent.getIntExtra("changeMode", 0);
                switch (changeMode) {
                    case CHANGE_LOCKSCREEN:
                        RadioButton chkLockScreen=findViewById(R.id.chkLockScreen);
                        chkLockScreen.setChecked(true);
                        break;
                    case CHANGE_WALLPAPER:
                        RadioButton chkWallPaper=findViewById(R.id.chkWallPaper);
                        chkWallPaper.setChecked(true);
                        break;
                    case CHANGE_BOTH:
                        RadioButton chkBoth=findViewById(R.id.chkBoth);
                        chkBoth.setChecked(true);
                        break;
                }
                int wpCenterMode=intent.getIntExtra("wpCenterMode", 0);
                if (wpCenterMode>0) {
                    Spinner spCenterWP = findViewById(R.id.spCenterWP);
                    spCenterWP.setSelection(wpCenterMode - 1);
                }
            }
            if (Objects.requireNonNull(intent.getStringExtra("lastAction")).equalsIgnoreCase("changeWP")) {
                flowButtons(true);
            }
        }
    };

    /**
     * Handles callbacks for changing the image folder
     */
    private final ActivityResultLauncher<Intent> selectFolder = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data.getData()!=null && data.getData().getPath()!=null) {
                        TextView folderImages = findViewById(R.id.txtFolderImages);
                        String appPath=data.getData().getPath();
                        int n=appPath.indexOf(":");
                        if (n!=-1) {
                            appPath=Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+appPath.substring(n+1);
                        }
                        folderImages.setText(appPath);
                    }
                }
            }
    );

}
