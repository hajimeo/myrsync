package com.linminitools.myrsync;


import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.widget.Toast;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.linminitools.myrsync.MainActivity.debug_log;
import static java.util.Calendar.getInstance;

public class rsyncJobScheduler extends JobService {

    @SuppressLint("ApplySharedPref")
    @Override
    public boolean onStartJob(JobParameters params) {

        SharedPreferences sched_prefs = getBaseContext().getSharedPreferences("schedulers", MODE_PRIVATE);
        SharedPreferences config_prefs = getBaseContext().getSharedPreferences("configs", MODE_PRIVATE);

        int jobid= params.getExtras().getInt("jobid");
        int id_of_config_to_run =params.getExtras().getInt("config_id");
        int sched_id=params.getExtras().getInt("sched_id");
        long next_time=params.getExtras().getLong("next_time");


        try {
            FileWriter debug_writer = new FileWriter(debug_log, true);
            Locale current_locale = getBaseContext().getResources().getConfiguration().locale;
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd/MM HH:mm", current_locale);
            CharSequence message= "\n\n\n[ "+formatter.format(Calendar.getInstance().getTime())+" ] "+"onStartJob { "+"\nJOBID = "+String.valueOf(jobid) +
                    "\nSCHEDULER ID = "+String.valueOf(sched_id)+
                    "\nNEXT TIME (WEEK) = "+formatter.format(next_time)+ " }";
            debug_writer.append(message);
            debug_writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }



        RS_Configuration config_to_run = null;

        Map<String,?> config_keys = config_prefs.getAll();
        for(Map.Entry<String,?> entry : config_keys.entrySet()){
            if (entry.getKey().contains("rs_id_")) {
                String id=String.valueOf(entry.getValue());

                if (Integer.parseInt(id)==id_of_config_to_run) {
                    RS_Configuration config = new RS_Configuration((Integer) entry.getValue());
                    config.rs_user = config_prefs.getString("rs_user_" + id, "");
                    config.rs_ip = config_prefs.getString("rs_ip_" + id, "");
                    config.rs_port = config_prefs.getString("rs_port_" + id, "");
                    config.rs_options = config_prefs.getString("rs_options_" + id, "");
                    config.rs_module = config_prefs.getString("rs_module_" + id, "");
                    config.local_path = config_prefs.getString("local_path_" + id, "");
                    config.name = config_prefs.getString("rs_name_" + id, "");
                    config.addedOn = config_prefs.getLong("rs_addedon_" + id, 0);
                    config_to_run=config;
                    break;
                }

            }
        }



        try{
            Objects.requireNonNull(config_to_run).executeConfig(getBaseContext(),sched_id);

            sched_prefs.edit().putLong("last_run_"+String.valueOf(sched_id), Calendar.getInstance().getTimeInMillis()).apply();
            reschedule(config_to_run.id,jobid,sched_id,next_time);
        }
        catch (NullPointerException e){
            Toast t= Toast.makeText(getBaseContext(),"Rsync Configuration Not Found! Execution aborted!",Toast.LENGTH_SHORT);
            t.show();
        }

        jobFinished(params,false);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void reschedule(int config_id, int jobid, int sched_id, long exact_time){

        ComponentName serviceComponent = new ComponentName(getBaseContext(), rsyncJobScheduler.class);
        long one_min_in_millis= 60000;
        long next_week_in_millis= 604800000;

        long delay_till_work_start = exact_time - getInstance().getTimeInMillis();
        long next_time= exact_time+next_week_in_millis;

        PersistableBundle perbun=new PersistableBundle();
        perbun.putInt("config_id",config_id);
        perbun.putLong("next_time",next_time);
        perbun.putInt("jobid",jobid);
        perbun.putInt("sched_id",sched_id);

        long seconds = delay_till_work_start / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        String remaining_time=days + "days  " + hours % 24 + "h  " + minutes % 60 + "m  " + seconds % 60 +"s";


        try {
            FileWriter debug_writer = new FileWriter(debug_log,true);
            Locale current_locale = getBaseContext().getResources().getConfiguration().locale;
            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd/MM HH:mm", current_locale);
            CharSequence message= "\n\n\n[ "+formatter.format(Calendar.getInstance().getTime())+" ] "+"reschedule { "+"\nJOBID = "+String.valueOf(jobid) +
                    "\nSCHEDULER ID = "+String.valueOf(sched_id)+
                    "\nNEXT TIME (WEEK) = "+formatter.format(next_time)+
                    "\nDELAY TILL WORK START = "+ remaining_time +" }";
            debug_writer.append(message);
            debug_writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


        JobInfo.Builder builder= new JobInfo.Builder(jobid, serviceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setMinimumLatency(delay_till_work_start)
                .setOverrideDeadline(delay_till_work_start + one_min_in_millis)
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .setExtras(perbun)
                .setPersisted(true);
        JobInfo rsyncJob= builder.build();

        JobScheduler jobScheduler = (JobScheduler)getBaseContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);

        Objects.requireNonNull(jobScheduler).schedule(rsyncJob);

        for (JobInfo ji : Objects.requireNonNull(jobScheduler).getAllPendingJobs()) {
            if (String.valueOf(sched_id).equals(String.valueOf(ji.getId()).substring(0, 1))) {

                long seconds2 = ji.getMinLatencyMillis() / 1000;
                long minutes2 = seconds2 / 60;
                long hours2 = minutes2 / 60;
                long days2 = hours2 / 24;
                String remaining_time2=days2 + "days  " + hours2 % 24 + "h  " + minutes2 % 60 + "m  " + seconds2 % 60 +"s";



                try {
                    FileWriter debug_writer = new FileWriter(debug_log,true);
                    Locale current_locale = getBaseContext().getResources().getConfiguration().locale;
                    SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd/MM HH:mm", current_locale);
                    CharSequence message= "\n\n[ "+formatter.format(Calendar.getInstance().getTime())+" ] "+"[after]reschedule { "+"JOBID = "+String.valueOf(ji.getId()) +
                            " | SCHEDULER ID = "+String.valueOf(sched_id)+
                            " | DELAY TILL WORK START = "+ remaining_time2 +" }";
                    debug_writer.append(message);
                    debug_writer.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }


    }


}
