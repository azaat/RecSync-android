/*
 * Copyright 2019 The Google Research Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googleresearch.capturesync;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.azaat.smstereo.OnSyncFrameAvailable;
import com.googleresearch.capturesync.softwaresync.TimeDomainConverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/** A class that processes frames on its own thread. */
public class ResultProcessor {
  private static final String TAG = "ResultProcessor";

  private final Handler handler;
  private final CameraActivity context;
  private final OnSyncFrameAvailable onSyncFrameAvailableListener;
  private final TimeDomainConverter timeDomainConverter;
  private final RenderScript mRenderScript;
  private final ScriptIntrinsicYuvToRGB mYuvToRgb;
  private final int jpgQuality;

  public ResultProcessor(
      TimeDomainConverter timeDomainConverter,
      CameraActivity context,
      boolean saveJpgFromYuv,
      OnSyncFrameAvailable onSyncFrameAvailableListener,
      int jpgQuality) {
    this.timeDomainConverter = timeDomainConverter;
    this.context = context;
    // Copy from constants... make it a user parameter.
    this.jpgQuality = jpgQuality;
    this.onSyncFrameAvailableListener = onSyncFrameAvailableListener;
    HandlerThread thread = new HandlerThread(TAG);
    thread.start();
    // getLooper() blocks until the thread started and its Looper is prepared.
    handler = new Handler(thread.getLooper());
    mRenderScript = RenderScript.create(context);
    mYuvToRgb = ScriptIntrinsicYuvToRGB.create(mRenderScript, Element.U8_4(mRenderScript));
  }

  /** Submit a request to process a Frame on the processor's thread. */
    public void submitProcessRequest(Frame capture, String filename) {
    handler.post(() -> processStill(capture, filename));
  }

  private void processStill(final Frame frame, String basename) {
    File captureDir = new File(basename);
    if (!captureDir.exists() && !captureDir.mkdirs()) {
      throw new IllegalStateException("Could not create dir " + captureDir);
    }
    // Timestamp in local domain ie. time since boot in nanoseconds.
    long localSensorTimestampNs = frame.result.get(CaptureResult.SENSOR_TIMESTAMP);
    // Timestamp in leader domain ie. synchronized time on leader device in nanoseconds.
    long syncedSensorTimestampNs =
        timeDomainConverter.leaderTimeForLocalTimeNs(localSensorTimestampNs);
    String filenameTimeString = syncedSensorTimestampNs + "_img";

    for (int i = 0; i < frame.output.images.size(); ++i) {
      Image image = frame.output.images.get(i);
      int format = image.getFormat();
      if (format == ImageFormat.YUV_420_888) {

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        byte[] nv21 = Yuv420ImageToNv21(image);
//        YuvImage yuvImage = yuvImageFromNv21Image(image);

        File jpgFile = new File(captureDir, filenameTimeString + ".jpg");
        // Push saving JPEG onto queue to let the frame close faster, necessary for some devices.
        handler.post(
                () -> {
                  Bitmap bitmap = yuv420ToBitmap(nv21, imageWidth, imageHeight);
                  onSyncFrameAvailableListener.onSyncFrameAvailable(new SynchronizedFrame(bitmap, syncedSensorTimestampNs));
//                  saveJpg(yuvImage, jpgFile);
                }
        );
      } else {
        Log.e(TAG, String.format("Cannot save unsupported image format: %d", image.getFormat()));
      }
    }

    frame.close();
  }

  /**
   * Converts byte array with NV21 data to Bitmap using yuvToRgb Renderscript intrinsic
   */
  public Bitmap yuv420ToBitmap(byte[] imageData, int width, int height) {
    Allocation aIn = Allocation.createSized(mRenderScript, Element.U8(mRenderScript), imageData.length, Allocation.USAGE_SCRIPT);
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Allocation aOut = Allocation.createFromBitmap(mRenderScript, bitmap);
    aIn.copyFrom(imageData);
    mYuvToRgb.setInput(aIn);
    mYuvToRgb.forEach(aOut);
    aOut.copyTo(bitmap);
    aOut.destroy();
    aIn.destroy();

    return bitmap;
  }

