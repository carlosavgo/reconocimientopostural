package com.example.reconocimientopostural;

import android.media.Image;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.common.PointF3D;

public class PoseAnalyzer implements ImageAnalysis.Analyzer {

    private final PoseDetector poseDetector;
    private final PoseResultCallback callback;

    // Interfaz para devolver resultados a MainActivity
    public interface PoseResultCallback {
        void handlePoseResult(String feedback);
    }

    public PoseAnalyzer(PoseDetector detector, PoseResultCallback callback) {
        this.poseDetector = detector;
        this.callback = callback;
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        @SuppressWarnings("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            poseDetector.process(image)
                    .addOnSuccessListener(this::processPoseResult)
                    .addOnFailureListener(e -> {
                        Log.e("PoseAnalyzer", "Error en detección: " + e.getMessage());
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private void processPoseResult(Pose pose) {
        String result = "Esperando gesto...";
        if (!pose.getAllPoseLandmarks().isEmpty()) {
            result = checkPoseForCommand(pose);
        }
        callback.handlePoseResult(result);
    }

    // Detectar gestos básicos (brazos arriba, mano en cadera, etc.)
    private String checkPoseForCommand(Pose pose) {
        PoseLandmark leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
        PoseLandmark rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
        PoseLandmark leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST);
        PoseLandmark rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST);

        // Gesto 1: Brazos levantados
        if (leftShoulder != null && rightShoulder != null && leftWrist != null && rightWrist != null) {
            if (leftWrist.getPosition3D().getY() < leftShoulder.getPosition3D().getY() &&
                    rightWrist.getPosition3D().getY() < rightShoulder.getPosition3D().getY())
            {
                return "ACCIÓN: Volumen Subido (Criterio d)";
            }
        }

        // Gesto 2: Mano en cadera
        PoseLandmark leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP);
        PoseLandmark leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW);

        if (leftHip != null && leftElbow != null && leftWrist != null) {
            double angle = getAngle(leftWrist.getPosition3D(),
                    leftElbow.getPosition3D(),
                    leftHip.getPosition3D());
            if (angle > 70 && angle < 110) {
                return "ACCIÓN: Pausar (Criterio e)";
            }
        }

        return "Esperando gesto...";
    }

    // Calcula el ángulo entre tres puntos (para el gesto de mano en cadera)
    private double getAngle(PointF3D firstPoint, PointF3D midPoint, PointF3D lastPoint) {
        double aX = firstPoint.getX(), aY = firstPoint.getY();
        double bX = midPoint.getX(), bY = midPoint.getY();
        double cX = lastPoint.getX(), cY = lastPoint.getY();

        double angle = Math.toDegrees(Math.atan2(cY - bY, cX - bX) -
                Math.atan2(aY - bY, aX - bX));
        angle = Math.abs(angle);
        if (angle > 180.0) angle = 360.0 - angle;
        return angle;
    }
}
