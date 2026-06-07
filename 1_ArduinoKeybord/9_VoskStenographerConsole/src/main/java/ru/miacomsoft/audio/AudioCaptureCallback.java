package ru.miacomsoft.audio;

public interface AudioCaptureCallback {
    void onAudioData(byte[] data);
}