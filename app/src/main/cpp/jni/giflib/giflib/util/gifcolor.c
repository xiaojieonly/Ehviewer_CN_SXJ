/*****************************************************************************

gifcolor - generate color test-pattern GIFs

*****************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gifcolor"

#define LINE_LEN		40
#define IMAGEWIDTH		LINE_LEN*GIF_FONT_WIDTH

static char
    *VersionStr =
	PROGRAM_NAME
	VERSION_COOKIE
	"	Gershon Elber,	"
	__DATE__ ",   " __TIME__ "\n"
	"(C) Copyright 1989 Gershon Elber.\n";
static char
    *CtrlStr = PROGRAM_NAME " v%- b%-Background!d h%-";

static int BackGround = 0;
static void QuitGifError(GifFileType *GifFile);
static void GenRasterTextLine(GifRowType *RasterBuffer, char *TextLine,
					int BufferWidth, int ForeGroundIndex);

/******************************************************************************
 Interpret the command line and generate the given GIF file.
******************************************************************************/
int main(int argc, char **argv)
{
    int	i, j, l, GifNoisyPrint, ColorMapSize, ErrorCode;
    bool Error, BackGroundFlag = false, HelpFlag = false;
    char Line[LINE_LEN];
    GifRowType RasterBuffer[GIF_FONT_HEIGHT];
    ColorMapObject *ColorMap;
    GifFileType *GifFile;
    GifColorType	ScratchMap[256];
    int red, green, blue;

    if ((Error = GAGetArgs(argc, argv, CtrlStr,
			   &GifNoisyPrint,
			   &BackGroundFlag, &BackGround,
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

    /* Allocate the raster buffer for GIF_FONT_HEIGHT scan lines. */
    for (i = 0; i < GIF_FONT_HEIGHT; i++)
    {
	if ((RasterBuffer[i] = (GifRowType) malloc(sizeof(GifPixelType) *
							IMAGEWIDTH)) == NULL)
	    GIF_EXIT("Failed to allocate memory required, aborted.");
    }

    /* Open stdout for the output file: */
    if ((GifFile = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /* Read the color map in ColorFile into this color map: */
    ColorMapSize = 0;
    while (fscanf(stdin,
		  "%*3d %3d %3d %3d\n",
		  &red, &green, &blue) == 3) {
	    ScratchMap[ColorMapSize].Red = red;
	    ScratchMap[ColorMapSize].Green = green;
	    ScratchMap[ColorMapSize].Blue = blue;
	    ColorMapSize++;
	}

    if ((ColorMap = GifMakeMapObject(1 << GifBitSize(ColorMapSize), ScratchMap)) == NULL)
	GIF_EXIT("Failed to allocate memory required, aborted.");

    if (EGifPutScreenDesc(GifFile,
			  IMAGEWIDTH, ColorMapSize * GIF_FONT_HEIGHT,
			  GifBitSize(ColorMapSize),
			  BackGround, ColorMap) == GIF_ERROR)
	QuitGifError(GifFile);

    /* Dump out the image descriptor: */
    if (EGifPutImageDesc(GifFile,
	0, 0, IMAGEWIDTH, ColorMapSize * GIF_FONT_HEIGHT, false, NULL) == GIF_ERROR)
	QuitGifError(GifFile);

    GifQprintf("\n%s: Image 1 at (%d, %d) [%dx%d]:     ",
		    PROGRAM_NAME, GifFile->Image.Left, GifFile->Image.Top,
		    GifFile->Image.Width, GifFile->Image.Height);

    for (i = l = 0; i < ColorMap->ColorCount; i++) {
	(void)snprintf(Line, sizeof(Line),
		       "Color %-3d: [%-3d, %-3d, %-3d] ", i,
		       ColorMap->Colors[i].Red,
		       ColorMap->Colors[i].Green,
		       ColorMap->Colors[i].Blue);
	GenRasterTextLine(RasterBuffer, Line, IMAGEWIDTH, i);
	for (j = 0; j < GIF_FONT_HEIGHT; j++) {
	    if (EGifPutLine(GifFile, RasterBuffer[j], IMAGEWIDTH) == GIF_ERROR)
		QuitGifError(GifFile);
	    GifQprintf("\b\b\b\b%-4d", l++);
	}
    }

    if (EGifCloseFile(GifFile, &ErrorCode) == GIF_ERROR)
    {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    return 0;
}

/******************************************************************************
 Close output file (if open), and exit.
******************************************************************************/
static void GenRasterTextLine(GifRowType *RasterBuffer, char *TextLine,
					int BufferWidth, int ForeGroundIndex)
{
    unsigned char c;
    unsigned char Byte, Mask;
    int i, j, k, CharPosX, Len = strlen(TextLine);

    for (i = 0; i < BufferWidth; i++)
        for (j = 0; j < GIF_FONT_HEIGHT; j++) RasterBuffer[j][i] = BackGround;

    for (i = CharPosX = 0; i < Len; i++, CharPosX += GIF_FONT_WIDTH) {
	c = TextLine[i];
	for (j = 0; j < GIF_FONT_HEIGHT; j++) {
	    Byte = GifAsciiTable8x8[(unsigned short)c][j];
	    for (k = 0, Mask = 128; k < GIF_FONT_WIDTH; k++, Mask >>= 1)
		if (Byte & Mask)
		    RasterBuffer[j][CharPosX + k] = ForeGroundIndex;
	}
    }
}

/******************************************************************************
 Close output file (if open), and exit.
******************************************************************************/
static void QuitGifError(GifFileType *GifFile)
{
    if (GifFile != NULL) {
	PrintGifError(GifFile->Error);
	EGifCloseFile(GifFile, NULL);
    }
    exit(EXIT_FAILURE);
}
