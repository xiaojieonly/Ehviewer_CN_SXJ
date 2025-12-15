/*
 * Copyright (C)2009-2014, 2017-2018 D. R. Commander.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of the libjpeg-turbo Project nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS",
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This program tests the various code paths in the TurboJPEG C Wrapper
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "tjutil.h"
#include "turbojpeg.h"
#include "md5/md5.h"
#include "cmyk.h"
#ifdef _WIN32
#include <time.h>
#define random()  rand()
#else
#include <unistd.h>
#endif


void usage(char *progName)
{
  printf("\nUSAGE: %s [options]\n\n", progName);
  printf("Options:\n");
  printf("-yuv = test YUV encoding/decoding support\n");
  printf("-noyuvpad = do not pad each line of each Y, U, and V plane to the nearest\n");
  printf("            4-byte boundary\n");
  printf("-alloc = test automatic buffer allocation\n");
  printf("-bmp = tjLoadImage()/tjSaveImage() unit test\n\n");
  exit(1);
}


#define _throwtj() { \
  printf("TurboJPEG ERROR:\n%s\n", tjGetErrorStr()); \
  bailout() \
}
#define _tj(f) { if ((f) == -1) _throwtj(); }
#define _throw(m) { printf("ERROR: %s\n", m);  bailout() }
#define _throwmd5(filename, md5sum, ref) { \
  printf("\n%s has an MD5 sum of %s.\n   Should be %s.\n", filename, md5sum, \
         ref); \
  bailout() \
}

const char *subNameLong[TJ_NUMSAMP] = {
  "4:4:4", "4:2:2", "4:2:0", "GRAY", "4:4:0", "4:1:1"
};
const char *subName[TJ_NUMSAMP] = {
  "444", "422", "420", "GRAY", "440", "411"
};

const char *pixFormatStr[TJ_NUMPF] = {
  "RGB", "BGR", "RGBX", "BGRX", "XBGR", "XRGB", "Grayscale",
  "RGBA", "BGRA", "ABGR", "ARGB", "CMYK"
};

const int _3byteFormats[] = { TJPF_RGB, TJPF_BGR };
const int _4byteFormats[] = {
  TJPF_RGBX, TJPF_BGRX, TJPF_XBGR, TJPF_XRGB, TJPF_CMYK
};
const int _onlyGray[] = { TJPF_GRAY };
const int _onlyRGB[] = { TJPF_RGB };

int doYUV = 0, alloc = 0, pad = 4;

int exitStatus = 0;
#define bailout() { exitStatus = -1;  goto bailout; }


void initBuf(unsigned char *buf, int w, int h, int pf, int flags)
{
  int roffset = tjRedOffset[pf];
  int goffset = tjGreenOffset[pf];
  int boffset = tjBlueOffset[pf];
  int ps = tjPixelSize[pf];
  int index, row, col, halfway = 16;

  if (pf == TJPF_GRAY) {
    memset(buf, 0, w * h * ps);
    for (row = 0; row < h; row++) {
      for (col = 0; col < w; col++) {
        if (flags & TJFLAG_BOTTOMUP) index = (h - row - 1) * w + col;
        else index = row * w + col;
        if (((row / 8) + (col / 8)) % 2 == 0)
          buf[index] = (row < halfway) ? 255 : 0;
        else buf[index] = (row < halfway) ? 76 : 226;
      }
    }
  } else if (pf == TJPF_CMYK) {
    memset(buf, 255, w * h * ps);
    for (row = 0; row < h; row++) {
      for (col = 0; col < w; col++) {
        if (flags & TJFLAG_BOTTOMUP) index = (h - row - 1) * w + col;
        else index = row * w + col;
        if (((row / 8) + (col / 8)) % 2 == 0) {
          if (row >= halfway) buf[index * ps + 3] = 0;
        } else {
          buf[index * ps + 2] = 0;
          if (row < halfway) buf[index * ps + 1] = 0;
        }
      }
    }
  } else {
    memset(buf, 0, w * h * ps);
    for (row = 0; row < h; row++) {
      for (col = 0; col < w; col++) {
        if (flags & TJFLAG_BOTTOMUP) index = (h - row - 1) * w + col;
        else index = row * w + col;
        if (((row / 8) + (col / 8)) % 2 == 0) {
          if (row < halfway) {
            buf[index * ps + roffset] = 255;
            buf[index * ps + goffset] = 255;
            buf[index * ps + boffset] = 255;
          }
        } else {
          buf[index * ps + roffset] = 255;
          if (row >= halfway) buf[index * ps + goffset] = 255;
        }
      }
    }
  }
}


#define checkval(v, cv) { \
  if (v < cv - 1 || v > cv + 1) { \
    printf("\nComp. %s at %d,%d should be %d, not %d\n", #v, row, col, cv, \
           v); \
    retval = 0;  exitStatus = -1;  goto bailout; \
  } \
}

#define checkval0(v) { \
  if (v > 1) { \
    printf("\nComp. %s at %d,%d should be 0, not %d\n", #v, row, col, v); \
    retval = 0;  exitStatus = -1;  goto bailout; \
  } \
}

#define checkval255(v) { \
  if (v < 254) { \
    printf("\nComp. %s at %d,%d should be 255, not %d\n", #v, row, col, v); \
    retval = 0;  exitStatus = -1;  goto bailout; \
  } \
}


int checkBuf(unsigned char *buf, int w, int h, int pf, int subsamp,
             tjscalingfactor sf, int flags)
{
  int roffset = tjRedOffset[pf];
  int goffset = tjGreenOffset[pf];
  int boffset = tjBlueOffset[pf];
  int aoffset = tjAlphaOffset[pf];
  int ps = tjPixelSize[pf];
  int index, row, col, retval = 1;
  int halfway = 16 * sf.num / sf.denom;
  int blocksize = 8 * sf.num / sf.denom;

  if (pf == TJPF_GRAY) roffset = goffset = boffset = 0;

  if (pf == TJPF_CMYK) {
    for (row = 0; row < h; row++) {
      for (col = 0; col < w; col++) {
        unsigned char c, m, y, k;

        if (flags & TJFLAG_BOTTOMUP) index = (h - row - 1) * w + col;
        else index = row * w + col;
        c = buf[index * ps];
        m = buf[index * ps + 1];
        y = buf[index * ps + 2];
        k = buf[index * ps + 3];
        if (((row / blocksize) + (col / blocksize)) % 2 == 0) {
          checkval255(c);  checkval255(m);  checkval255(y);
          if (row < halfway) checkval255(k)
          else checkval0(k)
        } else {
          checkval255(c);  checkval0(y);  checkval255(k);
          if (row < halfway) checkval0(m)
          else checkval255(m)
        }
      }
    }
    return 1;
  }

  for (row = 0; row < h; row++) {
    for (col = 0; col < w; col++) {
      unsigned char r, g, b, a;

      if (flags & TJFLAG_BOTTOMUP) index = (h - row - 1) * w + col;
      else index = row * w + col;
      r = buf[index * ps + roffset];
      g = buf[index * ps + goffset];
      b = buf[index * ps + boffset];
      a = aoffset >= 0 ? buf[index * ps + aoffset] : 0xFF;
      if (((row / blocksize) + (col / blocksize)) % 2 == 0) {
        if (row < halfway) {
          checkval255(r);  checkval255(g);  checkval255(b);
        } else {
          checkval0(r);  checkval0(g);  checkval0(b);
        }
      } else {
        if (subsamp == TJSAMP_GRAY) {
          if (row < halfway) {
            checkval(r, 76);  checkval(g, 76);  checkval(b, 76);
          } else {
            checkval(r, 226);  checkval(g, 226);  checkval(b, 226);
          }
        } else {
          if (row < halfway) {
            checkval255(r);  checkval0(g);  checkval0(b);
          } else {
            checkval255(r);  checkval255(g);  checkval0(b);
          }
        }
      }
      checkval255(a);
    }
  }

bailout:
  if (retval == 0) {
    for (row = 0; row < h; row++) {
      for (col = 0; col < w; col++) {
        if (pf == TJPF_CMYK)
          printf("%.3d/%.3d/%.3d/%.3d ", buf[(row * w + col) * ps],
                 buf[(row * w + col) * ps + 1], buf[(row * w + col) * ps + 2],
                 buf[(row * w + col) * ps + 3]);
        else
          printf("%.3d/%.3d/%.3d ", buf[(row * w + col) * ps + roffset],
                 buf[(row * w + col) * ps + goffset],
                 buf[(row * w + col) * ps + boffset]);
      }
      printf("\n");
    }
  }
  return retval;
}


#define PAD(v, p)  ((v + (p) - 1) & (~((p) - 1)))

int checkBufYUV(unsigned char *buf, int w, int h, int subsamp,
                tjscalingfactor sf)
{
  int row, col;
  int hsf = tjMCUWidth[subsamp] / 8, vsf = tjMCUHeight[subsamp] / 8;
  int pw = PAD(w, hsf), ph = PAD(h, vsf);
  int cw = pw / hsf, ch = ph / vsf;
  int ypitch = PAD(pw, pad), uvpitch = PAD(cw, pad);
  int retval = 1;
  int halfway = 16 * sf.num / sf.denom;
  int blocksize = 8 * sf.num / sf.denom;

  for (row = 0; row < ph; row++) {
    for (col = 0; col < pw; col++) {
      unsigned char y = buf[ypitch * row + col];

      if (((row / blocksize) + (col / blocksize)) % 2 == 0) {
        if (row < halfway) checkval255(y)
        else checkval0(y);
      } else {
        if (row < halfway) checkval(y, 76)
        else checkval(y, 226);
      }
    }
  }
  if (subsamp != TJSAMP_GRAY) {
    int halfway = 16 / vsf * sf.num / sf.denom;

    for (row = 0; row < ch; row++) {
      for (col = 0; col < cw; col++) {
        unsigned char u = buf[ypitch * ph + (uvpitch * row + col)],
          v = buf[ypitch * ph + uvpitch * ch + (uvpitch * row + col)];

        if (((row * vsf / blocksize) + (col * hsf / blocksize)) % 2 == 0) {
          checkval(u, 128);  checkval(v, 128);
        } else {
          if (row < halfway) {
            checkval(u, 85);  checkval255(v);
          } else {
            checkval0(u);  checkval(v, 149);
          }
        }
      }
    }
  }

bailout:
  if (retval == 0) {
    for (row = 0; row < ph; row++) {
      for (col = 0; col < pw; col++)
        printf("%.3d ", buf[ypitch * row + col]);
      printf("\n");
    }
    printf("\n");
    for (row = 0; row < ch; row++) {
      for (col = 0; col < cw; col++)
        printf("%.3d ", buf[ypitch * ph + (uvpitch * row + col)]);
      printf("\n");
    }
    printf("\n");
    for (row = 0; row < ch; row++) {
      for (col = 0; col < cw; col++)
        printf("%.3d ",
               buf[ypitch * ph + uvpitch * ch + (uvpitch * row + col)]);
      printf("\n");
    }
  }

  return retval;
}


void writeJPEG(unsigned char *jpegBuf, unsigned long jpegSize, char *filename)
{
  FILE *file = fopen(filename, "wb");

  if (!file || fwrite(jpegBuf, jpegSize, 1, file) != 1) {
    printf("ERROR: Could not write to %s.\n%s\n", filename, strerror(errno));
    bailout()
  }

bailout:
  if (file) fclose(file);
}


void compTest(tjhandle handle, unsigned char **dstBuf, unsigned long *dstSize,
              int w, int h, int pf, char *basename, int subsamp, int jpegQual,
              int flags)
{
  char tempStr[1024];
  unsigned char *srcBuf = NULL, *yuvBuf = NULL;
  const char *pfStr = pixFormatStr[pf];
  const char *buStrLong =
    (flags & TJFLAG_BOTTOMUP) ? "Bottom-Up" : "Top-Down ";
  const char *buStr = (flags & TJFLAG_BOTTOMUP) ? "BU" : "TD";

  if ((srcBuf = (unsigned char *)malloc(w * h * tjPixelSize[pf])) == NULL)
    _throw("Memory allocation failure");
  initBuf(srcBuf, w, h, pf, flags);

  if (*dstBuf && *dstSize > 0) memset(*dstBuf, 0, *dstSize);

  if (!alloc) flags |= TJFLAG_NOREALLOC;
  if (doYUV) {
    unsigned long yuvSize = tjBufSizeYUV2(w, pad, h, subsamp);
    tjscalingfactor sf = { 1, 1 };
    tjhandle handle2 = tjInitCompress();

    if (!handle2) _throwtj();

    if ((yuvBuf = (unsigned char *)malloc(yuvSize)) == NULL)
      _throw("Memory allocation failure");
    memset(yuvBuf, 0, yuvSize);

    printf("%s %s -> YUV %s ... ", pfStr, buStrLong, subNameLong[subsamp]);
    _tj(tjEncodeYUV3(handle2, srcBuf, w, 0, h, pf, yuvBuf, pad, subsamp,
                     flags));
    tjDestroy(handle2);
    if (checkBufYUV(yuvBuf, w, h, subsamp, sf)) printf("Passed.\n");
    else printf("FAILED!\n");

    printf("YUV %s %s -> JPEG Q%d ... ", subNameLong[subsamp], buStrLong,
           jpegQual);
    _tj(tjCompressFromYUV(handle, yuvBuf, w, pad, h, subsamp, dstBuf, dstSize,
                          jpegQual, flags));
  } else {
    printf("%s %s -> %s Q%d ... ", pfStr, buStrLong, subNameLong[subsamp],
           jpegQual);
    _tj(tjCompress2(handle, srcBuf, w, 0, h, pf, dstBuf, dstSize, subsamp,
                    jpegQual, flags));
  }

  snprintf(tempStr, 1024, "%s_enc_%s_%s_%s_Q%d.jpg", basename, pfStr, buStr,
           subName[subsamp], jpegQual);
  writeJPEG(*dstBuf, *dstSize, tempStr);
  printf("Done.\n  Result in %s\n", tempStr);

bailout:
  if (yuvBuf) free(yuvBuf);
  if (srcBuf) free(srcBuf);
}


void _decompTest(tjhandle handle, unsigned char *jpegBuf,
                 unsigned long jpegSize, int w, int h, int pf, char *basename,
                 int subsamp, int flags, tjscalingfactor sf)
{
  unsigned char *dstBuf = NULL, *yuvBuf = NULL;
  int _hdrw = 0, _hdrh = 0, _hdrsubsamp = -1;
  int scaledWidth = TJSCALED(w, sf);
  int scaledHeight = TJSCALED(h, sf);
  unsigned long dstSize = 0;

  _tj(tjDecompressHeader2(handle, jpegBuf, jpegSize, &_hdrw, &_hdrh,
                          &_hdrsubsamp));
  if (_hdrw != w || _hdrh != h || _hdrsubsamp != subsamp)
    _throw("Incorrect JPEG header");

  dstSize = scaledWidth * scaledHeight * tjPixelSize[pf];
  if ((dstBuf = (unsigned char *)malloc(dstSize)) == NULL)
    _throw("Memory allocation failure");
  memset(dstBuf, 0, dstSize);

  if (doYUV) {
    unsigned long yuvSize = tjBufSizeYUV2(scaledWidth, pad, scaledHeight,
                                          subsamp);
    tjhandle handle2 = tjInitDecompress();

    if (!handle2) _throwtj();

    if ((yuvBuf = (unsigned char *)malloc(yuvSize)) == NULL)
      _throw("Memory allocation failure");
    memset(yuvBuf, 0, yuvSize);

    printf("JPEG -> YUV %s ", subNameLong[subsamp]);
    if (sf.num != 1 || sf.denom != 1)
      printf("%d/%d ... ", sf.num, sf.denom);
    else printf("... ");
    _tj(tjDecompressToYUV2(handle, jpegBuf, jpegSize, yuvBuf, scaledWidth, pad,
                           scaledHeight, flags));
    if (checkBufYUV(yuvBuf, scaledWidth, scaledHeight, subsamp, sf))
      printf("Passed.\n");
    else printf("FAILED!\n");

    printf("YUV %s -> %s %s ... ", subNameLong[subsamp], pixFormatStr[pf],
           (flags & TJFLAG_BOTTOMUP) ? "Bottom-Up" : "Top-Down ");
    _tj(tjDecodeYUV(handle2, yuvBuf, pad, subsamp, dstBuf, scaledWidth, 0,
                    scaledHeight, pf, flags));
    tjDestroy(handle2);
  } else {
    printf("JPEG -> %s %s ", pixFormatStr[pf],
           (flags & TJFLAG_BOTTOMUP) ? "Bottom-Up" : "Top-Down ");
    if (sf.num != 1 || sf.denom != 1)
      printf("%d/%d ... ", sf.num, sf.denom);
    else printf("... ");
    _tj(tjDecompress2(handle, jpegBuf, jpegSize, dstBuf, scaledWidth, 0,
                      scaledHeight, pf, flags));
  }

  if (checkBuf(dstBuf, scaledWidth, scaledHeight, pf, subsamp, sf, flags))
    printf("Passed.");
  else printf("FAILED!");
  printf("\n");

bailout:
  if (yuvBuf) free(yuvBuf);
  if (dstBuf) free(dstBuf);
}


void decompTest(tjhandle handle, unsigned char *jpegBuf,
                unsigned long jpegSize, int w, int h, int pf, char *basename,
                int subsamp, int flags)
{
  int i, n = 0;
  tjscalingfactor *sf = tjGetScalingFactors(&n);

  if (!sf || !n) _throwtj();

  for (i = 0; i < n; i++) {
    if (subsamp == TJSAMP_444 || subsamp == TJSAMP_GRAY ||
        (subsamp == TJSAMP_411 && sf[i].num == 1 &&
         (sf[i].denom == 2 || sf[i].denom == 1)) ||
        (subsamp != TJSAMP_411 && sf[i].num == 1 &&
         (sf[i].denom == 4 || sf[i].denom == 2 || sf[i].denom == 1)))
      _decompTest(handle, jpegBuf, jpegSize, w, h, pf, basename, subsamp,
                  flags, sf[i]);
  }

bailout:
  return;
}


void doTest(int w, int h, const int *formats, int nformats, int subsamp,
            char *basename)
{
  tjhandle chandle = NULL, dhandle = NULL;
  unsigned char *dstBuf = NULL;
  unsigned long size = 0;
  int pfi, pf, i;

  if (!alloc)
    size = tjBufSize(w, h, subsamp);
  if (size != 0)
    if ((dstBuf = (unsigned char *)tjAlloc(size)) == NULL)
      _throw("Memory allocation failure.");

  if ((chandle = tjInitCompress()) == NULL ||
      (dhandle = tjInitDecompress()) == NULL)
    _throwtj();

  for (pfi = 0; pfi < nformats; pfi++) {
    for (i = 0; i < 2; i++) {
      int flags = 0;

      if (subsamp == TJSAMP_422 || subsamp == TJSAMP_420 ||
          subsamp == TJSAMP_440 || subsamp == TJSAMP_411)
        flags |= TJFLAG_FASTUPSAMPLE;
      if (i == 1) flags |= TJFLAG_BOTTOMUP;
      pf = formats[pfi];
      compTest(chandle, &dstBuf, &size, w, h, pf, basename, subsamp, 100,
               flags);
      decompTest(dhandle, dstBuf, size, w, h, pf, basename, subsamp, flags);
      if (pf >= TJPF_RGBX && pf <= TJPF_XRGB) {
        printf("\n");
        decompTest(dhandle, dstBuf, size, w, h, pf + (TJPF_RGBA - TJPF_RGBX),
                   basename, subsamp, flags);
      }
      printf("\n");
    }
  }
  printf("--------------------\n\n");

bailout:
  if (chandle) tjDestroy(chandle);
  if (dhandle) tjDestroy(dhandle);
  if (dstBuf) tjFree(dstBuf);
}


void bufSizeTest(void)
{
  int w, h, i, subsamp;
  unsigned char *srcBuf = NULL, *dstBuf = NULL;
  tjhandle handle = NULL;
  unsigned long dstSize = 0;

  if ((handle = tjInitCompress()) == NULL) _throwtj();

  printf("Buffer size regression test\n");
  for (subsamp = 0; subsamp < TJ_NUMSAMP; subsamp++) {
    for (w = 1; w < 48; w++) {
      int maxh = (w == 1) ? 2048 : 48;

      for (h = 1; h < maxh; h++) {
        if (h % 100 == 0) printf("%.4d x %.4d\b\b\b\b\b\b\b\b\b\b\b", w, h);
        if ((srcBuf = (unsigned char *)malloc(w * h * 4)) == NULL)
          _throw("Memory allocation failure");
        if (!alloc || doYUV) {
          if (doYUV) dstSize = tjBufSizeYUV2(w, pad, h, subsamp);
          else dstSize = tjBufSize(w, h, subsamp);
          if ((dstBuf = (unsigned char *)tjAlloc(dstSize)) == NULL)
            _throw("Memory allocation failure");
        }

        for (i = 0; i < w * h * 4; i++) {
          if (random() < RAND_MAX / 2) srcBuf[i] = 0;
          else srcBuf[i] = 255;
        }

        if (doYUV) {
          _tj(tjEncodeYUV3(handle, srcBuf, w, 0, h, TJPF_BGRX, dstBuf, pad,
                           subsamp, 0));
        } else {
          _tj(tjCompress2(handle, srcBuf, w, 0, h, TJPF_BGRX, &dstBuf,
                          &dstSize, subsamp, 100,
                          alloc ? 0 : TJFLAG_NOREALLOC));
        }
        free(srcBuf);  srcBuf = NULL;
        if (!alloc || doYUV) {
          tjFree(dstBuf);  dstBuf = NULL;
        }

        if ((srcBuf = (unsigned char *)malloc(h * w * 4)) == NULL)
          _throw("Memory allocation failure");
        if (!alloc || doYUV) {
          if (doYUV) dstSize = tjBufSizeYUV2(h, pad, w, subsamp);
          else dstSize = tjBufSize(h, w, subsamp);
          if ((dstBuf = (unsigned char *)tjAlloc(dstSize)) == NULL)
            _throw("Memory allocation failure");
        }

        for (i = 0; i < h * w * 4; i++) {
          if (random() < RAND_MAX / 2) srcBuf[i] = 0;
          else srcBuf[i] = 255;
        }

        if (doYUV) {
          _tj(tjEncodeYUV3(handle, srcBuf, h, 0, w, TJPF_BGRX, dstBuf, pad,
                           subsamp, 0));
        } else {
          _tj(tjCompress2(handle, srcBuf, h, 0, w, TJPF_BGRX, &dstBuf,
                          &dstSize, subsamp, 100,
                          alloc ? 0 : TJFLAG_NOREALLOC));
        }
        free(srcBuf);  srcBuf = NULL;
        if (!alloc || doYUV) {
          tjFree(dstBuf);  dstBuf = NULL;
        }
      }
    }
  }
  printf("Done.      \n");

bailout:
  if (srcBuf) free(srcBuf);
  if (dstBuf) tjFree(dstBuf);
  if (handle) tjDestroy(handle);
}


void initBitmap(unsigned char *buf, int width, int pitch, int height, int pf,
                int flags)
{
  int roffset = tjRedOffset[pf];
  int goffset = tjGreenOffset[pf];
  int boffset = tjBlueOffset[pf];
  int ps = tjPixelSize[pf];
  int i, j;

  for (j = 0; j < height; j++) {
    int row = (flags & TJFLAG_BOTTOMUP) ? height - j - 1 : j;

    for (i = 0; i < width; i++) {
      unsigned char r = (i * 256 / width) % 256;
      unsigned char g = (j * 256 / height) % 256;
      unsigned char b = (j * 256 / height + i * 256 / width) % 256;

      memset(&buf[row * pitch + i * ps], 0, ps);
      if (pf == TJPF_GRAY) buf[row * pitch + i * ps] = b;
      else if (pf == TJPF_CMYK)
        rgb_to_cmyk(r, g, b, &buf[row * pitch + i * ps + 0],
                    &buf[row * pitch + i * ps + 1],
                    &buf[row * pitch + i * ps + 2],
                    &buf[row * pitch + i * ps + 3]);
      else {
        buf[row * pitch + i * ps + roffset] = r;
        buf[row * pitch + i * ps + goffset] = g;
        buf[row * pitch + i * ps + boffset] = b;
      }
    }
  }
}


int cmpBitmap(unsigned char *buf, int width, int pitch, int height, int pf,
              int flags, int gray2rgb)
{
  int roffset = tjRedOffset[pf];
  int goffset = tjGreenOffset[pf];
  int boffset = tjBlueOffset[pf];
  int aoffset = tjAlphaOffset[pf];
  int ps = tjPixelSize[pf];
  int i, j;

  for (j = 0; j < height; j++) {
    int row = (flags & TJFLAG_BOTTOMUP) ? height - j - 1 : j;

    for (i = 0; i < width; i++) {
      unsigned char r = (i * 256 / width) % 256;
      unsigned char g = (j * 256 / height) % 256;
      unsigned char b = (j * 256 / height + i * 256 / width) % 256;

      if (pf == TJPF_GRAY) {
        if (buf[row * pitch + i * ps] != b)
          return 0;
      } else if (pf == TJPF_CMYK) {
        unsigned char rf, gf, bf;

        cmyk_to_rgb(buf[row * pitch + i * ps + 0],
                    buf[row * pitch + i * ps + 1],
                    buf[row * pitch + i * ps + 2],
                    buf[row * pitch + i * ps + 3], &rf, &gf, &bf);
        if (gray2rgb) {
          if (rf != b || gf != b || bf != b)
            return 0;
        } else if (rf != r || gf != g || bf != b) return 0;
      } else {
        if (gray2rgb) {
          if (buf[row * pitch + i * ps + roffset] != b ||
              buf[row * pitch + i * ps + goffset] != b ||
              buf[row * pitch + i * ps + boffset] != b)
            return 0;
        } else if (buf[row * pitch + i * ps + roffset] != r ||
                   buf[row * pitch + i * ps + goffset] != g ||
                   buf[row * pitch + i * ps + boffset] != b)
          return 0;
        if (aoffset >= 0 && buf[row * pitch + i * ps + aoffset] != 0xFF)
          return 0;
      }
    }
  }
  return 1;
}


int doBmpTest(const char *ext, int width, int align, int height, int pf,
              int flags)
{
  char filename[80], *md5sum, md5buf[65];
  int ps = tjPixelSize[pf], pitch = PAD(width * ps, align), loadWidth = 0,
    loadHeight = 0, retval = 0, pixelFormat = pf;
  unsigned char *buf = NULL;
  char *md5ref;

  if (pf == TJPF_GRAY) {
    md5ref = !strcasecmp(ext, "ppm") ? "112c682e82ce5de1cca089e20d60000b" :
                                       "51976530acf75f02beddf5d21149101d";
  } else {
    md5ref = !strcasecmp(ext, "ppm") ? "c0c9f772b464d1896326883a5c79c545" :
                                       "6d659071b9bfcdee2def22cb58ddadca";
  }

  if ((buf = (unsigned char *)tjAlloc(pitch * height)) == NULL)
    _throw("Could not allocate memory");
  initBitmap(buf, width, pitch, height, pf, flags);

  snprintf(filename, 80, "test_bmp_%s_%d_%s.%s", pixFormatStr[pf], align,
           (flags & TJFLAG_BOTTOMUP) ? "bu" : "td", ext);
  _tj(tjSaveImage(filename, buf, width, pitch, height, pf, flags));
  md5sum = MD5File(filename, md5buf);
  if (strcasecmp(md5sum, md5ref))
    _throwmd5(filename, md5sum, md5ref);

  tjFree(buf);  buf = NULL;
  if ((buf = tjLoadImage(filename, &loadWidth, align, &loadHeight, &pf,
                         flags)) == NULL)
    _throwtj();
  if (width != loadWidth || height != loadHeight) {
    printf("\n   Image dimensions of %s are bogus\n", filename);
    retval = -1;  goto bailout;
  }
  if (!cmpBitmap(buf, width, pitch, height, pf, flags, 0)) {
    printf("\n   Pixel data in %s is bogus\n", filename);
    retval = -1;  goto bailout;
  }
  if (pf == TJPF_GRAY) {
    tjFree(buf);  buf = NULL;
    pf = TJPF_XBGR;
    if ((buf = tjLoadImage(filename, &loadWidth, align, &loadHeight, &pf,
                           flags)) == NULL)
      _throwtj();
    pitch = PAD(width * tjPixelSize[pf], align);
    if (!cmpBitmap(buf, width, pitch, height, pf, flags, 1)) {
      printf("\n   Converting %s to RGB failed\n", filename);
      retval = -1;  goto bailout;
    }

    tjFree(buf);  buf = NULL;
    pf = TJPF_CMYK;
    if ((buf = tjLoadImage(filename, &loadWidth, align, &loadHeight, &pf,
                           flags)) == NULL)
      _throwtj();
    pitch = PAD(width * tjPixelSize[pf], align);
    if (!cmpBitmap(buf, width, pitch, height, pf, flags, 1)) {
      printf("\n   Converting %s to CMYK failed\n", filename);
      retval = -1;  goto bailout;
    }
  }
  /* Verify that tjLoadImage() returns the proper "preferred" pixel format for
     the file type. */
  tjFree(buf);  buf = NULL;
  pf = pixelFormat;
  pixelFormat = TJPF_UNKNOWN;
  if ((buf = tjLoadImage(filename, &loadWidth, align, &loadHeight,
                         &pixelFormat, flags)) == NULL)
    _throwtj();
  if ((pf == TJPF_GRAY && pixelFormat != TJPF_GRAY) ||
      (pf != TJPF_GRAY && !strcasecmp(ext, "bmp") &&
       pixelFormat != TJPF_BGR) ||
      (pf != TJPF_GRAY && !strcasecmp(ext, "ppm") &&
       pixelFormat != TJPF_RGB)) {
    printf("\n   tjLoadImage() returned unexpected pixel format: %s\n",
           pixFormatStr[pixelFormat]);
    retval = -1;
  }
  unlink(filename);

