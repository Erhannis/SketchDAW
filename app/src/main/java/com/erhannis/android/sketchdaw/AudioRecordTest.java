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

public class AudioRecordTest implements CSProcess {
  private static final String TAG = "AudioRecordTest";
  private static final int SAMPLE_RATE = 44100;
  private static final int CHUNK_SIZE = 4410;

  private final AltingChannelInputInt shutdownInput;
  private final AltingChannelInputInt playInput;

  private final AudioRecord mAr;

  protected final AudioData mAudio = new AudioData();

  public AudioRecordTest(AltingChannelInputInt shutdownInput, AltingChannelInputInt playInput) throws Exception {
    this.shutdownInput = shutdownInput;
    this.playInput = playInput;
    mAr = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2);
    if (mAr.getState() != AudioRecord.STATE_INITIALIZED) {
      throw new Exception("Failed to initialize AudioRecord!");
    }
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
      mAudio.add(new AudioChunk(tempChunk.clone()));
      if (playInput.pending()) {
        playInput.read();
        AudioTrack track = new AudioTrack( AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2, AudioTrack.MODE_STREAM);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
          throw new RuntimeException("Failed to initialize AudioData!");
        }
        tracks.add(track);
        int writeCount = track.write(mAudio.get(0).data, 0, CHUNK_SIZE);
        Log.d(TAG, "Initial write: " + writeCount);
        positions.put(track, 1);
        track.play();
      }
      for (AudioTrack track : tracks) {
        int writeCount = track.write(mAudio.get(positions.get(track)).data, 0, CHUNK_SIZE);
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
  }
}
