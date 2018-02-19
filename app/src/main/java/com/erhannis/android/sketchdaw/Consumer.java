package com.erhannis.android.sketchdaw;

/**
 * Created by erhannis on 2/18/18.
 */

public interface Consumer<T> {
  public void accept(T value);
}
