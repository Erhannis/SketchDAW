package com.erhannis.android.sketchdaw;

/**
 * Represents a playback reference - a section of audio taken from one spot and played at another.
 *
 * Created by erhannis on 2/13/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public class IntervalReference {
  //TODO Could include reference to source and/or dest audio....
  /**
   * Where to read from
   */
  public int sourceStart;

  /**
   * Where to "write" to
   */
  public int destStart;

  /**
   * How long the interval lasts.
   * Note, for technicality, that included points include start<=points<start+length,
   * i.e., it includes the start point and excludes the point at start+length.
   */
  public int duration;

  public IntervalReference(int sourceStart, int destStart, int duration) {
    this.sourceStart = sourceStart;
    this.destStart = destStart;
    this.duration = duration;
  }
}
