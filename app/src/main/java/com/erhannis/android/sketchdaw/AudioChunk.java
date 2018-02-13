package com.erhannis.android.sketchdaw;

/**
 * Represents a chunk of audio samples.
 *
 * //TODO Allow compression?
 * Created by erhannis on 2/13/18.
 */

public class AudioChunk {
  public final short[] data;

  public AudioChunk(int chunkLength) {
    data = new short[chunkLength];
  }

  public AudioChunk(short[] data) {
    this.data = data;
  }
}
