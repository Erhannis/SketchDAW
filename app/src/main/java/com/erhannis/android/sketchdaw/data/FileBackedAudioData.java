package com.erhannis.android.sketchdaw.data;

import android.support.annotation.NonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jcsp.lang.Crew;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Audio data, backed by a file.
 * It will attempt to keep/have relevant data cached.
 *
 * Created by erhannis on 2/13/18.
 *
 * Licensed under Apache License 2.0
 *
 */
public class FileBackedAudioData implements AudioData {
  protected static final ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
    @Override
    public Thread newThread(@NonNull Runnable runnable) {
      Thread t = new Thread(runnable);
      t.setDaemon(true);
      return t;
    }
  });

  //TODO Make some kind of AudioData wrapper?
  protected Crew lock = new Crew();

  protected final AudioDataFile file;
  protected final LoadingCache<Integer, AudioChunk> chunks;

  public FileBackedAudioData(final AudioDataFile file) {
    this.file = file;
    this.chunks = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<Integer, AudioChunk>() {
                      public AudioChunk load(Integer pos) throws IOException {
                        return file.getChunk(pos);
                      }
                    });
  }

  @Override
  public void add(AudioChunk chunk) {
    try {
      lock.startWrite();
      int pos = file.getChunkCount();
      file.putChunk(pos, chunk);
      chunks.put(pos, chunk);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      lock.endWrite();
    }
  }

  @Override
  public AudioChunk get(int pos) {
    try {
      lock.startRead();
      return chunks.get(pos);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    } finally {
      lock.endRead();
    }
  }

  @Override
  public int size() {
    try {
      lock.startRead();
      return (int) file.getChunkCount();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      lock.endRead();
    }
  }

  @Override
  public void clear() {
    try {
      lock.startWrite();
      file.clearChunks();
      chunks.invalidateAll();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      lock.endWrite();
    }
  }

  public void cache(final int pos) {
    stpe.execute(new Runnable() {
      @Override
      public void run() {
        try {
          lock.startRead();
          chunks.get(pos);
        } catch (ExecutionException e) {
          throw new RuntimeException(e);
        } finally {
          lock.endRead();
        }
      }
    });
  }
}
