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

import com.erhannis.android.sketchdaw.data.AudioChunk;
import com.erhannis.android.sketchdaw.data.IntervalReference;
import com.erhannis.android.sketchdaw.data.RawAudioData;
import com.erhannis.android.sketchdaw.data.SketchProject;
import com.erhannis.android.sketchdaw.data.Tag;
import com.erhannis.android.sketchdaw.jcsp.EncodeFlac;
import com.erhannis.android.sketchdaw.jcsp.SketchDAWCallsChannel;
import com.erhannis.android.sketchdaw.jcsp.SketchDAWProcess;
import com.erhannis.android.sketchdaw.misc.Consumer;

import org.jcsp.lang.Any2OneChannel;
import org.jcsp.lang.Any2OneChannelInt;
import org.jcsp.lang.Channel;
import org.jcsp.lang.ProcessManager;
import org.jcsp.lang.SharedChannelOutput;
import org.jcsp.lang.SharedChannelOutputInt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static com.erhannis.android.sketchdaw.Settings.FILENAME_DATE_FORMATTER;

/**
 * Licensed under Apache License 2.0
 */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  protected static final int FILE_VERSION = 1;


  //@BindView(R.id.btnStart) Button btnStart;
  //@BindView(R.id.btnPlay) Button btnPlay;
  protected Button btnBack30s;
  protected Button btnBack5s;
  protected Button btnForward5s;
  protected Button btnForwardAll;
  protected Button btnShutdown;

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

    btnBack30s = findViewById(R.id.btnBack30s);
    btnBack5s = findViewById(R.id.btnBack5s);
    btnForward5s = findViewById(R.id.btnForward5s);
    btnForwardAll = findViewById(R.id.btnForwardAll);
    btnShutdown = findViewById(R.id.btnShutdown);

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
      btnForward5s.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          seekSecondsOut.write(5);
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
              oos.writeInt(FILE_VERSION);
              oos.writeInt(micSize);
              oos.writeInt(playbacksSize);
              oos.writeInt(tagsSize);
              oos.writeInt(SketchDAWProcess.SAMPLE_RATE);
              if (micSize > 0) {
                oos.writeInt(project.mic.get(0).data.length);
              } else {
                oos.writeInt(SketchDAWProcess.CHUNK_SIZE);
              }
              for (int i = 0; i < micSize; i++) {
                AudioChunk chunk = project.mic.get(i);
                for (short s : chunk.data) {
                  oos.writeShort(s);
                }
              }
              for (int i = 0; i < playbacksSize - 1; i++) {
                IntervalReference ref = project.playbacks.get(i);
                oos.writeInt(ref.sourceStart);
                oos.writeInt(ref.destStart);
                oos.writeInt(ref.duration);
              }
              if (lastRef != null) {
                oos.writeInt(lastRef.sourceStart);
                oos.writeInt(lastRef.destStart);
                oos.writeInt(lastRef.duration);
              }
              for (int i = 0; i < tagsSize; i++) {
                Tag tag = project.tags.get(i);
                oos.writeInt(tag.pos);
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
    //TODO Open file browser
    //TODO Filter file open events
    menu.add("Load project...").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem menuItem) {
        getTextInput("Load filename (.sdp)", "/sdcard/SketchDAWProjects/", new Consumer<String>() {
          @Override
          public void accept(String filename) {
            String hash = null;
            Integer fileVersion = null;
            try {
              // Load
              File f = new File(filename);
              ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
              hash = ois.readUTF();
              fileVersion = ois.readInt();
              if (fileVersion != FILE_VERSION) {
                throw new IllegalArgumentException("Unhandled file version (" + fileVersion + ") vs current " + FILE_VERSION);
              }
              int micSize = ois.readInt();
              int playbacksSize = ois.readInt();
              int tagsSize = ois.readInt();
              int sampleRate = ois.readInt(); //TODO What do?
              if (sampleRate != SketchDAWProcess.SAMPLE_RATE) {
                throw new IllegalArgumentException("Unhandled sample rate (" + sampleRate + ") vs current " + SketchDAWProcess.SAMPLE_RATE);
              }
              int chunkSize = ois.readInt();
              //TODO Optionize not to disk cache, and/or move to cache folder
              //TODO There's a disk duplication of data implicit here
              SketchProject project = new SketchProject(new File(Settings.getDefaultCacheLocation(), FILENAME_DATE_FORMATTER.format(new Date()) + ".sdc"));
              for (int i = 0; i < micSize; i++) {
                AudioChunk chunk = new AudioChunk(chunkSize);
                for (int j = 0; j < chunkSize; j++) {
                  chunk.data[j] = ois.readShort();
                }
                project.mic.add(chunk);
              }
              for (int i = 0; i < playbacksSize; i++) {
                int sourceStart = ois.readInt();
                int destStart = ois.readInt();
                int duration = ois.readInt();
                IntervalReference ref = new IntervalReference(sourceStart, destStart, duration);
                project.playbacks.add(ref);
              }
              for (int i = 0; i < tagsSize; i++) {
                int pos = ois.readInt();
                String text = ois.readUTF();
                Tag tag = new Tag(pos, text);
                project.tags.add(tag);
              }
              ois.close();
              sketchDAWCallsChannel.importProject(project);
            } catch (Exception e) {
              showToast("Error loading project! " + e.getMessage());
              showToast("Saved under version: " + hash);
              showToast("File version: " + fileVersion);
              Log.e(TAG, "Error loading project!", e);
            }
          }
        });
        return true;
      }
    });
    menu.add("Export to FLAC...").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem menuItem) {
        //TODO Extract last save filename base
        getTextInput("Export (.flac)", "/sdcard/SketchDAWProjects/" + FILENAME_DATE_FORMATTER.format(new Date()) + ".flac", new Consumer<String>() {
          @Override
          public void accept(final String filename) {
            showToast("Warning: FLAC export appears broken");
            SketchProject project = sketchDAWCallsChannel.exportProject();
            //TODO This is kindof memory-wasteful
            EncodeFlac.encode(project.mic, 0, project.mic.size(), new Consumer<byte[]>() {
              @Override
              public void accept(byte[] value) {
                File f = new File(filename);
                f.getParentFile().mkdirs();
                try {
                  FileOutputStream fos = new FileOutputStream(f);
                  fos.write(value);
                  fos.flush();
                  fos.close();
                  showToast("Finished exporting");
                } catch (IOException e) {
                  e.printStackTrace();
                  showToast("Error exporting");
                }
              }
            });
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
