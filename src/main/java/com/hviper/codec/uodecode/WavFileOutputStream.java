// Copyright (c) 2015 hornedviper.com.
// Implementation distributed under the MIT licence; see LICENSE.
package com.hviper.codec.uodecode;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Base class for an {@link OutputStream} that generates a .wav file, for an encoding implemented
 * by a derived class.
 */
public abstract class WavFileOutputStream extends FilterOutputStream {
    private final int sampleCount;
    private final int bytesPerSample;

    /**
     * @return the number of bytes in the "fmt " chunk for this .wav file type
     */
    protected abstract int getFormatChunkLength();
    /**
     * Write the format chunk for this .wav file type into the stream.
     * This must be exactly {@link WavFileOutputStream#getFormatChunkLength()} bytes.
     */
    protected abstract void writeFormatChunk() throws IOException;

    /**
     * Write a single sample to the output stream. The input range is -1024 to +1024.
     *
     * @param sample an audio sample in the range -1024 to +1024
     */
    protected abstract void writeSample(float sample) throws IOException;

    public WavFileOutputStream(OutputStream out, int sampleCount, int bytesPerSample) {
        super(out);
        this.bytesPerSample = bytesPerSample;
        this.sampleCount = sampleCount;
    }

    /**
     * Generate a .wav file header.
     * This MUST be called before writing samples to the file.
     */
    public void writeHeader() throws IOException {
        int dataLength = bytesPerSample * sampleCount;
        int fmtLength = getFormatChunkLength();

        int contentLength = 4 // for the WAVE signature
             + 8 + fmtLength // for the fmt header and chunk
             + 8 + 4 // for the fact header and chunk
             + 8 + dataLength; // data header and chunk;

        writeFourCC("RIFF");
        writeInt(contentLength);
        writeFourCC("WAVE");

        writeFourCC("fmt ");
        writeInt(fmtLength);
        writeFormatChunk();

        writeFourCC("fact");
        writeInt(4);
        writeInt(sampleCount);

        writeFourCC("data");
        writeInt(dataLength);
    }

