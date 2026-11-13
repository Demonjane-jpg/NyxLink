package com.limelight.binding.audio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import android.os.Build;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

public class AndroidAudioRenderer implements AudioRenderer {

    private final Context context;
    private final boolean enableAudioFx;

    private AudioTrack track;
    private int savedChannelConfig;
    private int savedSampleRate;
    private int savedBytesPerFrame;
    private boolean activeEarpiece;
    private static boolean ownedAudioMode;
    private static boolean ownedCommDevice;
    private static volatile boolean privacyMuted = false;
    private static volatile AndroidAudioRenderer activeRenderer;

    public static synchronized void setPrivacyMuted(boolean muted) {
        privacyMuted = muted;
        if (activeRenderer != null && activeRenderer.track != null && activeRenderer.context != null) {
            try {
                if (muted) {
                    activeRenderer.track.setVolume(0f);
                } else {
                    android.content.SharedPreferences bedPrefs = activeRenderer.context.getSharedPreferences("bedmirror", Context.MODE_PRIVATE);
                    float vol = bedPrefs.getBoolean("phoneAudio", true) ? bedPrefs.getFloat("phoneVolume", 1f) : 0f;
                    activeRenderer.track.setVolume(vol);
                }
            } catch (Exception ignored) {}
        }
    }

    public static boolean isPrivacyMuted() {
        return privacyMuted;
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx) {
        this.context = context;
        this.enableAudioFx = enableAudioFx;
    }

