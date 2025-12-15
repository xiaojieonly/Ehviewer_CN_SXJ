Instructions for building libpng using Microsoft Visual Studio
==============================================================

Copyright (c) 2018 Cosmin Truta
Copyright (c) 2010,2013,2015 Glenn Randers-Pehrson

This code is released under the libpng license.
For conditions of distribution and use, see the disclaimer and license
in png.h

This directory contains a solution for building libpng under Microsoft
Visual Studio 2010.  It may also work under later versions of Visual
Studio.  You should be familiar with Visual Studio before using this
solution.

Initial preparations
--------------------
You must enter some information in zlib.props before attempting to
build with this 'solution'.  Please read and edit zlib.props first.
You will probably not be familiar with the contents of zlib.props -
do not worry, it is mostly harmless.

This is all you need to do to build the 'release' and 'release library'
configurations.

Debugging
---------
The release configurations default to /Ox optimization.  Full debugging
information is produced (in the .pdb), but if you encounter a problem
the optimization may make it difficult to debug.  Simply rebuild with a
lower optimization level (e.g. /Od.)

Linking your application
------------------------
Normally you should link against the 'release' configuration.  This
builds a DLL for libpng with the default runtime options used by Visual
Studio.  In particular the runtime library is the "MultiThreaded DLL"
version.  If you use Visual Studio defaults to build your application,
you should have no problems.

If you don't use the Visual Studio defaults your application must still
be built with the default runtime option (/MD).  If, for some reason,
it is not then your application will crash inside libpng16.dll as soon
as libpng tries to read from a file handle you pass in.

If you do not want to use the DLL, and prefer static linking instead,
you may choose the 'release library' configuration.  This is built with
a non-standard runtime library - the "MultiThreaded" version.  When you
build your application, it must be compiled with this option (/MT),
otherwise it will not build (if you are lucky) or it will crash (if you
are not.) See the WARNING file that is distributed with this README.

Stop reading here
-----------------
You have enough information to build a working application.

Debug versions have limited support
-----------------------------------
This solution includes limited support for debug versions of libpng.
You do not need these unless your own solution itself uses debug builds
(it is far more effective to debug on the release builds, there is no
point building a special debug build unless you have heap corruption
problems that you can't track down.)

The debug build of libpng is minimally supported.  Support for debug
builds of zlib is also minimal.  Please keep this in mind, if you want
to use it.

WARNING
-------
Libpng 1.6.x does not use the default run-time library when building
static library builds of libpng; instead of the shared DLL runtime, it
uses a static runtime.  If you need to change this, make sure to change
the setting on all the relevant projects:

    libpng
    zlib
    all the test programs

The runtime library settings for each build are as follows:

               Release        Debug
    DLL         /MD            /MDd
    Library     /MT            /MTd

Also, be sure to build libpng, zlib, and your project, all for the same
platform (e.g., 32-bit or 64-bit).
