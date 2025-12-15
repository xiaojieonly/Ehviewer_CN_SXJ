/*****************************************************************************

gifbuild - dump GIF data in a textual format, or undump it to a GIF

*****************************************************************************/

#include <stdlib.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <stdbool.h>

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gifbuild"

static char
    *VersionStr =
	PROGRAM_NAME
	VERSION_COOKIE
	"	Eric Raymond,	"
	__DATE__ ",   " __TIME__ "\n"
	"(C) Copyright 1992 Eric Raymond.\n";
static char
    *CtrlStr =
	PROGRAM_NAME
	" v%- d%- t%-Characters!s h%- GifFile(s)!*s";

static char KeyLetters[] = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!\"#$%&'()*+,-./:<=>?@[\\]^_`{|}~";
#define PRINTABLES	(sizeof(KeyLetters) - 1)

static void Icon2Gif(char *FileName, FILE *txtin, int fdout);
static void Gif2Icon(char *FileName,
		     int fdin, int fdout,
		     char NameTable[]);
static int EscapeString(char *cp, char *tp);

/******************************************************************************
 Main sequence
******************************************************************************/
int main(int argc, char **argv)
{
    int	NumFiles;
    bool Error,	DisasmFlag = false, HelpFlag = false, TextLineFlag = false;
    char **FileNames = NULL;
    char *TextLines[1];

    if ((Error = GAGetArgs(argc, argv, CtrlStr,
		&GifNoisyPrint, &DisasmFlag, &TextLineFlag, &TextLines[0],
		&HelpFlag, &NumFiles, &FileNames)) != false) {
	GAPrintErrMsg(Error);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }

    if (HelpFlag) {
	(void)fprintf(stderr, VersionStr, GIFLIB_MAJOR, GIFLIB_MINOR);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_SUCCESS);
    }

    if (!DisasmFlag && NumFiles > 1) {
	GIF_MESSAGE("Error in command line parsing - one  text input please.");
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }

    if (!DisasmFlag && TextLineFlag) {
	GIF_MESSAGE("Error in command line parsing - -t invalid without -d.");
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }


    if (NumFiles == 0)
    {
	if (DisasmFlag)
	    Gif2Icon("Stdin", 0, 1, TextLineFlag ? TextLines[0] : KeyLetters);
	else
	    Icon2Gif("Stdin", stdin, 1);
    }
    else 
    {
	int i;
	for (i = 0; i < NumFiles; i++)
	{
	    FILE	*fp;

	    if ((fp = fopen(FileNames[i], "r")) == (FILE *)NULL)
	    {
		(void) fprintf(stderr, "Can't open %s\n", FileNames[i]);
		exit(EXIT_FAILURE);
	    }

	    if (DisasmFlag)
	    {
		printf("#\n# GIF information from %s\n", FileNames[i]);
		Gif2Icon(FileNames[i], -1, 1, TextLineFlag ? TextLines[0] : KeyLetters);
	    }
	    else
	    {
		Icon2Gif(FileNames[i], fp, 1);
	    }

	    (void) fclose(fp);
	}
    }

    return 0;
}

/******************************************************************************
 Parse image directives
******************************************************************************/
#define PARSE_ERROR(str)  (void) fprintf(stderr,"%s:%d: %s\n",FileName,LineNum,str);

