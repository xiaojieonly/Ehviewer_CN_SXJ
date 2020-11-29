/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.ehviewer.ui.scene;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.SetSecurityActivity;
import com.hippo.hardware.ShakeDetector;
import com.hippo.widget.lockpattern.LockPatternUtils;
import com.hippo.widget.lockpattern.LockPatternView;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.ObjectUtils;
import com.hippo.yorozuya.ViewUtils;
import java.util.List;

public class SecurityScene extends SolidScene implements
        LockPatternView.OnPatternListener, ShakeDetector.OnShakeListener {

    private static final int MAX_RETRY_TIMES = 5;
    private static final long ERROR_TIMEOUT_MILLIS = 1200;
    private static final long SUCCESS_DELAY_MILLIS = 100;

    private static final String KEY_RETRY_TIMES = "retry_times";

    @Nullable
    private LockPatternView mPatternView;
    private ImageView mFingerprintIcon;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    @Nullable
    private FingerprintManager mFingerprintManager;

    private CancellationSignal mFingerprintCancellationSignal;

    private int mRetryTimes;

    @Override
    public boolean needShowLeftDrawer() {
        return false;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext2();
        AssertUtils.assertNotNull(context);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (null != mSensorManager) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (null != mAccelerometer) {
                mShakeDetector = new ShakeDetector();
                mShakeDetector.setOnShakeListener(this);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mFingerprintManager = context.getSystemService(FingerprintManager.class);
        }

        if (null == savedInstanceState) {
            mRetryTimes = MAX_RETRY_TIMES;
        } else {
            mRetryTimes = savedInstanceState.getInt(KEY_RETRY_TIMES);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSensorManager = null;
        mAccelerometer = null;
        mShakeDetector = null;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mShakeDetector) {
            mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        if (isFingerprintAuthAvailable()) {
            mFingerprintCancellationSignal = new CancellationSignal();
            // The line below prevents the false positive inspection from Android Studio
            // noinspection ResourceType
            mFingerprintManager.authenticate(null, mFingerprintCancellationSignal, 0,
                    new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationError(int errMsgId, CharSequence errString) {
                            fingerprintError(true);
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            fingerprintError(false);
                        }

                        @Override
                        public void onAuthenticationSucceeded(
                                FingerprintManager.AuthenticationResult result) {
                            mFingerprintIcon.setImageResource(R.drawable.fingerprint_success);
                            mFingerprintIcon.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    startSceneForCheckStep(CHECK_STEP_SECURITY, getArguments());
                                    finish();
                                }
                            }, SUCCESS_DELAY_MILLIS);

                        }
                    }, null);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (null != mShakeDetector) {
            mSensorManager.unregisterListener(mShakeDetector);
        }
        if (isFingerprintAuthAvailable() && mFingerprintCancellationSignal != null) {
            mFingerprintCancellationSignal.cancel();
            mFingerprintCancellationSignal = null;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_RETRY_TIMES, mRetryTimes);
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.scene_security, container, false);

        mPatternView = (LockPatternView) ViewUtils.$$(view, R.id.pattern_view);
        mPatternView.setOnPatternListener(this);

        mFingerprintIcon = (ImageView) ViewUtils.$$(view, R.id.fingerprint_icon);
        if (Settings.getEnableFingerprint() && isFingerprintAuthAvailable()) {
            mFingerprintIcon.setVisibility(View.VISIBLE);
            mFingerprintIcon.setImageResource(R.drawable.ic_fp_40px);
        }
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mPatternView = null;
    }

    @Override
    public void onPatternStart() {}

    @Override
    public void onPatternCleared() {}

    @Override
    public void onPatternCellAdded(List<LockPatternView.Cell> pattern) {}

    @Override
    public void onPatternDetected(List<LockPatternView.Cell> pattern) {
        MainActivity activity = getActivity2();
        if (null == activity || null == mPatternView) {
            return;
        }

        String enteredPatter = LockPatternUtils.patternToString(pattern);
        String targetPatter = Settings.getSecurity();

        if (ObjectUtils.equal(enteredPatter, targetPatter)) {
            startSceneForCheckStep(CHECK_STEP_SECURITY, getArguments());
            finish();
        } else {
            mPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
            mRetryTimes--;
            if (mRetryTimes <= 0) {
                finish();
            }
        }
    }

    @Override
    public void onShake(int count) {
        if (count == 10) {
            MainActivity activity = getActivity2();
            if (null == activity) {
                return;
            }
            Settings.putSecurity("");
            startSceneForCheckStep(CHECK_STEP_SECURITY, getArguments());
            finish();
        }
    }

    private boolean isFingerprintAuthAvailable() {
        // The line below prevents the false positive inspection from Android Studio
        // noinspection ResourceType
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.getEnableFingerprint()
                && mFingerprintManager != null
                && SetSecurityActivity.hasEnrolledFingerprints(mFingerprintManager);
    }

    private Runnable mResetFingerprintRunnable = new Runnable() {
        @Override
        public void run() {
            if (mFingerprintIcon != null)
                mFingerprintIcon.setImageResource(R.drawable.ic_fp_40px);
        }
    };

    private void fingerprintError(boolean unrecoverable) {
        // Do not decrease mRetryTimes here since Android system will handle it :)
        mFingerprintIcon.setImageResource(R.drawable.fingerprint_error);
        mFingerprintIcon.removeCallbacks(mResetFingerprintRunnable);
        if (unrecoverable) {
            mFingerprintIcon.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mFingerprintIcon.setVisibility(View.INVISIBLE);
                }
            }, ERROR_TIMEOUT_MILLIS);
        } else {
            mFingerprintIcon.postDelayed(mResetFingerprintRunnable, ERROR_TIMEOUT_MILLIS);
        }
    }
}
