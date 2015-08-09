// Copyright (c) 2015 hornedviper.com.
// Implementation distributed under the MIT licence; see LICENSE.
package com.hviper.codec.uodecode;

/**
 * UO frame decoder.
 *
 * The class contains the decode logic and the state that is preserved between frames.
 */
public class UOFrameDecode {

    public static final int SAMPLES_PER_FRAME = 192;

    /**
     * The previous frame's LSF values, as decoded from the packed frame via
     * {@link UOTables#LSF_TABLE}.
     *
     * If present, we interpolate from these values to the new frame's values for each subframe,
     * with the last subframe using entirely the new set of values. If not avaiable we use the
     * new set of values for the entire frame.
     *
     * The interpolated LSF values are used to compute the LPC (Linear Prediction Coefficients)
     * for each subframe.
     */
    private float prevLsf[];

    /**
     * A sliding window of recent output sample values.
     *
     * The final step of synthesis is to append each new value in turn to this buffer and apply
     * the computed LPCs to the whole buffer to compute the next output sample. Each of the twelve
     * codebook entries in a subframe produces (and hence shifts this buffer by) four samples.
     */
    private float synthesisBuffer[] = new float[10];

    /**
     * A logarithm of the current subframe gain. This is a 20 log_10 value, with a range of 60
     * (-32 to +28) i.e. the actual gain's scale is from 1 to 1000.
     *
     * This value is used before any updates by the codebook, i.e. in some sense
     * this is really a component of the next subframe's gain.
     */
    private float currentGainLevel = -32.0f;

    /**
     * A collection of the current gain energy over the previous frame. This is used to compute
     * {@link UOFrameDecode#codebookGainPower}.
     */
    private float currentGainEnergy[] = new float[3];

    /**
     * A logarithm of the previous subframe gain, the last value of
     * {@link UOFrameDecode#currentGainLevel}. Hence it has the same range: -32 to +28.
     */
    private float previousGainLevel = -32.0f;

    /**
     * A collection of gain energy over a previous frame, one subframe behind
     * {@link UOFrameDecode#currentGainEnergy}. Likewise this is used to compute
     * {@link UOFrameDecode#codebookGainPower}.
     */
    private float previousGainEnergy[] = new float[3];

    /**
     * A gain multiplier that is applied to the {@link UOFrameDecode#currentGainLevel} to compute
     * the actual gain to apply to the subframe.
     *
     * This is updated most subframes from the ratio between
     * {@link UOFrameDecode#previousGainEnergy} and {@link UOFrameDecode#currentGainEnergy}.
     * Takes values from {@link UOTables#CODEBOOK_GAIN_POWER_RATIOS_AND_VALUES} or
     * {@link UOTables#FALLBACK_CODEBOOK_GAIN_POWER}, and so can take values between -0.10 and
     * +0.92.
     */
    private float codebookGainPower;

    /**
     * A sliding window of recent pre-LCP-filtered sample values, as indexed by the subframe lag
     * decoded from the packed frame. The top (highest-index) 48 values are regenerated every
     * subframe.
     */
    private float lagBuffer[] = new float[169];

    /**
     * Reset the frame decoder state.
     *
     * This need not be called on a newly-constructed instance of this class, which will already
     * be in the reset state.
     */
    public void resetState() {
        prevLsf = null;
        synthesisBuffer = new float[10];
        currentGainLevel = -32.0f;
        currentGainEnergy = new float[3];
        previousGainLevel = -32.0f;
        previousGainEnergy = new float[3];
        lagBuffer = new float[169];
        codebookGainPower = 0f;
    }

    /**
     * Reads a portion of a byte array bit-by-bit.
     */
    static class BitReader {
        /**
         * The input buffer for the bits we're reading
         */
        private final byte[] buffer;

        /**
         * Offset into {@link com.hviper.codec.uodecode.UOFrameDecode.BitReader#buffer} of the
         * current whole or part byte that we're extracting bits from.
         */
        private int cursorByte;

        /**
         * The next bit to take from the current byte, starting at 0 for the least-significant bit
         * of the byte.
         */
        private int cursorBit;

        /**
         * Construct a new bit reader starting at the given offset in the buffer passed.
         *
         * @param buffer the buffer to read bits from
         * @param startOffset the first byte to read bits from
         */
        public BitReader(byte[] buffer, int startOffset) {
            this.buffer = buffer;
            this.cursorByte = startOffset;
            this.cursorBit = 0;
        }

