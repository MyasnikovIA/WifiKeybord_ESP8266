package ru.miacomsoft.audio;

import javax.sound.sampled.Mixer;

public class AudioDeviceInfo {
    private int id;
    private String name;
    private Mixer.Info mixerInfo;
    private boolean busy;

    public AudioDeviceInfo(int id, String name, Mixer.Info mixerInfo, boolean busy) {
        this.id = id;
        this.name = name;
        this.mixerInfo = mixerInfo;
        this.busy = busy;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Mixer.Info getMixerInfo() { return mixerInfo; }
    public boolean isBusy() { return busy; }

    @Override
    public String toString() {
        // Форматируем с номером устройства
        return String.format("[%d] %s%s",
                id,
                name,
                busy ? " 🔴 (ЗАНЯТО)" : " 🟢 (СВОБОДНО)"
        );
    }
}