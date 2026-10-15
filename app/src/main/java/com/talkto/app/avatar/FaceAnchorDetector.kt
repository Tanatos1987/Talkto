package com.talkto.app.avatar

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.talkto.core.avatar.FaceAnchors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Finds eyes, mouth and face box on the generated avatar with on-device ML Kit.
 * Stylised faces (chibi, pixel art) are sometimes not detected; then the defaults of [FaceAnchors]
 * are used, which match the portrait framing requested in every style prompt.
 */
class FaceAnchorDetector {

    /**
     * Null when ML Kit cannot start (in 1.1.94 R8 had removed what it needs): ZnaiKo still opens, and every avatar gets
     * the default anchors. This is created while the app starts, so a failure here must never be thrown.
     */
    private val detector: FaceDetector? = runCatching {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.2f)
                .build(),
        )
    }.onFailure { Log.w("ZnaiKo", "Face detection unavailable", it) }.getOrNull()

    suspend fun detect(bitmap: Bitmap): FaceAnchors = detector?.let { detect(it, bitmap) } ?: FaceAnchors()

    private suspend fun detect(detector: FaceDetector, bitmap: Bitmap): FaceAnchors = suspendCancellableCoroutine { cont ->
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null) {
                    cont.resume(FaceAnchors()); return@addOnSuccessListener
                }
                // ML Kit's LEFT_EYE is the subject's left eye, which appears on the right of the image.
                val subjectLeft = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val subjectRight = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
                val mouthL = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
                val mouthR = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
                val box = face.boundingBox
                val defaults = FaceAnchors()
                val mouthY = when {
                    mouthL != null && mouthR != null && mouthBottom != null -> ((mouthL.y + mouthR.y) / 2f * 0.6f + mouthBottom.y * 0.4f) / h
                    mouthBottom != null -> mouthBottom.y / h
                    else -> defaults.mouthY
                }
                val mouthX = if (mouthL != null && mouthR != null) (mouthL.x + mouthR.x) / 2f / w else box.exactCenterX() / w
                cont.resume(
                    FaceAnchors(
                        leftEyeX = (subjectRight?.x ?: (box.left + box.width() * 0.32f)) / w,
                        leftEyeY = (subjectRight?.y ?: (box.top + box.height() * 0.40f)) / h,
                        rightEyeX = (subjectLeft?.x ?: (box.left + box.width() * 0.68f)) / w,
                        rightEyeY = (subjectLeft?.y ?: (box.top + box.height() * 0.40f)) / h,
                        mouthX = mouthX,
                        mouthY = mouthY,
                        faceLeft = (box.left / w).coerceIn(0f, 1f),
                        faceTop = (box.top / h).coerceIn(0f, 1f),
                        faceRight = (box.right / w).coerceIn(0f, 1f),
                        faceBottom = (box.bottom / h).coerceIn(0f, 1f),
                        detected = true,
                    ),
                )
            }
            .addOnFailureListener { cont.resume(FaceAnchors()) }
    }

    fun close() = detector?.close()
}