        /**
         * Returns the specified number of bits from the input. The first bits read are treated as
         * the least-significant bits, and assembled in the order they were present in the byte,
         * e.g. if reading six bits that span two bytes as follows:
         *
         *    byte 1:   56xxxxxx
         *    byte 2:   xxxx1234
         *
         * the result will be '123456', which the four 'xxxx' bits in byte 2 the next to be read.
         *
         * @param bitCount the number of bits to read, up to 32
         * @return an integer containing the given number of bits, assembled in the order they
         *         appear in the constituent bytes with earlier bytes treated as least-significant.
         */
        public int getBits(int bitCount) {
            /**
             * The output assembled so far
             */
            int result = 0;
            /**
             * The total number of bits already read into result.
             */
            int resultBitCursor = 0;

            while ((bitCount > 0) && (cursorByte < buffer.length)) {
                int bitsLeftInCurrentByte = 8 - cursorBit;
                int currentBits = (buffer[cursorByte] & 0xff) >> cursorBit;

                int bitsClaimed = bitsLeftInCurrentByte;
                if (bitCount < bitsClaimed) {
                    bitsClaimed = bitCount;
                    currentBits &= ((1 << bitCount) - 1);
                }
                result |= (currentBits << resultBitCursor);

                cursorBit += bitsClaimed;
                if (cursorBit >= 8) {
                    ++cursorByte;
                    cursorBit = 0;
                }
                bitCount -= bitsClaimed;
                resultBitCursor += bitsClaimed;
            }
            return result;
        }
    }

