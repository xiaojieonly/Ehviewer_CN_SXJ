/*****************************************************************************

giffix - attempt to fix a truncated GIF

*****************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"giffix"

static char
    *VersionStr =
	PROGRAM_NAME
	VERSION_COOKIE
	"	Gershon Elber,	"
	__DATE__ ",   " __TIME__ "\n"
	"(C) Copyright 1989 Gershon Elber.\n";
static char
    *CtrlStr =
	PROGRAM_NAME
	" v%- h%- GifFile!*s";

static void QuitGifError(GifFileType *GifFileIn, GifFileType *GifFileOut);

/******************************************************************************
 Interpret the command line and scan the given GIF file.
******************************************************************************/
int main(int argc, char **argv)
{
    int	i, j, NumFiles, ExtCode, Row, Col, Width, Height, ErrorCode,
	DarkestColor = 0, ColorIntens = 10000;
    bool Error, HelpFlag = false;
    GifRecordType RecordType;
    GifByteType *Extension;
    char **FileName = NULL;
    GifRowType LineBuffer;
    ColorMapObject *ColorMap;
    GifFileType *GifFileIn = NULL, *GifFileOut = NULL;
    int ImageNum = 0;

    if ((Error = GAGetArgs(argc, argv, CtrlStr, &GifNoisyPrint, &HelpFlag,
		&NumFiles, &FileName)) != false ||
		(NumFiles > 1 && !HelpFlag)) {
	if (Error)
	    GAPrintErrMsg(Error);
	else if (NumFiles > 1)
	    GIF_MESSAGE("Error in command line parsing - one GIF file please.");
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }

    if (HelpFlag) {
	(void)fprintf(stderr, VersionStr, GIFLIB_MAJOR, GIFLIB_MINOR);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_SUCCESS);
    }

    if (NumFiles == 1) {
	if ((GifFileIn = DGifOpenFileName(*FileName, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }
    else
    {
	/* Use stdin instead: */
	if ((GifFileIn = DGifOpenFileHandle(0, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }

    /* Open stdout for the output file: */
    if ((GifFileOut = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /* Dump out exactly same screen information: */
    /* coverity[var_deref_op] */
    if (EGifPutScreenDesc(GifFileOut,
	GifFileIn->SWidth, GifFileIn->SHeight,
	GifFileIn->SColorResolution, GifFileIn->SBackGroundColor,
	GifFileIn->SColorMap) == GIF_ERROR)
	QuitGifError(GifFileIn, GifFileOut);

    if ((LineBuffer = (GifRowType) malloc(GifFileIn->SWidth)) == NULL)
	GIF_EXIT("Failed to allocate memory required, aborted.");

    /* Scan the content of the GIF file and load the image(s) in: */
    do {
	if (DGifGetRecordType(GifFileIn, &RecordType) == GIF_ERROR)
	    QuitGifError(GifFileIn, GifFileOut);

	switch (RecordType) {
	    case IMAGE_DESC_RECORD_TYPE:
		if (DGifGetImageDesc(GifFileIn) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		if (GifFileIn->Image.Interlace)
		    GIF_EXIT("Cannot fix interlaced images.");

		Row = GifFileIn->Image.Top; /* Image Position relative to Screen. */
		Col = GifFileIn->Image.Left;
		Width = GifFileIn->Image.Width;
		Height = GifFileIn->Image.Height;
		GifQprintf("\n%s: Image %d at (%d, %d) [%dx%d]:     ",
		    PROGRAM_NAME, ++ImageNum, Col, Row, Width, Height);
		if (Width > GifFileIn->SWidth)
		    GIF_EXIT("Image is wider than total");

		/* Put the image descriptor to out file: */
		if (EGifPutImageDesc(GifFileOut, Col, Row, Width, Height,
		    false, GifFileIn->Image.ColorMap) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);

		/* Find the darkest color in color map to use as a filler. */
		ColorMap = (GifFileIn->Image.ColorMap ? GifFileIn->Image.ColorMap :
						     GifFileIn->SColorMap);
		for (i = 0; i < ColorMap->ColorCount; i++) {
		    j = ((int) ColorMap->Colors[i].Red) * 30 +
			((int) ColorMap->Colors[i].Green) * 59 +
			((int) ColorMap->Colors[i].Blue) * 11;
		    if (j < ColorIntens) {
			ColorIntens = j;
			DarkestColor = i;
		    }
		}

		/* Load the image, and dump it. */
		for (i = 0; i < Height; i++) {
		    GifQprintf("\b\b\b\b%-4d", i);
		    if (DGifGetLine(GifFileIn, LineBuffer, Width)
			== GIF_ERROR) break;
		    if (EGifPutLine(GifFileOut, LineBuffer, Width)
			== GIF_ERROR) QuitGifError(GifFileIn, GifFileOut);
		}

		if (i < Height) {
		    fprintf(stderr,"\nFollowing error occurred (and ignored):");
		    PrintGifError(GifFileIn->Error);

		    /* Fill in with the darkest color in color map. */
		    for (j = 0; j < Width; j++)
			LineBuffer[j] = DarkestColor;
		    for (; i < Height; i++)
			if (EGifPutLine(GifFileOut, LineBuffer, Width)
			    == GIF_ERROR) QuitGifError(GifFileIn, GifFileOut);
		}
		break;
	    case EXTENSION_RECORD_TYPE:
		/* pass through extension records */
		if (DGifGetExtension(GifFileIn, &ExtCode, &Extension) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		if (EGifPutExtensionLeader(GifFileOut, ExtCode) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		if (Extension != NULL)
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
	    default:		    /* Should be trapped by DGifGetRecordType. */
		break;
	}
    }
    while (RecordType != TERMINATE_RECORD_TYPE);

    if (DGifCloseFile(GifFileIn, &ErrorCode) == GIF_ERROR) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }
    if (EGifCloseFile(GifFileOut, &ErrorCode) == GIF_ERROR) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }
    return 0;
}

/******************************************************************************
 Close both input and output file (if open), and exit.
******************************************************************************/
static void QuitGifError(GifFileType *GifFileIn, GifFileType *GifFileOut)
{
    fprintf(stderr, "\nFollowing unrecoverable error occured:");
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

/* end */
