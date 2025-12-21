
/* intprefix.c - generate an unprefixed internal symbol list
 *
 * Last changed in libpng version 1.6.16 [December 22, 2014]
 * Copyright (c) 2013-2014 Glenn Randers-Pehrson
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 */

#define PNG_INTERNAL_DATA(type, name, array)\
        PNG_DFN "@" name "@"

#define PNG_INTERNAL_FUNCTION(type, name, args, attributes)\
        PNG_DFN "@" name "@"

#define PNG_INTERNAL_CALLBACK(type, name, args, attributes)\
        PNG_DFN "@" name "@"

#define PNGPREFIX_H /* self generation */
#include "../pngpriv.h"
