package com.pschinis.stepcounter;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.fitness.FitnessLocal;
import com.google.android.gms.fitness.data.LocalBucket;
import com.google.android.gms.fitness.data.LocalDataSet;
import com.google.android.gms.fitness.data.LocalDataPoint;
import com.google.android.gms.fitness.data.LocalDataType;
import com.google.android.gms.fitness.data.LocalField;
import com.google.android.gms.fitness.request.LocalDataReadRequest;
import com.google.android.gms.fitness.result.LocalDataReadResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.fitness.LocalRecordingClient;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class StepCounterPlugin extends CordovaPlugin {

    private static final String TAG = "StepCounterPlugin";
    private final int PERMISSION_REQUEST_CODE = 598;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("start")) {
            start(callbackContext);
            return true;
        } else if (action.equals("read")) {
            read(callbackContext);
            return true;
        } else if (action.equals("stop")) {
            stop(callbackContext);
            return true;
        } else if (action.equals("hasMinPlayServicesVersion")) {
            boolean result = hasMinPlayServicesVersion();
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
            return true;
        }
        return false;
    }

    private void start(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();
        CordovaPlugin plugin = this;

        if (hasMinPlayServicesVersion()) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        int permissionResult = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACTIVITY_RECOGNITION);
                        if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                            //shouldShowRequestPermissionRationale will return false if the user has never seen the prompt before. Will also return false
                            //if user denied and hit don't ask again but that's ok because then system will prevent prompt from showing
                            boolean shouldShowPrompt = !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION);
                            if (shouldShowPrompt) {
                                String[] permissions = {Manifest.permission.ACTIVITY_RECOGNITION};
                                cordova.requestPermissions(plugin, PERMISSION_REQUEST_CODE, permissions);
                            }
                        } else {
                            Log.i(TAG, "Starting StepCounterService ...");
                            subscribeToSteps();
                        }
                    }
                }
            });
        } else {
            Log.i(TAG, "Play Services need to be upgraded for step counter ..." + LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE);
        }

        callbackContext.success();
    }

    private void read(CallbackContext callbackContext) {
        Context context = cordova.getActivity().getApplicationContext();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            LocalDateTime endTime = LocalDateTime.now().atZone(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime startTime = endTime.minusWeeks(1);

            Log.i(TAG, "Getting steps from " + startTime);
            Log.i(TAG, "... to " + endTime);

            if (!hasMinPlayServicesVersion()) {
                callbackContext.error("Play Services version not high enough...");
                return;
            }

            LocalDataReadRequest readRequest = new LocalDataReadRequest.Builder()
                    .aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime.toEpochSecond(ZoneId.systemDefault().getRules().getOffset(startTime)),
                            endTime.toEpochSecond(ZoneId.systemDefault().getRules().getOffset(endTime)),
                            TimeUnit.SECONDS)
                    .build();

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    FitnessLocal.getLocalRecordingClient(context).readData(readRequest)
                    .addOnSuccessListener(new OnSuccessListener<LocalDataReadResponse>() {
                        @Override
                        public void onSuccess(LocalDataReadResponse localDataReadResponse) {
                            List<LocalBucket> bucketList = localDataReadResponse.getBuckets();
                            JSONArray resultArray = new JSONArray();

                            for(LocalBucket bucket : bucketList) {
                                LocalDataSet dataSet = bucket.getDataSet(LocalDataType.TYPE_STEP_COUNT_DELTA);
                                if(dataSet != null) {
                                    for (LocalDataPoint dp : dataSet.getDataPoints()) {
                                        JSONObject dataPointJson = new JSONObject();
                                        try {
                                            // Convert start time to YYYY-MM-DD format
                                            LocalDateTime startDate = LocalDateTime.ofEpochSecond(
                                                    dp.getStartTime(TimeUnit.SECONDS),
                                                    0,
                                                    ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now())
                                            );
                                            String formattedDate = startDate.toLocalDate().toString();
                                            dataPointJson.put("date", formattedDate);
                                            dataPointJson.put("steps", dp.getValue(LocalField.FIELD_STEPS).asInt());
                                        } catch (JSONException e) {
                                            Log.i(TAG, "Failed adding object to steps array...");
                                            e.printStackTrace();
                                        }
                                        resultArray.put(dataPointJson);
                                    }
                                }
                            }
                            
                            callbackContext.success(resultArray);

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            Log.w(TAG, "There was a problem reading data.", e);
                            callbackContext.error("Failed to read data.");
                        }
                    });
                }
            });
        }
    }

    private void stop(CallbackContext callbackContext) {
        if (!hasMinPlayServicesVersion()) {
            callbackContext.error("Play Services version not high enough...");
            return;
        }

        Context context = cordova.getActivity().getApplicationContext();
        FitnessLocal.getLocalRecordingClient(context)
                .unsubscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.i(TAG, "Successfully unsubscribed!");
                        callbackContext.success("Successfully unsubscribed!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.w(TAG, "There was a problem unsubscribing.", e);
                        callbackContext.error("Unsubscription failed.");
                    }
                });
    }

    private void subscribeToSteps() {
        Context context = cordova.getActivity().getApplicationContext();
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        FitnessLocal.getLocalRecordingClient(context)
        .subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.i(TAG, "Successfully subscribed to count steps!");
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Log.w(TAG, "There was a problem subscribing to count steps.", e);
            }
        });
    }

    private boolean hasMinPlayServicesVersion() {
        Context context = cordova.getActivity().getApplicationContext();
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context, LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE);
        return resultCode == ConnectionResult.SUCCESS;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if(requestCode == PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            subscribeToSteps();
        }
    }
}
