// Copyright (c) 2015 hornedviper.com.
// Implementation distributed under the MIT licence; see LICENSE.
package com.hviper.codec.uodecode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * UO audio decoder simple entry points and command-line interface.
 */
public class UODecode
{
    /**
     * File parser callback implementation that decodes frames provided and writes samples
     * returned to a {@link WavFileOutputStream subclass}.
     */
    private static class WavOutputDecoder implements UOFileParser.FileParseCallback {
        private final WavFileOutputStream output;
        private final UOFrameDecode decoder = new UOFrameDecode();

        public WavOutputDecoder(WavFileOutputStream output) {
            this.output = output;
        }

        @Override
        public void resetState() {
            decoder.resetState();
        }

        @Override
        public void decodeFrame(byte[] input, int cursor) throws IOException {
            float[] samples = decoder.decodeFrame(input, cursor);
            output.writeSamples(samples);
        }
    }

    /**
     * Convert a UO file provided as a byte array into a 16-bit PCM .wav file, written to the
     * output stream provided.
     *
     * @param input a UO file loaded into a byte array
     * @param outputStream an output stream to recieve a 16-bit PCM .wav file
     */
    public static void uoToPcm16Wav(byte[] input, OutputStream outputStream) throws Exception {
        UOFileParser parser = new UOFileParser(input);
        int outputSamples = parser.countSamples();

        WavFileOutputStream output = new WavFileOutputStream.Pcm16(outputStream, outputSamples);
        output.writeHeader();
        WavOutputDecoder decoder = new WavOutputDecoder(output);
        parser.parseFile(decoder);

        output.close();
    }


    /**
     * Convert a UO file provided as a byte array into a G.711 mu-law .wav file, written to the
     * output stream provided.
     *
     * @param input a UO file loaded into a byte array
     * @param outputStream an output stream to recieve a G.711 mu-law .wav file
     */
    public static void uoToMuLawWav(byte[] input, OutputStream outputStream) throws Exception {
        UOFileParser parser = new UOFileParser(input);
        int outputSamples = parser.countSamples();

        WavFileOutputStream output = new WavFileOutputStream.MuLaw(outputStream, outputSamples);
        output.writeHeader();
        WavOutputDecoder decoder = new WavOutputDecoder(output);
        parser.parseFile(decoder);

        output.close();
    }

    /**
     * Command-line entry point.
     *
     * Accepts input and output filenames, plus an optional flag to select mu-law output rather
     * than the default 16-bit PCM.
     */
    public static void main( String[] args ) throws Exception {
        /**
         * Filenames collected from the command line; we expect two, input and output.
         */
        List<String> filenames = new ArrayList<String>(2);
        /**
         * Is the mu-law flag set on the command line?
         */
        boolean muLaw = false;
        /**
         * Encountered an error parsing the command line
         */
        boolean badArguments = false;

        /*
         * Simple argument parser.
         * Assuming anything that starts with a '-' in an option, else it's a filename.
         */
        for(String arg : args) {
            if (arg.startsWith("-")) {
                String lower = arg.toLowerCase();
                if (lower.equals("--mulaw")) {
                    muLaw = true;
                } else {
                    // Unknown argument
                    badArguments = true;
                    System.out.println("Unknown argument " + arg);
                    break;
                }
            } else {
                filenames.add(arg);
            }
        }

        if (!badArguments && (filenames.size() == 2)) {
            // We have two filename and no errors; transcode
            String inputFile = filenames.get(0);
            String outputFile = filenames.get(1);
            System.out.println("Transcoding " + inputFile + " to " + outputFile
                    + " (" + (muLaw ? "G.711 mu-law" : "16-bit PCM") + ")");

            byte[] input = Files.readAllBytes(Paths.get(inputFile));
            FileOutputStream outputFileStream = new FileOutputStream(outputFile);
            if (muLaw) {
                uoToMuLawWav(input, outputFileStream);
            } else {
                uoToPcm16Wav(input, outputFileStream);
            }
        } else {
            System.out.println( "Usage: UODecode [--mulaw] <input UO file> <output WAV file>" );
        }
    }
}