bailout:
  if (buf) tjFree(buf);
  if (exitStatus < 0) return exitStatus;
  return retval;
}


int bmpTest(void)
{
  int align, width = 35, height = 39, format;

  for (align = 1; align <= 8; align *= 2) {
    for (format = 0; format < TJ_NUMPF; format++) {
      printf("%s Top-Down BMP (row alignment = %d bytes)  ...  ",
             pixFormatStr[format], align);
      if (doBmpTest("bmp", width, align, height, format, 0) == -1)
        return -1;
      printf("OK.\n");

      printf("%s Top-Down PPM (row alignment = %d bytes)  ...  ",
             pixFormatStr[format], align);
      if (doBmpTest("ppm", width, align, height, format,
                    TJFLAG_BOTTOMUP) == -1)
        return -1;
      printf("OK.\n");

      printf("%s Bottom-Up BMP (row alignment = %d bytes)  ...  ",
             pixFormatStr[format], align);
      if (doBmpTest("bmp", width, align, height, format, 0) == -1)
        return -1;
      printf("OK.\n");

      printf("%s Bottom-Up PPM (row alignment = %d bytes)  ...  ",
             pixFormatStr[format], align);
      if (doBmpTest("ppm", width, align, height, format,
                    TJFLAG_BOTTOMUP) == -1)
        return -1;
      printf("OK.\n");
    }
  }

  return 0;
}


