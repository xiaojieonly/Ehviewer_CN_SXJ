
/* prefix.c - generate an unprefixed symbol list
 *
 * Last changed in libpng version 1.6.16 [December 22, 2014]
 * Copyright (c) 2013-2014 Glenn Randers-Pehrson
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 */

#define PNG_EXPORTA(ordinal, type, name, args, attributes)\
        PNG_DFN "@" name "@"

/* The configuration information *before* the additional of symbol renames,
 * the list is the C name list; no symbol prefix.
 */
#include "pnglibconf.out"

PNG_DFN_START_SORT 1

#include "../png.h"

PNG_DFN_END_SORT