static void Icon2Gif(char *FileName, FILE *txtin, int fdout)
{
    unsigned int	ColorMapSize = 0;
    GifColorType GlobalColorMap[256], LocalColorMap[256],
	*ColorMap = GlobalColorMap;
    char GlobalColorKeys[PRINTABLES], LocalColorKeys[PRINTABLES],
	*KeyTable = GlobalColorKeys;
    bool SortFlag = false;
    unsigned int ExtCode, intval;
    int red, green, blue, n;
    char buf[BUFSIZ * 2], InclusionFile[64];
    GifFileType *GifFileOut;
    SavedImage *NewImage = NULL;
    int LeadingExtensionBlockCount = 0;
    ExtensionBlock *LeadingExtensionBlocks = NULL;
    int ErrorCode, LineNum = 0;

    if ((GifFileOut = EGifOpenFileHandle(fdout, &ErrorCode)) == NULL) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }

    /* OK, interpret directives */
    /* coverity[tainted_data_transitive] */
    while (fgets(buf, sizeof(buf), txtin) != (char *)NULL)
    {
	char	*cp;

	++LineNum;

	/*
	 * Skip lines consisting only of whitespace and comments
	 */
	for (cp = buf; isspace((int)(*cp)); cp++)
	    continue;
	if (*cp == '#' || *cp == '\0')
	    continue;

	/*
	 * If there's a trailing comment, nuke it and all preceding whitespace.
	 * But preserve the EOL.
	 */
	if ((cp = strchr(buf, '#')) && (cp == strrchr(cp, '#')))
	{
	    while (isspace((int)(*--cp)))
		continue;
	    *++cp = '\n';
	    *++cp = '\0';
	}

	/*
	 * Explicit header declarations
	 */

	// cppcheck-suppress invalidscanf 
	if (sscanf(buf, "screen width %d\n", &GifFileOut->SWidth) == 1)
	    continue;

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "screen height %d\n", &GifFileOut->SHeight) == 1)
	    continue;

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "screen colors %d\n", &n) == 1)
	{
	    int	ResBits = GifBitSize(n);

	    if (n > 256 || n < 0 || n != (1 << ResBits))
	    {
		PARSE_ERROR("Invalid color resolution value.");
		exit(EXIT_FAILURE);
	    }

	    GifFileOut->SColorResolution = ResBits;
	    continue;
	}

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf,
			"screen background %d\n",
			&GifFileOut->SBackGroundColor) == 1)
	    continue;

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "pixel aspect byte %u\n", &intval) == 1) {
	    GifFileOut->AspectByte = (GifByteType)(intval & 0xff);
	    continue;
	}

	/*
	 * Color table parsing
	 */

	else if (strcmp(buf, "screen map\n") == 0)
	{
	    if (GifFileOut->SColorMap != NULL)
	    {
		PARSE_ERROR("You've already declared a global color map.");
		exit(EXIT_FAILURE);
	    }

	    ColorMapSize = 0;
	    ColorMap = GlobalColorMap;
	    SortFlag = false;
	    KeyTable = GlobalColorKeys;
	    memset(GlobalColorKeys, '\0', sizeof(GlobalColorKeys));
	}

	else if (strcmp(buf, "image map\n") == 0)
	{
	    if (NewImage == NULL)
	    {
		PARSE_ERROR("No previous image declaration.");
		exit(EXIT_FAILURE);
	    }

	    ColorMapSize = 0;
	    ColorMap = LocalColorMap;
	    KeyTable = LocalColorKeys;
	    memset(LocalColorKeys, '\0', sizeof(LocalColorKeys));
	}

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "	rgb %d %d %d is %c",
		   &red, &green, &blue, &KeyTable[ColorMapSize]) == 4)
	{
	    ColorMap[ColorMapSize].Red = red;
	    ColorMap[ColorMapSize].Green = green;
	    ColorMap[ColorMapSize].Blue = blue;
	    ColorMapSize++;
	}

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "	rgb %d %d %d", &red, &green, &blue) == 3)
	{
	    ColorMap[ColorMapSize].Red = red;
	    ColorMap[ColorMapSize].Green = green;
	    ColorMap[ColorMapSize].Blue = blue;
	    ColorMapSize++;
	}

	else if (strcmp(buf, "	sort flag on\n") == 0)
	    SortFlag = true;

	else if (strcmp(buf, "	sort flag off\n") == 0)
	    SortFlag = false;

	else if (strcmp(buf, "end\n") == 0)
	{
	    ColorMapObject	*NewMap;

	    NewMap = GifMakeMapObject(1 << GifBitSize(ColorMapSize), ColorMap);
	    if (NewMap == (ColorMapObject *)NULL)
	    {
		PARSE_ERROR("Out of memory while allocating new color map.");
		exit(EXIT_FAILURE);
	    }

	    NewMap->SortFlag = SortFlag;

	    if (NewImage)
		NewImage->ImageDesc.ColorMap = NewMap;
	    else
		GifFileOut->SColorMap = NewMap;
	}

	/* GIF inclusion */
	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "include %s", InclusionFile) == 1)
	{
	    int		ErrorCode;
	    bool	DoTranslation;
	    GifPixelType	Translation[256];

	    GifFileType	*Inclusion;
	    SavedImage	*NewImage, *CopyFrom;

	    if ((Inclusion = DGifOpenFileName(InclusionFile, &ErrorCode)) == NULL) {
		PrintGifError(ErrorCode);
		exit(EXIT_FAILURE);
	    }

	    if (DGifSlurp(Inclusion) == GIF_ERROR)
	    {
		PARSE_ERROR("Inclusion read failed.");
		if (Inclusion != NULL) {
		    PrintGifError(Inclusion->Error);
		    DGifCloseFile(Inclusion, NULL);
		}
		if (GifFileOut != NULL) {
		    EGifCloseFile(GifFileOut, NULL);
		};
		exit(EXIT_FAILURE);
	    }

	    if ((DoTranslation = (GifFileOut->SColorMap!=(ColorMapObject*)NULL)))
	    {
		ColorMapObject	*UnionMap;

		UnionMap = GifUnionColorMap(GifFileOut->SColorMap,
					 Inclusion->SColorMap, Translation);

		if (UnionMap == NULL)
		{
		    PARSE_ERROR("Inclusion failed --- global map conflict.");
		    PrintGifError(GifFileOut->Error);
		    if (Inclusion != NULL) DGifCloseFile(Inclusion, NULL);
		    if (GifFileOut != NULL) EGifCloseFile(GifFileOut, NULL);
		    exit(EXIT_FAILURE);
		}

		GifFreeMapObject(GifFileOut->SColorMap);
		GifFileOut->SColorMap = UnionMap;
	    }

	    for (CopyFrom = Inclusion->SavedImages;
		 CopyFrom < Inclusion->SavedImages + Inclusion->ImageCount;
		 CopyFrom++)
	    {
		if ((NewImage = GifMakeSavedImage(GifFileOut, CopyFrom)) == NULL)
		{
		    PARSE_ERROR("Inclusion failed --- out of memory.");
		    PrintGifError(GifFileOut->Error);
		    if (Inclusion != NULL) DGifCloseFile(Inclusion, NULL);
		    if (GifFileOut != NULL) EGifCloseFile(GifFileOut, NULL);
		    exit(EXIT_FAILURE);
		}
		else if (DoTranslation)
		    GifApplyTranslation(NewImage, Translation);

		GifQprintf(
		        "%s: Image %d at (%d, %d) [%dx%d]: from %s\n",
			PROGRAM_NAME, GifFileOut->ImageCount,
			NewImage->ImageDesc.Left, NewImage->ImageDesc.Top,
			NewImage->ImageDesc.Width, NewImage->ImageDesc.Height,
			InclusionFile);
	    }

	    (void) DGifCloseFile(Inclusion, NULL);
	}

	/*
	 * Extension blocks.
	 */
	else if (strcmp(buf, "comment\n") == 0)
	{
	    int bc = 0;
	    while (fgets(buf, sizeof(buf), txtin) != (char *)NULL)
		if (strcmp(buf, "end\n") == 0)
		    break;
	        else
		{
		    int Len;

		    buf[strlen(buf) - 1] = '\0';
		    Len = EscapeString(buf, buf);
		    if (GifAddExtensionBlock(&LeadingExtensionBlockCount,
					     &LeadingExtensionBlocks,
					     bc++ == CONTINUE_EXT_FUNC_CODE ? COMMENT_EXT_FUNC_CODE : 0,
					     Len,
					     (unsigned char *)buf) == GIF_ERROR) {
			PARSE_ERROR("out of memory while adding comment block.");
			exit(EXIT_FAILURE);
		    }
		}
	}
	else if (strcmp(buf, "plaintext\n") == 0)
	{
	    int bc = 0;
	    while (fgets(buf, sizeof(buf), txtin) != (char *)NULL)
		if (strcmp(buf, "end\n") == 0)
		    break;
	        else
		{
		    int Len;

		    buf[strlen(buf) - 1] = '\0';
		    Len = EscapeString(buf, buf);
		    if (GifAddExtensionBlock(&LeadingExtensionBlockCount,
					     &LeadingExtensionBlocks,
					     bc++ == CONTINUE_EXT_FUNC_CODE ? PLAINTEXT_EXT_FUNC_CODE : 0,
					     Len, 
					     (unsigned char *)buf) == GIF_ERROR) {
			PARSE_ERROR("out of memory while adding plaintext block.");
			exit(EXIT_FAILURE);
		    }
		}
	}
	else if (strcmp(buf, "graphics control\n") == 0)
	{
	    GraphicsControlBlock gcb;
	    size_t Len;

	    memset(&gcb, '\0', sizeof(gcb));
	    gcb.TransparentColor = NO_TRANSPARENT_COLOR;
	    while (fgets(buf, sizeof(buf), txtin) != (char *)NULL)
		if (strcmp(buf, "end\n") == 0)
		    break;
	        else
		{
		    char *tp = buf;

		    while (isspace(*tp))
			tp++;
		    // cppcheck-suppress invalidscanf 
		    if (sscanf(tp, "disposal mode %d\n", &gcb.DisposalMode))
			continue;
		    if (strcmp(tp, "user input flag on\n") == 0) {
			gcb.UserInputFlag = true;
			continue;
		    }
		    if (strcmp(tp, "user input flag off\n") == 0) {
			gcb.UserInputFlag = false;
			continue;
		    }
		    // cppcheck-suppress invalidscanf 
		    if (sscanf(tp, "delay %d\n", &gcb.DelayTime))
			continue;
		    // cppcheck-suppress invalidscanf 
		    if (sscanf(tp, "transparent index %d\n",
			       &gcb.TransparentColor))
			continue;
		    (void) fputs(tp, stderr);
		    PARSE_ERROR("unrecognized directive in GCB block.");
		    exit(EXIT_FAILURE);
		}
	    Len = EGifGCBToExtension(&gcb, (GifByteType *)buf);
	    if (GifAddExtensionBlock(&LeadingExtensionBlockCount,
				     &LeadingExtensionBlocks,
				     GRAPHICS_EXT_FUNC_CODE,
				     Len,
				     (unsigned char *)buf) == GIF_ERROR) {
		PARSE_ERROR("out of memory while adding GCB.");
		exit(EXIT_FAILURE);
	    }

	}
	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "netscape loop %u", &intval))
	{
	    unsigned char params[3] = {1, 0, 0};
	    /* Create a Netscape 2.0 loop block */
	    if (GifAddExtensionBlock(&LeadingExtensionBlockCount,
				     &LeadingExtensionBlocks,
				     APPLICATION_EXT_FUNC_CODE,
				     11,
				     (unsigned char *)"NETSCAPE2.0")==GIF_ERROR) {
		PARSE_ERROR("out of memory while adding loop block.");
		exit(EXIT_FAILURE);
	    }
	    params[1] = (intval & 0xff);
	    params[2] = (intval >> 8) & 0xff;
	    if (GifAddExtensionBlock(&LeadingExtensionBlockCount,
				     &LeadingExtensionBlocks,
				     0, sizeof(params), params) == GIF_ERROR) {
		PARSE_ERROR("out of memory while adding loop continuation.");
		exit(EXIT_FAILURE);
	    }
	    
	}
	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "extension %x", &ExtCode))
	{
	    int bc = 0;
	    while (fgets(buf, sizeof(buf), txtin) != (char *)NULL)
		if (strcmp(buf, "end\n") == 0)
		    break;
	        else
		{
		    int Len;

		    buf[strlen(buf) - 1] = '\0';
		    Len = EscapeString(buf, buf);
		    if (GifAddExtensionBlock(&LeadingExtensionBlockCount,
					     &LeadingExtensionBlocks,
					     bc++ == CONTINUE_EXT_FUNC_CODE ? ExtCode : 0, 
					     Len,
					     (unsigned char *)buf) == GIF_ERROR) {
			PARSE_ERROR("out of memory while adding extension block.");
			exit(EXIT_FAILURE);
		    }
		}
	}

	/*
	 * Explicit image declarations 
	 */

	else if (strcmp(buf, "image\n") == 0)
	{
	    if ((NewImage = GifMakeSavedImage(GifFileOut, NULL)) == (SavedImage *)NULL)
	    {
		PARSE_ERROR("Out of memory while allocating image block.");
		exit(EXIT_FAILURE);
	    }

	    /* use global table unless user specifies a local one */
	    ColorMap = GlobalColorMap;
	    KeyTable = GlobalColorKeys;

	    /* connect leading extension blocks */
	    NewImage->ExtensionBlockCount = LeadingExtensionBlockCount;
	    NewImage->ExtensionBlocks = LeadingExtensionBlocks;
	    LeadingExtensionBlockCount = 0;
	    LeadingExtensionBlocks = NULL;
	}

	/*
	 * Nothing past this point is valid unless we've seen a previous
	 * image declaration.
	 */
	else if (NewImage == (SavedImage *)NULL)
	{
	    (void) fputs(buf, stderr);
	    PARSE_ERROR("Syntax error in header block.");
	    exit(EXIT_FAILURE);
	}

	/*
	 * Accept image attributes
	 */
	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "image top %d\n", &NewImage->ImageDesc.Top) == 1)
	    continue;

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf, "image left %d\n", &NewImage->ImageDesc.Left)== 1)
	    continue;

	else if (strcmp(buf, "image interlaced\n") == 0)
	{
	    NewImage->ImageDesc.Interlace = true;
	    continue;
	}

	// cppcheck-suppress invalidscanf 
	else if (sscanf(buf,
			"image bits %d by %d",
			&NewImage->ImageDesc.Width,
			&NewImage->ImageDesc.Height) == 2)
	{
	    int i, j;
	    static GifPixelType *Raster, *cp;
	    int c;
	    bool hex = (strstr(buf, "hex") != NULL);

	    /* coverity[overflow_sink] */
	    if ((Raster = (GifPixelType *) malloc(sizeof(GifPixelType) * NewImage->ImageDesc.Width * NewImage->ImageDesc.Height))
		== NULL) {
		PARSE_ERROR("Failed to allocate raster block, aborted.");
		exit(EXIT_FAILURE);
	    }

	    GifQprintf("%s: Image %d at (%d, %d) [%dx%d]:     ",
		       PROGRAM_NAME, GifFileOut->ImageCount,
		       NewImage->ImageDesc.Left, NewImage->ImageDesc.Top,
		       NewImage->ImageDesc.Width, NewImage->ImageDesc.Height);

	    cp = Raster;
	    for (i = 0; i < NewImage->ImageDesc.Height; i++) {

		char	*dp;

		for (j = 0; j < NewImage->ImageDesc.Width; j++)
		    if ((c = fgetc(txtin)) == EOF) {
			PARSE_ERROR("input file ended prematurely.");
			exit(EXIT_FAILURE);
		    }
		    else if (c == '\n')
		    {
			--j;
			++LineNum;
		    }
		    else if (isspace(c))
			--j;
		    else if (hex) 
		    {
			const static char *hexdigits = "0123456789ABCDEF";
			unsigned char hi, lo;
			dp = strchr(hexdigits, toupper(c));
			if (dp == NULL) {
			    PARSE_ERROR("Invalid hex high byte.");
			    exit(EXIT_FAILURE);
			}
			hi = (dp - hexdigits);
			if ((c = fgetc(txtin)) == EOF) {
			    PARSE_ERROR("input file ended prematurely.");
			    exit(EXIT_FAILURE);
			}
			dp = strchr(hexdigits, toupper(c));
			if (dp == NULL) {
			    PARSE_ERROR("Invalid hex low byte.");
			    exit(EXIT_FAILURE);
			}
			lo = (dp - hexdigits);
			*cp++ = (hi << 4) | lo;
		    }
		    else if ((dp = strchr(KeyTable, c)))
			*cp++ = (dp - KeyTable);
		    else {
			PARSE_ERROR("Invalid ASCII pixel key.");
			exit(EXIT_FAILURE);
		    }

		if (GifNoisyPrint)
		    fprintf(stderr, "\b\b\b\b%-4d", i);
	    }

	    if (GifNoisyPrint)
		putc('\n', stderr);

	    NewImage->RasterBits = (unsigned char *) Raster;
	}
	else
	{
	    (void) fputs(buf, stderr);
	    PARSE_ERROR("Syntax error in image description.");
	    exit(EXIT_FAILURE);
	}
    }

    /* connect trailing extension blocks */
    GifFileOut->ExtensionBlockCount = LeadingExtensionBlockCount;
    GifFileOut->ExtensionBlocks = LeadingExtensionBlocks;
    //LeadingExtensionBlockCount = 0;
    LeadingExtensionBlocks = NULL;
 
    EGifSpew(GifFileOut);
}

