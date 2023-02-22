
package com.example.exerciseevaluator.preferance;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.exerciseevaluator.R;
import com.google.android.gms.common.images.Size;
import com.google.common.base.Preconditions;

import com.example.exerciseevaluator.utils.CameraSource;
import com.example.exerciseevaluator.utils.CameraSource.SizePair;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

/** Utility class to retrieve shared preferences. */
public class PreferenceUtils {

  private static final int POSE_DETECTOR_PERFORMANCE_MODE_FAST = 1;

  @Nullable
  public static SizePair getCameraPreviewSizePair(Context context, int cameraId) {
    Preconditions.checkArgument(
        cameraId == CameraSource.CAMERA_FACING_BACK
            || cameraId == CameraSource.CAMERA_FACING_FRONT);
    String previewSizePrefKey;
    String pictureSizePrefKey;
     pictureSizePrefKey = "rcpts";
    previewSizePrefKey = "rcpvs";

    try {
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      return new SizePair(
          Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
          Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)));
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean shouldHideDetectionInfo(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = "ih";
    return sharedPreferences.getBoolean(prefKey, false);
  }



  public static PoseDetectorOptionsBase getPoseDetectorOptionsForLivePreview(Context context) {
    int performanceMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_pose_detection_performance_mode,
            POSE_DETECTOR_PERFORMANCE_MODE_FAST);
    boolean preferGPU = preferGPUForPoseDetection(context);
    if (performanceMode == POSE_DETECTOR_PERFORMANCE_MODE_FAST) {
      PoseDetectorOptions.Builder builder =
          new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE);
//      if (preferGPU) {
        builder.setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU);
//      }
      return builder.build();
    } else {
      AccuratePoseDetectorOptions.Builder builder =
          new AccuratePoseDetectorOptions.Builder()
              .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE);
//      if (preferGPU) {
        builder.setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU);
//      }
      return builder.build();
    }
  }

  public static boolean preferGPUForPoseDetection(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_prefer_gpu);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  /**
   * Mode type preference is backed by {@link android.preference.ListPreference} which only support
   * storing its entry value as string type, so we need to retrieve as string and then convert to
   * integer.
   */
  private static int getModeTypePreferenceValue(
      Context context, @StringRes int prefKeyResId, int defaultValue) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(prefKeyResId);
    return Integer.parseInt(sharedPreferences.getString(prefKey, String.valueOf(defaultValue)));
  }

  public static boolean isCameraLiveViewportEnabled(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_camera_live_viewport);
    return sharedPreferences.getBoolean(prefKey, false);
  }


  private PreferenceUtils() {}
}
