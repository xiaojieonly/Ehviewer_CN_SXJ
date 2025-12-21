/*****************************************************************************

gifclrmap - extract colormaps from GIF images

*****************************************************************************/

#include <math.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>
#include <stdlib.h>
#include <assert.h>

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gifclrmp"

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
	" v%- s%- t%-TranslationFile!s l%-ColorMapFile!s g%-Gamma!F i%-Image#!d h%- GifFile!*s";

static bool
    SaveFlag = false,
    TranslateFlag = false,
    LoadFlag = false,
    GammaFlag = false;
static
    double Gamma = 1.0;
static
    FILE *ColorFile = NULL;
    FILE *TranslateFile = NULL;
static
    GifPixelType Translation[256];

static ColorMapObject *ModifyColorMap(ColorMapObject *ColorMap);
static void QuitGifError(GifFileType *GifFileIn, GifFileType *GifFileOut);

/******************************************************************************
 Interpret the command line and scan the given GIF file.
******************************************************************************/
int main(int argc, char **argv)
{
    int	NumFiles, ExtCode, CodeSize, ImageNum = 0, 
	ImageN, HasGIFOutput, ErrorCode;
    bool Error, ImageNFlag = false, HelpFlag = false;
    GifRecordType RecordType;
    GifByteType *Extension, *CodeBlock;
    char **FileName = NULL, *ColorFileName, *TranslateFileName;
    GifFileType *GifFileIn = NULL, *GifFileOut = NULL;

    if ((Error = GAGetArgs(argc, argv, CtrlStr, &GifNoisyPrint, &SaveFlag, 
		&TranslateFlag, &TranslateFileName,
		&LoadFlag, &ColorFileName,
		&GammaFlag, &Gamma, &ImageNFlag, &ImageN,
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

    if (SaveFlag + LoadFlag + GammaFlag + TranslateFlag > 1)
	GIF_EXIT("Can not handle more than one of -s -l, -t, or -g at the same time.");

    /* Default action is to dump colormaps */
    if (!SaveFlag && !LoadFlag && !GammaFlag && !TranslateFlag)
	SaveFlag = true;

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

    if (SaveFlag) {
	/* We are dumping out the color map as text file to stdout: */
	ColorFile = stdout;
    }
    else {
	if (TranslateFlag) {
	    /* We are loading new color map from specified file: */
	    if ((TranslateFile = fopen(TranslateFileName, "rt")) == NULL)
		GIF_EXIT("Failed to open specified color translation file.");
	}

	if (LoadFlag) {
	    /* We are loading new color map from specified file: */
	    if ((ColorFile = fopen(ColorFileName, "rt")) == NULL)
		GIF_EXIT("Failed to open specified color map file.");
	}
    }

    if ((HasGIFOutput = (LoadFlag || TranslateFlag || GammaFlag)) != 0) {
	/* Open stdout for GIF output file: */
	if ((GifFileOut = EGifOpenFileHandle(1, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }

    if (!ImageNFlag) {
	/* We are supposed to modify the screen color map, so do it: */
	if (!GifFileIn->SColorMap)
	    GIF_EXIT("No colormap to modify");
	GifFileIn->SColorMap = ModifyColorMap(GifFileIn->SColorMap);
	if (!HasGIFOutput) {
	    /* We can quit here, as we have the color map: */
	    DGifCloseFile(GifFileIn, NULL);
	    fclose(ColorFile);
	    exit(EXIT_SUCCESS);
	}
    }
    /* And dump out its new possible repositioned screen information: */
    if (HasGIFOutput)
	if (EGifPutScreenDesc(GifFileOut,
	    GifFileIn->SWidth, GifFileIn->SHeight,
	    GifFileIn->SColorResolution, GifFileIn->SBackGroundColor,
	    GifFileIn->SColorMap) == GIF_ERROR)
	    QuitGifError(GifFileIn, GifFileOut);

    /* Scan the content of the GIF file and load the image(s) in: */
    do {
	if (DGifGetRecordType(GifFileIn, &RecordType) == GIF_ERROR)
	    QuitGifError(GifFileIn, GifFileOut);

	switch (RecordType) {
	    case IMAGE_DESC_RECORD_TYPE:
		if (DGifGetImageDesc(GifFileIn) == GIF_ERROR)
		    QuitGifError(GifFileIn, GifFileOut);
		if ((++ImageNum == ImageN) && ImageNFlag) {
		    /* We are suppose to modify this image color map, do it: */
		    GifFileIn->SColorMap =ModifyColorMap(GifFileIn->SColorMap);
		    if (!HasGIFOutput) {
			/* We can quit here, as we have the color map: */
			DGifCloseFile(GifFileIn, NULL);
			fclose(ColorFile);
			exit(EXIT_SUCCESS);
		    }
		}
		if (HasGIFOutput)
		    if (EGifPutImageDesc(GifFileOut,
			GifFileIn->Image.Left, GifFileIn->Image.Top,
			GifFileIn->Image.Width, GifFileIn->Image.Height,
			GifFileIn->Image.Interlace,
			GifFileIn->Image.ColorMap) == GIF_ERROR)
			QuitGifError(GifFileIn, GifFileOut);

		if (!TranslateFlag || (ImageNFlag && (ImageN != ImageNum)))
		{
		    /* Now read image itself in decoded form as we don't */
		    /* really care what we have there, and this is much  */
		    /* faster.						 */
		    if (DGifGetCode(GifFileIn, &CodeSize, &CodeBlock) == GIF_ERROR)
			QuitGifError(GifFileIn, GifFileOut);
		    if (HasGIFOutput)
			if (EGifPutCode(GifFileOut, CodeSize, CodeBlock) == GIF_ERROR)
			    QuitGifError(GifFileIn, GifFileOut);
		    while (CodeBlock != NULL) {
			if (DGifGetCodeNext(GifFileIn, &CodeBlock) == GIF_ERROR)
			    QuitGifError(GifFileIn, GifFileOut);
			if (HasGIFOutput)
			    if (EGifPutCodeNext(GifFileOut, CodeBlock) == GIF_ERROR)
				QuitGifError(GifFileIn, GifFileOut);
		    }
		}
		else	/* we need to mung pixels intices */
		{
		    int	i;
		    register GifPixelType *cp;

		    GifPixelType *Line
			= (GifPixelType *) malloc(GifFileIn->Image.Width *
						  sizeof(GifPixelType));
		    for (i = 0; i < GifFileIn->Image.Height; i++) {
			if (DGifGetLine(GifFileIn, Line,GifFileIn->Image.Width)
			    == GIF_ERROR) {
			    QuitGifError(GifFileIn, GifFileOut);
			}

			/* translation step goes here */
			for (cp = Line; cp < Line+GifFileIn->Image.Width; cp++)
			    *cp = Translation[*cp];

			if (EGifPutLine(GifFileOut,
					Line, GifFileIn->Image.Width)
			    == GIF_ERROR) {
			    QuitGifError(GifFileIn, GifFileOut);
			}
		    }
		    free((char *) Line);
		}
		break;
	    case EXTENSION_RECORD_TYPE:
		assert(GifFileOut != NULL);	/* might pacify Coverity */
		/* pass through extension records */
		if (DGifGetExtension(GifFileIn, &ExtCode, &Extension) == GIF_ERROR)
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
	    default:		    /* Should be trapped by DGifGetRecordType. */
		break;
	}
    }
    while (RecordType != TERMINATE_RECORD_TYPE);

    if (DGifCloseFile(GifFileIn, &ErrorCode) == GIF_ERROR)
    {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }
    if (HasGIFOutput)
	if (EGifCloseFile(GifFileOut, &ErrorCode) == GIF_ERROR)
	{
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}

    return 0;
}

/******************************************************************************
 Modify the given colormap according to global variables setting.
******************************************************************************/
static ColorMapObject *ModifyColorMap(ColorMapObject *ColorMap)
{
    int i, Dummy, Red, Green, Blue;

    if (SaveFlag) {
	/* Save this color map to ColorFile: */
	for (i = 0; i < ColorMap->ColorCount; i++)
	    fprintf(ColorFile, "%3d %3d %3d %3d\n", i,
		    ColorMap->Colors[i].Red,
		    ColorMap->Colors[i].Green,
		    ColorMap->Colors[i].Blue);
	return(ColorMap);
    }
    else if (LoadFlag) {
	/* Read the color map in ColorFile into this color map: */
	for (i = 0; i < ColorMap->ColorCount; i++) {
	    if (feof(ColorFile))
		GIF_EXIT("Color file to load color map from, too small.");
	    if (fscanf(ColorFile, "%3d %3d %3d %3d\n", &Dummy, &Red, &Green, &Blue) == 4) {
		ColorMap->Colors[i].Red = Red;
		ColorMap->Colors[i].Green = Green;
		ColorMap->Colors[i].Blue = Blue;
	    }
	}
	return(ColorMap);
    }
    else if (GammaFlag) {
	/* Apply gamma correction to this color map: */
	double Gamma1 = 1.0 / Gamma;
	for (i = 0; i < ColorMap->ColorCount; i++) {
	    ColorMap->Colors[i].Red =
		((int) (255 * pow(ColorMap->Colors[i].Red / 255.0, Gamma1)));
	    ColorMap->Colors[i].Green =
		((int) (255 * pow(ColorMap->Colors[i].Green / 255.0, Gamma1)));
	    ColorMap->Colors[i].Blue =
		((int) (255 * pow(ColorMap->Colors[i].Blue / 255.0, Gamma1)));
	}
	return(ColorMap);
    }
    else if (TranslateFlag) {
	ColorMapObject *NewMap;
	int Max = 0;

	/* Read the translation table in TranslateFile: */
	for (i = 0; i < ColorMap->ColorCount; i++) {
	    int tmp;
	    if (feof(TranslateFile))
		GIF_EXIT("Color file to load color map from, too small.");
	    if (fscanf(TranslateFile, "%3d %3d\n", &Dummy, &tmp) == 2) {
		Translation[i] = tmp & 0xff;
		if (Translation[i] > Max)
		    Max = Translation[i];
	    }
	}

	if ((NewMap = GifMakeMapObject(1 << GifBitSize(Max+1), NULL)) == NULL)
	    GIF_EXIT("Out of memory while allocating color map!");

	/* Apply the translation; we'll do it to the pixels, too */
	for (i = 0; i < ColorMap->ColorCount; i++) {
	    NewMap->Colors[i] = ColorMap->Colors[Translation[i]];
	}
	
	return(NewMap);
    }
    else
    {
	GIF_EXIT("Nothing to do!");
	return(ColorMap);
    }
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