  // Method taken from this answer:
  // https://stackoverflow.com/questions/44022062/converting-yuv-420-888-to-jpeg-and-saving-file-results-distorted-image
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public static byte[] Yuv420ImageToNv21(Image image) {
    Rect crop = image.getCropRect();
    int format = image.getFormat();
    int width = crop.width();
    int height = crop.height();
    Image.Plane[] planes = image.getPlanes();
    byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
    byte[] rowData = new byte[planes[0].getRowStride()];

    int channelOffset = 0;
    int outputStride = 1;
    for (int i = 0; i < planes.length; i++) {
      switch (i) {
        case 0:
          channelOffset = 0;
          outputStride = 1;
          break;
        case 1:
          channelOffset = width * height + 1;
          outputStride = 2;
          break;
        case 2:
          channelOffset = width * height;
          outputStride = 2;
          break;
      }

      ByteBuffer buffer = planes[i].getBuffer();
      int rowStride = planes[i].getRowStride();
      int pixelStride = planes[i].getPixelStride();

      int shift = (i == 0) ? 0 : 1;
      int w = width >> shift;
      int h = height >> shift;
      buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
      for (int row = 0; row < h; row++) {
        int length;
        if (pixelStride == 1 && outputStride == 1) {
          length = w;
          buffer.get(data, channelOffset, length);
          channelOffset += length;
        } else {
          length = (w - 1) * pixelStride + 1;
          buffer.get(rowData, 0, length);
          for (int col = 0; col < w; col++) {
            data[channelOffset] = rowData[col * pixelStride];
            channelOffset += outputStride;
          }
        }
        if (row < h - 1) {
          buffer.position(buffer.position() + rowStride - length);
        }
      }
    }
    return data;
  }

  private static boolean saveNv21(Image yuvImage, File nv21File, File nv21metadataFile) {
    long t0 = System.nanoTime();

    Image.Plane[] planes = yuvImage.getPlanes();
    Image.Plane luma = planes[0];
    Image.Plane chromaU = planes[1];
    Image.Plane chromaV = planes[2];

    int width = yuvImage.getWidth();
    int height = yuvImage.getHeight();

    // Luma should be tightly packed.
    assert (luma.getPixelStride() == 1);
    // TODO(jiawen): Consider relaxing this restriction and write row by row, skipping the row
    // padding. This requires looping over the luma plane one row at a time.
    assert (luma.getRowStride() == width);

    assert (chromaU.getPixelStride() == 2);
    assert (chromaU.getRowStride() == width);
    assert (chromaV.getPixelStride() == 2);
    assert (chromaV.getRowStride() == width);

    ByteBuffer lumaBuffer = luma.getBuffer().duplicate();
    ByteBuffer chromaUBuffer = chromaU.getBuffer().duplicate();
    ByteBuffer chromaVBuffer = chromaV.getBuffer().duplicate();

    assert (lumaBuffer.capacity() == width * height);
    assert (chromaUBuffer.capacity() + 1 == width * height / 2);
    assert (chromaVBuffer.capacity() + 1 == width * height / 2);

    {
      // Set chromaUBuffer's position to the last byte. slice() will make a new buffer that's a
      // view
      // of the last byte. Send that last byte to FileChannel.
      ByteBuffer chromaUBufferCopy = chromaUBuffer.duplicate();
      chromaUBufferCopy.position(chromaUBufferCopy.capacity() - 1);
      ByteBuffer lastChromaUByte = chromaUBufferCopy.slice();

      try (FileOutputStream outputStream = new FileOutputStream(nv21File)) {
        FileChannel outputChannel = outputStream.getChannel();

        outputChannel.write(lumaBuffer);
        // The V buffer contains the U data since it's arranged VUVUVUVU...
        // It contains all but the last U byte.
        outputChannel.write(chromaVBuffer);
        outputChannel.write(lastChromaUByte);
      } catch (IOException e) {
        // TODO(jiawen,samansari): Toast.
        Log.w(TAG, "Error saving YUV image to: " + nv21File.getAbsolutePath());
        return false;
      }
    }

    // Save NV21 metadata.
    {
      try (PrintWriter writer = new PrintWriter(nv21metadataFile, UTF_8.name())) {
        writer.printf("width: %d\n", width);
        writer.printf("height: %d\n", height);
        writer.printf("pixel_format: NV21 (tightly packed)\n");
        writer.printf("luma_buffer_bytes: %d\n", lumaBuffer.capacity());
        writer.printf("interleaved_chroma_buffers_bytes: %d\n", chromaVBuffer.capacity() + 1);
      } catch (IOException e) {
        // TODO(jiawen,samansari): Toast.
        Log.w(TAG, "Error saving metadata to: " + nv21metadataFile.getAbsolutePath());
        return false;
      }
    }

    long t1 = System.nanoTime();
    Log.i(TAG, String.format("saveNv21 took %f ms.", (t1 - t0) * 1e-6f));

    return true;
  }

  private boolean saveJpg(YuvImage yuvImage, File jpgFile) {
    // Save JPEG and also add to the photos gallery by inserting into MediaStore.
    long t0 = System.nanoTime();
    if (saveJpg(yuvImage, jpgQuality, jpgFile)) {
      try {
        MediaStore.Images.Media.insertImage(
            context.getContentResolver(),
            jpgFile.getAbsolutePath(),
            jpgFile.getName(),
            "Full path: " + jpgFile.getAbsolutePath());
      } catch (FileNotFoundException e) {
        Log.e(TAG, "Unable to find file to link in media store.");
      }
      long t1 = System.nanoTime();
      Log.i(TAG, String.format("Saving JPG to disk took %f ms.", (t1 - t0) * 1e-6f));
      return true;
    }
    return false;
  }

