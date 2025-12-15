/*****************************************************************************

gifhisto - make a color histogram from image color frequencies

*****************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gifhisto"

#define DEFAULT_HISTO_WIDTH	100	      /* Histogram image diemnsions. */
#define DEFAULT_HISTO_HEIGHT	256
#define HISTO_BITS_PER_PIXEL	2	/* Size of bitmap for histogram GIF. */

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
	" v%- t%- s%-Width|Height!d!d n%-ImageNumber!d b%- h%- GifFile!*s";

static int
    ImageWidth = DEFAULT_HISTO_WIDTH,
    ImageHeight = DEFAULT_HISTO_HEIGHT,
    ImageN = 1;
static GifColorType
    HistoColorMap[] = {			 /* Constant bit map for histograms: */
	{ 0, 0, 0 },
	{ 255,   0,   0 },
	{   0, 255,   0 },
	{   0,   0, 255 }
    };

static void QuitGifError(GifFileType *GifFileIn, GifFileType *GifFileOut);

/******************************************************************************
 Interpret the command line and scan the given GIF file.
******************************************************************************/
int main(int argc, char **argv)
{
    int	i, j, ErrorCode, NumFiles, ExtCode, CodeSize, NumColors = 2, ImageNum = 0;
    bool Error, TextFlag = false, SizeFlag = false,
	ImageNFlag = false, BackGroundFlag = false, HelpFlag = false;
    long Histogram[256];
    GifRecordType RecordType;
    GifByteType *Extension, *CodeBlock;
    char **FileName = NULL;
    GifRowType Line;
    GifFileType *GifFileIn = NULL, *GifFileOut = NULL;

    /* Same image dimension vars for both Image & ImageN as only one allowed */
    if ((Error = GAGetArgs(argc, argv, CtrlStr, &GifNoisyPrint,
		&TextFlag, &SizeFlag, &ImageWidth, &ImageHeight,
		&ImageNFlag, &ImageN, &BackGroundFlag,
		&HelpFlag, &NumFiles, &FileName)) != false ||
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
    else {
	/* Use stdin instead: */
	if ((GifFileIn = DGifOpenFileHandle(0, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }

    for (i = 0; i < 256; i++) Histogram[i] = 0;		  /* Reset counters. */

    /* Scan the content of the GIF file and load the image(s) in: */
    do {
	if (DGifGetRecordType(GifFileIn, &RecordType) == GIF_ERROR)
	    QuitGifError(GifFileIn, GifFileOut);

	switch (RecordType) {
	    case IMAGE_DESC_RECORD_TYPE:
		if (DGifGetImageDesc(GifFileIn) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);

		if (GifFileIn->Image.ColorMap)
		    NumColors = GifFileIn->Image.ColorMap->ColorCount;
		else if (GifFileIn->SColorMap)
		    NumColors = GifFileIn->SColorMap->ColorCount;
		else
		    GIF_EXIT("Neither Screen nor Image color map exists.");

		if ((ImageHeight / NumColors) * NumColors != ImageHeight)
		    GIF_EXIT("Image height specified not dividable by #colors.");

		if (++ImageNum == ImageN) {
		    /* This is the image we should make histogram for:       */
		    Line = (GifRowType) malloc(GifFileIn->Image.Width *
							sizeof(GifPixelType));
		    GifQprintf("\n%s: Image %d at (%d, %d) [%dx%d]:     ",
			PROGRAM_NAME, ImageNum,
			GifFileIn->Image.Left, GifFileIn->Image.Top,
			GifFileIn->Image.Width, GifFileIn->Image.Height);

		    for (i = 0; i < GifFileIn->Image.Height; i++) {
			if (DGifGetLine(GifFileIn, Line, GifFileIn->Image.Width)
			    == GIF_ERROR)
			    QuitGifError(GifFileIn, GifFileOut);
			for (j = 0; j < GifFileIn->Image.Width; j++)
			    Histogram[Line[j]]++;
			GifQprintf("\b\b\b\b%-4d", i);
		    }

		    free((char *) Line);
		}
		else {
		    /* Skip the image: */
		    /* Now read image itself in decoded form as we dont      */
		    /* really care what is there, and this is much faster.   */
		    if (DGifGetCode(GifFileIn, &CodeSize, &CodeBlock) == GIF_ERROR)
			QuitGifError(GifFileIn, GifFileOut);
		    while (CodeBlock != NULL)
			if (DGifGetCodeNext(GifFileIn, &CodeBlock) == GIF_ERROR)
			    QuitGifError(GifFileIn, GifFileOut);
		}
		break;
	    case EXTENSION_RECORD_TYPE:
		/* Skip any extension blocks in file: */
		if (DGifGetExtension(GifFileIn, &ExtCode, &Extension) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);

		while (Extension != NULL) {
		    if (DGifGetExtensionNext(GifFileIn, &Extension) == GIF_ERROR)
			QuitGifError(GifFileIn, GifFileOut);
		}
		break;
	    case TERMINATE_RECORD_TYPE:
		break;
	    default:		    /* Should be trapped by DGifGetRecordType. */
		break;
	}
    }
    while (RecordType != TERMINATE_RECORD_TYPE);

    /* We requested suppression of the background count: */
    if (BackGroundFlag) Histogram[GifFileIn->SBackGroundColor] = 0;

    if (DGifCloseFile(GifFileIn, &ErrorCode) == GIF_ERROR)
    {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }


    /* We may required to dump out the histogram as text file: */
    if (TextFlag) {
	for (i = 0; i < NumColors; i++)
	    printf("%12ld  %3d\n", Histogram[i], i);
    }
    else {
	int Color, Count;
	long Scaler;
	/* Open stdout for the histogram output file: */
	if ((GifFileOut = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}

	/* Dump out screen descriptor to fit histogram dimensions: */
	if (EGifPutScreenDesc(GifFileOut,
	    ImageWidth, ImageHeight, HISTO_BITS_PER_PIXEL, 0,
	    GifMakeMapObject(4, HistoColorMap)) == GIF_ERROR)
		QuitGifError(GifFileIn, GifFileOut);

	/* Dump out image descriptor to fit histogram dimensions: */
	if (EGifPutImageDesc(GifFileOut,
			     0, 0, ImageWidth, ImageHeight, false, NULL) == GIF_ERROR)
		QuitGifError(GifFileIn, GifFileOut);

	/* Prepare scan line for histogram file, and find scaler to scale    */
	/* histogram to be between 0 and ImageWidth:			     */
	Line = (GifRowType) malloc(ImageWidth * sizeof(GifPixelType));
	for (Scaler = 0, i = 0; i < NumColors; i++) if (Histogram[i] > Scaler)
	    Scaler = Histogram[i];
	Scaler /= ImageWidth;
	if (Scaler == 0) Scaler = 1;  /* In case maximum is less than width. */

	/* Dump out the image itself: */
	for (Count = ImageHeight, i = 0, Color = 1; i < NumColors; i++) {
	    int Size;
	    if ((Size = Histogram[i] / Scaler) > ImageWidth) Size = ImageWidth;
	    for (j = 0; j < Size; j++)
		Line[j] = Color;
	    for (j = Size; j < ImageWidth; j++)
		Line[j] = GifFileOut->SBackGroundColor;

	    /* Move to next color: */
	    if (++Color >= (1 << HISTO_BITS_PER_PIXEL)) Color = 1;

	    /* Dump this histogram entry as many times as required: */
	    for (j = 0; j < ImageHeight / NumColors; j++) {
		if (EGifPutLine(GifFileOut, Line, ImageWidth) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		GifQprintf("\b\b\b\b%-4d", Count--);
	    }
	}

	if (EGifCloseFile(GifFileOut, &ErrorCode) == GIF_ERROR)
	{
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }

    return 0;
}

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

/* end */