static void VisibleDumpBuffer(GifByteType *buf, int len)
/* Visibilize a given string */
{
    GifByteType	*cp;

    for (cp = buf; cp < buf + len; cp++)
    {
	if (isprint((int)(*cp)) || *cp == ' ')
	    putchar(*cp);
	else if (*cp == '\n')
	{
	    putchar('\\'); putchar('n');
	}
	else if (*cp == '\r')
	{
	    putchar('\\'); putchar('r');
	}
	else if (*cp == '\b')
	{
	    putchar('\\'); putchar('b');
	}
	else if (*cp < ' ')
	{
	    putchar('\\'); putchar('^'); putchar('@' + *cp);
	}
	else
	    printf("\\0x%02x", *cp);
    }
}

static void DumpExtensions(GifFileType *GifFileOut, 
			   int ExtensionBlockCount,
			   ExtensionBlock *ExtensionBlocks)
{
    ExtensionBlock *ep;

    for (ep = ExtensionBlocks; 
	 ep < ExtensionBlocks + ExtensionBlockCount;
	 ep++) {
	bool last = (ep - ExtensionBlocks == (ExtensionBlockCount - 1));
	if (ep->Function == COMMENT_EXT_FUNC_CODE) {
	    printf("comment\n");
	    VisibleDumpBuffer(ep->Bytes, ep->ByteCount);
	    putchar('\n');
	    while (!last && ep[1].Function == CONTINUE_EXT_FUNC_CODE) {
		++ep;
		last = (ep - ExtensionBlocks == (ExtensionBlockCount - 1));
		VisibleDumpBuffer(ep->Bytes, ep->ByteCount);
		putchar('\n');
	    }
	    printf("end\n\n");
	}
	else if (ep->Function == PLAINTEXT_EXT_FUNC_CODE) {
	    printf("plaintext\n");
	    VisibleDumpBuffer(ep->Bytes, ep->ByteCount);
	    putchar('\n');
	    while (!last && ep[1].Function == CONTINUE_EXT_FUNC_CODE) {
		++ep;
		last = (ep - ExtensionBlocks == (ExtensionBlockCount - 1));
		VisibleDumpBuffer(ep->Bytes, ep->ByteCount);
		putchar('\n');
	    }
	    printf("end\n\n");
	}
	else if (ep->Function == GRAPHICS_EXT_FUNC_CODE)
	{
	    GraphicsControlBlock gcb;
	    printf("graphics control\n");
	    if (DGifExtensionToGCB(ep->ByteCount, ep->Bytes, &gcb) == GIF_ERROR) {
		GIF_MESSAGE("invalid graphics control block");
		exit(EXIT_FAILURE);
	    }
	    printf("\tdisposal mode %d\n", gcb.DisposalMode);
	    printf("\tuser input flag %s\n", 
		   gcb.UserInputFlag ? "on" : "off");
	    printf("\tdelay %d\n", gcb.DelayTime);
	    printf("\ttransparent index %d\n", gcb.TransparentColor);
	    printf("end\n\n");
	}
	else if (!last
		 && ep->Function == APPLICATION_EXT_FUNC_CODE
		 && ep->ByteCount >= 11
		 && (ep+1)->ByteCount >= 3
		 && memcmp(ep->Bytes, "NETSCAPE2.0", 11) == 0) {
	    unsigned char *params = (++ep)->Bytes;
	    unsigned int loopcount = params[1] | (params[2] << 8);
	    printf("netscape loop %u\n\n", loopcount);
	}
	else {
	    printf("extension 0x%02x\n", ep->Function);
	    VisibleDumpBuffer(ep->Bytes, ep->ByteCount);
	    while (!last && ep[1].Function == CONTINUE_EXT_FUNC_CODE) {
		++ep;
		last = (ep - ExtensionBlocks == (ExtensionBlockCount - 1));
		VisibleDumpBuffer(ep->Bytes, ep->ByteCount);
		putchar('\n');
	    }
	    printf("end\n\n");
	}
    }
}

