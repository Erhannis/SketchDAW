package com.erhannis.android.sketchdaw.jcsp;

import com.erhannis.android.sketchdaw.data.SketchProject;

/**
 * Created by erhannis on 2/15/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public interface SketchDAWCalls {
  public static final int GET_POS = 0;
  public static final int EXPORT_PROJECT = 1;
  public static final int IMPORT_PROJECT = 2;

  /**
   * Gets the current playback position, in chunks.
   * If not currently playing back, returns -1.
   *
   * @return
   */
  public int getPos();

  /**
   * Returns the current project.
   * @return
   */
  public SketchProject exportProject();

  /**
   * Loads a project.
   * @param project The project to load.
   */
  public void importProject(SketchProject project);
}
