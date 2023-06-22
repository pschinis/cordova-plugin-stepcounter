package net.texh.cordovapluginstepcounter;

/*
    Copyright 2015 Jarrod Linahan <jarrod@texh.net>
    Permission is hereby granted, free of charge, to any person obtaining
    a copy of this software and associated documentation files (the
    "Software"), to deal in the Software without restriction, including
    without limitation the rights to use, copy, modify, merge, publish,
    distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to
    the following conditions:
    The above copyright notice and this permission notice shall be
    included in all copies or substantial portions of the Software.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
    EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
    MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
    NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
    LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
    OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
    WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CordovaStepCounter extends CordovaPlugin {

    private final String TAG = "CordovaStepCounter";

    //private final String ACTION_CONFIGURE        = "configure";
    private final String ACTION_START            = "start";
    private final String ACTION_STOP             = "stop";
    private final String ACTION_GET_STEPS        = "get_step_count";
    private final String ACTION_GET_TODAY_STEPS  = "get_today_step_count";
    private final String ACTION_CAN_COUNT_STEPS  = "can_count_steps";
    private final String ACTION_GET_HISTORY      = "get_history";
    private final int PERMISSION_REQUEST_CODE = 598;
    private final int JOB_ID = 1375;


    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext)  {
        LOG.i(TAG, "execute()");

        Activity activity = this.cordova.getActivity();
        Intent stepCounterIntent = new Intent(activity, StepCounterService.class);

        if (ACTION_CAN_COUNT_STEPS.equals(action)) {
            Boolean can = deviceHasStepCounter(activity.getPackageManager());
            Log.i(TAG, "Checking if device has step counter APIS: "+ can);
            callbackContext.success( can ? 1 : 0 );
        }
        else if (ACTION_START.equals(action)) {
            if(!deviceHasStepCounter(activity.getPackageManager())){
                Log.i(TAG, "Step detector not supported");
                return true;
            }
            
            try {
                String postUrl = data.getString(0);
                StepCounterHelper.saveDataUrl(postUrl,activity.getApplicationContext());
            } catch (JSONException e) {
                Log.i(TAG, "Saving data url failed... exiting.");
                e.printStackTrace();
                return false;
            }
            CordovaPlugin plugin = this;

            cordova.getThreadPool().execute(new Runnable(){
                @Override
                public void run() {
                    int permissionResult = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACTIVITY_RECOGNITION);
                    if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                        //shouldShowRequestPermissionRationale will return false if the user has never seen the prompt before. Will also return false 
                        //if user denied and hit don't ask again but that's ok because then system will prevent prompt from showing
                        Boolean shouldShowPrompt = !ActivityCompat.shouldShowRequestPermissionRationale(activity,Manifest.permission.ACTIVITY_RECOGNITION);
                        if(shouldShowPrompt) {
                            String[] permissions = {Manifest.permission.ACTIVITY_RECOGNITION};
                            cordova.requestPermissions(plugin,PERMISSION_REQUEST_CODE,permissions);
                        }
                    } else {
                        Log.i(TAG, "Starting StepCounterService ...");
                        scheduleStepCounterJob();
                    }
                }
            });
        }
        else if (ACTION_STOP.equals(action)) {
            Log.i(TAG, "Stopping StepCounterService");

            //Stop the running step counter background service...
            activity.stopService(stepCounterIntent);
        }
        else if (ACTION_GET_STEPS.equals(action)) {
            Integer steps = StepCounterHelper.getTotalCount(activity);
            Log.i(TAG, "Fetching steps counted from stepCounterService: " + steps);
            callbackContext.success(steps);
        }
        else if (ACTION_GET_TODAY_STEPS.equals(action)) {
            SharedPreferences sharedPref = getDefaultSharedPreferencesMultiProcess(activity,"UserData");
            if(sharedPref.contains("pedometerData")){
                String pDataString = sharedPref.getString("pedometerData", "{}");

                Date currentDate = new Date();
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String currentDateString = dateFormatter.format(currentDate);

                JSONObject pData = new JSONObject();
                JSONObject dayData;
                Integer daySteps = -1;
                try{
                    pData = new JSONObject(pDataString);
                    Log.d(TAG," got json shared prefs "+pData.toString());
                }catch (JSONException err){
                    Log.d(TAG," Exception while parsing json string : "+pDataString);
                }

                if(pData.has(currentDateString)){
                    try {
                        dayData = pData.getJSONObject(currentDateString);
                        daySteps = dayData.getInt("steps");
                    }catch(JSONException err){
                        Log.e(TAG,"Exception while getting Object from JSON for "+currentDateString);
                    }
                }

                Log.i(TAG, "Getting steps for today: " + daySteps);
                callbackContext.success(daySteps);
            }else{
                Log.i(TAG, "No steps history found in stepCounterService !");
                callbackContext.success(-1);
            }
        } else if(ACTION_GET_HISTORY.equals(action)){
            SharedPreferences sharedPref = getDefaultSharedPreferencesMultiProcess(activity,"UserData");
            if(sharedPref.contains("pedometerData")){
                String pDataString = sharedPref.getString("pedometerData","{}");
                Log.i(TAG, "Getting steps history from stepCounterService: " + pDataString);
                callbackContext.success(pDataString);
            }else{
                Log.i(TAG, "No steps history found in stepCounterService !");
                callbackContext.success("{}");
            }
        }
        else {
            Log.e(TAG, "Invalid action called on class " + TAG + ", " + action);
            callbackContext.error("Invalid action called on class " + TAG + ", " + action);
        }

        return true;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if(requestCode == PERMISSION_REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            this.scheduleStepCounterJob();
        }
    }

    private static boolean deviceHasStepCounter(PackageManager pm) {
        // Check that the device supports the step counter and detector sensors
        return Build.VERSION.SDK_INT >= 19 && pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);
    }

    static SharedPreferences getDefaultSharedPreferencesMultiProcess(   @NonNull Context context,
                                                                        @NonNull String key) {
        //NOTE: We need to set MODE_MULTI_PROCESS when accessing the SharedPreferences both in the
        // StepCounter sensor and in the UI process. Because we have specified the service to run
        // in its own process in the AndroidManifest.xml.
        return context.getSharedPreferences(key, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    private void scheduleStepCounterJob() {
        Context context = this.cordova.getActivity().getApplicationContext();
        ComponentName componentName = new ComponentName(context, StepCounterService.class);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(60 * 60 * 1000)  // 15 minutes (in milliseconds)
                .setPersisted(true)           // Persist job across device reboots
                .build();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int resultCode = jobScheduler.schedule(jobInfo);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.i("MyJobService", "Job scheduled successfully!");
        } else {
            Log.i("MyJobService", "Job scheduling failed!");
        }
    }
}