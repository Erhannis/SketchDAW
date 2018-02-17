package com.erhannis.android.sketchdaw;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import org.jcsp.lang.Alternative;
import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.AltingChannelInputInt;
import org.jcsp.lang.CSProcess;
import org.jcsp.lang.Guard;
import org.jcsp.lang.One2OneCallChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * The core of SketchDAW functionality.  Tracks one project at a time, recording audio and playback,
 * and emitting sound, etc.
 *
 * Created by erhannis on 2/12/18.
 *
 * Licensed under Apache License 2.0
 *
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
  protected int mSafeChunksLeft = Integer.MAX_VALUE;

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

  /**
   * Channels checked in order:
   *
   * shutdown
   * tag
   * seek
   * stopRecord, resumeRecord
   * SketchDAWCallsChannel
   *   getPos
   *   export
   *   import
   *
   */
  @Override
  public void run() {
    Alternative recordAlt = new Alternative(new Guard[]{stopRecordInput, resumeRecordInput});
    try {
      init();
      //TODO Maybe don't START with autorecord?  Optionize?
      mAr.startRecording();
      mRecording = true;
      short[] tempChunk = new short[CHUNK_SIZE];
      while (true) {
        // Ch: shutdown
        if (shutdownInput.pending()) {
          shutdownInput.read();
          break;
        }

        // Ch: tag
        if (tagInput.pending()) {
          //TODO Should you just pass in a string, and it tags the recording spot?...or the playback spot?
          Tag tag = tagInput.read();
          mProject.tags.add(tag);
        }

        // Read audio
        if (mRecording) {
          int readCount = mAr.read(tempChunk, 0, CHUNK_SIZE);
          if (readCount != CHUNK_SIZE) {
            throw new RuntimeException("readCount is wrong and I don't know what it means!!! " + readCount);
          }
          Log.d(TAG, "Read: " + readCount);
          mProject.mic.add(new AudioChunk(tempChunk.clone()));
        }

        // Ch: seek
        //TODO Fix
        if (seekSecondsInput.pending()) {
          int seekSeconds = seekSecondsInput.read();
          //TODO Break/create IntervalReference
          //TODO Examine this closer once "mRecording" is more of a thing
          if (mRecording) {
            capIntervalReference();
          }
          if (seekSeconds == Integer.MAX_VALUE) {
            stopPlayback();
          } else {
            // Seek
            int seekChunks = (seekSeconds * SAMPLE_RATE) / CHUNK_SIZE;
            if (0 <= seekSeconds) {
              // Skipping forward
              if (mPlaying) { // Can't start playing from the future
                AudioTrack track = mTracks.get(0);
                int newPos = mPositions.get(track) + seekChunks;
                if (newPos < 0) {
                  newPos = 0;
                }
                if (newPos >= mProject.mic.size()) {
                  // We've hit the leading edge of recording
                  stopPlayback();
                } else {
                  mPositions.put(track, newPos);
                  track.flush(); //TODO Doesn't work?
                }
              }
            } else {
              // Skipping backward
              if (!mPlaying) {
                // Extract into startPlayback?
long timeStart = System.currentTimeMillis();
                AudioTrack track = new AudioTrack( AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2, AudioTrack.MODE_STREAM);
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                  throw new RuntimeException("Failed to initialize AudioData!");
                }
                mTracks.add(track);
                mPositions.put(track, mProject.mic.size()); //TODO Between reading and writing?
                track.play();
                mPlaying = true;
Log.d(TAG, "AudioTrack initialization took " + (System.currentTimeMillis() - timeStart) + " ms");
              }
              AudioTrack track = mTracks.get(0);
              int newPos = mPositions.get(track) + seekChunks;
              if (newPos < 0) {
                newPos = 0;
              }
              mPositions.put(track, newPos);
              track.flush(); //TODO Doesn't work?
            }
            // If we've ended up playing somewhere new, now, make a new ReferenceInterval for it
            if (mPlaying) {
              //TODO Does it matter that we're between reading (+1) and writing (=0)?
              IntervalReference ref = new IntervalReference(mPositions.get(mTracks.get(0)), mProject.mic.size() - 1, IntervalReference.INFINITY); //TODO Off-by-one errors!!!
              mProject.playbacks.add(ref);
              updateRecursivePlayback();
            }
          }
        }

        // Play audio
        if (mPlaying) {
          if (mSafeChunksLeft <= 0) {
            updateRecursivePlayback();
          }
          boolean hitEnd = false;
          for (AudioTrack track : mTracks) {
            if (mPositions.get(track) >= mProject.mic.size()) {
              hitEnd = true;
              break;
            }
            int writeCount = track.write(mProject.mic.get(mPositions.get(track)).data, 0, CHUNK_SIZE);
            if (writeCount != CHUNK_SIZE) {
              throw new RuntimeException("writeCount is wrong and I don't know what it means!!! " + writeCount);
            }
            Log.d(TAG, "Wrote: " + writeCount);
            mPositions.put(track, mPositions.get(track) + 1);
          }
          mSafeChunksLeft--;
          if (hitEnd) {
            //TODO Should THIS cap the IntervalReference?
            stopPlayback();
          }
        }

        //TODO stopRecord, resumeRecord
        //TODO When resumeRecord, restore playback position
        //TODO SketchDAWCallsChannel
      }
    } finally {
      cleanup();
    }
  }

  //TODO Would this be clearer inlined?  Hmm
  protected void stopPlayback() {
    for (AudioTrack track : mTracks) {
      track.pause();
      track.release();
    }
    mTracks.clear();
    mPositions.clear();
    mSafeChunksLeft = Integer.MAX_VALUE;
    mPlaying = false;
    //TODO This may be redundant with all the calling code
    capIntervalReference();
  }

  protected void capIntervalReference() {
    if (mProject.playbacks.size() > 0) {
      IntervalReference ref = mProject.playbacks.get(mProject.playbacks.size() - 1);
      if (ref.duration == IntervalReference.INFINITY) {
        // This is the active IntervalReference
        ref.duration = mProject.mic.size() - ref.destStart;
      }
    }
  }

  /**
   * Takes the position of the first AudioTrack and determines where the others should be.
   * Also calculates and sets the chunks left before this should be called again, according to
   * the current project structure.
   */
  protected void updateRecursivePlayback() {
    if (mTracks.size() < 1) {
      mSafeChunksLeft = Integer.MAX_VALUE;
      return;
    }
    LinkedList<Integer> newPositions = mProject.getRecursivePlaybackPositions(mPositions.get(mTracks.get(0)));
    mSafeChunksLeft = newPositions.removeLast();
    newPositions.addFirst(mPositions.get(mTracks.get(0)));
    //TODO Maybe just return a HashSet, to begin with?
    HashSet<Integer> remainingPositionSet = new HashSet<>(newPositions);
    Iterator<AudioTrack> iTrack = mTracks.iterator();
    // Ignore tracks already playing where we need them to
    while (iTrack.hasNext()) {
      AudioTrack track = iTrack.next();
      Integer pos = mPositions.get(track);
      if (!remainingPositionSet.remove(pos)) {
        // This track is not pointing somewhere we need to play; get rid of it.
        track.pause();
        track.release();
        iTrack.remove();
        mPositions.remove(track);
      }
    }
    // Now make tracks for the places we need to play
    for (Integer pos : remainingPositionSet) {
      //TODO Keep a cache of initialized AudioTracks?
      AudioTrack track = new AudioTrack( AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, SAMPLE_RATE*2, AudioTrack.MODE_STREAM);
      if (track.getState() != AudioTrack.STATE_INITIALIZED) {
        throw new RuntimeException("Failed to initialize AudioData!");
      }
      mTracks.add(track);
      mPositions.put(track, pos);
      track.play();
    }
  }
}
