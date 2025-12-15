/*****************************************************************************

gifinto - save GIF on stdin to file if size over set threshold

*****************************************************************************/

#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>
#include <stdbool.h>

#ifdef _WIN32
#include <io.h>
#endif /* _WIN32 */

#include "gif_lib.h"
#include "getarg.h"

#define PROGRAM_NAME	"gifinto"

#define STRLEN		512

#define DEFAULT_MIN_FILE_SIZE	14     /* More than GIF stamp + screen desc. */
#define	DEFAULT_OUT_NAME	"GifInto.Gif"
#define DEFAULT_TMP_NAME	"TempInto.XXXXXX"

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
	" v%- s%-MinFileSize!d h%- GifFile!*s";

static int
    MinFileSize = DEFAULT_MIN_FILE_SIZE;

#ifdef _WIN32
#include <errno.h>
#include <sys/stat.h>
int
mkstemp(char *tpl)
{
  int fd = -1;
  char *p;
  int e = errno;

  errno = 0;
  p = _mktemp(tpl);
  if (*p && errno == 0)
    {
      errno = e;
      fd = _open(p, _O_RDWR | _O_CREAT | _O_EXCL | _O_BINARY,
		 _S_IREAD | _S_IWRITE);
    }
  return fd;
}
#endif

/******************************************************************************
 This is simply: read until EOF, then close the output, test its length, and
 if non zero then rename it.
******************************************************************************/
int main(int argc, char **argv)
{
    int FD;
    int	NumFiles;
    bool Error, MinSizeFlag = false, HelpFlag = false;
    char **FileName = NULL, FoutTmpName[STRLEN+1], FullPath[STRLEN+1], *p;
    FILE *Fin, *Fout;

    if ((Error = GAGetArgs(argc, argv, CtrlStr, &GifNoisyPrint,
		&MinSizeFlag, &MinFileSize, &HelpFlag,
		&NumFiles, &FileName)) != false ||
		(NumFiles > 1 && !HelpFlag)) {
	if (Error)
	    GAPrintErrMsg(Error);
	else if (NumFiles != 1)
	    GIF_MESSAGE("Error in command line parsing - one GIF file please.");
	GAPrintHowTo(CtrlStr);
	exit(EXIT_FAILURE);
    }

    if (HelpFlag) {
	(void)fprintf(stderr, VersionStr, GIFLIB_MAJOR, GIFLIB_MINOR);
	GAPrintHowTo(CtrlStr);
	exit(EXIT_SUCCESS);
    }

    /* Open the stdin in binary mode and increase its buffer size: */
#ifdef _WIN32
    _setmode(0, O_BINARY);		  /* Make sure it is in binary mode. */
#endif

    Fin = fdopen(0, "rb");   /* Make it into a stream: */

    if (Fin == NULL)
    {
        GIF_EXIT("Failed to open input.");
    }

    /* Isolate the directory where our destination is, and set tmp file name */
    /* in the very same directory. This code is isecure because it creates   */
    /* predictable names, but it's not worth the effort and risk to fix.     */
    if ( *FileName == NULL ) GIF_EXIT("No valid Filename given.");
    if ( strlen(*FileName) > STRLEN-1 ) GIF_EXIT("Filename too long.");
    memset(FullPath, '\0', sizeof(FullPath));
    // cppcheck-suppress redundantCopy
    strncpy(FullPath, *FileName, STRLEN);
    if ((p = strrchr(FullPath, '/')) != NULL ||
	(p = strrchr(FullPath, '\\')) != NULL)
	p[1] = 0;
    else if ((p = strrchr(FullPath, ':')) != NULL)
	p[1] = 0;
    else
	FullPath[0] = 0;		  /* No directory or disk specified. */

    if ( strlen(FullPath) > STRLEN-1 ) GIF_EXIT("Filename too long.");
    strncpy(FoutTmpName, FullPath, STRLEN);   /* First setup the Path */
    /* then add a name for the tempfile */
    if ( (strlen(FoutTmpName) + strlen(DEFAULT_TMP_NAME))  > STRLEN-1 ) GIF_EXIT("Filename too long.");
    strcat(FoutTmpName, DEFAULT_TMP_NAME);
#ifdef _WIN32
    char *tmpFN = _mktemp(FoutTmpName);
    if (tmpFN)
	FD = open(tmpFN, O_CREAT | O_EXCL | O_WRONLY);
    else
	FD = -1;
#else
    FD = mkstemp(FoutTmpName); /* returns filedescriptor */
#endif
    if (FD == -1 )
    {
	GIF_EXIT("Failed to open output.");
    }
    Fout = fdopen(FD, "wb"); /* returns a stream with FD */
    if (Fout == NULL )
    {
	GIF_EXIT("Failed to open output.");
    }

    while (1) {
	int c = getc(Fin);

	if (feof(Fin))
	    break;
	if (putc(c, Fout) == EOF)
	    GIF_EXIT("Failed to write output.");
    }

    fclose(Fin);
    if (ftell(Fout) >= (long) MinFileSize) {
	fclose(Fout);
	unlink(*FileName);
	if (rename(FoutTmpName, *FileName) != 0) {
	    char DefaultName[STRLEN+1];
	    memset(DefaultName, '\0', sizeof(DefaultName));
	    if ( (strlen(FullPath) + strlen(DEFAULT_OUT_NAME)) > STRLEN-1 ) GIF_EXIT("Filename too long.");
	    strncpy(DefaultName, FullPath, STRLEN);
	    // cppcheck-suppress uninitstring
	    strcat(DefaultName, DEFAULT_OUT_NAME);
	    if (rename(FoutTmpName, DefaultName) == 0) {
		char s[STRLEN];
		snprintf(s, STRLEN, "Failed to rename out file - left as %s.",
			      DefaultName);
		GIF_MESSAGE(s);
	    }
	    else {
		unlink(FoutTmpName);
		GIF_MESSAGE("Failed to rename out file - deleted.");
	    }
	}
    }
    else {
	fclose(Fout);
	unlink(FoutTmpName);
	GIF_MESSAGE("File too small - not renamed.");
    }

    return 0;
}

/* end */
