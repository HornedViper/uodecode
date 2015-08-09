package com.hviper.codec.uodecode;

/**
 * UO codebook and other decoding tables.
 *
 * These are specified as integer values which are divided by powers of two before use in the
 * algorithm. Here we perform this division now as we assemble the float arrays of the prepared
 * constants.
 */
class UOTables {
    /**
     * Given a varargs array of integers and a float divisor, construct a float array of the same
     * length with each integer divided by the divisor.
     */
    private static float[] fx(float divisor, int... n) {
        float[] f = new float[n.length];
        for (int i = 0; i < n.length; ++i) {
            f[i] = ((float)n[i]) / divisor;
        }
        return f;
    }
    /**
     * Divide these integers by 2^15.
     */
    private static float[] f15(int... n) { return fx(32768.0f, n); }
    /**
     * Divide these integers by 2^13.
     */
    private static float[] f13(int... n) { return fx( 8192.0f, n); }
    /**
     * Divide these integers by 2^2.
     */
    private static float[] f12(int... n) { return fx( 4096.0f, n); }

    /**
     * These are the coefficients applied to the pitch lag buffer to compute the pitch component
     * of the synthesis input vector. The first value is applied to the most recent value in the
     * lag buffer, and work backwards from there. These are always halved before use, and so have
     * are stored in the halved form here. Different lag coefficients and lag offsets are chosen
     * per subframe.
     */
    public static final float SUBFRAME_LAG_COEFFICIENTS[][] = {
            f15(      0,      0,      0 ),
            f15(   3003,   4159,   2511 ),
            f15(   9094,  13583,   9435 ),
            f15(   1085,   2590,    547 ),
            f15(   1026,  16932,  14154 ),
            f15(   7930,   8681,   7681 ),
            f15(   8980,  14967,   8031 ),
            f15(  -6636,  27045,   9831 ),
            f15(   4451,   7427,   4779 ),
            f15(   3754,  26088,   2270 ),
            f15(  13508,  17468,   1137 ),
            f15(  14209,  22743,  -6722 ),
            f15(  10229,   -465,   2310 ),
            f15(    691,   6446,   4087 ),
            f15(   8509,  20397,   3206 ),
            f15(   1364,   7450,    253 ),
            f15(   9787,  24630,  -2798 ),
            f15(   6245,  29873,  -4916 ),
            f15(  -2042,  27572,   5226 ),
            f15(   3558,   6031,  -3291 ),
            f15(    113,    -44,    439 ),
            f15(   1975,  15120,  13035 ),
            f15(   8383,  10063,   3112 ),
            f15(   -379,   4135,  -1231 ),
            f15(  12715,  15627,   1844 ),
            f15(   3857,  10817,   8170 ),
            f15(   3658,  20477,   7977 ),
            f15(   5127,   7345,    826 ),
            f15(  -1018,  10175,   7433 ),
            f15(  14143,  19889,  -5920 ),
            f15(  -5439,  20391,  14773 ),
            f15(  -2345,  21393,   9029 ),
            f15(  -2400,  29370,   -344 ),
            f15(   6714,  17917,  -3581 ),
            f15(    359,  25499,  -2057 ),
            f15(  -8674,  21380,  14614 ),
            f15(   2653,  11153,  -2884 ),
            f15(  -4360,   7079,   4791 ),
            f15(   1387,  20492, -12372 ),
            f15(   2408,   2747,   9004 ),
            f15(  -6656,  11479,   1898 ),
            f15(  -1898,   7159,  -1626 ),
            f15(   5740,  13561,   2338 ),
            f15(  -1011,   9361,  -6838 ),
            f15(   7425,  10840,  -1967 ),
            f15(   1674,  11487,   2533 ),
            f15(  -9077,  14205,   8557 ),
            f15(  -1415,   3845,   2438 ),
            f15(  -1938,  12024,  -1336 ),
            f15(   3154,  20840,   8119 ),
            f15(   9949,  12255,   9909 ),
            f15(  -3195,  15485,   5113 ),
            f15(  -1646,   9276,   2540 ),
            f15(  -8800,  13880,  -7340 ),
            f15(   2550,  15522,   6820 ),
            f15( -10754,  18685,  -2674 ),
            f15(   5963,  11781,  -8257 ),
            f15(  14472,  12047,  -5293 ),
            f15(  11891,   9821,  10400 ),
            f15(   1747,  19052,   1931 ),
            f15(   6592,  25948, -11065 ),
            f15(  -2812,  17014,  -3155 ),
            f15(   5474,  -4816,  16360 ),
            f15(  -6565,   6736,  -1984 ),
    };

