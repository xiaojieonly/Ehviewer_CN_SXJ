/*****************************************************************************

gif2rgb - convert GIF to 24-bit RGB pixel triples or vice-versa

*****************************************************************************/

/***************************************************************************

Toshio Kuratomi had written this in a comment about the rgb2gif code:

  Besides fixing bugs, what's really needed is for someone to work out how to
  calculate a colormap for writing GIFs from rgb sources.  Right now, an rgb
  source that has only two colors (b/w) is being converted into an 8 bit GIF....
  Which is horrendously wasteful without compression.

I (ESR) took this off the main to-do list in 2012 because I don't think
the GIFLIB project actually needs to be in the converters-and-tools business.
Plenty of hackers do that; our job is to supply stable library capability
with our utilities mainly interesting as test tools.

***************************************************************************/

#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>
#include <fcntl.h>

#ifdef _WIN32
#include <io.h>
#endif /* _WIN32 */

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gif2rgb"

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
	" v%- c%-#Colors!d s%-Width|Height!d!d 1%- o%-OutFileName!s h%- GifFile!*s";

static void LoadRGB(char *FileName,
		    int OneFileFlag,
		    GifByteType **RedBuffer,
		    GifByteType **GreenBuffer,
		    GifByteType **BlueBuffer,
		    int Width, int Height);
static void SaveGif(GifByteType *OutputBuffer,
		    int Width, int Height, 
		    int ExpColorMapSize, ColorMapObject *OutputColorMap);

/******************************************************************************
 Load RGB file into internal frame buffer.
******************************************************************************/
static void LoadRGB(char *FileName,
		    int OneFileFlag,
		    GifByteType **RedBuffer,
		    GifByteType **GreenBuffer,
		    GifByteType **BlueBuffer,
		    int Width, int Height)
{
    int i;
    unsigned long Size;
    GifByteType *RedP, *GreenP, *BlueP;
    FILE *rgbfp[3];

    Size = ((long) Width) * Height * sizeof(GifByteType);

    if ((*RedBuffer = (GifByteType *) malloc((unsigned int) Size)) == NULL ||
	(*GreenBuffer = (GifByteType *) malloc((unsigned int) Size)) == NULL ||
	(*BlueBuffer = (GifByteType *) malloc((unsigned int) Size)) == NULL)
	GIF_EXIT("Failed to allocate memory required, aborted.");

    RedP = *RedBuffer;
    GreenP = *GreenBuffer;
    BlueP = *BlueBuffer;

    if (FileName != NULL) {
	if (OneFileFlag) {
	    if ((rgbfp[0] = fopen(FileName, "rb")) == NULL)
		GIF_EXIT("Can't open input file name.");
	}
	else {
	    static char *Postfixes[] = { ".R", ".G", ".B" };
	    char OneFileName[80];

	    for (i = 0; i < 3; i++) {
		strncpy(OneFileName, FileName, sizeof(OneFileName)-1);
		/* cppcheck-suppress uninitstring */
		strncat(OneFileName, Postfixes[i], 
			sizeof(OneFileName) - 1 - strlen(OneFileName));

		if ((rgbfp[i] = fopen(OneFileName, "rb")) == NULL)
		    GIF_EXIT("Can't open input file name.");
	    }
	}
    }
    else {
	OneFileFlag = true;

#ifdef _WIN32
	_setmode(0, O_BINARY);
#endif /* _WIN32 */

	rgbfp[0] = stdin;
    }

    GifQprintf("\n%s: RGB image:     ", PROGRAM_NAME);

    if (OneFileFlag) {
	GifByteType *Buffer, *BufferP;

	if ((Buffer = (GifByteType *) malloc(Width * 3)) == NULL)
	    GIF_EXIT("Failed to allocate memory required, aborted.");

	for (i = 0; i < Height; i++) {
	    int j;
	    GifQprintf("\b\b\b\b%-4d", i);
	    if (fread(Buffer, Width * 3, 1, rgbfp[0]) != 1)
		GIF_EXIT("Input file(s) terminated prematurly.");
	    for (j = 0, BufferP = Buffer; j < Width; j++) {
		*RedP++ = *BufferP++;
		*GreenP++ = *BufferP++;
		*BlueP++ = *BufferP++;
	    }
	}

	free((char *) Buffer);
	fclose(rgbfp[0]);
    }
    else {
	for (i = 0; i < Height; i++) {
	    GifQprintf("\b\b\b\b%-4d", i);
	    if (fread(RedP, Width, 1, rgbfp[0]) != 1 ||
		fread(GreenP, Width, 1, rgbfp[1]) != 1 ||
		fread(BlueP, Width, 1, rgbfp[2]) != 1)
		GIF_EXIT("Input file(s) terminated prematurly.");
	    RedP += Width;
	    GreenP += Width;
	    BlueP += Width;
	}

	fclose(rgbfp[0]);
	// cppcheck-suppress useClosedFile
	fclose(rgbfp[1]);
	// cppcheck-suppress useClosedFile
	fclose(rgbfp[2]);
    }
}

