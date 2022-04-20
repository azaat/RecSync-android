package com.googleresearch.capturesync;

import java.io.File;
import java.util.ArrayDeque;

public interface FrameInfo {
    public ArrayDeque<SynchronizedFrame> getLatestFrames();

    public void displayStreamFrame(SynchronizedFrame streamFrame);

    public File getExternalDir();
}