static void Gif2Icon(char *FileName,
		     int fdin, int fdout,
		     char NameTable[])
{
    int ErrorCode, im, i, j, ColorCount = 0;
    GifFileType *GifFile;

    if (fdin == -1) {
	if ((GifFile = DGifOpenFileName(FileName, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }
    else {
	/* Use stdin instead: */
	if ((GifFile = DGifOpenFileHandle(fdin, &ErrorCode)) == NULL) {
	    PrintGifError(ErrorCode);
	    exit(EXIT_FAILURE);
	}
    }

    if (DGifSlurp(GifFile) == GIF_ERROR) {
	PrintGifError(GifFile->Error);
	exit(EXIT_FAILURE);
    }

    printf("screen width %d\nscreen height %d\n",
	   GifFile->SWidth, GifFile->SHeight);

    printf("screen colors %d\nscreen background %d\npixel aspect byte %u\n\n",
	   1 << GifFile->SColorResolution,
	   GifFile->SBackGroundColor,
	   (unsigned)GifFile->AspectByte);

    if (GifFile->SColorMap)
    {
	printf("screen map\n");

	printf("\tsort flag %s\n", GifFile->SColorMap->SortFlag ? "on" : "off");

	for (i = 0; i < GifFile->SColorMap->ColorCount; i++)
	    if (GifFile->SColorMap->ColorCount < PRINTABLES)
		printf("\trgb %03d %03d %03d is %c\n",
		       GifFile->SColorMap ->Colors[i].Red,
		       GifFile->SColorMap ->Colors[i].Green,
		       GifFile->SColorMap ->Colors[i].Blue,
		       NameTable[i]);
	    else
		printf("\trgb %03d %03d %03d\n",
		       GifFile->SColorMap ->Colors[i].Red,
		       GifFile->SColorMap ->Colors[i].Green,
		       GifFile->SColorMap ->Colors[i].Blue);
	printf("end\n\n");
    }

    for (im = 0; im < GifFile->ImageCount; im++) {
	SavedImage *image = &GifFile->SavedImages[im];

	DumpExtensions(GifFile, 
		       image->ExtensionBlockCount, image->ExtensionBlocks);

	printf("image # %d\nimage left %d\nimage top %d\n",
	       im+1, image->ImageDesc.Left, image->ImageDesc.Top);
	if (image->ImageDesc.Interlace)
	    printf("image interlaced\n");

	if (image->ImageDesc.ColorMap)
	{
	    printf("image map\n");

	    printf("\tsort flag %s\n", 
		   image->ImageDesc.ColorMap->SortFlag ? "on" : "off");

	    if (image->ImageDesc.ColorMap->ColorCount < PRINTABLES)
		for (i = 0; i < image->ImageDesc.ColorMap->ColorCount; i++)
		    printf("\trgb %03d %03d %03d is %c\n",
			   image->ImageDesc.ColorMap ->Colors[i].Red,
			   image->ImageDesc.ColorMap ->Colors[i].Green,
			   image->ImageDesc.ColorMap ->Colors[i].Blue,
			   NameTable[i]);
	    else
		for (i = 0; i < image->ImageDesc.ColorMap->ColorCount; i++)
		    printf("\trgb %03d %03d %03d\n",
			   image->ImageDesc.ColorMap ->Colors[i].Red,
			   image->ImageDesc.ColorMap ->Colors[i].Green,
			   image->ImageDesc.ColorMap ->Colors[i].Blue);
	    printf("end\n\n");
	}

	/* one of these conditions has to be true */
	if (image->ImageDesc.ColorMap)
	    ColorCount = image->ImageDesc.ColorMap->ColorCount;
	else if (GifFile->SColorMap)
	    ColorCount = GifFile->SColorMap->ColorCount;

	if (ColorCount < PRINTABLES)
	    printf("image bits %d by %d\n",
		   image->ImageDesc.Width, image->ImageDesc.Height);
	else
	    printf("image bits %d by %d hex\n",
		   image->ImageDesc.Width, image->ImageDesc.Height);
	for (i = 0; i < image->ImageDesc.Height; i++) {
	    for (j = 0; j < image->ImageDesc.Width; j++) {
		GifByteType ch = image->RasterBits[i*image->ImageDesc.Width + j];
		if (ColorCount < PRINTABLES && ch < PRINTABLES)
		    putchar(NameTable[ch]);
		else
		    printf("%02x", ch);
	    }
	    putchar('\n');
	}
	putchar('\n');
    }

    DumpExtensions(GifFile, 
		   GifFile->ExtensionBlockCount, GifFile->ExtensionBlocks);

    /* Tell EMACS this is a picture... */
    printf("# The following sets edit modes for GNU EMACS\n");
    printf("# Local ");		/* ...break this up, so that EMACS doesn't */
    printf("Variables:\n");	/* get confused when visiting *this* file! */
    printf("# mode:picture\n");
    printf("# truncate-lines:t\n");
    printf("# End:\n");

    if (fdin == -1)
	(void) printf("# End of %s dump\n", FileName);


    /*
     * Sanity checks.
     */

    /* check that the background color isn't garbage (SF bug #87) */
    if (GifFile->SBackGroundColor < 0
	    || GifFile->SBackGroundColor >= GifFile->SColorMap->ColorCount) {
        fprintf(stderr, "gifbuild: background color invalid for screen colormap.\n");
    }

    if (DGifCloseFile(GifFile, &ErrorCode) == GIF_ERROR) {
	PrintGifError(ErrorCode);
	exit(EXIT_FAILURE);
    }
}

static int EscapeString(char *cp, char *tp)
/* process standard C-style escape sequences in a string */
{
    char *StartAddr = tp;

    while (*cp)
    {
	int	cval = 0;

	if (*cp == '\\' && strchr("0123456789xX", cp[1]))
	{
	    int dcount = 0;

	    if (*++cp == 'x' || *cp == 'X') {
		char *dp, *hex = "00112233445566778899aAbBcCdDeEfF";
		for (++cp; (dp = strchr(hex, *cp)) && (dcount++ < 2); cp++)
		    cval = (cval * 16) + (dp - hex) / 2;
	    } else if (*cp == '0')
		while (strchr("01234567",*cp) != (char*)NULL && (dcount++ < 3))
		    cval = (cval * 8) + (*cp++ - '0');
	    else
		while ((strchr("0123456789",*cp)!=(char*)NULL)&&(dcount++ < 3))
		    cval = (cval * 10) + (*cp++ - '0');
	}
	else if (*cp == '\\')		/* C-style character escapes */
	{
	    switch (*++cp)
	    {
	    case '\\': cval = '\\'; break;
	    case 'n': cval = '\n'; break;
	    case 't': cval = '\t'; break;
	    case 'b': cval = '\b'; break;
	    case 'r': cval = '\r'; break;
	    default: cval = *cp;
	    }
	    cp++;
	}
	else if (*cp == '^')		/* expand control-character syntax */
	{
	    cval = (*++cp & 0x1f);
	    cp++;
	}
	else
	    cval = *cp++;
	*tp++ = cval;
    }

    return(tp - StartAddr);
}

/* end */