    /**
     * Decode a frame of UO from a byte-aligned section of an input buffer. Packed frames are
     * 48 bytes long.
     *
     * @param input the input buffer to read the packed UO frame from
     * @param startOffset the byte offset of the first byte of the packed frame
     * @return an array of output samples for the frame, as floating point numbers in the range
     *         -1024 to +1024 (although these are not verified or clipped here)
     */
    public float[] decodeFrame(byte[] input, int startOffset) {
        BitReader bitReader = new BitReader(input, startOffset);
        float output[] = new float[192];

        /*
         * The first bits in the packed frame are the subframe lag coefficients and lag index for
         * each of the four subframes. Read these first.
         */
        float subframeLagCoefficients[][] = new float[4][];
        int subframeLag[] = new int[4];
        for(int subframe = 0; subframe < 4; ++subframe) {
            subframeLagCoefficients[subframe] =
                    UOTables.SUBFRAME_LAG_COEFFICIENTS[bitReader.getBits(6)];
            subframeLag[subframe] = bitReader.getBits(7);
        }

        /*
         * The next bits are lookup indexes for the ten LSF values that are interpolated between
         * frames and used to compute the LPC values for output synthesis. These are read from
         * varying numbers of index bits, decreasing towards the later values (which form the
         * later LPC coefficients, i.e. the ones that predict from oldest previous output
         * samples).
         */
        float lsf[] = new float[10];
        for(int i = 0; i < 10; ++i) {
            lsf[i] = UOTables.LSF_TABLE[i][bitReader.getBits(UOTables.LSF_INDEX_BITS[i])];
        }

        /*
         * For each of the four subframes:
         */
        for(int subframe = 0; subframe < 4; ++subframe) {
            /*
             * Interpolate the LSF values across the frame from the previous frame's values, where
             * available. If this is the first frame (or we have reset state between frames) use
             * the current frame's values only to generate the LPC.
             */
            float lpc[];
            if (prevLsf != null) {
                float interpolatedLsf[] = interpolateLsf(prevLsf, lsf, subframe);
                lpc = lsfToLpc(interpolatedLsf);
            } else {
                lpc = lsfToLpc(lsf);
            }

            /*
             * Advance the lag buffer by 48 places (the number of samples we'll generate this
             * subframe) to make room for the new values from this subframe.
             */
            for(int i = 48; i < lagBuffer.length; ++i) {
                lagBuffer[i - 48] = lagBuffer[i];
            }

            /*
             * For each of the twelve values per subframe:
             */
            for(int value = 0; value < 12; ++value) {
                /** The index of this value within the whole frame, 0-47 */
                int index = (subframe * 12) + value;

                /*
                 * Update the gain energy buffers, remembering the previous top value of the gain
                 * energy which might be used below in the ratio calculation.
                 */
                float initialGainEnergy2 = currentGainEnergy[2];
                updateGainEnergy(currentGainLevel, currentGainLevel, currentGainEnergy);
                updateGainEnergy(currentGainLevel, previousGainLevel, previousGainEnergy);

                /*
                 * If this isn't the first subframe of the frame, use the ratio between the two
                 * gain energies to select a new codebook gain power value from the ratio tables.
                 */
                if ((subframe != 0) && (value == 0)) {
                    float currentEnergy = (initialGainEnergy2 * 0.8836f) + currentGainEnergy[2];
                    float previousEnergy = previousGainEnergy[2] * 1.88f;

                    codebookGainPower = UOTables.FALLBACK_CODEBOOK_GAIN_POWER;
                    for(float ratioAndPower[] : UOTables.CODEBOOK_GAIN_POWER_RATIOS_AND_VALUES) {
                        if ((currentEnergy * ratioAndPower[0]) < previousEnergy) {
                            codebookGainPower = ratioAndPower[1];
                            break;
                        }
                    }
                }

                /*
                 * Compute the codebook gain for this value, both as a logarithmic (20log10) level
                 * between -32 and +28 and as an absolute gain from 1-1000.
                 */
                float codebookGainLevel = codebookGainPower * currentGainLevel;
                if (codebookGainLevel < -32f) codebookGainLevel = -32f;
                if (codebookGainLevel > 28f) codebookGainLevel = 28f;
                float codebookGain = (float)Math.pow(10.0, (codebookGainLevel + 32.0) / 20.0);

                /*
                 * Read the codebook sign and index for these values.
                 */
                boolean codebookSign = (bitReader.getBits(1) != 0);
                int codebookIndex = bitReader.getBits(5);

                /*
                 * Use the codebook index to update the gain level for the next set of values from
                 * the delta gain in the codebook.
                 */
                previousGainLevel = currentGainLevel;
                currentGainLevel = codebookGainLevel +
                        UOTables.CODEBOOK_DELTA_GAIN[codebookIndex];

                /*
                 * Update the gain level with the sign from the codebook.
                 *
                 * Defer computing the scaled codebook vector; we'll do that as we need the values
                 * later.
                 */
                float codebookVector[] = UOTables.CODEBOOK_VECTOR_TABLE[codebookIndex];
                if (codebookSign) codebookGain = -codebookGain;

                /*
                 * Compute the pitch vector from the lag buffer. We will write compute values from
                 * this subframe into the last 48 entries of the lag buffer; use the subframe lag
                 * index as an offset from the place we're about to write to.
                 *
                 * (Note that the buffer here isn't quite big enough to support full seven-bit
                 * inputs for lag offset for the first subframe of a frame.)
                 */
                float pitchVector[] = new float[4];
                int lagBufferWriteOffset = lagBuffer.length - 48 + (value * 4);
                int lagBufferReadOffset = lagBufferWriteOffset - subframeLag[subframe] - 1;
                float lagCoefficients[] = subframeLagCoefficients[subframe];
                for(int i = 0; i < 4; ++i) {
                    pitchVector[i] = (
                            (lagBuffer[lagBufferReadOffset + i] * lagCoefficients[2])
                            + (lagBuffer[lagBufferReadOffset + i + 1] * lagCoefficients[1])
                            + (lagBuffer[lagBufferReadOffset + i + 2] * lagCoefficients[0]));
                }

                /*
                 * Combine the pitch vector with the scaled codebook vector, also writing the
                 * (pre-synthesised) values back into the lag buffer in the appropriate spot.
                 */
                float combinedVector[] = new float[4];
                for (int i = 0; i < 4; ++i) {
                    float scaledCodebookVectorValue = codebookGain * codebookVector[i];
                    combinedVector[i] = scaledCodebookVectorValue + pitchVector[i];
                    lagBuffer[lagBufferWriteOffset + i] = combinedVector[i];
                }

                /*
                 * Use the combined values, LPC and the previous ten samples generated for LPC
                 * synthesis, to generate the final output values.
                 */
                lpSynthesisFilter(combinedVector, synthesisBuffer, lpc);

                /*
                 * Take four synthesised values as our final output. Note that we take lag the
                 * newly synthesised values by one, i.e. we take the last synthesised sample from
                 * the last set of values and three from this set, leaving the final value for the
                 * first of the next set.
                 *
                 * These samples are generated in the range -1024 to +1024. We do not (currently)
                 * clip values here, nor monitor excessive clipping for bad frames.
                 */
                int synthesisBufferOffset = synthesisBuffer.length - 5;
                for (int i = 0; i < 4; ++i) {
                    output[(index * 4) + i] = synthesisBuffer[synthesisBufferOffset + i];
                }
            }
        }

        /*
         * Save the LSF from this frame for interpolation in the next frame.
         */
        prevLsf = lsf;

        return output;
    }

