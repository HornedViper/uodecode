// Copyright (c) 2015 hornedviper.com.
// Implementation distributed under the MIT licence; see LICENSE.
package com.hviper.codec.uodecode;

import java.io.IOException;

/**
 * UO file format parser.
 */
public class UOFileParser {
    private final byte[] input;
    private int cursor;

    public UOFileParser(byte[] input) {
        this.input = input;
        cursor = 0;
    }

    /**
     * Parser callback interface
     */
    static interface FileParseCallback {
        /**
         * Reset decoder state
         */
        void resetState();
        /**
         * Decode a full-rate packed UO frame from the given offset in the buffer passed
         */
        void decodeFrame(byte[] input, int cursor) throws IOException;
    }

    /**
     * Simple implementation of the parser callback to count the number of output samples in a
     * given UO file.
     */
    static class SampleCounter implements FileParseCallback {
        private int samples = 0;

        /**
         * @return the number of audio samples in the parsed UO file
         */
        public int getSamples() { return samples; }

        @Override
        public void resetState() {
            // No action
        }

        @Override
        public void decodeFrame(byte[] input, int cursor) {
            // A full frame decodes to 192 samples
            samples += 192;
        }
    }

    /**
     * Parse up to four frames from the current cursor location, stopping if we encounter a block
     * header.
     */
    private void parseFrames(FileParseCallback callback) throws IOException {
        for (int i = 0; (i < 4) && ((cursor + 4) < input.length); ++i) {
            int startFrame = (input[cursor] & 0xff) | ((input[cursor + 1] & 0xff) << 8);
            if (startFrame == 0xffaa) {
                // New block header
                break;
            } else {
                // A frame of data
                callback.decodeFrame(input, cursor);
                cursor += 0x30;
            }
        }
    }

    /**
     * Parse a UO file, calling methods on the callback for resets and frames encountered.
     * @param callback an implementation of
     *                 {@link com.hviper.codec.uodecode.UOFileParser.FileParseCallback} that is
     *                 called with reset events and frames to decode
     */
    public void parseFile(FileParseCallback callback) throws Exception {
        cursor = 0;
        while ((cursor + 6) < input.length) {
            int word = (input[cursor] & 0xff) | ((input[cursor + 1] & 0xff) << 8);
            if (word == 0xffaa) {
                int blockType = (input[cursor + 2] & 0xff) | ((input[cursor + 3] & 0xff) << 8);
                switch (blockType) {
                    case 0x140:
                        // Full-rate block with reset; two extra bytes than that case
                        cursor += 6;
                        callback.resetState();
                        parseFrames(callback);
                        break;
                    case 0x040:
                        // Full-rate block; up to four frames
                        cursor += 4;
                        parseFrames(callback);
                        break;
                    default:
                        throw new Exception("Unsupported block type " + Integer.toHexString(blockType) + " at offset " + cursor);
                }
            } else {
                throw new Exception("Expected UO header, got " + Integer.toHexString(word) + " at offset " + cursor);
            }
        }
    }

    /**
     * Parse the file to count the number of audio samples it will generate
     * @return the number of audio samples in the parsed UO file
     */
    public int countSamples() throws Exception {
        SampleCounter counter = new SampleCounter();
        parseFile(counter);
        return counter.getSamples();
    }
}
