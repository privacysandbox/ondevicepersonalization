/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.odpclient;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.Trace;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends Activity {
    private static final String TAG = "OdpClient";
    private static final String SERVICE_PACKAGE = "com.example.odpsamplenetwork";
    private static final String SERVICE_CLASS = "com.example.odpsamplenetwork.SampleService";
    private static final String ODP_APEX = "com.google.android.ondevicepersonalization";
    private static final String ADSERVICES_APEX = "com.google.android.adservices";
    private static final int SURFACE_VIEW_INDEX = 0;
    private static final int MESSAGE_BOX_INDEX = 1;
    private EditText mTextBox;
    private Button mGetAdButton;
    private EditText mScheduleTrainingTextBox;
    private EditText mScheduleIntervalTextBox;
    private Button mScheduleTrainingButton;
    private Button mCancelTrainingButton;
    private EditText mReportConversionTextBox;
    private Button mReportConversionButton;
    private SurfaceView mRenderedView;
    private TextView mMessageBox;
    private ViewSwitcher mViewSwitcher;
    private Context mContext;
    private static Executor sCallbackExecutor = Executors.newSingleThreadExecutor();

    private static final ListeningExecutorService sLightweightExecutor =
            MoreExecutors.listeningDecorator(
                    Executors.newSingleThreadExecutor(
                            createThreadFactory(
                                    "Lite Thread",
                                    Process.THREAD_PRIORITY_DEFAULT,
                                    Optional.of(getAsyncThreadPolicy()))));

    private static ThreadFactory createThreadFactory(
            final String name, final int priority, final Optional<StrictMode.ThreadPolicy> policy) {
        return new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat(name + " #%d")
                .setThreadFactory(
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(final Runnable runnable) {
                                return new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (policy.isPresent()) {
                                            StrictMode.setThreadPolicy(policy.get());
                                        }
                                        // Process class operates on the current thread.
                                        Process.setThreadPriority(priority);
                                        runnable.run();
                                    }
                                });
                            }
                        })
                .build();
    }

    private static StrictMode.ThreadPolicy getAsyncThreadPolicy() {
        return new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build();
    }

    class SurfaceCallback implements SurfaceHolder.Callback {
        @Override public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated");
        }
        @Override public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed");
        }
        @Override public void surfaceChanged(
                SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = getApplicationContext();
        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setVisibility(View.INVISIBLE);
        mRenderedView.getHolder().addCallback(new SurfaceCallback());
        mGetAdButton = findViewById(R.id.get_ad_button);
        mScheduleTrainingButton = findViewById(R.id.schedule_training_button);
        mCancelTrainingButton = findViewById(R.id.cancel_training_button);
        mReportConversionButton = findViewById(R.id.report_conversion_button);
        mTextBox = findViewById(R.id.text_box);
        mScheduleTrainingTextBox = findViewById(R.id.schedule_training_text_box);
        mScheduleIntervalTextBox = findViewById(R.id.schedule_interval_text_box);
        mReportConversionTextBox = findViewById(R.id.report_conversion_text_box);
        mMessageBox = findViewById(R.id.message_box);
        mMessageBox.setMovementMethod(new ScrollingMovementMethod());
        mViewSwitcher = findViewById(R.id.view_switcher);
        registerGetAdButton();
        registerScheduleTrainingButton();
        registerReportConversionButton();
        registerCancelTrainingButton();

        Object unusedFuture = Futures.submit(
                () -> printDebuggingInfo(),
                sCallbackExecutor);
    }

    private void registerGetAdButton() {
        mGetAdButton.setOnClickListener(
                v -> {
                    var unused = sLightweightExecutor.submit(() -> makeRequest());
                });
    }

    private void registerReportConversionButton() {
        mReportConversionButton.setOnClickListener(
                v -> {
                    var unused = sLightweightExecutor.submit(() -> reportConversion());
                });
    }

    private OnDevicePersonalizationManager getOdpManager() throws NoClassDefFoundError {
        return mContext.getSystemService(OnDevicePersonalizationManager.class);
    }

    private void makeRequest() {
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.get_ad)
                    + " with " + mTextBox.getHint().toString() + ": "
                    + mTextBox.getText().toString());
            AtomicReference<ExecuteResult> executeResult = new AtomicReference<>();
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("keyword", mTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:makeRequest:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            SERVICE_PACKAGE,
                            SERVICE_CLASS),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection("OdpClient:makeRequest:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            if (result != null) {
                                executeResult.set(result);
                            } else {
                                Log.e(TAG, "No results!");
                            }
                            clearText();
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection("OdpClient:makeRequest:odpManager.execute", 0);
                            showError("OdpClient:makeRequest:odpManager.execute", e);
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "makeRequest:odpManager.execute wait success");

            if (executeResult.get() == null
                    || executeResult.get().getSurfacePackageToken() == null) {
                Log.i(TAG, "No surfacePackageToken returned, skipping render.");
                return;
            }

            Trace.beginAsyncSection("OdpClient:makeRequest:odpManager.requestSurfacePackage", 0);
            odpManager.requestSurfacePackage(
                    executeResult.get().getSurfacePackageToken(),
                    mRenderedView.getHostToken(),
                    getDisplay().getDisplayId(),
                    mRenderedView.getWidth(),
                    mRenderedView.getHeight(),
                    sCallbackExecutor,
                    new OutcomeReceiver<SurfacePackage, Exception>() {
                        @Override
                        public void onResult(SurfacePackage surfacePackage) {
                            Trace.endAsyncSection(
                                    "OdpClient:makeRequest:odpManager.requestSurfacePackage", 0);
                            Log.i(TAG,
                                    "requestSurfacePackage() success: "
                                    + surfacePackage.toString());
                            clearText();
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (surfacePackage != null) {
                                    mRenderedView.setChildSurfacePackage(
                                            surfacePackage);
                                }
                                mRenderedView.setZOrderOnTop(true);
                                mRenderedView.setVisibility(View.VISIBLE);
                                mViewSwitcher.setDisplayedChild(SURFACE_VIEW_INDEX);
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:makeRequest:odpManager.requestSurfacePackage", 0);
                            showError(
                                    "OdpClient:makeRequest:odpManager.requestSurfacePackage", e);
                        }
                    });
        } catch (Throwable e) {
            showError("makeRequest", e);
        }
    }

    private void registerScheduleTrainingButton() {
        mScheduleTrainingButton.setOnClickListener(
                v -> {
                    var unused = sLightweightExecutor.submit(() -> scheduleTraining());
                });
    }

    private void scheduleTraining() {
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(
                    TAG,
                    "Starting execute() "
                            + getResources().getString(R.string.schedule_training)
                            + " with "
                            + mScheduleTrainingTextBox.getHint().toString()
                            + ": "
                            + mScheduleTrainingTextBox.getText().toString());
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("schedule_training", mScheduleTrainingTextBox.getText().toString());
            if (mScheduleIntervalTextBox.getText() != null
                    && mScheduleIntervalTextBox.getText().toString() != null
                    && !mScheduleIntervalTextBox.getText().toString().isBlank()) {
                appParams.putLong(
                        "schedule_interval",
                        Long.parseUnsignedLong(mScheduleIntervalTextBox.getText().toString()));
            }

            Trace.beginAsyncSection("OdpClient:scheduleTraining:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            SERVICE_PACKAGE,
                            SERVICE_CLASS),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection(
                                    "OdpClient:scheduleTraining:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            clearText();
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:scheduleTraining:odpManager.execute", 0);
                            showError("OdpClient:scheduleTraining:odpManager.execute", e);
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "scheduleTraining:odpManager.execute wait success");
        } catch (Throwable e) {
            showError("scheduleTraining", e);
        }
    }

    private void registerCancelTrainingButton() {
        mCancelTrainingButton.setOnClickListener(
                v -> {
                    var unused = sLightweightExecutor.submit(() -> cancelTraining());
                });
    }

    private void cancelTraining() {
        Log.d(TAG, "Odp Client Cancel Training called!");
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.cancel_training)
                    + " with " + mScheduleTrainingTextBox.getHint().toString() + ": "
                    + mScheduleTrainingTextBox.getText().toString());
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("cancel_training", mScheduleTrainingTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:cancelTraining:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            SERVICE_PACKAGE,
                            SERVICE_CLASS),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection(
                                    "OdpClient:cancelTraining:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            clearText();
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:cancelTraining:odpManager.execute", 0);
                            showError("OdpClient:cancelTraining:odpManager.execute", e);
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "cancelTraining:odpManager.execute wait success");
        } catch (Throwable e) {
            showError("cancelTraining", e);
        }
    }

    private void reportConversion() {
        try {
            var odpManager = getOdpManager();
            CountDownLatch latch = new CountDownLatch(1);
            Log.i(TAG, "Starting execute() " + getResources().getString(R.string.report_conversion)
                    + " with " + mReportConversionTextBox.getHint().toString() + ": "
                    + mReportConversionTextBox.getText().toString());
            PersistableBundle appParams = new PersistableBundle();
            appParams.putString("conversion_ad_id", mReportConversionTextBox.getText().toString());

            Trace.beginAsyncSection("OdpClient:reportConversion:odpManager.execute", 0);
            odpManager.execute(
                    ComponentName.createRelative(
                            SERVICE_PACKAGE,
                            SERVICE_CLASS),
                    appParams,
                    sCallbackExecutor,
                    new OutcomeReceiver<ExecuteResult, Exception>() {
                        @Override
                        public void onResult(ExecuteResult result) {
                            Trace.endAsyncSection(
                                    "OdpClient:reportConversion:odpManager.execute", 0);
                            Log.i(TAG, "execute() success: " + result);
                            clearText();
                            latch.countDown();
                        }

                        @Override
                        public void onError(Exception e) {
                            Trace.endAsyncSection(
                                    "OdpClient:reportConversion:odpManager.execute", 0);
                            showError("OdpClient:reportConversion:odpManager.execute", e);
                            latch.countDown();
                        }
                    });
            latch.await();
            Log.d(TAG, "reportConversion:odpManager.execute wait success");
        } catch (Throwable e) {
            showError("reportConversion", e);
        }
    }

    private void showError(String message, Throwable e) {
        Log.i(TAG, "Error: " + message, e);
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        pw.println("Error: " + message);
        e.printStackTrace(pw);
        pw.flush();
        showText(out.toString());
    }

    private void showText(String s) {
        runOnUiThread(() -> {
            mMessageBox.setText(s);
            mViewSwitcher.setDisplayedChild(MESSAGE_BOX_INDEX);
        });
    }

    private void clearText() {
        runOnUiThread(() -> mMessageBox.setText(""));
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d(TAG, "onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    private void printDebuggingInfo() {
        printPackageVersion(getPackageName());
        printPackageVersion(SERVICE_PACKAGE);
        printApexVersion(ODP_APEX);
        printApexVersion(ADSERVICES_APEX);
    }

    private void printPackageVersion(String packageName) {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(packageName, 0);
            String versionName = packageInfo.versionName;
            Log.i(TAG, "packageName: " + packageName + ", versionName: " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            showError("can't find package name " + packageName, e);
        }
    }

    private void printApexVersion(String apexName) {
        try {
            PackageInfo apexInfo =
                    getPackageManager().getPackageInfo(apexName, PackageManager.MATCH_APEX);
            if (apexInfo != null && apexInfo.isApex) {
                Long apexVersionCode = apexInfo.getLongVersionCode();
                Log.i(TAG, "apexName: " + apexName + ", longVersionCode: " + apexVersionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            showError("apex " + apexName + " not found", e);
        }
    }

}
