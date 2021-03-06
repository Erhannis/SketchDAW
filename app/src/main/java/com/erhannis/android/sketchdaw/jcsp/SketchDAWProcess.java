package com.erhannis.android.sketchdaw.jcsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import com.erhannis.android.sketchdaw.Settings;
import com.erhannis.android.sketchdaw.data.AudioChunk;
import com.erhannis.android.sketchdaw.data.IntervalReference;
import com.erhannis.android.sketchdaw.data.SketchProject;
import com.erhannis.android.sketchdaw.data.Tag;

import org.jcsp.lang.Alternative;
import org.jcsp.lang.AltingChannelInput;
import org.jcsp.lang.AltingChannelInputInt;
import org.jcsp.lang.CSProcess;
import org.jcsp.lang.CSTimer;
import org.jcsp.lang.Guard;
import org.jcsp.lang.Skip;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import static com.erhannis.android.sketchdaw.Settings.FILENAME_DATE_FORMATTER;

/**
 * The core of SketchDAW functionality.  Tracks one project at a time, recording audio and playback,
 * and emitting sound, etc.
 *
 * Created by erhannis on 2/12/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public class SketchDAWProcess implements CSProcess, SketchDAWCalls {
  private static final String TAG = "SketchDAWProcess";
  public static final int SAMPLE_RATE = 44100;
  public static final int SAMPLE_BYTES = 2;
  public static final int CHUNK_SIZE = 4410;
  public static final int RECORD_BUFFER = SAMPLE_RATE*SAMPLE_BYTES;
  public static final int TRACK_BUFFER = ((SAMPLE_RATE*2) / 10) * SAMPLE_BYTES;

  protected final AltingChannelInputInt seekSecondsInput;
  protected final AltingChannelInput<Tag> tagInput;
  protected final AltingChannelInputInt stopRecordInput;
  protected final AltingChannelInputInt resumeRecordInput;
  protected final SketchDAWCallsChannel sketchDAWCallsChannel;
  protected final AltingChannelInputInt shutdownInput;

  protected AudioRecord mAr;
  protected AudioTrack mTrack;
  protected SketchProject mProject;

  protected boolean mPlaying = false;
  protected boolean mRecording = false;
  protected final ArrayList<Integer> mPositions = new ArrayList<>();
  protected int mSafeChunksLeft = Integer.MAX_VALUE;

  protected short[] mOverflow;
  protected int mOverflowStart = 0;
  protected int mOverflowLength = 0;

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
    Log.d(TAG, "Cleaning up");
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
    try {
      mTrack.pause();
      mTrack.release();
    } catch (Exception e) {
      Log.d(TAG, "Error cleaning up AudioTrack", e);
    }
    clearOverflow();
    mPositions.clear();
  }

  /**
   * Initializes everything for a new run.
   */
  protected void init() throws IOException {
    //TODO Configurize, optionate, bebutton
    //TODO Optionize not to disk cache, and/or move to cache folder
    mProject = new SketchProject(new File(Settings.getDefaultCacheLocation(), FILENAME_DATE_FORMATTER.format(new Date()) + ".sdc"));
    mAr = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, RECORD_BUFFER);
    if (mAr.getState() != AudioRecord.STATE_INITIALIZED) {
      throw new RuntimeException("Failed to initialize AudioRecord!");
    }
    mTrack = new AudioTrack( AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, TRACK_BUFFER, AudioTrack.MODE_STREAM);
    if (mTrack.getState() != AudioTrack.STATE_INITIALIZED) {
      throw new RuntimeException("Failed to initialize AudioData!");
    }
    mOverflow = new short[SAMPLE_RATE * 2];
    mOverflowStart = 0;
    mOverflowLength = 0;
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
    Log.d(TAG, "Starting up");
    CSTimer timer = new CSTimer();
    Alternative recordAlt = new Alternative(new Guard[]{stopRecordInput, resumeRecordInput});
    Alternative callsAlt = new Alternative(new Guard[]{sketchDAWCallsChannel, new Skip()});
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

        long postRecord = -1;
        // Read audio
        if (mRecording) {
          logClock(0, timer.read(), "everything else: ");
          int readCount = mAr.read(tempChunk, 0, CHUNK_SIZE);
          postRecord = timer.read();
          logClock(0, timer.read(), "recording: ");
          if (readCount != CHUNK_SIZE) {
            throw new RuntimeException("readCount is wrong and I don't know what it means!!! " + readCount);
          }
          Log.d(TAG, "Read: " + readCount);
          mProject.mic.add(new AudioChunk(tempChunk.clone()));
        } else {
          //TODO I'm not sure this is necessary
          postRecord = timer.read();
        }

        // Ch: seek
        //TODO Fix
        //Ok, so when we seek, everything should come to a halt, be cleared, and resumed afresh.
        //Vs., when the end of stuffs happen, the changes should just be piled on to queued data
        boolean seeked = false; // So the playback section knows whether to add partial and advance
        seek:
        if (seekSecondsInput.pending()) {
          seeked = true;
          int seekSeconds = seekSecondsInput.read();
          //TODO Examine this closer once "mRecording" is more of a thing
          if (mRecording) {
            //TODO Tweak for things already/not yet played?; buffering?
            capIntervalReference();
          }
          if (seekSeconds == Integer.MAX_VALUE) {
            stopPlayback();
          } else {
            // Seek
            if (mPositions.size() > 0) {
              // Clear out all but the main position
              int curPos = mPositions.get(0);
              mPositions.clear();
              mPositions.add(curPos);
            }
            int seekChunks = (seekSeconds * SAMPLE_RATE) / CHUNK_SIZE;
            if (0 <= seekSeconds) {
              // Skipping forward
              if (mPlaying) { // Can't start playing from the future
                int newPos = mPositions.get(0) + seekChunks;
                if (newPos < 0) {
                  newPos = 0;
                }
                if (newPos >= mProject.mic.size()) {
                  // We've hit the leading edge of recording
                  stopPlayback();
                } else {
                  mPositions.set(0, newPos);
                  mTrack.pause();
                  mTrack.flush();
                  //mTrack.play();
                  clearOverflow();
                }
              }
            } else {
              // Skipping backward
              if (!mPlaying) {
                // Extract into startPlayback?
                mPositions.add(mProject.mic.size()); //TODO Between reading and writing?
                mPlaying = true; // Well, technically not true for another few lines, maybe
              }
              int newPos = mPositions.get(0) + seekChunks;
              if (newPos < 0) {
                newPos = 0;
              }
              mPositions.set(0, newPos);
              mTrack.pause();
              mTrack.flush();
              //mTrack.play();
              clearOverflow();
            }
            // If we've ended up playing somewhere new, now, make a new ReferenceInterval for it
            if (mPlaying) {
              //TODO Does it matter that we're between reading (+1) and writing (=0)?
              //TODO Wait wat
              if (mRecording) {
                //TODO I swear that -1 should not be there
                IntervalReference ref = new IntervalReference(mPositions.get(0), mProject.mic.size(), IntervalReference.INFINITY); //TODO Off-by-one errors!!!
                mProject.playbacks.add(ref);
              }
              updateRecursivePlayback();
            }
          }
        }

        // Play audio
        if (mPlaying) {
          //TODO Do asynchronously??  Skip the part we already know won't be used?
          final int lookahead = 6;
          boolean hitBufferCap = false;
          for (int s = (seeked ? 1 : lookahead); s <= lookahead; s++) { // Do an extra time if seeked
            if (mSafeChunksLeft <= 0) {
              updateRecursivePlayback();
            }
            logClock(1, timer.read(), "updateRecursivePlayback: ");
            boolean hitEnd = false;
            AudioChunk sumChunk = new AudioChunk(CHUNK_SIZE);
            for (int i = 0; i < mPositions.size(); i++) {
              int pos = mPositions.get(i);
              if (pos >= mProject.mic.size()) {
                Log.e(TAG, "Playback hit the end of the recording; that shouldn't currently happen!");
                hitEnd = true;
                break;
              }
              AudioChunk chunk = mProject.mic.get(pos);
              long start = System.currentTimeMillis();
              for (int j = 0; j < CHUNK_SIZE; j++) {
                //TODO Check clipping?
                sumChunk.data[j] += chunk.data[j];
              }
              long end = System.currentTimeMillis();
              Log.d(TAG, "Time to sum: " + (end - start));
              mPositions.set(i, pos + 1);
            }
            logClock(1, timer.read(), "Time to sum all: ");
            if (!fillOverflow(sumChunk.data, 0, CHUNK_SIZE)) {
              throw new RuntimeException("Failed to buffer all the samples!");
            }
            mSafeChunksLeft--;
            for (Integer pos : upcomingPositions(mPositions.get(0), 2)) {
              if (pos < mProject.mic.size()) {
                mProject.mic.cache(pos);
              }
            }
            logClock(1, timer.read(), "time to request cache: ");
            if (hitEnd) {
              //TODO Should THIS cap the IntervalReference?
              stopPlayback();
            }
          }
          if (seeked) {
            int discard = (int) ((timer.read() - postRecord + 125) * SAMPLE_RATE / 1000.0);
            System.out.println("Discarding " + discard);
            discardOverflow(discard);
            logClock(1, timer.read(), "discardOverflow: ");
            if (!playOverflow(TRACK_BUFFER / SAMPLE_BYTES)) {
              throw new RuntimeException("Failed to play all the samples!");
            }
          } else {
            if (!playOverflow(CHUNK_SIZE)) {
              throw new RuntimeException("Failed to play all the samples!");
            }
          }
          logClock(1, timer.read(), "playOverflow: ");
          if (mTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            //TODO Not convinced this won't incur delay
            Log.d(TAG, "Playing");
            mTrack.play();
          }
          logClock(1, timer.read(), "getPlayState/play: ");
        }

        //TODO stopRecord, resumeRecord
        //TODO When resumeRecord, restore playback position

        //TODO SketchDAWCallsChannel
        switch (callsAlt.priSelect()) {
          case 0: // Call
            int select = sketchDAWCallsChannel.accept(this);
            //TODO Do we do anything???
            break;
          default: // Skip
            break;
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      cleanup();
    }
    Log.d(TAG, "Shutdown");
  }

  /**
   * Attempts to empty `count` samples of the overflow into mTrack.  Returns true if it emptied that many samples, otherwise false.
   * @return
   */
  protected boolean playOverflow(int count) {
    logClock(2, System.currentTimeMillis(), "playOverflow start: ");
    int toEnd = mOverflow.length - mOverflowStart;
    boolean truncated = false;
    if (count > mOverflowLength) {
      System.out.println("playOverflow count " + count + " -> " + mOverflowLength);
      count = mOverflowLength;
      truncated = true;
    } else {
      System.out.println("playOverflow count " + count);
    }
    if (count > toEnd) {
      // Write toEnd
      int writeCount = mTrack.write(mOverflow, mOverflowStart, toEnd);
      logClock(2, System.currentTimeMillis(), "playOverflow 1 writeCount " + writeCount + "/" + toEnd + ": ");
      if (writeCount < 0) {
        throw new RuntimeException("Failed to write audio, with error: " + writeCount);
      }
      Log.d(TAG, "Wrote: " + writeCount);
      if (writeCount != toEnd) {
        mOverflowStart += writeCount;
        mOverflowLength -= writeCount;
        return false;
      }
      mOverflowStart = 0;
      mOverflowLength -= writeCount;
      count -= writeCount;

      // Start from beginning
      writeCount = mTrack.write(mOverflow, mOverflowStart, count);
      logClock(2, System.currentTimeMillis(), "playOverflow 2 writeCount " + writeCount + "/" + count + ": ");
      if (writeCount < 0) {
        throw new RuntimeException("Failed to write audio, with error: " + writeCount);
      }
      Log.d(TAG, "Wrote: " + writeCount);
      mOverflowStart += writeCount;
      mOverflowLength -= writeCount;
      if (writeCount != count) {
        return false;
      }
      return !truncated;
    } else if (count < toEnd) {
      int writeCount = mTrack.write(mOverflow, mOverflowStart, count);
      logClock(2, System.currentTimeMillis(), "playOverflow 3 writeCount " + writeCount + "/" + count + ": ");
      if (writeCount < 0) {
        throw new RuntimeException("Failed to write audio, with error: " + writeCount);
      }
      Log.d(TAG, "Wrote: " + writeCount);
      mOverflowStart += writeCount;
      mOverflowLength -= writeCount;
      if (writeCount != count) {
        return false;
      }
      return !truncated;
    } else {
      // They happen to be exactly equal
      int writeCount = mTrack.write(mOverflow, mOverflowStart, count);
      logClock(2, System.currentTimeMillis(), "playOverflow 4 writeCount " + writeCount + "/" + count + ": ");
      if (writeCount < 0) {
        throw new RuntimeException("Failed to write audio, with error: " + writeCount);
      }
      Log.d(TAG, "Wrote: " + writeCount);
      mOverflowStart += writeCount;
      mOverflowLength -= writeCount;
      if (writeCount != count) {
        return false;
      }
      return !truncated;
    }
  }

  protected boolean fillOverflow(short[] data, int pos, int count) {
    boolean truncated = false;
    int remainingSpace = mOverflow.length - mOverflowLength;
    if (remainingSpace < count) {
      count = remainingSpace;
      truncated = true;
    }
    int curPos = (mOverflowStart + mOverflowLength) % mOverflow.length;
    int nextPos = curPos + count;
    if (nextPos > mOverflow.length) {
      // Gonna have to split it up
      int firstLength = mOverflow.length - curPos;
      System.arraycopy(data, pos, mOverflow, curPos, firstLength);
      mOverflowLength += firstLength;

      count -= firstLength;
      curPos = 0;

      System.arraycopy(data, pos, mOverflow, curPos, count);
      mOverflowLength += count;
    } else {
      // Exactly equal doesn't get a special handling, here
      System.arraycopy(data, pos, mOverflow, curPos, count);
      mOverflowLength += count;
    }

    return !truncated;
  }

  protected boolean discardOverflow(int count) {
    if (count > mOverflowLength) {
      clearOverflow();
      return false;
    } else {
      mOverflowStart = (mOverflowStart + count) % mOverflow.length;
      mOverflowLength -= count;
      if (mOverflowLength == 0) {
        mOverflowStart = 0; // Might as well, maybe
      }
      return true;
    }
  }

  protected void clearOverflow() {
    mOverflowStart = 0;
    mOverflowLength = 0;
  }

  //TODO Would this be clearer inlined?  Hmm
  protected void stopPlayback() {
    mTrack.pause();
    mTrack.flush();
    clearOverflow();
    mPositions.clear();
    mSafeChunksLeft = Integer.MAX_VALUE;
    mPlaying = false;
    //TODO This may be redundant with all the calling code
    capIntervalReference();
  }

  protected void capIntervalReference() {
    //TODO Would it be better to keep a reference to the active IntervalReference?
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
    if (!mPlaying) {
      mSafeChunksLeft = Integer.MAX_VALUE;
      return;
    }
    //TODO This seems slightly roundabout
    LinkedList<Integer> newPositions = mProject.getRecursivePlaybackPositions(mPositions.get(0));
    mSafeChunksLeft = newPositions.removeLast();
    newPositions.addFirst(mPositions.get(0));
    mPositions.clear();
    mPositions.addAll(newPositions);
  }

  /**
   * Returns all positions involved in playing the chunks in
   * start <= i < start+lookahead
   * @param start
   * @param lookahead
   * @return
   */
  protected HashSet<Integer> upcomingPositions(int start, int lookahead) {
    HashSet<Integer> upcoming = new HashSet<>();
    for (int i = 0; i < lookahead; i++) {
      LinkedList<Integer> newPositions = mProject.getRecursivePlaybackPositions(start + i);
      newPositions.removeLast();
      upcoming.addAll(newPositions);
      upcoming.add(start + i);
    }
    return upcoming;
  }

  @Override
  public int getPos() {
    return mProject.mic.size();
  }

  @Override
  public SketchProject exportProject() {
    return mProject;
  }

  @Override
  public void importProject(SketchProject project) {
    //TODO Fix
    Log.e(TAG, "IMPORT PROJECT IS CURRENTLY A HACK");
    mProject = project;
    mPlaying = false;
    mRecording = true;
    mPositions.clear();
    mTrack.pause();
    mTrack.flush();
    clearOverflow();
    capIntervalReference();
    mSafeChunksLeft = Integer.MAX_VALUE;
  }

  protected HashMap<Integer, Long> lastTimes = new HashMap<Integer, Long>();
  protected void logClock(int category, long time, String text) {
    Long lastTime = lastTimes.get(category);
    if (lastTime == null) {
      lastTime = time;
    }
    Log.d(TAG, category + " " + text + (time - lastTime) + "ms");
    lastTimes.put(category, time);
  }
}
