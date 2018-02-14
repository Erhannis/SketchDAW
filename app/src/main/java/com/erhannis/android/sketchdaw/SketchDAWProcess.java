package com.erhannis.android.sketchdaw;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import org.jcsp.lang.AltingChannelInputInt;
import org.jcsp.lang.CSProcess;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by erhannis on 2/12/18.
 */

public class SketchDAWProcess implements CSProcess {
  private static final String TAG = "SketchDAWProcess";
  private static final int SAMPLE_RATE = 44100;
  private static final int CHUNK_SIZE = 4410;

  private final AltingChannelInputInt shutdownInput;
  private final AltingChannelInputInt seekSecondsInput;

  private final AudioRecord mAr;

  protected SketchProject mProject;

  public SketchDAWProcess(AltingChannelInputInt shutdownInput, AltingChannelInputInt seekSecondsInput) throws Exception {
    this.shutdownInput = shutdownInput;
    this.seekSecondsInput = seekSecondsInput;
    mAr = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2);
    if (mAr.getState() != AudioRecord.STATE_INITIALIZED) {
      throw new Exception("Failed to initialize AudioRecord!");
    }
    //TODO Configurize, optionate, bebutton
    mProject = new SketchProject();
    mProject.mic = new RawAudioData();
    mProject.playbacks = new ArrayList<IntervalReference>();
  }

  @Override
  public void run() {
    mAr.startRecording();
    ArrayList<AudioTrack> tracks = new ArrayList<AudioTrack>();
    HashMap<AudioTrack, Integer> positions = new HashMap<AudioTrack, Integer>();
    short[] tempChunk = new short[CHUNK_SIZE];
    while (true) {
      if (shutdownInput.pending()) {
        shutdownInput.read();
        break;
      }
      int readCount = mAr.read(tempChunk, 0, CHUNK_SIZE);
      if (readCount != CHUNK_SIZE) {
        throw new RuntimeException("readCount is wrong and I don't know what it means!!! " + readCount);
      }
      Log.d(TAG, "Read: " + readCount);
      mProject.mic.add(new AudioChunk(tempChunk.clone()));
      //TODO seekInput
      if (seekSecondsInput.pending()) {
        int seekSeconds = seekSecondsInput.read();
        if (seekSeconds == Integer.MAX_VALUE) {
          // Stop playback
          for (AudioTrack track : tracks) {
            track.stop();
          }
          tracks.clear();
        } else {
          // Seek
          //TODO Calc layers, not just the one
          if (tracks.size() < 1) {
            AudioTrack track = new AudioTrack( AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2, AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
              throw new RuntimeException("Failed to initialize AudioData!");
            }
            tracks.add(track);
            positions.put(track, mProject.mic.size());
            track.play();
          }
          AudioTrack track = tracks.get(0);
          int seekChunks = (seekSeconds * SAMPLE_RATE) / CHUNK_SIZE;
          positions.put(track, positions.get(track) + seekChunks);
          track.flush(); //TODO Doesn't work?
        }
      }
      for (AudioTrack track : tracks) {
        int writeCount = track.write(mProject.mic.get(positions.get(track)).data, 0, CHUNK_SIZE);
        if (writeCount != CHUNK_SIZE) {
          throw new RuntimeException("writeCount is wrong and I don't know what it means!!! " + writeCount);
        }
        Log.d(TAG, "Wrote: " + writeCount);
        positions.put(track, positions.get(track) + 1);
      }
    }
    mAr.stop();
    for (AudioTrack track : tracks) {
      track.stop();
    }
    //TODO tracks.clear();?
  }
}
