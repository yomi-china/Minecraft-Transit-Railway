package mtr.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NetworkAudioPlayer {

    private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MTR-NetworkAudio");
        t.setDaemon(true);
        return t;
    });

    private static final int MAX_CACHE_SIZE = 20;

    private static final Map<String, byte[]> AUDIO_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, byte[]>(MAX_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    private static final int TIMEOUT_MS = 10000;
    private static final int BUFFER_SIZE = 8192;

    public static void playAsync(String urlString, Runnable onError) {
        AUDIO_EXECUTOR.submit(() -> {
            try {
                byte[] audioData = getAudioData(urlString);
                if (audioData == null) {
                    if (onError != null) onError.run();
                    return;
                }
                playWavFromMemory(audioData);
            } catch (Exception e) {
                System.err.println("[MTR] Network audio playback failed for URL: " + urlString);
                e.printStackTrace();
                if (onError != null) onError.run();
            }
        });
    }

    private static byte[] getAudioData(String urlString) throws IOException {
        // 先从缓存获取
        byte[] cached = AUDIO_CACHE.get(urlString);
        if (cached != null) {
            return cached;
        }

        URL url;
        try {
            url = URI.create(urlString).toURL();
        } catch (IllegalArgumentException e) {
            System.err.println("[MTR] Invalid URL: " + urlString);
            return null;
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "MTR-Mod/1.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.err.println("[MTR] Failed to fetch audio: HTTP " + responseCode + " for " + urlString);
                return null;
            }

            byte[] data;
            try (InputStream is = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                data = baos.toByteArray();
            }

            // 验证WAV头
            if (data.length < 12 || data[0] != 0x52 || data[1] != 0x49 || data[2] != 0x46 || data[3] != 0x46) {
                System.err.println("[MTR] Downloaded data is not a valid WAV file (missing RIFF header): " + urlString);
                return null;
            }

            AUDIO_CACHE.put(urlString, data);
            return data;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void playWavFromMemory(byte[] audioData) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
             AudioInputStream audioStream = AudioSystem.getAudioInputStream(bais)) {

            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[MTR] Audio format not supported: " + format);
                return;
            }

            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.open(audioStream);
            clip.start();

        } catch (UnsupportedAudioFileException e) {
            System.err.println("[MTR] Not a valid WAV audio file: " + e.getMessage());
            throw e;
        }
    }

    public static void clearCache() {
        AUDIO_CACHE.clear();
    }

    public static void shutdown() {
        AUDIO_EXECUTOR.shutdown();
        try {
            if (!AUDIO_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                AUDIO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            AUDIO_EXECUTOR.shutdownNow();
        }
    }
}