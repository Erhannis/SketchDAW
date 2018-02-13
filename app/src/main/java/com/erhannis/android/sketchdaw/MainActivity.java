package com.erhannis.android.sketchdaw;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.jcsp.lang.Any2OneChannelInt;
import org.jcsp.lang.Channel;
import org.jcsp.lang.ProcessManager;
import org.jcsp.lang.SharedChannelOutputInt;
import org.jcsp.util.ints.InfiniteBufferInt;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
  //@BindView(R.id.btnStart) Button btnStart;
  //@BindView(R.id.btnPlay) Button btnPlay;
  @BindView(R.id.btnBack30s) Button btnBack30s;
  @BindView(R.id.btnBack5s) Button btnBack5s;
  @BindView(R.id.btnForwardAll) Button btnForwardAll;
  @BindView(R.id.btnShutdown) Button btnShutdown;

  protected static final SharedChannelOutputInt backSecondsOut;
  protected static final SharedChannelOutputInt shutdownOut;
  protected static final AudioRecordTest audioRecordTest;
  static {
    Any2OneChannelInt backSecondsChannel = Channel.any2oneInt(new InfiniteBufferInt());
    backSecondsOut = backSecondsChannel.out();
    Any2OneChannelInt shutdownChannel = Channel.any2oneInt(new InfiniteBufferInt());
    shutdownOut = shutdownChannel.out();
    try {
      audioRecordTest = new AudioRecordTest(shutdownChannel.in(), backSecondsChannel.in());
      new ProcessManager(audioRecordTest).start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    checkPermissions();

    try {
//      btnStart.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View view) {
//          new ProcessManager(audioRecordTest).start();
//        }
//      });
//      btnPlay.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View view) {
//          playOut.write(0);
//        }
//      });
      btnBack30s.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          backSecondsOut.write(30);
        }
      });
      btnBack5s.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          backSecondsOut.write(5);
        }
      });
      btnForwardAll.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          //TODO Fix
          backSecondsOut.write(-5);
        }
      });
      btnShutdown.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          shutdownOut.write(0);
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  protected void checkPermissions() {
    if(checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
    }
  }
}
