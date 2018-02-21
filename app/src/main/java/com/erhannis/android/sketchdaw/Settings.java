package com.erhannis.android.sketchdaw;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by erhannis on 2/20/18.
 */

public class Settings {
  public static final SimpleDateFormat FILENAME_DATE_FORMATTER = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss", Locale.US);

  public static File getDefaultCacheLocation() {
    //TODO Optionize
    return new File("/sdcard/SketchDAWProjects");
  }
}
