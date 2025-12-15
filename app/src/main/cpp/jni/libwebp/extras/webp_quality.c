// Simple tool to roughly evaluate the quality encoding of a webp bitstream
//
// Result is a *rough* estimation of the quality. You should just consider
// the bucket it's in (q > 80? > 50? > 20?) and not take it for face value.
/*
 gcc -o webp_quality webp_quality.c -O3 -I../ -L. -L../imageio \
    -limageio_util -lwebpextras -lwebp -lm -lpthread
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "extras/extras.h"
#include "imageio/imageio_util.h"
#include "../examples/unicode.h"

// Returns EXIT_SUCCESS on success, EXIT_FAILURE on failure.
int main(int argc, const char* argv[]) {
  int c;
  int quiet = 0;
  int ok = 1;

  INIT_WARGV(argc, argv);

  for (c = 1; ok && c < argc; ++c) {
    if (!strcmp(argv[c], "-quiet")) {
      quiet = 1;
    } else if (!strcmp(argv[c], "-help") || !strcmp(argv[c], "-h")) {
      printf("webp_quality [-h][-quiet] webp_files...\n");
      FREE_WARGV_AND_RETURN(EXIT_SUCCESS);
    } else {
      const char* const filename = (const char*)GET_WARGV(argv, c);
      const uint8_t* data = NULL;
      size_t data_size = 0;
      int q;
      ok = ImgIoUtilReadFile(filename, &data, &data_size);
      if (!ok) break;
      q = VP8EstimateQuality(data, data_size);
      if (!quiet) WPRINTF("[%s] ", (const W_CHAR*)filename);
      if (q < 0) {
        fprintf(stderr, "Not a WebP file, or not a lossy WebP file.\n");
        ok = 0;
      } else {
        if (!quiet) {
          printf("Estimated quality factor: %d\n", q);
        } else {
          printf("%d\n", q);   // just print the number
        }
      }
      free((void*)data);
    }
  }
  FREE_WARGV_AND_RETURN(ok ? EXIT_SUCCESS : EXIT_FAILURE);
}
