package com.erhannis.android.sketchdaw.data;

import android.util.Log;

import com.erhannis.android.sketchdaw.jcsp.SketchDAWProcess;

import java.io.IOException;
import java.io.RandomAccessFile;

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

  public AudioDataFile(RandomAccessFile file) {
    this.file = file;
    this.chunkSize = SketchDAWProcess.CHUNK_SIZE; //TODO Allow change?
  }

  public synchronized void putChunk(int pos, AudioChunk chunk) throws IOException {
    file.seek(pos * chunkSize * 2);
    for (short s : chunk.data) {
      file.writeShort(s);
    }
  }

  public synchronized void clearChunks() throws IOException {
    file.setLength(0);
  }

  public synchronized AudioChunk getChunk(int pos) throws IOException {
    Log.d(TAG, "getChunk from file @ " + pos);
    file.seek(pos * chunkSize * 2);
    AudioChunk chunk = new AudioChunk(chunkSize);
    for (int i = 0; i < chunkSize; i++) {
      chunk.data[i] = file.readShort();
    }
    return chunk;
  }

  public synchronized int getChunkCount() throws IOException {
    return (int) (file.length() / (chunkSize * 2));
  }
}