/******************************************************************************
 Save the GIF resulting image.
******************************************************************************/
static void SaveGif(GifByteType *OutputBuffer,
		    int Width, int Height,
		    int ExpColorMapSize, ColorMapObject *OutputColorMap)
{
    int i, Error;
    GifFileType *GifFile;
    GifByteType *Ptr = OutputBuffer;

    /* Open stdout for the output file: */
    if ((GifFile = EGifOpenFileHandle(1, &Error)) == NULL) {
	PrintGifError(Error);
	exit(EXIT_FAILURE);
    }

    if (EGifPutScreenDesc(GifFile,
			  Width, Height, ExpColorMapSize, 0,
			  OutputColorMap) == GIF_ERROR ||
	EGifPutImageDesc(GifFile,
			 0, 0, Width, Height, false, NULL) ==
	                                                             GIF_ERROR)
	PrintGifError(Error);
	exit(EXIT_FAILURE);

    GifQprintf("\n%s: Image 1 at (%d, %d) [%dx%d]:     ",
	       PROGRAM_NAME, GifFile->Image.Left, GifFile->Image.Top,
	       GifFile->Image.Width, GifFile->Image.Height);

    for (i = 0; i < Height; i++) {
	if (EGifPutLine(GifFile, Ptr, Width) == GIF_ERROR)
	    exit(EXIT_FAILURE);
	GifQprintf("\b\b\b\b%-4d", Height - i - 1);

	Ptr += Width;
    }

    if (EGifCloseFile(GifFile, &Error) == GIF_ERROR)
	PrintGifError(Error);
	exit(EXIT_FAILURE);
}

/******************************************************************************
 Close output file (if open), and exit.
******************************************************************************/
static void RGB2GIF(bool OneFileFlag, int NumFiles, char *FileName,
		    int ExpNumOfColors, int Width, int Height)
{
    int ColorMapSize;

    GifByteType *RedBuffer = NULL, *GreenBuffer = NULL, *BlueBuffer = NULL,
	*OutputBuffer = NULL;
    ColorMapObject *OutputColorMap = NULL;

    ColorMapSize = 1 << ExpNumOfColors;

    if (NumFiles == 1) {
	LoadRGB(FileName, OneFileFlag,
		&RedBuffer, &GreenBuffer, &BlueBuffer, Width, Height);
    }
    else {
	LoadRGB(NULL, OneFileFlag,
		&RedBuffer, &GreenBuffer, &BlueBuffer, Width, Height);
    }

    if ((OutputColorMap = GifMakeMapObject(ColorMapSize, NULL)) == NULL ||
	(OutputBuffer = (GifByteType *) malloc(Width * Height *
					    sizeof(GifByteType))) == NULL)
	GIF_EXIT("Failed to allocate memory required, aborted.");

    if (GifQuantizeBuffer(Width, Height, &ColorMapSize,
		       RedBuffer, GreenBuffer, BlueBuffer,
		       OutputBuffer, OutputColorMap->Colors) == GIF_ERROR)
	exit(EXIT_FAILURE);
    free((char *) RedBuffer);
    free((char *) GreenBuffer);
    free((char *) BlueBuffer);

    SaveGif(OutputBuffer, Width, Height, ExpNumOfColors, OutputColorMap);
}

