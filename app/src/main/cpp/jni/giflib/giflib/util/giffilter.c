/******************************************************************************
 
giffilter.c - skeleton file for generic GIF `filter' program 

Sequentially read GIF records from stdin, process them, send them out.
Most of the junk above `int main' isn't needed for the skeleton, but
is likely to be for what you'll do with it.

If you compile this, it will turn into an expensive GIF copying routine;
stdin to stdout with no changes and minimal validation.  Well, it's a
decent test of the low-level routines, anyway.

Note: due to the vicissitudes of Lempel-Ziv compression, the output of this
copier may not be bitwise identical to its input.  This can happen if you
copy an image from a much more (or much *less*) memory-limited system; your
compression may use more (or fewer) bits.  The uncompressed rasters should,
however, be identical (you can check this with gifbuild -d).

******************************************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>

#include "getarg.h"
#include "gif_lib.h"

#define PROGRAM_NAME	"giffilter"

/******************************************************************************
 Close both input and output file (if open), and exit.
******************************************************************************/
static void QuitGifError(GifFileType *GifFileIn, GifFileType *GifFileOut)
{
    if (GifFileIn != NULL) {
	PrintGifError(GifFileIn->Error);
	EGifCloseFile(GifFileIn, NULL);
    }
    if (GifFileOut != NULL) {
	PrintGifError(GifFileOut->Error);
	EGifCloseFile(GifFileOut, NULL);
    }
    exit(EXIT_FAILURE);
}

/******************************************************************************
 Main sequence
******************************************************************************/
int main(int argc, char **argv)
{
    GifFileType *GifFileIn = NULL, *GifFileOut = NULL;
    GifRecordType RecordType;
    int CodeSize, ExtCode, ErrorCode;
    GifByteType *CodeBlock, *Extension;

    /*
     * Command-line processing goes here.
     */

    /* Use stdin as input (note this also read screen descriptor in: */
    if ((GifFileIn = DGifOpenFileHandle(0, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /* Use the stdout as output: */
    if ((GifFileOut = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /* And dump out its screen information: */
    if (EGifPutScreenDesc(GifFileOut,
	GifFileIn->SWidth, GifFileIn->SHeight,
	GifFileIn->SColorResolution, GifFileIn->SBackGroundColor,
	GifFileIn->SColorMap) == GIF_ERROR)
	QuitGifError(GifFileIn, GifFileOut);

    /* Scan the content of the input GIF file and load the image(s) in: */
    do {
	if (DGifGetRecordType(GifFileIn, &RecordType) == GIF_ERROR)
	    QuitGifError(GifFileIn, GifFileOut);

	switch (RecordType) {
	    case IMAGE_DESC_RECORD_TYPE:
		if (DGifGetImageDesc(GifFileIn) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		/* Put image descriptor to out file: */
		if (EGifPutImageDesc(GifFileOut,
		    GifFileIn->Image.Left, GifFileIn->Image.Top,
		    GifFileIn->Image.Width, GifFileIn->Image.Height,
		    GifFileIn->Image.Interlace,
		    GifFileIn->Image.ColorMap) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);

		/* Now read image itself in decoded form as we dont really   */
		/* care what we have there, and this is much faster.	     */
		if (DGifGetCode(GifFileIn, &CodeSize, &CodeBlock) == GIF_ERROR ||
		    EGifPutCode(GifFileOut, CodeSize, CodeBlock) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		while (CodeBlock != NULL) {
		    if (DGifGetCodeNext(GifFileIn, &CodeBlock) == GIF_ERROR ||
			EGifPutCodeNext(GifFileOut, CodeBlock) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		}
		break;
	    case EXTENSION_RECORD_TYPE:
		/* pass through extension records */
		if (DGifGetExtension(GifFileIn, &ExtCode, &Extension) == GIF_ERROR || Extension == NULL)
		    QuitGifError(GifFileIn, GifFileOut);
		if (EGifPutExtensionLeader(GifFileOut, ExtCode) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		if (EGifPutExtensionBlock(GifFileOut, 
					  Extension[0],
					  Extension + 1) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		while (Extension != NULL) {
		    if (DGifGetExtensionNext(GifFileIn, &Extension)==GIF_ERROR)
			QuitGifError(GifFileIn, GifFileOut);
		    if (Extension != NULL)
			if (EGifPutExtensionBlock(GifFileOut, 
						  Extension[0],
						  Extension + 1) == GIF_ERROR)
			    QuitGifError(GifFileIn, GifFileOut);
		}
		if (EGifPutExtensionTrailer(GifFileOut) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		break;
	    case TERMINATE_RECORD_TYPE:
		break;
	    default:	     /* Should be trapped by DGifGetRecordType */
		break;
	}
    }
    while (RecordType != TERMINATE_RECORD_TYPE);

    if (DGifCloseFile(GifFileIn, &ErrorCode) == GIF_ERROR)
    {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }
    if (EGifCloseFile(GifFileOut, &ErrorCode) == GIF_ERROR)
    {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    return 0;
}

/* end */
