package research.mingming.sensorchat.soqrclient;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import research.mingming.sensorchat.R;
import research.mingming.sensorchat.activeprobing.ExtAudioRecorder;
import research.mingming.sensorchat.activeprobing.Utils;

public class MainActivity extends Activity {
    PowerManager.WakeLock wl = null;
    AudioManager mAudioManager;
    MediaPlayer mp = null;
    ExtAudioRecorder extAudioRecorder = null;
    Timer mTimerPeriodic = null;
    File myDir;
    String mRecordFileName;  // file name of recorded sound clip
    File mRecordFile = null;
    private int playtime_cnt = 0;

    private ListView mList;
    private ArrayList<String> arrayList;
    private MyCustomAdapter mAdapter;
    private TCPClient mTcpClient;

    private String currentObjectName = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mywakeuplock");
        wl.acquire();

        arrayList = new ArrayList<String>();

        final EditText editText = (EditText) findViewById(R.id.editText);
        Button send = (Button) findViewById(R.id.send_button);

        //relate the listView from java to the one created in xml
        mList = (ListView) findViewById(R.id.list);
        mAdapter = new MyCustomAdapter(this, arrayList);
        mList.setAdapter(mAdapter);

        if (isExternalStorageWritable()) {
            String root = Environment.getExternalStorageDirectory().toString();
            myDir = new File(root + "/containerprobe");
            myDir.mkdirs();
        }

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        // connect to the server
        new connectTask().execute("");

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String message = editText.getText().toString();

                currentObjectName = message;

                //add the text in the arrayList
                arrayList.add("c: " + message);

                //sends the message to the server
                if (mTcpClient != null) {
                    mTcpClient.sendMessage(message);
                }

                //refresh the list
                mAdapter.notifyDataSetChanged();
                editText.setText("");
            }
        });

    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    protected void onDestroy() {
        if (wl != null) {
            if (wl.isHeld())
                wl.release();
            wl = null;
        }
        super.onDestroy();
    }

    public class connectTask extends AsyncTask<String, String, TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {

            //we create a TCPClient object and
            mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            });
            mTcpClient.run();

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);

            //in the arrayList we add the messaged received from server
            arrayList.add(values[0]);
            // notify the adapter that the data set has changed. This means that new message received
            // from server was added to the list
            mAdapter.notifyDataSetChanged();

            //if the message is a command, then do the following things
            if (values[0].equals("start")) {
                startPeriodProbing();
            } else if (values[0].equals("stop")) {
                stopPeriodProbing();
            }
        }
    }

    protected void startPeriodProbing() {
        if (mTimerPeriodic != null) {
            mTimerPeriodic.cancel();
        }
        Log.d("startPeriodProbing", "before create period timer");
        mTimerPeriodic = new Timer();
        mTimerPeriodic.scheduleAtFixedRate(new myTimerTask(), 0, Utils.repeat_period);
    }


    private void stopPeriodProbing() {
        if (mTimerPeriodic != null) {
            mTimerPeriodic.cancel();
        }
    }

    class myTimerTask extends TimerTask {

        @Override
        public void run() {
            final Date currentTime = new Date();
            final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
            mRecordFileName = currentObjectName + "_" + sdf.format(currentTime) + ".wav";

            if (isExternalStorageWritable())
                mRecordFile = new File(myDir, mRecordFileName);
            else
                mRecordFile = new File(getApplicationContext().getFilesDir(), mRecordFileName);

            startRecordSound();

            int delayTime =  Integer.parseInt(currentObjectName);

            //if this is the target object, then do the following
            if (Utils.targetObject.equals("1")) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playSweepSound();
                    }
                }, delayTime);
            }
        }
    }


    private void startRecordSound() {
        // Start recording
        //extAudioRecorder = ExtAudioRecorder.getInstanse(true);	  // Compressed recording (AMR)
        extAudioRecorder = ExtAudioRecorder.getInstanse(false); // Uncompressed recording (WAV)

        extAudioRecorder.setOutputFile(mRecordFile.getAbsolutePath());

        //Log.i("TAG", "after init extAudioRecorder");

        extAudioRecorder.prepare();
        //Log.i("TAG", "after preparing");

        extAudioRecorder.start();

        //startTimerTask(Utils.probing_duration);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                     @Override
                                                     public void run() {

                                                         new CountDownTimer(Utils.probing_duration, Utils.probing_duration) {
                                                             public void onTick(long millisUntilFinished) {
                                                             }

                                                             public void onFinish() {
                                                                 StopRecordSound();
                                                             }
                                                         }.start();
                                                     }
                                                 }
        );
    }

    private void playSweepSound() {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                (int) (1.0 * mAudioManager
                        .getStreamMaxVolume(AudioManager.STREAM_MUSIC)), 0);
        mp = MediaPlayer.create(getApplicationContext(), R.raw.sin20hz20000hzlin_left);
        if (mp != null) {
            mp.start();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(final MediaPlayer mp) {
                    // TODO Auto-generated method stub
                    ReleaseMediaPlayer();
                }
            });
        }
    }


    private void ReleaseMediaPlayer() {
        if (mp != null) {
            mp.reset();
            mp.release();
            mp = null;
        }
    }

/*
    private void startTimerTask(int timeinterval)
    {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                new CountDownTimer(timeinterval, timeinterval) {
                    public void onTick(long millisUntilFinished) {
                    }

                    public void onFinish() {
                        StopRecordSound();
                    }
                }.start();
            }
        });

    }
*/

    private void StopRecordSound() {
        // Stop recording
        extAudioRecorder.stop();
        extAudioRecorder.reset();
        extAudioRecorder.release();
    }
}
