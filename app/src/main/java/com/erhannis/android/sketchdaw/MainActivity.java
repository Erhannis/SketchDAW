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
  @BindView(R.id.btnStart) Button btnStart;
  @BindView(R.id.btnPlay) Button btnPlay;
  @BindView(R.id.btnShutdown) Button btnShutdown;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);

    checkPermissions();

    Any2OneChannelInt playChannel = Channel.any2oneInt(new InfiniteBufferInt());
    final SharedChannelOutputInt playOut = playChannel.out();
    Any2OneChannelInt shutdownChannel = Channel.any2oneInt(new InfiniteBufferInt());
    final SharedChannelOutputInt shutdownOut = shutdownChannel.out();

    try {
      final AudioRecordTest audioRecordTest = new AudioRecordTest(shutdownChannel.in(), playChannel.in());
      btnStart.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          new ProcessManager(audioRecordTest).start();
        }
      });
      btnPlay.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          playOut.write(0);
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
