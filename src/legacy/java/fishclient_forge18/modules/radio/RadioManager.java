package com.fishclient.modules.radio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class RadioManager {

    private static RadioManager INSTANCE;

    public static final List<RadioStation> DEFAULT_STATIONS;

    static {
        List<RadioStation> defaults = new ArrayList<RadioStation>();
        defaults.add(new RadioStation("Groove Salad", "Ambient/Electronica", "https://ice1.somafm.com/groovesalad-128-mp3"));
        defaults.add(new RadioStation("Lush", "Chillout", "https://ice1.somafm.com/lush-128-mp3"));
        defaults.add(new RadioStation("Secret Agent", "Lounge/Spy", "https://ice1.somafm.com/secretagent-128-mp3"));
        defaults.add(new RadioStation("Drone Zone", "Atmospheric Drone", "https://ice1.somafm.com/dronezone-128-mp3"));
        defaults.add(new RadioStation("Suburbs of Goa", "Psytrance", "https://ice1.somafm.com/suburbsofgoa-128-mp3"));
        defaults.add(new RadioStation("Fluid", "Jazz Fusion", "https://ice1.somafm.com/fluid-128-mp3"));
        DEFAULT_STATIONS = Collections.unmodifiableList(defaults);
    }

    public static synchronized RadioManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RadioManager();
        }
        return INSTANCE;
    }

    private final List<RadioStation> stations = new ArrayList<RadioStation>();
    private final ExecutorService audioExecutor;

    private volatile Future<?> currentTask;
    private volatile RadioStation currentStation;
    private volatile boolean playing;
    private volatile boolean paused;
    private volatile boolean stopRequested;
    private volatile float volume = 0.8f;
    private volatile String currentTrackTitle = "";

    private volatile SourceDataLine activeLine;
    private volatile InputStream activeStream;
    private volatile AudioInputStream activeAudio;

    private RadioManager() {
        stations.addAll(DEFAULT_STATIONS);
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "FishClient-Radio");
                thread.setDaemon(true);
                return thread;
            }
        };
        audioExecutor = Executors.newSingleThreadExecutor(factory);
    }

    public synchronized void play(RadioStation station) {
        if (station == null) {
            return;
        }
        stop();
        currentStation = station;
        currentTrackTitle = station.getName();
        stopRequested = false;
        paused = false;
        playing = true;

        currentTask = audioExecutor.submit(new Runnable() {
            @Override
            public void run() {
                streamStation(station);
            }
        });
    }

    public synchronized void stop() {
        stopRequested = true;
        paused = false;
        playing = false;
        Future<?> task = currentTask;
        currentTask = null;
        if (task != null) {
            task.cancel(true);
        }
        closeActiveAudio();
    }

    public void pause() {
        if (playing) {
            paused = true;
        }
    }

    public void resume() {
        if (playing) {
            paused = false;
        }
    }

    public boolean isPlaying() {
        return playing && !paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        if (Float.isNaN(volume)) {
            return;
        }
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public RadioStation getCurrentStation() {
        return currentStation;
    }

    public String getCurrentTrackTitle() {
        return currentTrackTitle;
    }

    public List<RadioStation> getAllStations() {
        return Collections.unmodifiableList(stations);
    }

    public synchronized void addStation(RadioStation station) {
        if (station != null) {
            stations.add(station);
        }
    }

    public synchronized void clearCustomStations() {
        stations.clear();
        stations.addAll(DEFAULT_STATIONS);
    }

    public void shutdown() {
        stop();
        audioExecutor.shutdownNow();
    }

    private void streamStation(RadioStation station) {
        HttpURLConnection connection = null;
        BufferedInputStream networkStream = null;

        try {
            URL url = new URL(station.getStreamUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "FishClient/1.0");
            connection.setRequestProperty("Icy-MetaData", "1");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.connect();

            networkStream = new BufferedInputStream(connection.getInputStream());
            activeStream = networkStream;

            // Best-effort decode path. If Java Sound cannot decode stream codec, we keep
            // the connection alive so the UI remains responsive and station switching works.
            if (!tryDecodeAndPlay(networkStream)) {
                byte[] sink = new byte[8192];
                while (!stopRequested && !Thread.currentThread().isInterrupted()) {
                    if (paused) {
                        sleepQuietly(60L);
                        continue;
                    }
                    int read = networkStream.read(sink);
                    if (read < 0) {
                        break;
                    }
                }
            }
        } catch (IOException ignored) {
            // Keep silent in production client. UI can poll state.
        } finally {
            closeActiveAudio();
            if (connection != null) {
                connection.disconnect();
            }
            if (!stopRequested) {
                playing = false;
                paused = false;
            }
        }
    }

    private boolean tryDecodeAndPlay(InputStream stream) {
        AudioInputStream decodedStream = null;
        SourceDataLine line = null;

        try {
            AudioInputStream source = AudioSystem.getAudioInputStream(stream);
            AudioFormat baseFormat = source.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
            );

            decodedStream = AudioSystem.getAudioInputStream(pcmFormat, source);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcmFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(pcmFormat);
            line.start();

            activeAudio = decodedStream;
            activeLine = line;

            byte[] buffer = new byte[4096];
            int read;
            while (!stopRequested && !Thread.currentThread().isInterrupted() && (read = decodedStream.read(buffer, 0, buffer.length)) != -1) {
                if (paused) {
                    sleepQuietly(60L);
                    continue;
                }
                applyVolume(buffer, read, volume);
                line.write(buffer, 0, read);
            }

            line.drain();
            return true;
        } catch (Exception ignored) {
            closeQuietly(decodedStream);
            closeLine(line);
            return false;
        }
    }

    private void applyVolume(byte[] buffer, int length, float volume) {
        if (volume >= 0.999f) {
            return;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            int scaled = (int) (sample * volume);
            if (scaled > Short.MAX_VALUE) {
                scaled = Short.MAX_VALUE;
            } else if (scaled < Short.MIN_VALUE) {
                scaled = Short.MIN_VALUE;
            }
            buffer[i] = (byte) (scaled & 0xFF);
            buffer[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
    }

    private void closeActiveAudio() {
        closeQuietly(activeAudio);
        activeAudio = null;

        closeQuietly(activeStream);
        activeStream = null;

        closeLine(activeLine);
        activeLine = null;
    }

    private void closeLine(SourceDataLine line) {
        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void closeQuietly(InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeQuietly(AudioInputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}

