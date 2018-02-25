package com.erhannis.android.sketchdaw.data;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.jcsp.lang.Crew;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
  private static final String TAG = "FileBackedAudioData";

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

  protected final AtomicInteger chunkCount;
  protected final AudioDataFile file;
  protected final ConcurrentHashMap<Integer, AudioChunk> pendingChunks;
  protected final LoadingCache<Integer, AudioChunk> chunks;

  public FileBackedAudioData(final AudioDataFile file) {
    this.file = file;
    this.chunkCount = new AtomicInteger(file.getChunkCount());
    this.pendingChunks = new ConcurrentHashMap<Integer, AudioChunk>();
    this.chunks = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<Integer, AudioChunk>() {
                      public AudioChunk load(Integer pos) throws IOException {
                        AudioChunk chunk = pendingChunks.get(pos);
                        if (chunk != null) {
                          return chunk;
                        } else {
                          return file.getChunk(pos);
                        }
                      }
                    });
  }

  /*
  Things to track:
    Chunk count
    Chunk data
   */

  @Override
  public void add(AudioChunk chunk) {
    try {
      lock.startWrite();
      int pos = chunkCount.getAndIncrement();
      pendingChunks.put(pos, chunk);
      chunks.put(pos, chunk);
      queue(pos, chunk);
    } finally {
      lock.endWrite();
    }
  }

  protected void queue(final int pos, final AudioChunk chunk) {
    stpe.execute(new Runnable() {
      @Override
      public void run() {
        try {
          int c = pendingChunks.size();
          if (c > 10) {
            Log.e(TAG, "File backing is falling behind! Unsaved chunks: " + c);
          }
          file.putChunk(pos, chunk);
          pendingChunks.remove(pos);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
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
      return chunkCount.get();
    } finally {
      lock.endRead();
    }
  }

  @Override
  public void cache(final int pos) {
    // This is not read/write locked, but I THINK it's safe.
    stpe.execute(new Runnable() {
      @Override
      public void run() {
        try {
          chunks.get(pos);
        } catch (ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
