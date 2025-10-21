package com.example.reconocimientopostural;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.reconocimientopostural.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ReconocimientoPostural";
    private static final int CAMERA_REQUEST_CODE = 100;

    // 1. View Binding
    private ActivityMainBinding binding;

    // 2. Detector de Pose (ML Kit)
    private PoseDetector poseDetector;

    // 3. Hilo para análisis de la cámara
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inicializar View Binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Configurar el detector de pose (modo de vídeo)
        AccuratePoseDetectorOptions options = new AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .build();
        poseDetector = PoseDetection.getClient(options);

        // Crear un hilo para el análisis de frames
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Comprobar permisos de cámara
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE
            );
        }
    }

    // Método que comprueba si el permiso de cámara está concedido
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // Resultado de la solicitud de permisos
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permiso de cámara no concedido. Cerrando aplicación.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // Inicia la cámara y conecta el análisis de pose
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 1. Vista previa (PreviewView del layout)
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                // 2. Análisis de imágenes (frames)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // Conectar el analizador de poses
                imageAnalysis.setAnalyzer(cameraExecutor, new PoseAnalyzer(poseDetector, this::handlePoseResult));

                // 3. Selección de cámara (frontal)
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Enlazar al ciclo de vida
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Error al iniciar la cámara", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Actualiza el texto del TextView según el gesto detectado
    private void handlePoseResult(String feedback) {
        runOnUiThread(() -> {
            binding.tvFeedback.setText(feedback);

            if (feedback.contains("Volumen Subido")) {
                subirVolumen();
            } else if (feedback.contains("Pausar")) {
                bajarVolumen();
            }
        });
    }

    private void subirVolumen() {
        android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.ADJUST_RAISE,
                    android.media.AudioManager.FLAG_SHOW_UI
            );
        }
    }
    private void bajarVolumen() {
        android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.ADJUST_LOWER,
                    android.media.AudioManager.FLAG_SHOW_UI
            );
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown(); // Detiene el hilo de análisis
    }
}