    /**
     * The number of bits to read to determine the index for each successive
     * {@link UOTables#LSF_TABLE} entry. For example the first row has 64 possible values so
     * needs a 6-bit index, whereas the last row has 8 possible values and so needs only three
     * bits for the index. There are 46 bits here in total. The LSF values (and hence the LPC
     * values they compute) are changed every frame.
     */
    public static final int LSF_INDEX_BITS[] = { 6, 6, 5, 5, 4, 4, 4, 4, 3, 3 };

    /**
     * Look-up tables for each successive LSF value used to compute the Linear Prediction
     * Coefficients used for final synthesis of the output samples based on previously-generated
     * output. The initial values correspond to the effects of the most-recent sample and so have
     * larger magnitudes and more precisely-selected values.
     */
    public static final float LSF_TABLE[][] = {
            f15( -32651, -32558, -32463, -32362, -32261, -32161, -32058, -31943,
                 -31816, -31677, -31531, -31389, -31234, -31071, -30911, -30741,
                 -30552, -30335, -30131, -29915, -29676, -29416, -29148, -28871,
                 -28593, -28268, -27958, -27632, -27281, -26901, -26512, -26096,
                 -25605, -25117, -24633, -24121, -23563, -23003, -22372, -21690,
                 -20979, -20253, -19276, -18367, -17267, -16162, -15004, -13717,
                 -12312, -10748,  -8971,  -7125,  -5457,  -3372,  -1592,    174,
                   2622,   5094,   7534,   9871,  12724,  15773,  19324,  24116 ),

            f15( -26896, -22124, -18432, -15256, -12751, -10739,  -8930,  -7448,
                  -6169,  -5088,  -4017,  -3043,  -2043,  -1127,   -177,    593,
                   1369,   2158,   2978,   3822,   4686,   5531,   6430,   7327,
                   8113,   9005,   9834,  10674,  11488,  12282,  13062,  13936,
                  14709,  15482,  16211,  16917,  17705,  18429,  19186,  19888,
                  20505,  21162,  21837,  22498,  23050,  23600,  24150,  24657,
                  25176,  25699,  26175,  26660,  27133,  27617,  28084,  28574,
                  29042,  29513,  29965,  30380,  30798,  31250,  31749,  32653 ),

            f15( -27245, -25062, -23511, -22105, -20835, -19700, -18618, -17528,
                 -16401, -15323, -14353, -13347, -12367, -11374, -10311,  -9213,
                  -8120,  -6994,  -5799,  -4628,  -3467,  -2292,  -1075,    229,
                   1837,   3545,   5198,   6876,   9008,  11430,  14471,  18699 ),

            f15( -16768, -11510,  -8351,  -5721,  -3640,  -1877,   -360,    953,
                   2142,   3245,   4358,   5421,   6471,   7435,   8430,   9452,
                  10460,  11482,  12488,  13538,  14559,  15574,  16670,  17779,
                  18959,  20008,  21092,  22355,  23659,  25210,  26952,  28709 ),

            f15( -21421, -17381, -14380, -11962,  -9878,  -7929,  -6147,  -4417,
                  -2648,   -832,    999,   3151,   5634,   8570,  12739,  19532 ),

            f15(  -9634,  -5007,  -1968,    390,   2426,   4040,   5534,   7026,
                   8462,   9971,  11439,  13122,  15009,  17233,  19802,  23045 ),

            f15( -20451, -17085, -14483, -12014,  -9734,  -7827,  -6140,  -4573,
                  -2997,  -1445,    141,   1890,   3981,   6436,   9373,  13642 ),

            f15( -12322,  -8437,  -5747,  -3591,  -1824,   -328,   1032,   2374,
                   3614,   4945,   6266,   7773,   9511,  11663,  14247,  18179 ),

            f15( -17094, -12340,  -8649,  -5469,  -2609,    226,   3473,   8085 ),

            f15(  -8037,  -3630,   -698,   1720,   4053,   6449,   9144,  12718 )
    };