  private static boolean saveJpg(YuvImage src, int quality, File file) {
    long t0 = System.nanoTime();
    try (FileOutputStream outputStream = new FileOutputStream(file)) {
      Rect rect = new Rect(0, 0, src.getWidth(), src.getHeight());
      boolean ok = src.compressToJpeg(rect, quality, outputStream);
      if (!ok) {
        Log.w(TAG, "Error saving JPEG to: " + file.getAbsolutePath());
      }
      long t1 = System.nanoTime();
      Log.i(TAG, String.format("saveJpg took %f ms.", (t1 - t0) * 1e-6f));
      return ok;
    } catch (IOException e) {
      Log.w(TAG, "Error saving JPEG image to: " + file.getAbsolutePath());
      return false;
    }
  }

  // Utility method to convert an NV21 android.media.Image to an android.graphics.YuvImage. The
  // latter is just a wrapper around a byte[] but can compress to JPEG.
  private static YuvImage yuvImageFromNv21Image(Image src) {
    long t0 = System.nanoTime();

    Image.Plane[] planes = src.getPlanes();
    Image.Plane luma = planes[0];
    Image.Plane chromaU = planes[1];
    Image.Plane chromaV = planes[2];

    int width = src.getWidth();
    int height = src.getHeight();

    // Luma should be tightly packed and chroma should be tightly interleaved.
    assert (luma.getPixelStride() == 1);
    assert (chromaU.getPixelStride() == 2);
    assert (chromaV.getPixelStride() == 2);

    // Duplicate (shallow copy) each buffer so as to not disturb the underlying position/limit/etc.
    ByteBuffer lumaBuffer = luma.getBuffer().duplicate();
    ByteBuffer chromaUBuffer = chromaU.getBuffer().duplicate();
    ByteBuffer chromaVBuffer = chromaV.getBuffer().duplicate();

    // Yes, y, v, then u since it's NV21.
    int[] yvuRowStrides =
        new int[] {luma.getRowStride(), chromaV.getRowStride(), chromaU.getRowStride()};

    // Compute bytes needed to concatenate all the (potentially padded) YUV data in one buffer.
    int lumaBytes = height * luma.getRowStride();
    int interleavedChromaBytes = (height / 2) * chromaV.getRowStride();
    assert (lumaBuffer.capacity() == lumaBytes);
    int packedYVUBytes = lumaBytes + interleavedChromaBytes;
    byte[] packedYVU = new byte[packedYVUBytes];

    int packedYVUOffset = 0;
    lumaBuffer.get(
        packedYVU,
        packedYVUOffset,
        lumaBuffer.capacity()); // packedYVU[0..lumaBytes) <-- lumaBuffer.
    packedYVUOffset += lumaBuffer.capacity();

    // Write the V buffer. Since the V buffer contains U data, write all of V and then check how
    // much U data is left over. There be at most 1 byte plus padding.
    chromaVBuffer.get(packedYVU, packedYVUOffset, /*length=*/ chromaVBuffer.capacity());
    packedYVUOffset += chromaVBuffer.capacity();

    // Write the remaining portion of the U buffer (if any).
    int chromaUPosition = chromaVBuffer.capacity() - 1;
    if (chromaUPosition < chromaUBuffer.capacity()) {
      chromaUBuffer.position(chromaUPosition);

      int remainingBytes = Math.min(chromaUBuffer.remaining(), lumaBytes - packedYVUOffset);

      if (remainingBytes > 0) {
        chromaUBuffer.get(packedYVU, packedYVUOffset, remainingBytes);
      }
    }
    YuvImage yuvImage = new YuvImage(packedYVU, ImageFormat.NV21, width, height, yvuRowStrides);

    long t1 = System.nanoTime();
    Log.i(TAG, String.format("yuvImageFromNv212Image took %f ms.", (t1 - t0) * 1e-6f));

    return yuvImage;
  }

  private static String getTimeStr(long timestampMs) {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
    simpleDateFormat.setTimeZone(TimeZone.getDefault());
    return simpleDateFormat.format(timestampMs);
  }

  // Save metadata.
  private static void saveTimingMetadata(
      long leaderSensorTimestamp, long localSensorTimestamp, File metaFile) {
    try (PrintWriter writer = new PrintWriter(metaFile, UTF_8.name())) {
      writer.printf("leader_sensor_timestamp_ns: %d\n", leaderSensorTimestamp);
      writer.printf("local_sensor_timestamp_ns: %d\n", localSensorTimestamp);
    } catch (IOException e) {
      Log.e(TAG, "Error saving timing metadata to: " + metaFile.getAbsolutePath());
      return;
    }
    Log.v(TAG, "Saved timing metadata to: " + metaFile.getAbsolutePath());
  }
}
