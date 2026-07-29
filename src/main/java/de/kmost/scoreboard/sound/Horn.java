package de.kmost.scoreboard.sound;

import java.io.File;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/**
 * Hupe des Kampfgerichts. Die eingebauten Töne werden zur Laufzeit als
 * PCM-Samples generiert — es wird kein Audio-Asset und kein javafx-media
 * benötigt. Alternativ kann eine externe Audiodatei abgespielt werden
 * (Formate von javax.sound.sampled: WAV/AIFF/AU). Ist keine Audio-Ausgabe
 * verfügbar, bleibt die Hupe stumm statt die App zu blockieren.
 */
public final class Horn {

    /** Eingebaute Huptöne, zur Laufzeit generiert. */
    public enum Tone {
        KLASSISCH("Klassisch"),
        TIEF("Tief"),
        HELL("Hell"),
        DOPPELT("Doppelhupe"),
        SIRENE("Sirene");

        private final String label;

        Tone(String label) {
            this.label = label;
        }

        /** Beschriftung im Konfigurationsfenster. */
        public String label() {
            return label;
        }
    }

    private static final float SAMPLE_RATE = 44_100f;

    private Clip clip;

    public Horn() {
        useTone(Tone.KLASSISCH);
    }

    /** Gespeicherter Ton-Name aus den Properties; unbekannt/leer = Klassisch. */
    public static Tone toneOrDefault(String name) {
        try {
            return Tone.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Tone.KLASSISCH;
        }
    }

    /** Spielt den aktuellen Ton asynchron ab (blockiert den FX-Thread nicht). */
    public void play() {
        if (clip == null) {
            return;
        }
        clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    /** Wechselt auf einen eingebauten Ton. */
    public void useTone(Tone tone) {
        byte[] pcm = generateSamples(tone);
        Clip newClip = null;
        try {
            newClip = AudioSystem.getClip();
            newClip.open(new AudioFormat(SAMPLE_RATE, 16, 1, true, false), pcm, 0, pcm.length);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("Hupe nicht verfügbar: " + e.getMessage());
        }
        swap(newClip);
    }

    /**
     * Lädt eine externe Audiodatei; true bei Erfolg. Schlägt das Laden fehl
     * (fehlende Datei, nicht unterstütztes Format wie MP3), bleibt der
     * bisherige Ton aktiv.
     */
    public boolean useFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            // auf PCM 16 Bit wandeln, damit auch komprimierte WAV-Varianten laufen
            AudioFormat base = in.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16, base.getChannels(),
                    base.getChannels() * 2, base.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
                Clip newClip = AudioSystem.getClip();
                newClip.open(pcm);
                swap(newClip);
                return true;
            }
        } catch (Exception e) {
            System.err.println("Hupen-Datei „" + file.getName() + "“ nicht ladbar: "
                    + e.getMessage());
            return false;
        }
    }

    private void swap(Clip newClip) {
        if (clip != null) {
            clip.stop();
            clip.close();
        }
        clip = newClip;
    }

    private static byte[] generateSamples(Tone tone) {
        return switch (tone) {
            case KLASSISCH -> blast(400, 1.5);
            case TIEF -> blast(250, 1.5);
            case HELL -> blast(620, 1.2);
            case DOPPELT -> concat(blast(400, 0.45), silence(0.12), blast(400, 0.8));
            case SIRENE -> sweep(400, 800, 1.8);
        };
    }

    /**
     * Ein Hupstoß: Grundton mit abfallender Obertonreihe (voller, blechbläser-
     * artiger Klang statt hartem Rechteck) plus eine um 4 Hz verstimmte zweite
     * Stimme — die langsame Schwebung macht den Ton „fett“ wie eine
     * Drucklufthupe, bleibt aber angenehm und trägt durch die Halle.
     */
    private static byte[] blast(double frequencyHz, double seconds) {
        int frames = (int) (SAMPLE_RATE * seconds);
        byte[] pcm = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double t = i / SAMPLE_RATE;
            double value = (voice(2 * Math.PI * frequencyHz * t)
                    + voice(2 * Math.PI * (frequencyHz + 4) * t)) / 2;
            writeSample(pcm, i, envelope(saturate(value), t, seconds));
        }
        return pcm;
    }

    /** Sirene: Tonhöhe steigt und fällt einmal; Phase fortlaufend gegen Knacksen. */
    private static byte[] sweep(double fromHz, double toHz, double seconds) {
        int frames = (int) (SAMPLE_RATE * seconds);
        byte[] pcm = new byte[frames * 2];
        double phase = 0;
        for (int i = 0; i < frames; i++) {
            double t = i / SAMPLE_RATE;
            double frequency = fromHz + (toHz - fromHz) * Math.sin(Math.PI * t / seconds);
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            writeSample(pcm, i, envelope(saturate(voice(phase)), t, seconds));
        }
        return pcm;
    }

    /** Eine Stimme zur Basisphase: Grundton plus Obertöne 2–5, je 0.55-fach leiser. */
    private static double voice(double phase) {
        double value = 0;
        double amplitude = 1;
        double total = 0;
        for (int harmonic = 1; harmonic <= 5; harmonic++) {
            value += amplitude * Math.sin(phase * harmonic);
            total += amplitude;
            amplitude *= 0.55;
        }
        return value / total;
    }

    /**
     * Weiche Sättigung: hebt die Lautheit (RMS) deutlich an und komprimiert
     * Spitzen, ohne hart zu klippen — durchsetzungsfähig, aber nicht schrill.
     */
    private static double saturate(double value) {
        return Math.tanh(2 * value) / Math.tanh(2);
    }

    private static byte[] silence(double seconds) {
        return new byte[2 * (int) (SAMPLE_RATE * seconds)];
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] pcm = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, pcm, offset, part.length);
            offset += part.length;
        }
        return pcm;
    }

    /** Weiches Ein-/Ausblenden gegen Knacksen; etwas längere Ausklingzeit. */
    private static double envelope(double value, double t, double seconds) {
        double attack = Math.min(1, t / 0.02);
        double release = Math.min(1, (seconds - t) / 0.25);
        return Math.clamp(value, -1, 1) * attack * release * 0.85;
    }

    private static void writeSample(byte[] pcm, int i, double sample) {
        short s = (short) (Math.clamp(sample, -1, 1) * Short.MAX_VALUE);
        pcm[2 * i] = (byte) s;
        pcm[2 * i + 1] = (byte) (s >> 8);
    }
}
