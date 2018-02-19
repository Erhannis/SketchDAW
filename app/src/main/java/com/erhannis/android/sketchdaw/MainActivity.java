package com.erhannis.android.sketchdaw;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Licensed under Apache License 2.0
 */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  protected static final int FILE_VERSION = 1;
  protected static final SimpleDateFormat FILENAME_DATE_FORMATTER = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss", Locale.US);


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

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    //TODO Add normal "save"
    menu.add("Save project as...").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem menuItem) {
        getTextInput("Save to filename (.sdp)", "/sdcard/SketchDAWProjects/" + FILENAME_DATE_FORMATTER.format(new Date()) + ".sdp", new Consumer<String>() {
          @Override
          public void accept(String filename) {
            try {
              // Save
              // We're running the gun, here; I want it to keep recording while saving, so we have to be careful what we access, in what order, when
              /*
              TODO Note that this relies on the following assumptions:
              1. Both project.mic and .tags are only added to, not altered or removed
              2. Only the last item of project.playbacks can be changed
               */
              SketchProject project = sketchDAWCallsChannel.exportProject();
              int playbacksSize = project.playbacks.size();
              // This is the one at risk of being changed; I want to get it out of the way, though it's arguable I ought to preserve the other indices, first.
              IntervalReference lastRef = null;
              if (playbacksSize > 0) {
                lastRef = project.playbacks.get(playbacksSize - 1);
                lastRef = lastRef.clone();
              }
              int micSize = project.mic.size();
              int tagsSize = project.tags.size();
              File f = new File(filename);
              f.getParentFile().mkdirs();
              ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
              oos.writeUTF(BuildConfig.GIT_HASH);
              oos.write(FILE_VERSION);
              oos.write(micSize);
              oos.write(playbacksSize);
              oos.write(tagsSize);
              oos.write(SketchDAWProcess.SAMPLE_RATE);
              if (micSize > 0) {
                oos.write(project.mic.get(0).data.length);
              } else {
                oos.write(SketchDAWProcess.CHUNK_SIZE);
              }
              for (int i = 0; i < micSize; i++) {
                AudioChunk chunk = project.mic.get(i);
                for (short s : chunk.data) {
                  oos.writeShort(s);
                }
              }
              for (int i = 0; i < playbacksSize - 1; i++) {
                IntervalReference ref = project.playbacks.get(i);
                oos.write(ref.sourceStart);
                oos.write(ref.destStart);
                oos.write(ref.duration);
              }
              if (lastRef != null) {
                oos.write(lastRef.sourceStart);
                oos.write(lastRef.destStart);
                oos.write(lastRef.duration);
              }
              for (int i = 0; i < tagsSize; i++) {
                Tag tag = project.tags.get(i);
                oos.write(tag.pos);
                oos.writeUTF(tag.text);
              }
              oos.flush();
              oos.close();
            } catch (Exception e) {
              showToast("Error saving project! " + e.getMessage());
              Log.e(TAG, "Error saving project!", e);
            }
          }
        });
        return true;
      }
    });
    return true;
  }

  protected boolean checkPermissions() {
    if(checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            || checkCallingOrSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            || checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
      //TODO Retry on return?
      return false;
    }
    return true;
  }

  // https://stackoverflow.com/a/10904665/513038
  protected void getTextInput(String title, String def, final Consumer<String> callback) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(title);

    // Set up the input
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setText(def);
    builder.setView(input);

    // Set up the buttons
    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        callback.accept(input.getText().toString());
      }
    });
    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
      }
    });

    AlertDialog dialog = builder.show();
  }

  protected void showToast(final String text) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
      }
    });
  }
}
