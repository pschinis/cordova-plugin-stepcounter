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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.strongrfastr.cordova.R;

import java.util.Locale;

public class StepCounterService extends JobService {
    private static int NOTIFICATION_ID = 777;
    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private SensorEventListener sensorEventListener;
    private final String TAG = "StepCounterService";
    private static boolean isRunning = false;

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating step counter job... ");
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if(isRunning) {
            return false;
        }
        Log.d(TAG, "StepCounterService: starting...");

        StepCounterService me = this;

        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if(sensorManager != null) {
                stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if(stepCounterSensor != null) {
                    sensorEventListener = new SensorEventListener() {
                        @Override
                        public void onSensorChanged(SensorEvent event) {
                            // Handle sensor data here
                            float steps = event.values[0];
                            Log.d(TAG, "Storing steps... " + steps);
                            StepCounterHelper.saveSteps(steps, me);
                            StepCounterHelper.sendSteps(me);

                            // Stop listening to sensor changes
                            sensorManager.unregisterListener(sensorEventListener);

                            Log.d(TAG, "StepCounterService: stopping foreground service...");
                            // Stop the foreground service
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                stopForeground(JobService.STOP_FOREGROUND_REMOVE);
                            } else {
                                stopForeground(true);
                            }
                            isRunning = false;

                            // Notify the system that the job has finished
                            jobFinished(params, false);
                        }

                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        }
                    };

                    // Start the foreground service with a notification
                    startForeground(NOTIFICATION_ID, createNotification());
                    isRunning = true;
                    
                    // Start listening for sensor changes
                    sensorManager.registerListener(sensorEventListener, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);

                    return true;
                }
            }

        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        sensorManager.unregisterListener(sensorEventListener);
        Log.d(TAG, "Step counter job interrupted... ");
        return true; // Return true to restart the job if it's stopped before completion.
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying step counter job... ");
        super.onDestroy();
    }

        /* Used to build and start foreground service. */
    private Notification createNotification()
    {
        Log.d(TAG, "StepCounterService: creating foreground notification...");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, createChannel());
        builder.setSmallIcon(getResources().getIdentifier(  "notification_icon", "drawable", getPackageName()));
        builder.setAutoCancel(false);
        builder.setOngoing(true);
        builder.setSilent(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED);
        }
        builder.setPriority(Notification.PRIORITY_MIN);
        builder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
        builder.setContentTitle("Counting steps...");

        Notification notification = builder.build();

        return notification;
    }

    private String createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if(manager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(  "step_counter", "Step counter",
                                                                    NotificationManager.IMPORTANCE_LOW);

            channel.enableLights(false);
            manager.createNotificationChannel(channel);
        }

        return "step_counter";
    }
}