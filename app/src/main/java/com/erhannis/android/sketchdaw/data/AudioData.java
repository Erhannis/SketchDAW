package com.erhannis.android.sketchdaw.data;

/**
 * Represents audio data, stored as chunks of samples.
 * You can tack chunks onto the end, request a chunk from the middle,
 * get the chunk count, and clear the data.
 *
 * It is recommended, but not required, that all chunks be of equal length.
 *
 * Created by erhannis on 2/13/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public interface AudioData {
  public void add(AudioChunk chunk);
  public AudioChunk get(int pos);
  public int size();
  public void cache(int pos);
}
