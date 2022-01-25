package com.googleresearch.recsyncar;

import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoHelpers {


    public VideoHelpers() {

    }

    public static final String TAG = "com.googleresearch.capturesync.VideoHelpers";
    public static final String SUBDIR_NAME = "RecSync";

    protected String lastTimeStamp;
    public String getLastVideoPath() {
        return lastVideoPath;
    }

    private String lastVideoPath;

    protected Integer lastVideoSeqId;

    protected void createRecorderSurface(Surface surface) throws IOException {
        surface = MediaCodec.createPersistentInputSurface();

        MediaRecorder recorder = setUpMediaRecorder(surface, false);
        recorder.prepare();
        recorder.release();
        deleteUnusedVideo();
    }

    public void deleteUnusedVideo() {
        String videoPath = getLastVideoPath();
        File videoFile = new File(videoPath);
        boolean result = videoFile.delete();
        if (!result) {
            Log.d(TAG, "Video file could not be deleted");
        }
    }

    protected MediaRecorder setUpMediaRecorder(Surface surface) throws IOException {
        return setUpMediaRecorder(surface, true);
    }

    protected MediaRecorder setUpMediaRecorder(Surface surface, boolean specifyOutput) throws IOException {
        MediaRecorder recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        lastVideoPath = getOutputMediaFilePath();
        recorder.setOutputFile(lastVideoPath);

        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        recorder.setVideoEncodingBitRate(profile.videoBitRate);

        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setInputSurface(surface);
        return recorder;
    }

    /**
     * Create directory and return file
     * returning video file
     */
    protected String getOutputMediaFilePath() throws IOException {

        File sdcard = Environment.getExternalStorageDirectory();

        Path dir = Files.createDirectories(Paths.get(sdcard.getAbsolutePath(), SUBDIR_NAME, "VID"));

        lastTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        String mediaFile;
        mediaFile = dir.toString() + File.separator + "VID_" + lastTimeStamp + ".mp4";
        return mediaFile;

    }
}
