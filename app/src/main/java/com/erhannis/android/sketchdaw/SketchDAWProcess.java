package com.erhannis.android.sketchdaw;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.AltingChannelInputInt;
import org.jcsp.lang.CSProcess;
import org.jcsp.lang.One2OneCallChannel;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * The core of SketchDAW functionality.  Tracks one project at a time, recording audio and playback,
 * and emitting sound, etc.
 *
 * Created by erhannis on 2/12/18.
 */

public class SketchDAWProcess implements CSProcess {
  protected static final String TAG = "SketchDAWProcess";
  protected static final int SAMPLE_RATE = 44100;
  protected static final int CHUNK_SIZE = 4410;

  protected final AltingChannelInputInt seekSecondsInput;
  protected final AltingChannelInput<Tag> tagInput;
  protected final AltingChannelInputInt stopRecordInput;
  protected final AltingChannelInputInt resumeRecordInput;
  protected final SketchDAWCallsChannel sketchDAWCallsChannel;
  protected final AltingChannelInputInt shutdownInput;

  protected AudioRecord mAr;
  protected SketchProject mProject;

  protected boolean mPlaying = false;
  protected boolean mRecording = false;
  protected final ArrayList<AudioTrack> mTracks = new ArrayList<AudioTrack>();
  protected final HashMap<AudioTrack, Integer> mPositions = new HashMap<AudioTrack, Integer>();

  /**
   * Channels:
   * seek
   * tag
   * stopRecord
   * resumeRecord
   * SketchDAWCallsChannel
   *   getPos
   *   export
   *   import
   * shutdown
   *
   * @param seekSecondsInput
   * @param tagInput
   * @param stopRecordInput
   * @param resumeRecordInput
   * @param sketchDAWCallsChannel
   * @param shutdownInput
   * @throws Exception
   */
  public SketchDAWProcess(
          AltingChannelInputInt seekSecondsInput,
          AltingChannelInput<Tag> tagInput,
          AltingChannelInputInt stopRecordInput,
          AltingChannelInputInt resumeRecordInput,
          SketchDAWCallsChannel sketchDAWCallsChannel,
          AltingChannelInputInt shutdownInput
         ) throws Exception {
    this.seekSecondsInput = seekSecondsInput;
    this.tagInput = tagInput;
    this.stopRecordInput = stopRecordInput;
    this.resumeRecordInput = resumeRecordInput;
    this.sketchDAWCallsChannel = sketchDAWCallsChannel;
    this.shutdownInput = shutdownInput;
  }

  /**
   * Reset everything to a clean slate.
   */
  protected void cleanup() {
    if (mAr != null) {
      try {
        mAr.stop();
        mAr.release();
      } catch (Exception e) {
        Log.d(TAG, "Error cleaning up AudioRecord", e);
      }
      mAr = null;
    }
    mProject = null;
    mPlaying = false;
    mRecording = false;
    for (AudioTrack track : mTracks) {
      try {
        track.pause();
        track.flush();
        track.release();
      } catch (Exception e) {
        Log.d(TAG, "Error cleaning up AudioTrack", e);
      }
    }
    mTracks.clear();
    mPositions.clear();
  }

  /**
   * Initializes everything for a new run.
   */
  protected void init() {
    //TODO Configurize, optionate, bebutton
    mProject = new SketchProject();
    mProject.mic = new RawAudioData();
    mProject.playbacks = new ArrayList<IntervalReference>();

    mAr = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2);
    if (mAr.getState() != AudioRecord.STATE_INITIALIZED) {
      throw new RuntimeException("Failed to initialize AudioRecord!");
    }
  }

  @Override
  public void run() {
    init();
    //TODO Maybe don't START with autorecord?  Optionize?
    mAr.startRecording();
    mRecording = true;
  }

  public void run2() {
    mAr.startRecording();
    mRecording = true;
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
      if (seekSecondsInput.pending()) {
        int seekSeconds = seekSecondsInput.read();
        if (seekSeconds == Integer.MAX_VALUE) {
          stopPlayback(tracks, positions);
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
    cleanup();
  }

  protected void stopPlayback(ArrayList<AudioTrack> tracks, HashMap<AudioTrack, Integer> positions) {
    // Stop playback
    for (AudioTrack track : tracks) {
      track.stop();
    }
    tracks.clear();
    positions.clear();
    mPlaying = false;
  }
}
