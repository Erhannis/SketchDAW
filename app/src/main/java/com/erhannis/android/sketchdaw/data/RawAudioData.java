package com.erhannis.android.sketchdaw.data;

import com.erhannis.android.sketchdaw.data.AudioData;

import org.jcsp.lang.Crew;

import java.util.ArrayList;

/**
 * Raw audio data.  Stores track data as chunks of samples, uncompressed.
 * You can tack chunks onto the end, and request a chunk from the middle.
 *
 * Created by erhannis on 2/13/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public class RawAudioData implements AudioData {
  protected ArrayList<AudioChunk> chunks = new ArrayList<>();

  protected Crew lock = new Crew();

  public RawAudioData() {
  }

  @Override
  public void add(AudioChunk chunk) {
    try {
      lock.startWrite();
      chunks.add(chunk);
    } finally {
      lock.endWrite();
    }
  }

  @Override
  public AudioChunk get(int pos) {
    try {
      lock.startRead();
      return chunks.get(pos);
    } finally {
      lock.endRead();
    }
  }

  @Override
  public int size() {
    try {
      lock.startRead();
      return chunks.size();
    } finally {
      lock.endRead();
    }
  }

  @Override
  public void clear() {
    try {
      lock.startWrite();
      chunks.clear();
    } finally {
      lock.endWrite();
    }
  }
}
