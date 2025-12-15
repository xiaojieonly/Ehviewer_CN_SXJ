
/* symbols.c - find all exported symbols
 *
 * Last changed in libpng version 1.6.16 [December 22, 2014]
 * Copyright (c) 2011-2014 Glenn Randers-Pehrson
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 */

/* NOTE: making 'symbols.chk' checks both that the exported
 * symbols in the library don't change and (implicitly) that
 * scripts/pnglibconf.h.prebuilt is as expected.
 * If scripts/pnglibconf.h.prebuilt is remade using
 * scripts/pnglibconf.dfa then this checks the .dfa file too.
 */

#define PNG_EXPORTA(ordinal, type, name, args, attributes)\
        PNG_DFN "@" name "@ @@" ordinal "@"
#define PNG_REMOVED(ordinal, type, name, args, attributes)\
        PNG_DFN "; @" name "@ @@" ordinal "@"
#define PNG_EXPORT_LAST_ORDINAL(ordinal)\
        PNG_DFN "; @@" ordinal "@"

/* Read the defaults, but use scripts/pnglibconf.h.prebuilt; the 'standard'
 * header file.
 */
#include "pnglibconf.h.prebuilt"
#include "../png.h"

/* Some things are turned off by default.  Turn these things
 * on here (by hand) to get the APIs they expose and validate
 * that no harm is done.  This list is the set of options
 * defaulted to 'off' in scripts/pnglibconf.dfa
 *
 * Maintenance: if scripts/pnglibconf.dfa options are changed
 * from, or to, 'disabled' this needs updating!
 */
#define PNG_BENIGN_ERRORS_SUPPORTED
#define PNG_ERROR_NUMBERS_SUPPORTED
#define PNG_READ_BIG_ENDIAN_SUPPORTED  /* should do nothing! */
#define PNG_INCH_CONVERSIONS_SUPPORTED
#define PNG_READ_16_TO_8_ACCURATE_SCALE_SUPPORTED
#define PNG_SET_OPTION_SUPPORTED

#undef PNG_H
#include "../png.h"

/* Finally there are a couple of places where option support
 * actually changes the APIs revealed using a #if/#else/#endif
 * test in png.h, test these here.
 */
#undef  PNG_FLOATING_POINT_SUPPORTED /* Exposes 'fixed' APIs */
#undef  PNG_ERROR_TEXT_SUPPORTED     /* Exposes unsupported APIs */

#undef PNG_H
#include "../png.h"
