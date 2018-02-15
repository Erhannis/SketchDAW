package com.erhannis.android.sketchdaw;

import org.jcsp.lang.Any2OneCallChannel;
import org.jcsp.lang.Any2OneChannel;

/**
 * Created by erhannis on 2/15/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public class SketchDAWCallsChannel extends Any2OneCallChannel implements SketchDAWCalls {
  @Override
  public int getPos() {
    join();
    int result = ((SketchDAWCalls) server).getPos();
    selected = GET_POS;
    fork();
    return result;
  }

  /**
   * Returns the current project.
   * WARNING!  To avoid massive data duplication, this is a reference, not an immutable copy!
   * It is recommended that you consider pausing the recording before calling this, and resuming
   * after you have processed the data.  Or, at the very least, be careful about the newest edge
   * of the data.

   * @return Reference to current project
   */
  @Override
  public SketchProject exportProject() {
    join();
    SketchProject result = ((SketchDAWCalls) server).exportProject();
    selected = EXPORT_PROJECT;
    fork();
    return result;
  }

  /**
   * Loads a project.
   * Recording and playback will be stopped.
   *
   * @param project The project to load
   */
  @Override
  public void importProject(SketchProject project) {
    join();
    ((SketchDAWCalls) server).importProject(project);
    selected = IMPORT_PROJECT;
    fork();
  }
}
