This is a decode-only implementation of speech audio codec I'll call U.O.

U.O. is a CELP codec, transmitting 8 KHz mono audio in 16 Kb/s plus 3-4% for in-stream headers. It
uses 192 samples (24 ms) per frame. This implementation supports full-rate frames only. U.O. is
not commonly used nowadays as more modern (and more widely accepted) speech codecs exist at
similar or better bit rates, but the quality's pretty good.

Output from this implementation mostly matches the output from a reference decoder barring a few
of the least significant bits in a small fraction of 16-bit samples, and certainly without any
audible difference. This was written to work without any particular thought for performance: it
won't be slow, but could likely be faster.

This project builds an executable .jar with no dependencies. It has a simple command-line
interface you can use to convert U.O. files. If you want to transcode U.O. programatically then
see either `UODecode.uoToPcm16Wav()` or `UODecode.uoToMuLawWav()`.

Licence: if this code is useful to you, i.e. you have a source of U.O. files or a large corpus of
U.O. files, then you'll already know if you're allowed to use it or not. For what it's worth my
work here is licensed under [the MIT Licence](http://choosealicense.com/licenses/mit/). In
particular no algorithm licence or patent grant is included with this code, because it's not mine
to give. User beware.

Third-party files: `src/test/resources` contains an audio sample taken from the [the Opus
website](http://www.opus-codec.org/examples/), which is licensed under [CC BY
3.0](http://creativecommons.org/licenses/by/3.0/deed.en_US). This is used for a confidence test of
the decoder and isn't included in the binary distribution.

This was written just-in-case for a one-off problem so I make no promises I'll continue to develop
this code. I have plenty of ideas for refactoring and improvements (more tests, better exceptions!)
but this current version meets my needs.
