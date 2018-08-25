package research.mingming.sensorUI.soqrclient;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;

import research.mingming.sensorUI.R;
import research.mingming.sensorUI.activeprobing.ExtAudioRecorder;

public class MainActivity extends Activity {
    PowerManager.WakeLock wl = null;//A wake lock is to indicate your application needs to have the device stays on
    AudioManager mAudioManager;//Audio manager provides acess to volume and ringer model control
    MediaPlayer mp = null;//can be used to control playback of audio/video files
    ExtAudioRecorder extAudioRecorder = null;// I guess this is for recording audio files
    Timer mTimerPeriodic = null;// used for scheduling tasks for future application
    File myDir;
    String mRecordFileName;  // file name of recorded sound clip
    File mRecordFile = null;
    private int playtime_cnt = 0;
    private int fileCounter ; //This counter is use to sync files between different devices

    //timer stuff
    private boolean timerRunning;
    private static final long START_TIME_IN_MILLIS = 600000;
    private CountDownTimer mCountDownTimer;
    private long mTimerLeftInMillis = START_TIME_IN_MILLIS;
    private long mTimeElapsed;
    private Button send;
    private Button start_pause;
    private Button finish;
    private  TextView timeView;
    private TextView targetView;
    private TextView openessView;
    private String targetObject="---";
    private String openess = "---";

    private ListView mList;// displays a vertically scrollable collection of views
    private ArrayList<String> arrayList;
    private MyCustomAdapter mAdapter;
    private TCPClient mTcpClient;


    private String currentObjectName = "";

    @Override
    //why is the this onCreate method public? I thought it should be protected
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mywakeuplock");
        wl.acquire();

        arrayList = new ArrayList<String>();

        //By setting variable type to final you are keeping the values constant
        //You are allowed to initialize final variables only once
        //These are interactions with the UI. They are finding the corresponding UI element
        //final EditText etIP = (EditText) findViewById(R.id.et_ip);//ip address
        timeView = (TextView) findViewById(R.id.timeView);
        targetView = (TextView) findViewById(R.id.textTarget);
        openessView = (TextView) findViewById(R.id.textOpeness);
       // final EditText editText = (EditText) findViewById(R.id.editText);//message
        send = (Button) findViewById(R.id.send_button);
        start_pause = (Button) findViewById(R.id.start_pause_button);
        finish = (Button) findViewById(R.id.finish_button);

        fileCounter = 1;

        //relate the listView from java to the one created in xml
        mList = (ListView) findViewById(R.id.list);// find the corresponding UI element
        mAdapter = new MyCustomAdapter(this, arrayList);//ArrayAdapter is still holding a reference to the orginal list
        //However, it does not know if the you have changed the orginal list in Activity
        mList.setAdapter(mAdapter);// set the data behind this ListView



        // waiting for send button to be clicked connect to server if pressed
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            //    String ipaddress = etIP.getText().toString();// waiting for input ip address

              //    String ipaddress = "172.20.10.3"; //the ipaddress for my phone
              String ipaddress = "100.64.194.54";// the ipaddress of my computer in DGP Lab


             //   etIP.setText(ipaddress);//clear the input
               // timeView.setText(ipaddress);
                //What if ipaddress does not accpet new ipadress
                TCPClient.SERVERIP = ipaddress;// Why not mTCPClient?

                Log.d("DEBUG", "IP: " + TCPClient.SERVERIP);//Log.d sends out a DEBUG log message

                //String message = editText.getText().toString();

                //currentObjectName = message;


                    new connectTask().execute("");//execute the asychronous task
                    //what if you press send button multiple times which also triggers execute multiple times
                    //parameters of execute() goes to  doInBackground()
                    //add the text in the arrayList

                  //  arrayList.add("c: " + message);


                //sends the message to the server
                if (mTcpClient != null) {
                   // mTcpClient.sendMessage(message);

                }

                //refresh the list
                mAdapter.notifyDataSetChanged();// arrayList, which is now the underlying data of mAdapeter, can be changed by using this function
               // editText.setText("");//clear the input
              //  etIP.setText("");//clear the input

            }
        });

        start_pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mTcpClient.sendMessage("view");

                //insert a piece of code to enable viewing
                if(timerRunning){
                    pauseTimer();
                } else{
                    startTimer();
                }


            }
        });

        finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                mTcpClient.sendMessage("probe"+ Integer.toString((int)mTimeElapsed));//send the keyword probe along with time elapsed to server
                pauseTimer(); //pause the timer

            }
        });

    }

    private void startTimer(){
        mCountDownTimer = new CountDownTimer(mTimerLeftInMillis,1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                mTimerLeftInMillis = millisUntilFinished;//time left
                mTimeElapsed = START_TIME_IN_MILLIS-mTimerLeftInMillis; //time elapsed

                updateCountDownText();
            }

            @Override
            public void onFinish() {

                timerRunning = false;//at the end of countdown the timer stops running
                start_pause.setText("START");


            }
        }.start();
        timerRunning = true;
        start_pause.setText("PAUSE");
    }

    private void pauseTimer(){
        mCountDownTimer.cancel();
        timerRunning = false;
        start_pause.setText("START");
    }

    private void resetTimer(){
        mTimerLeftInMillis = START_TIME_IN_MILLIS;
        updateCountDownText();
    }

    private void updateCountDownText(){
        int minutes = (int) (mTimeElapsed/1000)/60; //turns into minutes
        int seconds = (int) (mTimeElapsed/1000)%60; //turns into seconds after divided by 60;
        String timeLeftFormatted = String.format(Locale.getDefault(),"%02d:%02d",minutes,seconds);
        timeView.setText(timeLeftFormatted);

    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

//always release resource at the end of the activity's life cycle
    protected void onDestroy() {
        if (wl != null) {
            if (wl.isHeld())
                wl.release();
            wl = null;
        }
        super.onDestroy();
    }

    //AsyncTask run the network operation in the background
    //connectTask is just a subclass of AsynTask
    //Note for some reason some of functions of the AsyncTask subclass are not explicity called
    public class connectTask extends AsyncTask<String, String, TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {
        //we would like to maintain the connectino to server in the background
            //we create a TCPClient object and
            mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    //this method publish (not display) the message to the main UI thread
                    if(message.length()>20) {
                        publishProgress(message); //cue message tend to be much longer than target object name
                    }
                    else {
                        if(message.equals("Open")||message.equals("Closed"))
                        {
                            openess = message;
                        }
                        else {
                            targetObject = message; //assign target object name to target object view
                        }
                    }
                }
            });

            mTcpClient.clientID = currentObjectName;// assign a client ID to each object


            mTcpClient.run();// keep running until the program is terminated



            return null;
        }

        @Override
        //executed on the main UI thread
        protected void onProgressUpdate(String... values) {

            super.onProgressUpdate(values);

            //in the arrayList we add the messaged received from server
            arrayList.add(values[0]);

            // notify the adapter that the data set has changed. This means that new message received
            // from server was added to the list
            mAdapter.notifyDataSetChanged();

            targetView.setText(targetObject);
            openessView.setText(openess);

           // int delay = (currentObjectName-1)*1000;  //create delay for the client

            //if the message is a command, then do the following things
        }
    }


}