    /**
     * Tuples of energy ratios and codebook gain power values. If the ratio of the current gain
     * energy to the previous gain energy is between two successive ratio values here then the
     * second value is taken as the gain power for this subframe. If the ratio exceeds the top
     * value then the fallback value -0.1 below is used.
     */
    public static final float[][] CODEBOOK_GAIN_POWER_RATIOS_AND_VALUES = {
            {  32190 / 32768.0f, 0.92f },
            {  31482 / 32768.0f, 0.90f },
            {  30775 / 32768.0f, 0.88f },
            {  29890 / 32768.0f, 0.86f },
            {  28829 / 32768.0f, 0.83f },
            {  27415 / 32768.0f, 0.80f },
            {  25646 / 32768.0f, 0.75f },
            {  23877 / 32768.0f, 0.70f },
            {  22109 / 32768.0f, 0.65f },
            {  19456 / 32768.0f, 0.60f },
            {  15919 / 32768.0f, 0.50f },
            {  12381 / 32768.0f, 0.40f },
            {   7960 / 32768.0f, 0.30f },
            {   2654 / 32768.0f, 0.15f },
            {  -1768 / 32768.0f, 0.00f }
    };
    /**
     * The fallback codebook gain power value for when the energies ratio exceeds the largest
     * value in {@link UOTables#CODEBOOK_GAIN_POWER_RATIOS_AND_VALUES}.
     */
    public static final float FALLBACK_CODEBOOK_GAIN_POWER = -0.1f;

    /**
     * Four consecutive values for the codebook innovation part of the CELP synthesis, with a
     * sign bit and cumulative gain and power values applied. This is chosen for each of the
     * twelve values within a subframe.
     */
    public static final float[][] CODEBOOK_VECTOR_TABLE = {
            f12(  22121,  15251, -22182,   8509 ),
            f12(  26649, -15167,   4834,   -632 ),
            f12( -11594,   9911,  -8591,   9190 ),
            f12(  -2125,   -653,  21205,  29253 ),
            f12(   7904,   7263, -16050, -10413 ),
            f12(   3831,  28808,   5596, -29133 ),
            f12(  -9213,  18548,  -6515,  -1558 ),
            f12(  13657,  20022,  24688,  13796 ),
            f12(  10801,   1688,  -7373,   1157 ),
            f12(   8148,  -6858,   -914,   -631 ),
            f12(   2195,  -1658,  -8843,   5367 ),
            f12(   2494,  -4885,   -730,   6115 ),
            f12(   2550,   3187,  -6035,  -4193 ),
            f12(   3413,   8036,  -2000,  -9696 ),
            f12(  -5193,  -2796,  -3195,   3049 ),
            f12(  -2872,   3263,   7075,   4588 ),
            f12(  12433, -10905, -17041,   9587 ),
            f12(  12117,  -7497,   1951,   4792 ),
            f12(     69,   9261,  -9186,   6728 ),
            f12(   4103,   1405,   6634,  12567 ),
            f12(  10913,   3169,   1228,   1750 ),
            f12(   2216,  11248,   7320,  -8561 ),
            f12(    764,   8030,   1943,   3537 ),
            f12(   9229,   8364,   9223,   4193 ),
            f12(   6276,   -643,   -128,   -786 ),
            f12(   4878,  -5668,   6503,   -423 ),
            f12(   2731,    682,  -3006,   2809 ),
            f12(   4026,    582,   2227,   4704 ),
            f12(   1744,  -2621,   1597,     -3 ),
            f12(   3199,  -1886,   3758,  -5391 ),
            f12(  -1593,   1084,   1869,   2347 ),
            f12(    560,   3429,    782,    179 )
    };

    /**
     * The delta gain level (on the -32 to +28 logarithmic scale used internally) after each of
     * the values within a subframe. The rows correspond to the codebook rows above, i.e. are
     * indexed by the same value ignoring the sign bit, and applied after the current codebook
     * value has been synthesised, i.e. these input into the gain for the next codebook values
     * synthesised.
     */
    public static final float[] CODEBOOK_DELTA_GAIN = f13(
            105070,
             94805,
             62695,
            105725,
             70090,
            115500,
             69535,
            107755,
             34145,
             19055,
             19030,
               475,
              1835,
             33945,
             -7540,
             10440,
             81100,
             43790,
             41495,
             42360,
             24525,
             47950,
              6845,
             47880,
            -18025,
             13610,
            -35345,
            -15315,
            -59900,
             -5825,
            -59185,
            -59185
    );
}
