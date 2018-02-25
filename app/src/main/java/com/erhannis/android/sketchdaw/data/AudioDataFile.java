package com.erhannis.android.sketchdaw.data;

import android.util.Log;

import com.erhannis.android.sketchdaw.jcsp.SketchDAWProcess;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * I'm undecided whether this should just hold audio data or project data.
 * If project data, I've gotta deal with mixing different types of data in a file I'm streaming
 * to, which seems painful.
 *
 * Starting with raw audio data, for now.
 *
 * //TODO Should store/show/give chunk count?
 *
 * Created by erhannis on 2/20/18.
 */

public class AudioDataFile {
  private static final String TAG = "AudioDataFile";
  protected final RandomAccessFile file;
  protected final int chunkSize;
  protected final AtomicInteger chunkCount;
  protected final byte[] bucket;

  public AudioDataFile(RandomAccessFile file) throws IOException {
    //TODO Is it possible to read from multiple places at once?
    this.file = file;
    this.chunkSize = SketchDAWProcess.CHUNK_SIZE; //TODO Allow change?
    this.chunkCount = new AtomicInteger((int) (file.length() / (chunkSize * 2)));
    this.bucket = new byte[2 * chunkSize];
  }

  public synchronized void putChunk(int pos, AudioChunk chunk) throws IOException {
    file.seek(pos * chunkSize * 2);
    int i = 0;
    for (short s : chunk.data) {
      bucket[i++] = (byte)((s >> 8) & 0xFF);
      bucket[i++] = (byte)(s & 0xFF);
    }
    file.write(bucket);
  }

  public synchronized AudioChunk getChunk(int pos) throws IOException {
    Log.d(TAG, "getChunk from file @ " + pos);
    file.seek(pos * chunkSize * 2);
    AudioChunk chunk = new AudioChunk(chunkSize);
    int read = file.read(bucket);
    if (read != bucket.length) {
      throw new IOException("Read " + read + " bytes, expected " + bucket.length);
    }
    for (int i = 0; i < chunkSize; i++) {
      chunk.data[i] = (short)(((bucket[i*2] & 0xFF) << 8) | (bucket[(i*2)+1] & 0xFF));
    }
    return chunk;
  }

  public synchronized int getChunkCount() {
    return chunkCount.get();
  }
}
