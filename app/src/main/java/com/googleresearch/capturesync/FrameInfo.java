package com.googleresearch.capturesync;

import java.util.ArrayDeque;

public interface FrameInfo {
    ArrayDeque<SynchronizedFrame> getLatestFrames();
}
