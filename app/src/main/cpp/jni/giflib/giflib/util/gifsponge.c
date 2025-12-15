/****************************************************************************

gifsponge.c - skeleton file for generic GIF `sponge' program

Slurp a GIF into core, operate on it, spew it out again.  Most of the
junk above `int main' isn't needed for the skeleton, but is likely to
be for what you'll do with it.

If you compile this, it will turn into an expensive GIF copying routine;
stdin to stdout with no changes and minimal validation.  Well, it's a
decent test of DGifSlurp() and EGifSpew(), anyway.

Note: due to the vicissitudes of Lempel-Ziv compression, the output of this
copier may not be bitwise identical to its input.  This can happen if you
copy an image from a much more (or much *less*) memory-limited system; your
compression may use more (or fewer) bits.  The uncompressed rasters should,
however, be identical (you can check this with gifbuild -d).

****************************************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>

#include "getarg.h"
#include "gif_lib.h"

#define PROGRAM_NAME	"gifsponge"

int main(int argc, char **argv)
{
    int	i, ErrorCode;
    GifFileType *GifFileIn, *GifFileOut = (GifFileType *)NULL;

    if ((GifFileIn = DGifOpenFileHandle(0, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }
    if (DGifSlurp(GifFileIn) == GIF_ERROR) {
	PrintGifError(GifFileIn->Error);
	exit(EXIT_FAILURE);
    }
    if ((GifFileOut = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /*
     * Your operations on in-core structures go here.  
     * This code just copies the header and each image from the incoming file.
     */
    GifFileOut->SWidth = GifFileIn->SWidth;
    GifFileOut->SHeight = GifFileIn->SHeight;
    GifFileOut->SColorResolution = GifFileIn->SColorResolution;
    GifFileOut->SBackGroundColor = GifFileIn->SBackGroundColor;
    if (GifFileIn->SColorMap) {
	GifFileOut->SColorMap = GifMakeMapObject(
				   GifFileIn->SColorMap->ColorCount,
				   GifFileIn->SColorMap->Colors);
    } else {
	GifFileOut->SColorMap = NULL;
    }

    for (i = 0; i < GifFileIn->ImageCount; i++)
	(void) GifMakeSavedImage(GifFileOut, &GifFileIn->SavedImages[i]);

    /*
     * Note: don't do DGifCloseFile early, as this will
     * deallocate all the memory containing the GIF data!
     *
     * Further note: EGifSpew() doesn't try to validity-check any of this
     * data; it's *your* responsibility to keep your changes consistent.
     * Caveat hacker!
     */
    if (EGifSpew(GifFileOut) == GIF_ERROR)
	PrintGifError(GifFileOut->Error);

    if (DGifCloseFile(GifFileIn, &ErrorCode) == GIF_ERROR)
	PrintGifError(ErrorCode);

    return 0;
}

/* end */
