/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.exerciseevaluator.PoseDetection.classification;

import static java.lang.Math.atan2;

import android.content.Context;
import android.graphics.PointF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.google.common.base.Preconditions;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accepts a stream of {@link Pose} for classification and Rep counting.
 */
public class PoseClassifierProcessor {
  private static final String TAG = "PoseClassifierProcessor";
  private static final String POSE_SAMPLES_FILE = "pose/fitness_pose_samples.csv";

  // Specify classes for which we want rep counting.
  // These are the labels in the given {@code POSE_SAMPLES_FILE}. You can set your
  // own class labels
  // for your pose samples.
  private static final String PUSHUPS_CLASS = "bridge_down";
  private static final String SQUATS_CLASS = "bridge_up";
  private boolean STARTED = false;
  // current state of the exercise
  private String currentState = "none";
  private static final String[] POSE_CLASSES = {
      PUSHUPS_CLASS, SQUATS_CLASS
  };

  private final boolean isStreamMode;

  private EMASmoothing emaSmoothing;
  private List<RepetitionCounter> repCounters;
  private PoseClassifier poseClassifier;
  private String lastRepResult;

  @WorkerThread
  public PoseClassifierProcessor(Context context, boolean isStreamMode) {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
    this.isStreamMode = isStreamMode;
    if (isStreamMode) {
      emaSmoothing = new EMASmoothing();
      repCounters = new ArrayList<>();
      lastRepResult = "";
    }
    // loadPoseSamples(context);
  }

  private void loadPoseSamples(Context context) {
    List<PoseSample> poseSamples = new ArrayList<>();
    try {
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(context.getAssets().open(POSE_SAMPLES_FILE)));
      String csvLine = reader.readLine();
      while (csvLine != null) {
        // If line is not a valid {@link PoseSample}, we'll get null and skip adding to
        // the list.
        PoseSample poseSample = PoseSample.getPoseSample(csvLine, ",");
        if (poseSample != null) {
          poseSamples.add(poseSample);
        }
        csvLine = reader.readLine();
      }
    } catch (IOException e) {
      Log.e(TAG, "Error when loading pose samples.\n" + e);
    }
    poseClassifier = new PoseClassifier(poseSamples);
    if (isStreamMode) {
      for (String className : POSE_CLASSES) {
        repCounters.add(new RepetitionCounter(className));
      }
    }
  }

  /**
   * Given a new {@link Pose} input, returns a list of formatted {@link String}s
   * with Pose
   * classification results.
   *
   * <p>
   * Currently it returns up to 2 strings as following:
   * 0: PoseClass : X reps
   * 1: PoseClass : [0.0-1.0] confidence
   */
  @WorkerThread
  public List<String> getPoseResult(Pose pose) {
    Preconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
    List<String> result = new ArrayList<>();
    ClassificationResult classification = poseClassifier.classify(pose);

    // Update {@link RepetitionCounter}s if {@code isStreamMode}.
    if (isStreamMode) {
      // Feed pose to smoothing even if no pose found.
      classification = emaSmoothing.getSmoothedResult(classification);

      // Return early without updating repCounter if no pose found.
      if (pose.getAllPoseLandmarks().isEmpty()) {
        result.add(lastRepResult);
        return result;
      }

      for (RepetitionCounter repCounter : repCounters) {
        int repsBefore = repCounter.getNumRepeats();
        int repsAfter = repCounter.addClassificationResult(classification);
        if (repsAfter > repsBefore) {
          // Play a fun beep when rep counter updates.
          ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
          tg.startTone(ToneGenerator.TONE_PROP_BEEP);
          lastRepResult = String.format(
              Locale.US, "%s : %d reps", repCounter.getClassName(), repsAfter);
          break;
        }
      }
      result.add(lastRepResult);
    }

    // Add maxConfidence class of current frame to result if pose is found.
    if (!pose.getAllPoseLandmarks().isEmpty()) {
      String maxConfidenceClass = classification.getMaxConfidenceClass();
      String maxConfidenceClassResult = String.format(
          Locale.US,
          "%s : %.2f confidence",
          maxConfidenceClass,
          classification.getClassConfidence(maxConfidenceClass)
              / poseClassifier.confidenceRange());
      result.add(maxConfidenceClassResult);
    }

    return result;
  }

  public String classifyPose(Pose poseLandmarks) {
    // Check if the pose is lying on the ground
    if (!isLyingOnGround(poseLandmarks)) {
      if (this.STARTED) {
        this.STARTED = false;
        this.currentState = "none";
      }
      return "Please lie on the ground";
    }
    if (!this.STARTED) {
      this.currentState = "bridge_down";
      this.STARTED = true;
    }
    // angle of foot, knee, hip
    double angleOfKnee = getAngle(
        poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_ANKLE),
        poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_KNEE),
        poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_HIP));
    // angle shoulder, hip, knee
    double angleOfShoulder = getAngle(
        poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
        poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_HIP),
        poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_KNEE));
    // print the angle of the pose
    System.out.println("Angle of shoulder: " + angleOfShoulder);
    System.out.println("Angle of knee: " + angleOfKnee);
    if ((angleOfShoulder > 140 && angleOfShoulder < 180) && angleOfKnee > 40) {
      return "Bridge up";
    }
    if (angleOfKnee < 80 && (angleOfShoulder > 90 && (angleOfShoulder < 140))) {
      System.out.println("Bridge down");
      if (angleOfKnee > 35) {
        return "Bridge down";
      } else {
        System.out.println("issue in down");
        return "Fold your knee";
      }
    }

    System.out.println("Unknown");
    return "Unknown";
  }

  static double getAngle(PoseLandmark firstPoint, PoseLandmark midPoint, PoseLandmark lastPoint) {
    if (firstPoint != null && midPoint != null && lastPoint != null) {
      double result = Math.toDegrees(
          atan2(lastPoint.getPosition().y - midPoint.getPosition().y,
              lastPoint.getPosition().x - midPoint.getPosition().x)
              - atan2(firstPoint.getPosition().y - midPoint.getPosition().y,
                  firstPoint.getPosition().x - midPoint.getPosition().x));
      result = Math.abs(result); // Angle should never be negative
      if (result > 180) {
        result = (360.0 - result); // Always get the acute representation of the angle
      }
      return result;
    } else
      return 0;

  }

  // lying on ground
  static boolean isLyingOnGround(Pose poseLandmarks) {
    // check is pose is available
    if (poseLandmarks == null) {
      return false;
    }
    // get coordinates of the shoulders left and right
    PoseLandmark leftShoulder = poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_SHOULDER);
    PoseLandmark rightShoulder = poseLandmarks.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER);
    // get coordinates of the nose
    PoseLandmark nose = poseLandmarks.getPoseLandmark(PoseLandmark.NOSE);
    // get coordinates of the feet left and right
    PoseLandmark leftFoot = poseLandmarks.getPoseLandmark(PoseLandmark.LEFT_ANKLE);
    PoseLandmark rightFoot = poseLandmarks.getPoseLandmark(PoseLandmark.RIGHT_ANKLE);
    // Check if the person is lying down based on the position of their head,
    // shoulders, and feet
    if (leftShoulder == null || rightShoulder == null || nose == null || leftFoot == null || rightFoot == null) {
      return false;
    }
    // print the coordinates of the nose
    return nose.getPosition().y > leftShoulder.getPosition().y &&
        nose.getPosition().y > rightShoulder.getPosition().y &&
        nose.getPosition().y > leftFoot.getPosition().y &&
        nose.getPosition().y > rightFoot.getPosition().y;
  }

}