    /**
     * Apply the Linear Prediction Coefficients to the four new input values and a sliding buffer
     * of the previous ten output samples to produce four output samples. Includes updatingg the
     * previous-ten-output buffer with the four new output values.
     *
     * @param combinedVector four new values for synthesis
     * @param synthesisBuffer a buffer of the previous ten output values generated, for linear
     *                        prediction
     * @param lpc the linear prediction coefficients, applied 0 to the new sample then 1-10 to the
     *            previous ten output samples, 1 to the most recent of them.
     */
    private static void lpSynthesisFilter(float[] combinedVector, float[] synthesisBuffer, float[] lpc) {
        /*
         * Compute the first new output value from the first input and applying the LPC
         * coefficients to the previous ten samples from them synthesis buffer.
         * The first line assumes lpc[0] == 1, as it always is, and so does not multiply by it.
         */
        float o0 = combinedVector[0];
        for(int i = 1; i < 11; ++i) o0 -= lpc[i] * synthesisBuffer[10 - i];

        /*
         * Compute the second new output from the input and LPC over the value just generated and
         * the last nine from the buffer.
         */
        float o1 = combinedVector[1];
        o1 -= lpc[1] * o0;
        for(int i = 2; i < 11; ++i) o1 -= lpc[i] * synthesisBuffer[11 - i];

        /*
         * Compute the third from the input and LPC over the two values just generated and the
         * last eight from the buffer.
         */
        float o2 = combinedVector[2];
        o2 -= lpc[1] * o1;
        o2 -= lpc[2] * o0;
        for(int i = 3; i < 11; ++i) o2 -= lpc[i] * synthesisBuffer[12 - i];

        /*
         * Finally the fourth from the input and LPC over the three values just generated and
         * the last seven from the buffer.
         */
        float o3 = combinedVector[3];
        o3 -= lpc[1] * o2;
        o3 -= lpc[2] * o1;
        o3 -= lpc[3] * o0;
        for(int i = 4; i < 11; ++i) o3 -= lpc[i] * synthesisBuffer[13 - i];

        /*
         * Shuffle the buffer up four spaces to make room for the new output samples.
         */
        for(int i = 4; i < 10; ++i) synthesisBuffer[i - 4] = synthesisBuffer[i];

        /*
         * Store the new output samples in the synthesis buffer.
         */
        synthesisBuffer[6] = o0;
        synthesisBuffer[7] = o1;
        synthesisBuffer[8] = o2;
        synthesisBuffer[9] = o3;
    }

    /**
     * Ratio used to decay previous values in the gain energy buffer.
     */
    private static float GAIN_ENERGY_FACTOR = 0.94f * 0.94f;

    /**
     * Feed the current gain level into the gain energy buffer, used for gain energy ratio
     * calculations.
     *
     * @param gain1 the current gain level, -28 to +32
     * @param gain2 either the current or previous gain level, -28 to +32, depending on which
     *              buffer we're updating
     * @param energy the gain energy buffer
     * @return the top value from the updated buffer, used in gain energy calculations.
     */
    private static float updateGainEnergy(float gain1, float gain2, float[] energy) {
        /** The two gain levels multiplied, added to each entry of the energy buffer */
        float accumulator = gain1 * gain2;
        for(int i = 0; i < energy.length; ++i) {
            /*
             * Decay the current value by 6% (squared) and add the new value plus all of the
             * previous decayed value.
             */
            accumulator += GAIN_ENERGY_FACTOR * energy[i];
            energy[i] = accumulator;
        }
        return energy[energy.length - 1];
    }

    /**
     * Compute the Linear Prediction Coefficients (LPC) from the interpolated LSF values read per
     * frame.
     *
     * @param lsf the input interpolated LSF values, 10 entries
     * @return the computed LPC values, 11 entries.
     */
    private static float[] lsfToLpc(float[] lsf) {
        float lpc[] = new float[lsf.length + 1];

        /*
         * The first LPC value is the coefficient for the newly computed output value and so is
         * always one.
         */
        lpc[0] = 1.0f;

        /*
         * For each new LSF value store it as the next LPC and then apply it in a polynomial-like
         * expansion to the previous LPC values.
         */
        for (int i = 1; i < lsf.length + 1; ++i) {
            float fl = lsf[i - 1];
            lpc[i] = fl;

            int a = 1;
            int b = i - 1;
            while (b >= a) {
                float fa = lpc[a];
                float fb = lpc[b];
                lpc[a] = (fl * fb) + fa;
                lpc[b] = (fl * fa) + fb;
                ++a;
                --b;
            }
        }

        return lpc;
    }

    /**
     * Interpolate the LSF values for a subframe, stepping 25% towards the new values for each
     * subframe, i.e.
     *
     * subframe 0: 75% old, 25% new
     * subframe 1: 50% old, 50% new
     * subframe 2: 25% old, 75% new
     * subframe 3: 100% new
     *
     * @param prevLsf the previous frame's LSF values; must be the same length as lsf
     * @param lsf the new frame's LSF values
     * @param subframe the current subframe number, from 0-3
     * @return an array of interpolated values, the same length as the lsf parameter
     */
    private static float[] interpolateLsf(float[] prevLsf, float[] lsf, int subframe) {
        float result[] = new float[lsf.length];
        float newRatio = 0.25f * (subframe + 1);
        float oldRatio = 1.0f - newRatio;

        for(int i = 0; i < lsf.length; ++i) {
            result[i] = oldRatio * prevLsf[i] + newRatio * lsf[i];
        }

        return result;
    }
}
