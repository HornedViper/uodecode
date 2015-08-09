This directory contains test audio files for peak signal-to-noise ratio confidence checks
in the decoder.

* Opus example speech files from [the Opus website](http://www.opus-codec.org/examples/).    
  The Opus website content (and hence these files) is licensed under [CC BY
  3.0](http://creativecommons.org/licenses/by/3.0/deed.en_US).
  * opus-speech.uo   
    This is the speech example which was then resampled as 8 KHz 16-bit signed PCM audio
    using ffmpeg, fixed so that it would work with the reference U.O. encoder (the 'LIST'
    chunk replaced with a 'fact' chunk) and then encoded using the U.O. reference encoder.
  * opus-speech-reference.wav   
    This is the above U.O. file decoded back to 16-bit signed PCM using the reference
    encoder.
