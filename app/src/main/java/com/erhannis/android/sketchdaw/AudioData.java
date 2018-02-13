package com.erhannis.android.sketchdaw;

import org.jcsp.lang.Crew;

import java.util.ArrayList;

/**
 * Represents an audio track.  Stores track data as chunks of samples.
 * You can tack chunks onto the end, and request a chunk from the middle.
 *
 * Created by erhannis on 2/13/18.
 */

public class AudioData {
  protected ArrayList<AudioChunk> chunks = new ArrayList<>();

  protected Crew lock = new Crew();

  public AudioData() {
  }

  public void add(AudioChunk chunk) {
    try {
      lock.startWrite();
      chunks.add(chunk);
    } finally {
      lock.endWrite();
    }
  }

  public AudioChunk get(int pos) {
    try {
      lock.startRead();
      return chunks.get(pos);
    } finally {
      lock.endRead();
    }
  }

  public int size() {
    try {
      lock.startRead();
      return chunks.size();
    } finally {
      lock.endRead();
    }
  }

  public void clear() {
    try {
      lock.startWrite();
      chunks.clear();
    } finally {
      lock.endWrite();
    }
  }
}
