package com.example.camerax.ui;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.camerax.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // 定数
    private static final String TAG = "CameraX";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final List<String> REQUIRED_PERMISSIONS =
            Collections.singletonList(Manifest.permission.CAMERA);

    // 変数
    private ImageCapture imageCapture = null;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // PreviewViewの取得
        previewView = findViewById(R.id.viewFinder);

        // カメラ権限の確認
        if (allPermissionsGranted()) {
            // 全て権限付与済の場合はカメラの処理を開始する
            startCamera();
        } else {
            // 権限が付与されていない場合は、付与の為のリクエストをユーザーに依頼する
            String[] str = new String[REQUIRED_PERMISSIONS.size()];
            String[] array = REQUIRED_PERMISSIONS.toArray(str);
            ActivityCompat.requestPermissions(
                    this, array, REQUEST_CODE_PERMISSIONS
            );
        }
        // 写真撮影ボタンの設定
        findViewById(R.id.image_capture_button).setOnClickListener(v -> takePhoto());

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    /**
     * 必要権限の確認用メソッド
     * @return 必要権限の許可が全てある場合はtrue、それ以外はfalse
     */
    private boolean allPermissionsGranted() {
        boolean isAllGranted = true;
        for (int i = 0; i < REQUIRED_PERMISSIONS.size(); i++) {
            int permission = ContextCompat.checkSelfPermission(getBaseContext(), REQUIRED_PERMISSIONS.get(i));
            if (permission != PackageManager.PERMISSION_GRANTED) {
                isAllGranted = false;
            }
        }
        return isAllGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 依頼した権限リクエストからの戻りか確認する
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // 権限が全て付与されたか確認する
            if (allPermissionsGranted()) {
                // 全て権限付与済の場合はカメラの処理を開始する
                startCamera();
            } else {
                // 権限が付与されていない場合は、Toastでメッセージを表示する
                Toast.makeText(
                        this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                ).show();
                finish();
            }
        }
    }

    private void startCamera() {
        // ProcessCameraProviderのインスタンスを取得する
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        // リスナーの追加
        cameraProviderFuture.addListener(() -> {
            if (previewView == null) {
                Log.w(TAG, "PreviewView is not found.");
                return;
            }
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // ImageCaptureインスタンスを生成する
                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        // ImageCaptureがまだ生成されていない場合はリターンする
        if (imageCapture == null) return;
        // タイムスタンプから一意になるMediaStoreの表示名を作成する
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        // MediaStoreへのエントリ情報を作成する
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        // MediaStoreへの出力用オブジェクトの作成
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(
            getContentResolver(),
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build();
        // takePictureを実行する
        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        // 画像の保存に成功した場合の処理
                        String uriStr = outputFileResults.getSavedUri().toString();
                        String message = "Photo capture succeeded: " + uriStr;
                        Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, message);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        // 画像の保存に失敗した場合（Errorの場合）の処理
                        String message = exception.getMessage();
                        Log.e(TAG, "Photo capture failed: " + message);
                    }
                }
        );
    }
}
