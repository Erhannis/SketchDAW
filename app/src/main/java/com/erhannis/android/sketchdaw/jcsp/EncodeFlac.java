package com.erhannis.android.sketchdaw.jcsp;

import android.support.annotation.NonNull;

import com.erhannis.android.sketchdaw.data.AudioChunk;
import com.erhannis.android.sketchdaw.data.AudioData;
import com.erhannis.android.sketchdaw.misc.Consumer;

import org.jcsp.lang.CSProcess;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.FlacEncoder;
import io.nayuki.flac.encode.RandomAccessFileOutputStream;
import io.nayuki.flac.encode.SubframeEncoder;

/**
 * Created by erhannis on 2/20/18.
 */

public class EncodeFlac {
  protected static final ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
    @Override
    public Thread newThread(@NonNull Runnable runnable) {
      Thread t = new Thread(runnable);
      t.setDaemon(true);
      return t;
    }
  });

  public static void encode(final AudioData data, final int pos, final int count, final Consumer<byte[]> callback) {
    stpe.execute(new Runnable() {
      @Override
      public void run() {
        callback.accept(encode(data, pos, count));
      }
    });
  }

  /**
   * Encode audio chunks as flac data.
   *
   * Assumes at least one data chunk, and all chunks same length.
   *
   * @param data
   * @param pos
   * @param count
   * @return
   */
  protected static byte[] encode(AudioData data, int pos, int count) {
    int sampleRate = 44100;
    int sampleDepth = 16;
    // Parse and check WAV header
    int numChannels = 1;
    int byteRate = 88200;
    int bytesPerSample = sampleDepth / 8;
    if (bytesPerSample * numChannels * sampleRate != byteRate)
      throw new RuntimeException("Invalid byte rate value");

    int[][] samples = new int[numChannels][count * data.get(0).data.length];
    int index = 0;
    int limit = Math.min(data.size(), pos + count);
    for (int i = pos; i < limit; i++) {
      AudioChunk chunk = data.get(i);
      for (int j = 0; j < chunk.data.length; j++) {
        samples[0][index++] = chunk.data[j];
      }
    }

    // Note: There might be chunks after "data", but they can be ignored

    // Open output file and encode samples to FLAC
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      BitOutputStream out = new BitOutputStream(baos);
      out.writeInt(32, 0x664C6143);

      // Populate and write the stream info structure
      StreamInfo info = new StreamInfo();
      info.sampleRate = sampleRate;
      info.numChannels = samples.length;
      info.sampleDepth = sampleDepth;
      info.numSamples = samples[0].length;
      info.md5Hash = StreamInfo.getMd5Hash(samples, sampleDepth);
      info.write(true, out);

      // Encode all frames
      new FlacEncoder(info, samples, 4096, SubframeEncoder.SearchOptions.SUBSET_BEST, out);
      out.flush();

      // Rewrite the stream info metadata block, which is
      // located at a fixed offset in the file by definition
      //raf.seek(4);
      byte[] result = baos.toByteArray();
      ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
      BitOutputStream out2 = new BitOutputStream(baos2);
      info.write(true, out2);
      out2.flush();
      byte[] infoBytes = baos2.toByteArray();
      for (int i = 0; i < infoBytes.length; i++) {
        result[4 + i] = infoBytes[i];
      }
      return result;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
}