    /**
     * Write a four character code into the output file.
     */
    protected void writeFourCC(String code) throws IOException {
        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 4) {
            throw new IllegalArgumentException(
                    "Bad four character code \"" + code + "\"; length = " + bytes.length);
        }
        out.write(bytes);
    }

    /**
     * Write a 32-bit value to the output, little-endian.
     * @param v 32-bit value to write
     */
    protected void writeInt(int v) throws IOException {
        out.write((v >>>  0) & 0xFF);
        out.write((v >>>  8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    /**
     * Write a 16-bit value to the output, little-endian. Works for both signed and unsigned.
     * @param v 16-bit value to write
     */
    protected void writeShort(int v) throws IOException {
        out.write((v >>>  0) & 0xFF);
        out.write((v >>>  8) & 0xFF);
    }

    /**
     * Write an array of output samples to the .wav file. The samples are floating-point numbers
     * in the range -1024 to +1024.
     *
     * @param samples an array of floating point numbers between -1024 and +1024.
     */
    public void writeSamples(float samples[]) throws IOException {
        for(float sample : samples) {
            writeSample(sample);
        }
    }

    /**
     * Outputs a signed 16-bit PCM .wav file.
     */
    public static class Pcm16 extends WavFileOutputStream {
        public Pcm16(OutputStream out, int sampleCount) {
            super(out, sampleCount, 2);
        }

        @Override
        protected int getFormatChunkLength() {
            return 16;
        }

        @Override
        protected void writeFormatChunk() throws IOException {
            writeShort(1); // 1 = PCM
            writeShort(1); // mono
            writeInt(8000); // sample rate
            writeInt(16000); // average bytes / second
            writeShort(2); // block align
            writeShort(16); // bits per sample
        }

        @Override
        protected void writeSample(float sample) throws IOException {
            float sample16 = sample * 0.125f * 256.0f;
            if (sample16 < -32767.0f) sample16 = -32767.0f;
            if (sample16 > 32767.0f) sample16 = 32767.0f;
            writeShort((int)(sample16));
        }
    }

    /**
     * Outputs a mu-law encoded .wav file.
     */
    public static class MuLaw extends WavFileOutputStream {
        public MuLaw(OutputStream out, int sampleCount) {
            super(out, sampleCount, 1);
        }

        @Override
        protected int getFormatChunkLength() {
            return 18;
        }

        @Override
        protected void writeFormatChunk() throws IOException {
            writeShort(7); // 7 = G.711 mu-law
            writeShort(1); // mono
            writeInt(8000); // sample rate
            writeInt(8000); // average bytes / second
            writeShort(1); // block align
            writeShort(8); // bits per sample
            writeShort(0); // extra format bytes
        }

        @Override
        protected void writeSample(float sample) throws IOException {
            float sample16 = sample * 0.125f * 256.0f;
            if (sample16 < -32767.0f) sample16 = -32767.0f;
            if (sample16 > 32767.0f) sample16 = 32767.0f;
            int s16 = (int)(sample16);
            int input = (s16 + 32768) >> 2;
            write(muLawMap[input]);
        }

        /**
         * Map of (signed 14-bit input value + 8192) to mu-Law output byte.
         * (Populated below.)
         */
        private static byte[] muLawMap = new byte[16384];

        /* From wikipedia: Quantized mu-law values
         *
         *  14 bit Binary Linear input code             8 bit Compressed code
         *
         *  +8158 to +4063 in 16 intervals of 256       0x80 + interval number
         *  +4062 to +2015 in 16 intervals of 128       0x90 + interval number
         *  +2014 to +991 in 16 intervals of 64         0xA0 + interval number
         *   +990 to +479 in 16 intervals of 32         0xB0 + interval number
         *   +478 to +223 in 16 intervals of 16         0xC0 + interval number
         *   +222 to +95 in 16 intervals of 8           0xD0 + interval number
         *    +94 to +31 in 16 intervals of 4           0xE0 + interval number
         *    +30 to +1 in 15 intervals of 2            0xF0 + interval number
         *      0                                       0xFF
         *     −1                                       0x7F
         *    −31 to −2 in 15 intervals of 2            0x70 + interval number
         *    −95 to −32 in 16 intervals of 4           0x60 + interval number
         *   −223 to −96 in 16 intervals of 8           0x50 + interval number
         *   −479 to −224 in 16 intervals of 16         0x40 + interval number
         *   −991 to −480 in 16 intervals of 32         0x30 + interval number
         *  −2015 to −992 in 16 intervals of 64         0x20 + interval number
         *  −4063 to −2016 in 16 intervals of 128       0x10 + interval number
         *  −8159 to −4064 in 16 intervals of 256       0x00 + interval number
         */
        static {
            /*
             *  14 bit Binary Linear input code             8 bit Compressed code
             *
             *  +8158 to +4063 in 16 intervals of 256       0x80 + interval number
             *  +4062 to +2015 in 16 intervals of 128       0x90 + interval number
             *  +2014 to +991 in 16 intervals of 64         0xA0 + interval number
             *   +990 to +479 in 16 intervals of 32         0xB0 + interval number
             *   +478 to +223 in 16 intervals of 16         0xC0 + interval number
             *   +222 to +95 in 16 intervals of 8           0xD0 + interval number
             *    +94 to +31 in 16 intervals of 4           0xE0 + interval number
             *    +30 to +1 in 15 intervals of 2            0xF0 + interval number
             */
            {
                int c = 8192 + 8158;
                int interval = 256;
                for (int b = 0x80; b < 0xff; ++b) {
                    for (int i = 0; i < interval; ++i) {
                        muLawMap[c] = (byte) b;
                        --c;
                    }
                    if ((b & 0xf) == 0xf) interval >>>= 1;
                }
            }

            /*
             *  14 bit Binary Linear input code             8 bit Compressed code
             *
             *  −8159 to −4064 in 16 intervals of 256       0x00 + interval number
             *  −4063 to −2016 in 16 intervals of 128       0x10 + interval number
             *  −2015 to −992 in 16 intervals of 64         0x20 + interval number
             *   −991 to −480 in 16 intervals of 32         0x30 + interval number
             *   −479 to −224 in 16 intervals of 16         0x40 + interval number
             *   −223 to −96 in 16 intervals of 8           0x50 + interval number
             *    −95 to −32 in 16 intervals of 4           0x60 + interval number
             *    −31 to −2 in 15 intervals of 2            0x70 + interval number
             */
            {
                int c = 8192 - 8159;
                int interval = 256;
                for (int b = 0x0; b < 0x7f; ++b) {
                    for (int i = 0; i < interval; ++i) {
                        muLawMap[c] = (byte) b;
                        ++c;
                    }
                    if ((b & 0xf) == 0xf) interval >>>= 1;
                }
            }

            /*
             *  14 bit Binary Linear input code             8 bit Compressed code
             *
             *      0                                       0xFF
             *     −1                                       0x7F
             */
            muLawMap[8191] = (byte)0x7f;
            muLawMap[8192] = (byte)0xff;

            // Finally fill in the top end up to the end of the map, as if clipped
            // (The equivalent lower-end needs to be 0, which it will be by default.)
            for (int i = 8192 + 8158; i < muLawMap.length; ++i) {
                muLawMap[i] = (byte)0x80;
            }
        }
    }
}