    public static AudioDeviceInfo findOutputDevice(Context context, int type) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return null;
        for (AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (dev.isSink() && dev.getType() == type) return dev;
        }
        return null;
    }

    public static boolean hasEarpiece(Context context) {
        return findOutputDevice(context, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) != null;
    }

    public static synchronized void restoreNormalAudioRouting(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 31 && ownedCommDevice) {
                am.clearCommunicationDevice();
                ownedCommDevice = false;
            }
            if (ownedAudioMode) {
                if (am.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                    am.setMode(AudioManager.MODE_NORMAL);
                }
                ownedAudioMode = false;
            }
        } catch (Exception ignored) {}
    }

    private synchronized void applyRoutingForMode(boolean earpiece) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        if (!earpiece) {
            restoreNormalAudioRouting(context);
            if (track != null) track.setPreferredDevice(null);
            return;
        }
        AudioDeviceInfo earpieceDev = findOutputDevice(context, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        if (earpieceDev == null) return;
        if (am.getMode() == AudioManager.MODE_NORMAL) {
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            ownedAudioMode = true;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            am.setCommunicationDevice(earpieceDev);
            ownedCommDevice = true;
        } else {
            am.setSpeakerphoneOn(false);
        }
        if (track != null) {
            track.setPreferredDevice(earpieceDev);
        }
    }

    private AudioTrack createAudioTrack(int channelConfig, int sampleRate, int bufferSize, boolean lowLatency) {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setUsage(activeEarpiece ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_GAME);
        if (activeEarpiece) {
            attributesBuilder.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        }
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Use FLAG_LOW_LATENCY on L through N
            if (lowLatency) {
                attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder trackBuilder = new AudioTrack.Builder()
                    .setAudioFormat(format)
                    .setAudioAttributes(attributesBuilder.build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize);

            // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
            if (lowLatency) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }

            return trackBuilder.build();
        }
        else {
            return new AudioTrack(attributesBuilder.build(),
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
        }
    }

    private int openTrackForCurrentMode(int channelConfig, int sampleRate, int bytesPerFrame) {
        applyRoutingForMode(activeEarpiece);

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.

        for (int i = 0; i < 4; i++) {
            boolean lowLatency;
            int bufferSize;

            // We will try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode

            switch (i) {
                case 0:
                case 1:
                    lowLatency = true;
                    break;
                case 2:
                case 3:
                    lowLatency = false;
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            switch (i) {
                case 0:
                case 2:
                    bufferSize = bytesPerFrame * 2;
                    break;

                case 1:
                case 3:
                    // Try the larger buffer size
                    bufferSize = Math.max(AudioTrack.getMinBufferSize(sampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                            bytesPerFrame * 2);

                    // Round to next frame
                    bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            // Skip low latency options if hardware sample rate doesn't match the content
            if (AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) != sampleRate && lowLatency) {
                continue;
            }

            // Skip low latency options when using audio effects, since low latency mode
            // precludes the use of the audio effect pipeline (as of Android 13).
            if ((enableAudioFx || activeEarpiece) && lowLatency) {
                continue;
            }

            try {
                track = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency);
                applyRoutingForMode(activeEarpiece);
                track.play();
                activeRenderer = this;
                if (privacyMuted) {
                    try {
                        track.setVolume(0f);
                    } catch (Exception ignored) {}
                }

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: "+bufferSize+" "+lowLatency+" earpiece="+activeEarpiece);
                break;
            } catch (Exception e) {
                // Try to release the AudioTrack if we got far enough
                e.printStackTrace();
                try {
                    if (track != null) {
                        track.release();
                        track = null;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return -2;
        }

        return 0;
    }

    @Override
    public int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame) {
        int channelConfig;
        int bytesPerFrame;

        switch (audioConfiguration.channelCount)
        {
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 4:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            case 8:
                // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND isn't available until Android 6.0,
                // yet the CHANNEL_OUT_SIDE_LEFT and CHANNEL_OUT_SIDE_RIGHT constants were added
                // in 5.0, so just hardcode the constant so we can work on Lollipop.
                channelConfig = 0x000018fc; // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                break;
            default:
                LimeLog.severe("Decoder returned unhandled channel count");
                return -1;
        }

        LimeLog.info("Audio channel config: "+String.format("0x%X", channelConfig));

        bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2;
        savedChannelConfig = channelConfig;
        savedSampleRate = sampleRate;
        savedBytesPerFrame = bytesPerFrame;
        android.content.SharedPreferences bedPrefs = context.getSharedPreferences("bedmirror", Context.MODE_PRIVATE);
        activeEarpiece = bedPrefs.getBoolean("earpieceMode", false) && hasEarpiece(context);

        return openTrackForCurrentMode(channelConfig, sampleRate, bytesPerFrame);
    }

    private void switchAudioModeIfNeeded(boolean wantEarpiece) {
        if (wantEarpiece == activeEarpiece || savedSampleRate == 0) return;
        activeEarpiece = wantEarpiece;
        if (track != null) {
            try {
                track.pause();
                track.flush();
                track.release();
            } catch (Exception ignored) {}
            track = null;
        }
        openTrackForCurrentMode(savedChannelConfig, savedSampleRate, savedBytesPerFrame);
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
        android.content.SharedPreferences bedPrefs = context.getSharedPreferences("bedmirror", Context.MODE_PRIVATE);
        boolean wantEarpiece = bedPrefs.getBoolean("earpieceMode", false) && hasEarpiece(context);
        if (wantEarpiece != activeEarpiece) {
            switchAudioModeIfNeeded(wantEarpiece);
        }
        if (track == null) return;
        track.setVolume(privacyMuted ? 0f : (bedPrefs.getBoolean("phoneAudio", true) ? bedPrefs.getFloat("phoneVolume", 1f) : 0f));
        // Only queue up to 40 ms of pending audio data in addition to what AudioTrack is buffering for us.
        if (MoonBridge.getPendingAudioDuration() < 40) {
            // This will block until the write is completed. That can cause a backlog
            // of pending audio data, so we do the above check to be able to bound
            // latency at 40 ms in that situation.
            track.write(audioData, 0, audioData.length);
        }
        else {
            LimeLog.info("Too much pending audio data: " + MoonBridge.getPendingAudioDuration() +" ms");
        }
    }

    @Override
    public void start() {
        if (enableAudioFx && track != null) {
            // Open an audio effect control session to allow equalizers to apply audio effects
            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, track.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME);
            context.sendBroadcast(i);
        }
    }

    @Override
    public void stop() {
        if (enableAudioFx && track != null) {
            // Close our audio effect control session when we're stopping
            Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, track.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            context.sendBroadcast(i);
        }
    }

    @Override
    public void cleanup() {
        try {
            if (activeRenderer == this) {
                activeRenderer = null;
            }
            privacyMuted = false;
            if (track != null) {
                // Immediately drop all pending data
                track.pause();
                track.flush();
                track.release();
            }
        } finally {
            restoreNormalAudioRouting(context);
        }
    }
}
