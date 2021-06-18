// Copyright (c) 2015 hornedviper.com.
// Implementation distributed under the MIT licence; see LICENSE.
package com.hviper.codec.uodecode;

import org.apache.commons.io.IOUtils;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * Confidence check: verify the peak signal-to-noise ratio of decoded audio vs the output of the
 * reference decoder is within reasonable limits.
 */
public class PeakSignalNoiseRatioTest
{
    private double computePsnrPcm16(String uoResource, String referenceResource) throws Exception {
        // Read the UO file and the reference encoder's decode output into byte arrays
        byte uoFile[] = IOUtils.toByteArray(
                this.getClass().getResourceAsStream(uoResource));
        byte referenceWavFile[] = IOUtils.toByteArray(
                this.getClass().getResourceAsStream(referenceResource));

        // Decode the UO file ourselves into a byte array
        ByteArrayOutputStream decodedWavOutputStream = new ByteArrayOutputStream(referenceWavFile.length);
        UODecode.uoToPcm16Wav(uoFile, decodedWavOutputStream);
        byte decodedWavFile[] = decodedWavOutputStream.toByteArray();

        // Find the start of the sample data; this will be after a 'data' header and four bytes of
        // content length.
        int dataStart = -1;
        for(int i = 0; i < decodedWavFile.length - 4; ++i) {
            if ((decodedWavFile[i] == 'd')
                    && (decodedWavFile[i + 1] == 'a')
                    && (decodedWavFile[i + 2] == 't')
                    && (decodedWavFile[i + 3] == 'a')) {
                dataStart = i + 8; // 8 = length of header + chunk length
                break;
            }
        }
        assertFalse("No 'data' header in decoded output", dataStart < 0);

        // Headers must be equal. Compare as hex strings for better assert failures here.
        String refHeaders = Hex.encodeHexString(
                Arrays.copyOfRange(referenceWavFile, 0, dataStart));
        String ourHeaders = Hex.encodeHexString(
                Arrays.copyOfRange(decodedWavFile, 0, dataStart));
        assertEquals("WAV headers do not match", refHeaders, ourHeaders);
        assertEquals("File lengths do not match", referenceWavFile.length, decodedWavFile.length);

        // Compute total squared error
        int cursor = dataStart;
        long totalSqError = 0;
        int sampleCount = 0;
        int worstSoFar = 0;
        for (; (cursor + 1) < referenceWavFile.length; cursor += 2) {
            short refSample = (short)((referenceWavFile[cursor] & 0xff)
                    | ((referenceWavFile[cursor + 1] & 0xff) << 8));
            short ourSample = (short)((decodedWavFile[cursor] & 0xff)
                    | ((decodedWavFile[cursor + 1] & 0xff) << 8));
            int absDiff = Math.abs(ourSample - refSample);

            long sqError = ((long)absDiff) * ((long)absDiff);
            totalSqError += sqError;
            ++sampleCount;
        }
        assertNotEquals("No samples read!", 0, sampleCount);

        // Compute the PSNR in decibels; higher the better
        double psnr;
        if (totalSqError > 0) {
            double sqrtMeanSquaredError = Math.sqrt((double) (totalSqError) / (double) (sampleCount));
            double maxValue = 65535.0;
            psnr = 20.0 * Math.log10(maxValue / sqrtMeanSquaredError);
        } else {
            // Identical! Pick a large PSNR result
            psnr = 1000.0;
        }

        return psnr;
    }

    @Test
    public void OpusSpeechDecodeVsReferenceDecoding() throws Exception {
        double psnr = computePsnrPcm16("/opus-speech.uo", "/opus-speech-reference.wav");
        System.out.println("PSNR of decoded audio vs reference decoding = " +
                new DecimalFormat("#.#").format(psnr) + " db");
        assertTrue("PSNR against a reference decoding should be 65 db or greater", psnr >= 65.0);
    }
}