int main(int argc, char *argv[])
{
  int i, num4bf = 5;

#ifdef _WIN32
  srand((unsigned int)time(NULL));
#endif
  if (argc > 1) {
    for (i = 1; i < argc; i++) {
      if (!strcasecmp(argv[i], "-yuv")) doYUV = 1;
      else if (!strcasecmp(argv[i], "-noyuvpad")) pad = 1;
      else if (!strcasecmp(argv[i], "-alloc")) alloc = 1;
      else if (!strcasecmp(argv[i], "-bmp")) return bmpTest();
      else usage(argv[0]);
    }
  }
  if (alloc) printf("Testing automatic buffer allocation\n");
  if (doYUV) num4bf = 4;
  doTest(35, 39, _3byteFormats, 2, TJSAMP_444, "test");
  doTest(39, 41, _4byteFormats, num4bf, TJSAMP_444, "test");
  doTest(41, 35, _3byteFormats, 2, TJSAMP_422, "test");
  doTest(35, 39, _4byteFormats, num4bf, TJSAMP_422, "test");
  doTest(39, 41, _3byteFormats, 2, TJSAMP_420, "test");
  doTest(41, 35, _4byteFormats, num4bf, TJSAMP_420, "test");
  doTest(35, 39, _3byteFormats, 2, TJSAMP_440, "test");
  doTest(39, 41, _4byteFormats, num4bf, TJSAMP_440, "test");
  doTest(41, 35, _3byteFormats, 2, TJSAMP_411, "test");
  doTest(35, 39, _4byteFormats, num4bf, TJSAMP_411, "test");
  doTest(39, 41, _onlyGray, 1, TJSAMP_GRAY, "test");
  doTest(41, 35, _3byteFormats, 2, TJSAMP_GRAY, "test");
  doTest(35, 39, _4byteFormats, 4, TJSAMP_GRAY, "test");
  bufSizeTest();
  if (doYUV) {
    printf("\n--------------------\n\n");
    doTest(48, 48, _onlyRGB, 1, TJSAMP_444, "test_yuv0");
    doTest(48, 48, _onlyRGB, 1, TJSAMP_422, "test_yuv0");
    doTest(48, 48, _onlyRGB, 1, TJSAMP_420, "test_yuv0");
    doTest(48, 48, _onlyRGB, 1, TJSAMP_440, "test_yuv0");
    doTest(48, 48, _onlyRGB, 1, TJSAMP_411, "test_yuv0");
    doTest(48, 48, _onlyRGB, 1, TJSAMP_GRAY, "test_yuv0");
    doTest(48, 48, _onlyGray, 1, TJSAMP_GRAY, "test_yuv0");
  }

  return exitStatus;
}