/******************************************************************************
 The real screen dumping routine.
******************************************************************************/
static void DumpScreen2RGB(char *FileName, int OneFileFlag,
			   ColorMapObject *ColorMap,
			   GifRowType *ScreenBuffer,
			   int ScreenWidth, int ScreenHeight)
{
    int i, j;
    GifRowType GifRow;
    GifColorType *ColorMapEntry;
    FILE *rgbfp[3];

    if (FileName != NULL) {
        if (OneFileFlag) {
            if ((rgbfp[0] = fopen(FileName, "wb")) == NULL)
            GIF_EXIT("Can't open input file name.");
        } else {
            static char *Postfixes[] = { ".R", ".G", ".B" };
	    char OneFileName[80];

            for (i = 0; i < 3; i++) {
                strncpy(OneFileName, FileName, sizeof(OneFileName)-1);
		/* cppcheck-suppress uninitstring */
                strncat(OneFileName, Postfixes[i], 
			sizeof(OneFileName) - 1 - strlen(OneFileName));
    
                if ((rgbfp[i] = fopen(OneFileName, "wb")) == NULL) {
                    GIF_EXIT("Can't open input file name.");
                }
            }
        }
    } else {
        OneFileFlag = true;

#ifdef _WIN32
	_setmode(1, O_BINARY);
#endif /* _WIN32 */
        
        rgbfp[0] = stdout;
    }

    if (OneFileFlag) {
        unsigned char *Buffer, *BufferP;

        if ((Buffer = (unsigned char *) malloc(ScreenWidth * 3)) == NULL)
            GIF_EXIT("Failed to allocate memory required, aborted.");
        for (i = 0; i < ScreenHeight; i++) {
            GifRow = ScreenBuffer[i];
            GifQprintf("\b\b\b\b%-4d", ScreenHeight - i);
            for (j = 0, BufferP = Buffer; j < ScreenWidth; j++) {
                ColorMapEntry = &ColorMap->Colors[GifRow[j]];
                *BufferP++ = ColorMapEntry->Red;
                *BufferP++ = ColorMapEntry->Green;
                *BufferP++ = ColorMapEntry->Blue;
            }
            if (fwrite(Buffer, ScreenWidth * 3, 1, rgbfp[0]) != 1)
                GIF_EXIT("Write to file(s) failed.");
        }

        free((char *) Buffer);
        fclose(rgbfp[0]);
    } else {
        unsigned char *Buffers[3];

        if ((Buffers[0] = (unsigned char *) malloc(ScreenWidth)) == NULL ||
            (Buffers[1] = (unsigned char *) malloc(ScreenWidth)) == NULL ||
            (Buffers[2] = (unsigned char *) malloc(ScreenWidth)) == NULL)
            GIF_EXIT("Failed to allocate memory required, aborted.");

        for (i = 0; i < ScreenHeight; i++) {
            GifRow = ScreenBuffer[i];
            GifQprintf("\b\b\b\b%-4d", ScreenHeight - i);
            for (j = 0; j < ScreenWidth; j++) {
                ColorMapEntry = &ColorMap->Colors[GifRow[j]];
                Buffers[0][j] = ColorMapEntry->Red;
                Buffers[1][j] = ColorMapEntry->Green;
                Buffers[2][j] = ColorMapEntry->Blue;
            }
            if (fwrite(Buffers[0], ScreenWidth, 1, rgbfp[0]) != 1 ||
                fwrite(Buffers[1], ScreenWidth, 1, rgbfp[1]) != 1 ||
                fwrite(Buffers[2], ScreenWidth, 1, rgbfp[2]) != 1)
                GIF_EXIT("Write to file(s) failed.");
        }

        free((char *) Buffers[0]);
        free((char *) Buffers[1]);
        free((char *) Buffers[2]);
        fclose(rgbfp[0]);
	// cppcheck-suppress useClosedFile
        fclose(rgbfp[1]);
	// cppcheck-suppress useClosedFile
        fclose(rgbfp[2]);
    }
}

