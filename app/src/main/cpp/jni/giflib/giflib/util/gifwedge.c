/*****************************************************************************

gifwedge - create a GIF test pattern

*****************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gifwedge"

#define DEFAULT_WIDTH	640
#define DEFAULT_HEIGHT	350

#define DEFAULT_NUM_LEVELS	16     /* Number of colors to gen the image. */

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
	" v%- l%-#Lvls!d s%-Width|Height!d!d h%-";

static int
    NumLevels = DEFAULT_NUM_LEVELS,
    ImageWidth = DEFAULT_WIDTH,
    ImageHeight = DEFAULT_HEIGHT;

/******************************************************************************
 Interpret the command line and scan the given GIF file.
******************************************************************************/
int main(int argc, char **argv)
{
    int	i, j, l, c, LevelStep, LogNumLevels, ErrorCode, Count = 0; 
    bool Error, LevelsFlag = false, SizeFlag = false, HelpFlag = false;
    GifRowType Line;
    ColorMapObject *ColorMap;
    GifFileType *GifFile;

    if ((Error = GAGetArgs(argc, argv, CtrlStr,
		&GifNoisyPrint, &LevelsFlag, &NumLevels,
		&SizeFlag, &ImageWidth, &ImageHeight,
		&HelpFlag)) != false) {
	GAPrintErrMsg(Error);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }

    if (HelpFlag) {
	(void)fprintf(stderr, VersionStr, GIFLIB_MAJOR, GIFLIB_MINOR);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_SUCCESS);
    }

    /* Make sure the number of levels is power of 2 (up to 32 levels.). */
    for (i = 1; i < 6; i++) if (NumLevels == (1 << i)) break;
    if (i == 6) GIF_EXIT("#Lvls (-l option) is not power of 2 up to 32.");
    LogNumLevels = i + 3;		       /* Multiple by 8 (see below). */
    LevelStep = 256 / NumLevels;

    /* Make sure the image dimension is a multiple of NumLevels horizontally */
    /* and 7 (White, Red, Green, Blue and Yellow Cyan Magenta) vertically.   */
    ImageWidth = (ImageWidth / NumLevels) * NumLevels;
    ImageHeight = (ImageHeight / 7) * 7;

    /* Open stdout for the output file: */
    if ((GifFile = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /* Dump out screen description with given size and generated color map:  */
    /* The color map has 7 NumLevels colors for White, Red, Green and then   */
    /* The secondary colors Yellow Cyan and magenta.			     */
    if ((ColorMap = GifMakeMapObject(8 * NumLevels, NULL)) == NULL)
	GIF_EXIT("Failed to allocate memory required, aborted.");

    for (i = 0; i < 8; i++)				   /* Set color map. */
	for (j = 0; j < NumLevels; j++) {
	    l = LevelStep * j;
	    c = i * NumLevels + j;
	    ColorMap->Colors[c].Red = (i == 0 || i == 1 || i == 4 || i == 6) * l;
	    ColorMap->Colors[c].Green =	(i == 0 || i == 2 || i == 4 || i == 5) * l;
	    ColorMap->Colors[c].Blue = (i == 0 || i == 3 || i == 5 || i == 6) * l;
	}

    if (EGifPutScreenDesc(GifFile, ImageWidth, ImageHeight, LogNumLevels, 0, ColorMap) == GIF_ERROR) {
	PrintGifError(GifFile->Error);
    }

    /* Dump out the image descriptor: */
    if (EGifPutImageDesc(GifFile,
			 0, 0, ImageWidth, ImageHeight, 
			 false, NULL) == GIF_ERROR) {

	PrintGifError(GifFile->Error);
	exit(EXIT_FAILURE);
    }

    GifQprintf("\n%s: Image 1 at (%d, %d) [%dx%d]:     ",
		    PROGRAM_NAME, GifFile->Image.Left, GifFile->Image.Top,
		    GifFile->Image.Width, GifFile->Image.Height);

    /* Allocate one scan line to be used for all image.			     */
    if ((Line = (GifRowType) malloc(sizeof(GifPixelType) * ImageWidth)) == NULL)
	GIF_EXIT("Failed to allocate memory required, aborted.");

    /* Dump the pixels: */
    for (c = 0; c < 7; c++) {
	for (i = 0, l = 0; i < NumLevels; i++)
	    for (j = 0; j < ImageWidth / NumLevels; j++)
		Line[l++] = i + NumLevels * c;
	for (i = 0; i < ImageHeight / 7; i++) {
	    if (EGifPutLine(GifFile, Line, ImageWidth) == GIF_ERROR) {
		PrintGifError(GifFile->Error);
		exit(EXIT_FAILURE);
	    }
	    GifQprintf("\b\b\b\b%-4d", Count++);
	}
    }

    if (EGifCloseFile(GifFile, &ErrorCode) == GIF_ERROR) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    return 0;
}

/* end */
