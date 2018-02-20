package com.erhannis.android.sketchdaw.data;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by erhannis on 2/13/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public class SketchProject {
  public AudioData mic;
  /**
   * List of playback references.
   * Keep this sorted by destStart, ascending, or getNextActive won't work right.
   */
  public ArrayList<IntervalReference> playbacks;
  public ArrayList<Tag> tags;

  /**
   * Get the IntervalReference to next become active soonest after the specified dest chunkPos.
   * If more than one fit the criteria, choice is arbitrary.  (but hint hint, is probably the first in
   * the list to match.)
   *
   * WARNING: This assumes that playbacks is sorted by destStart, ascending.
   *
   * (Pure; Threadsafe if playbacks not modified)
   *
   * @param chunkPos
   * @return
   */
  public IntervalReference getNextActive(int chunkPos) {
    for (int i = 0; i < playbacks.size(); i++) {
      IntervalReference ref = playbacks.get(i);
      if (ref.destStart > chunkPos) {
        return ref;
      }
    }
    return null;
  }

  /**
   * Get an IntervalReference active at the specified dest chunkPos, if any exist.
   * Otherwise null.
   * If more than one were active, choice is arbitrary.  (but hint hint, is probably the first in
   * the list to match.)
   *
   * (Pure; Threadsafe if playbacks not modified)
   *
   * @param chunkPos
   * @return
   */
  public IntervalReference getActive(int chunkPos) {
    for (int i = 0; i < playbacks.size(); i++) {
      IntervalReference ref = playbacks.get(i);
      if (ref.destStart <= chunkPos && chunkPos < ref.destStart + ref.duration) {
        return ref;
      }
    }
    return null;
  }

  /**
   * Given a spot to play at, returns:
   * 1. If there are no additional associated playbacks: an empty list
   * 2. Else: a list of all associated playback positions, recursively, and ended with a integer
   *      representing how many chunks you have left before something changes and you need to update
   *      the list and players (a playback ended, a playback started, etc.)
   *
   *
   * (Pure; Threadsafe if playbacks not modified)
   *
   * @param playPos
   * @return
   */
  public LinkedList<Integer> getRecursivePlaybackPositions(int playPos) {
    LinkedList<Integer> results = new LinkedList<>();
    IntervalReference ref = getActive(playPos);
    int nearPos = playPos;
    int soonestChange = Integer.MAX_VALUE;
    // Check playback ends
    while (ref != null) {
      // Must also play at farPos
      int change = ref.destStart + ref.duration - nearPos;
      if (change < soonestChange) {
        soonestChange = change;
      }
      int farPos = ref.sourceStart + (nearPos - ref.destStart);
      results.add(farPos);
      ref = getActive(farPos);
      nearPos = farPos;
    }
    // Check for upcoming new playbacks on the far end
    ref = getNextActive(nearPos);
    if (ref != null) {
      int change = ref.destStart - nearPos;
      if (change < soonestChange) {
        soonestChange = change;
      }
    }
    // Tack on soonestChange
    if (soonestChange < Integer.MAX_VALUE) {
      //TODO This is kindof bleh
      results.add(soonestChange);
    }
    return results;
  }
}
