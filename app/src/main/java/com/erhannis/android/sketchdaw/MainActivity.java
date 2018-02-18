package com.erhannis.android.sketchdaw;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.AltingChannelInputInt;
import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.Any2OneChannelInt;
import org.jcsp.lang.Channel;
import org.jcsp.lang.ProcessManager;
import org.jcsp.lang.SharedChannelOutput;
import org.jcsp.lang.SharedChannelOutputInt;
import org.jcsp.util.ints.InfiniteBufferInt;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Licensed under Apache License 2.0
 */
public class MainActivity extends AppCompatActivity {
  //@BindView(R.id.btnStart) Button btnStart;
  //@BindView(R.id.btnPlay) Button btnPlay;
  @BindView(R.id.btnBack30s) Button btnBack30s;
  @BindView(R.id.btnBack5s) Button btnBack5s;
  @BindView(R.id.btnForwardAll) Button btnForwardAll;
  @BindView(R.id.btnShutdown) Button btnShutdown;

  protected static final SharedChannelOutputInt seekSecondsOut;
  protected static final SharedChannelOutput<Tag> tagOut;
  protected static final SharedChannelOutputInt stopRecordOut;
  protected static final SharedChannelOutputInt resumeRecordOut;
  protected static final SketchDAWCallsChannel sketchDAWCallsChannel;
  protected static final SharedChannelOutputInt shutdownOut;
  protected static final SketchDAWProcess sketchDAWProcess;
  static {
    Any2OneChannelInt seekSecondsChannel = Channel.any2oneInt();
    seekSecondsOut = seekSecondsChannel.out();
    Any2OneChannel<Tag> tagChannel = Channel.<Tag>any2one();
    tagOut = tagChannel.out();
    Any2OneChannelInt stopRecordChannel = Channel.any2oneInt();
    stopRecordOut = stopRecordChannel.out();
    Any2OneChannelInt resumeRecordChannel = Channel.any2oneInt();
    resumeRecordOut = resumeRecordChannel.out();
    sketchDAWCallsChannel = new SketchDAWCallsChannel();
    Any2OneChannelInt shutdownChannel = Channel.any2oneInt();
    shutdownOut = shutdownChannel.out();
    try {
      sketchDAWProcess = new SketchDAWProcess(seekSecondsChannel.in(), tagChannel.in(), stopRecordChannel.in(), resumeRecordChannel.in(), sketchDAWCallsChannel, shutdownChannel.in());
      new ProcessManager(sketchDAWProcess).start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    if (!checkPermissions()) {
      //TODO Continue the app once permissions granted
      this.finish();
    }

    try {
//      btnStart.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View view) {
//          new ProcessManager(sketchDAWProcess).start();
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
          seekSecondsOut.write(-30);
        }
      });
      btnBack5s.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          seekSecondsOut.write(-5);
        }
      });
      btnForwardAll.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          seekSecondsOut.write(Integer.MAX_VALUE);
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

  protected boolean checkPermissions() {
    if(checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
      //TODO Retry on return?
      return false;
    }
    return true;
  }
}
