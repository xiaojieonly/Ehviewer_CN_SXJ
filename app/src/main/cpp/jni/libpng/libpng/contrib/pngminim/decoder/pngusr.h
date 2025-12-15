/* minrdpngconf.h: headers to make a minimal png-read-only library
 *
 * Copyright (c) 2007, 2010-2013 Glenn Randers-Pehrson
 *
 * This code is released under the libpng license.
 * For conditions of distribution and use, see the disclaimer
 * and license in png.h
 *
 * Derived from pngcrush.h, Copyright 1998-2007, Glenn Randers-Pehrson
 */

#ifndef MINRDPNGCONF_H
#define MINRDPNGCONF_H

/* To include pngusr.h set -DPNG_USER_CONFIG in CPPFLAGS */

/* List options to turn off features of the build that do not
 * affect the API (so are not recorded in pnglibconf.h)
 */

#define PNG_ALIGN_TYPE PNG_ALIGN_NONE

#endif /* MINRDPNGCONF_H */
