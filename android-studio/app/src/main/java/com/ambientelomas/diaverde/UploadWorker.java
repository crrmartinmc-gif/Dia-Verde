package com.ambientelomas.diaverde;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/** Solo transmite puntos ya guardados. Nunca solicita ubicación en segundo plano. */
public final class UploadWorker extends Worker {
    public UploadWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){return QueueUploader.drain(getApplicationContext())?Result.success():Result.retry();}
    private static Constraints network(){return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();}
    public static void schedule(Context c){WorkManager.getInstance(c).enqueueUniqueWork("dv-pending-upload",ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(UploadWorker.class).setConstraints(network()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,10,TimeUnit.SECONDS).build());}
    public static void ensurePeriodic(Context c){WorkManager.getInstance(c).enqueueUniquePeriodicWork("dv-upload-recovery",ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(UploadWorker.class,15,TimeUnit.MINUTES).setConstraints(network()).build());}
}