static void GIF2RGB(int NumFiles, char *FileName, 
		    bool OneFileFlag, 
		    char *OutFileName)
{
    int	i, j, Size, Row, Col, Width, Height, ExtCode, Count;
    GifRecordType RecordType;
    GifByteType *Extension;
    GifRowType *ScreenBuffer;
    GifFileType *GifFile;
    int
	InterlacedOffset[] = { 0, 4, 2, 1 }, /* The way Interlaced image should. */
	InterlacedJumps[] = { 8, 8, 4, 2 };    /* be read - offsets and jumps... */
    int ImageNum = 0;
    ColorMapObject *ColorMap;
    int Error;

    if (NumFiles == 1) {
	int Error;
	if ((GifFile = DGifOpenFileName(FileName, &Error)) == NULL) {
	    PrintGifError(Error);
	    exit(EXIT_FAILURE);
	}
    }
    else {
	int Error;
	/* Use stdin instead: */
	if ((GifFile = DGifOpenFileHandle(0, &Error)) == NULL) {
	    PrintGifError(Error);
	    exit(EXIT_FAILURE);
	}
    }

    if (GifFile->SHeight == 0 || GifFile->SWidth == 0) {
	fprintf(stderr, "Image of width or height 0\n");
	exit(EXIT_FAILURE);
    }

    /* 
     * Allocate the screen as vector of column of rows. Note this
     * screen is device independent - it's the screen defined by the
     * GIF file parameters.
     */
    if ((ScreenBuffer = (GifRowType *)
	malloc(GifFile->SHeight * sizeof(GifRowType))) == NULL)
	    GIF_EXIT("Failed to allocate memory required, aborted.");

    Size = GifFile->SWidth * sizeof(GifPixelType);/* Size in bytes one row.*/
    if ((ScreenBuffer[0] = (GifRowType) malloc(Size)) == NULL) /* First row. */
	GIF_EXIT("Failed to allocate memory required, aborted.");

    for (i = 0; i < GifFile->SWidth; i++)  /* Set its color to BackGround. */
	ScreenBuffer[0][i] = GifFile->SBackGroundColor;
    for (i = 1; i < GifFile->SHeight; i++) {
	/* Allocate the other rows, and set their color to background too: */
	if ((ScreenBuffer[i] = (GifRowType) malloc(Size)) == NULL)
	    GIF_EXIT("Failed to allocate memory required, aborted.");

	memcpy(ScreenBuffer[i], ScreenBuffer[0], Size);
    }

    /* Scan the content of the GIF file and load the image(s) in: */
    do {
	if (DGifGetRecordType(GifFile, &RecordType) == GIF_ERROR) {
	    PrintGifError(GifFile->Error);
	    exit(EXIT_FAILURE);
	}
	switch (RecordType) {
	    case IMAGE_DESC_RECORD_TYPE:
		if (DGifGetImageDesc(GifFile) == GIF_ERROR) {
		    PrintGifError(GifFile->Error);
		    exit(EXIT_FAILURE);
		}
		Row = GifFile->Image.Top; /* Image Position relative to Screen. */
		Col = GifFile->Image.Left;
		Width = GifFile->Image.Width;
		Height = GifFile->Image.Height;
		GifQprintf("\n%s: Image %d at (%d, %d) [%dx%d]:     ",
		    PROGRAM_NAME, ++ImageNum, Col, Row, Width, Height);
		if (GifFile->Image.Left + GifFile->Image.Width > GifFile->SWidth ||
		   GifFile->Image.Top + GifFile->Image.Height > GifFile->SHeight) {
		    fprintf(stderr, "Image %d is not confined to screen dimension, aborted.\n",ImageNum);
		    exit(EXIT_FAILURE);
		}
		if (GifFile->Image.Interlace) {
		    /* Need to perform 4 passes on the images: */
		    for (Count = i = 0; i < 4; i++)
			for (j = Row + InterlacedOffset[i]; j < Row + Height;
						 j += InterlacedJumps[i]) {
			    GifQprintf("\b\b\b\b%-4d", Count++);
			    if (DGifGetLine(GifFile, &ScreenBuffer[j][Col],
				Width) == GIF_ERROR) {
				PrintGifError(GifFile->Error);
				exit(EXIT_FAILURE);
			    }
			}
		}
		else {
		    for (i = 0; i < Height; i++) {
			GifQprintf("\b\b\b\b%-4d", i);
			if (DGifGetLine(GifFile, &ScreenBuffer[Row++][Col],
				Width) == GIF_ERROR) {
			    PrintGifError(GifFile->Error);
			    exit(EXIT_FAILURE);
			}
		    }
		}
		break;
	    case EXTENSION_RECORD_TYPE:
		/* Skip any extension blocks in file: */
		if (DGifGetExtension(GifFile, &ExtCode, &Extension) == GIF_ERROR) {
		    PrintGifError(GifFile->Error);
		    exit(EXIT_FAILURE);
		}
		while (Extension != NULL) {
		    if (DGifGetExtensionNext(GifFile, &Extension) == GIF_ERROR) {
			PrintGifError(GifFile->Error);
			exit(EXIT_FAILURE);
		    }
		}
		break;
	    case TERMINATE_RECORD_TYPE:
		break;
	    default:		    /* Should be trapped by DGifGetRecordType. */
		break;
	}
    } while (RecordType != TERMINATE_RECORD_TYPE);
    
    /* Lets dump it - set the global variables required and do it: */
    ColorMap = (GifFile->Image.ColorMap
		? GifFile->Image.ColorMap
		: GifFile->SColorMap);
    if (ColorMap == NULL) {
        fprintf(stderr, "Gif Image does not have a colormap\n");
        exit(EXIT_FAILURE);
    }

    /* check that the background color isn't garbage (SF bug #87) */
    if (GifFile->SBackGroundColor < 0 || GifFile->SBackGroundColor >= ColorMap->ColorCount) {
        fprintf(stderr, "Background color out of range for colormap\n");
        exit(EXIT_FAILURE);
    }

    DumpScreen2RGB(OutFileName, OneFileFlag,
		   ColorMap,
		   ScreenBuffer, 
		   GifFile->SWidth, GifFile->SHeight);

    (void)free(ScreenBuffer);

    if (DGifCloseFile(GifFile, &Error) == GIF_ERROR) {
	PrintGifError(Error);
	exit(EXIT_FAILURE);
    }

}

/******************************************************************************
* Interpret the command line and scan the given GIF file.
******************************************************************************/
int main(int argc, char **argv)
{
    bool Error, OutFileFlag = false, ColorFlag = false, SizeFlag = false;
    int NumFiles, Width = 0, Height = 0, ExpNumOfColors = 8;
    char *OutFileName,
	**FileName = NULL;
    static bool
	OneFileFlag = false,
	HelpFlag = false;

    if ((Error = GAGetArgs(argc, argv, CtrlStr, &GifNoisyPrint,
		&ColorFlag, &ExpNumOfColors, &SizeFlag, &Width, &Height, 
		&OneFileFlag, &OutFileFlag, &OutFileName,
		&HelpFlag, &NumFiles, &FileName)) != false ||
		(NumFiles > 1 && !HelpFlag)) {
	if (Error)
	    GAPrintErrMsg(Error);
	else if (NumFiles > 1)
	    GIF_MESSAGE("Error in command line parsing - one input file please.");
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }

    if (HelpFlag) {
	(void)fprintf(stderr, VersionStr, GIFLIB_MAJOR, GIFLIB_MINOR);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_SUCCESS);
    }
    if (!OutFileFlag) OutFileName = NULL;

    if (SizeFlag && Width > 0 && Height > 0)
	RGB2GIF(OneFileFlag, NumFiles, *FileName, 
		ExpNumOfColors, Width, Height);
    else
	GIF2RGB(NumFiles, *FileName, OneFileFlag, OutFileName);

    return 0;
}

/* end */
