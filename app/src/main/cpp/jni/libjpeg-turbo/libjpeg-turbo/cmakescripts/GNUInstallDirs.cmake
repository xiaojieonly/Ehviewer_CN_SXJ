#.rst:
# GNUInstallDirs
# --------------
#
# Define GNU standard installation directories
#
# Provides install directory variables as defined by the
# `GNU Coding Standards`_.
#
# .. _`GNU Coding Standards`: https://www.gnu.org/prep/standards/html_node/Directory-Variables.html
#
# Result Variables
# ^^^^^^^^^^^^^^^^
#
# Inclusion of this module defines the following variables:
#
# ``CMAKE_INSTALL_<dir>``
#
#   Destination for files of a given type.  This value may be passed to
#   the ``DESTINATION`` options of :command:`install` commands for the
#   corresponding file type.
#
# ``CMAKE_INSTALL_FULL_<dir>``
#
#   The absolute path generated from the corresponding ``CMAKE_INSTALL_<dir>``
#   value.  If the value is not already an absolute path, an absolute path
#   is constructed typically by prepending the value of the
#   :variable:`CMAKE_INSTALL_PREFIX` variable.  However, there are some
#   `special cases`_ as documented below.
#
# where ``<dir>`` is one of:
#
# ``BINDIR``
#   user executables (``bin``)
# ``SBINDIR``
#   system admin executables (``sbin``)
# ``LIBEXECDIR``
#   program executables (``libexec``)
# ``SYSCONFDIR``
#   read-only single-machine data (``etc``)
# ``SHAREDSTATEDIR``
#   modifiable architecture-independent data (``com``)
# ``LOCALSTATEDIR``
#   modifiable single-machine data (``var``)
# ``LIBDIR``
#   object code libraries (``lib`` or ``lib64``
#   or ``lib/<multiarch-tuple>`` on Debian)
# ``INCLUDEDIR``
#   C header files (``include``)
# ``OLDINCLUDEDIR``
#   C header files for non-gcc (``/usr/include``)
# ``DATAROOTDIR``
#   read-only architecture-independent data root (``share``)
# ``DATADIR``
#   read-only architecture-independent data (``DATAROOTDIR``)
# ``INFODIR``
#   info documentation (``DATAROOTDIR/info``)
# ``LOCALEDIR``
#   locale-dependent data (``DATAROOTDIR/locale``)
# ``MANDIR``
#   man documentation (``DATAROOTDIR/man``)
# ``DOCDIR``
#   documentation root (``DATAROOTDIR/doc/PROJECT_NAME``)
#
# If the includer does not define a value the above-shown default will be
# used and the value will appear in the cache for editing by the user.
#
# Special Cases
# ^^^^^^^^^^^^^
#
# The following values of :variable:`CMAKE_INSTALL_PREFIX` are special:
#
# ``/``
#
#   For ``<dir>`` other than the ``SYSCONFDIR`` and ``LOCALSTATEDIR``,
#   the value of ``CMAKE_INSTALL_<dir>`` is prefixed with ``usr/`` if
#   it is not user-specified as an absolute path.  For example, the
#   ``INCLUDEDIR`` value ``include`` becomes ``usr/include``.
#   This is required by the `GNU Coding Standards`_, which state:
#
#     When building the complete GNU system, the prefix will be empty
#     and ``/usr`` will be a symbolic link to ``/``.
#
# ``/usr``
#
#   For ``<dir>`` equal to ``SYSCONFDIR`` or ``LOCALSTATEDIR``, the
#   ``CMAKE_INSTALL_FULL_<dir>`` is computed by prepending just ``/``
#   to the value of ``CMAKE_INSTALL_<dir>`` if it is not user-specified
#   as an absolute path.  For example, the ``SYSCONFDIR`` value ``etc``
#   becomes ``/etc``.  This is required by the `GNU Coding Standards`_.
#
# ``/opt/...``
#
#   For ``<dir>`` equal to ``SYSCONFDIR`` or ``LOCALSTATEDIR``, the
#   ``CMAKE_INSTALL_FULL_<dir>`` is computed by *appending* the prefix
#   to the value of ``CMAKE_INSTALL_<dir>`` if it is not user-specified
#   as an absolute path.  For example, the ``SYSCONFDIR`` value ``etc``
#   becomes ``/etc/opt/...``.  This is defined by the
#   `Filesystem Hierarchy Standard`_.
#
# .. _`Filesystem Hierarchy Standard`: https://refspecs.linuxfoundation.org/FHS_3.0/fhs/index.html
#
# Macros
# ^^^^^^
#
# .. command:: GNUInstallDirs_get_absolute_install_dir
#
#   ::
#
#     GNUInstallDirs_get_absolute_install_dir(absvar var)
#
#   Set the given variable ``absvar`` to the absolute path contained
#   within the variable ``var``.  This is to allow the computation of an
#   absolute path, accounting for all the special cases documented
#   above.  While this macro is used to compute the various
#   ``CMAKE_INSTALL_FULL_<dir>`` variables, it is exposed publicly to
#   allow users who create additional path variables to also compute
#   absolute paths where necessary, using the same logic.

#=============================================================================
# Copyright 2016 D. R. Commander
# Copyright 2016 Dmitry Marakasov
# Copyright 2016 Roger Leigh
# Copyright 2015 Alex Turbov
# Copyright 2014 Rolf Eike Beer
# Copyright 2014 Daniele E. Domenichelli
# Copyright 2013 Dimitri John Ledkov
# Copyright 2011 Alex Neundorf
# Copyright 2011 Eric NOULARD
# Copyright 2011, 2013-2015 Kitware, Inc.
# Copyright 2011 Nikita Krupen'ko
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
# * Redistributions of source code must retain the above copyright
#   notice, this list of conditions and the following disclaimer.
#
# * Redistributions in binary form must reproduce the above copyright
#   notice, this list of conditions and the following disclaimer in the
#   documentation and/or other materials provided with the distribution.
#
# * Neither the names of Kitware, Inc., the Insight Software Consortium,
#   nor the names of their contributors may be used to endorse or promote
#   products derived from this software without specific prior written
#   permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
# SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
# LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#=============================================================================

# Installation directories
#

macro(GNUInstallDirs_set_install_dir var docstring)
  # If CMAKE_INSTALL_PREFIX changes and CMAKE_INSTALL_*DIR is still set to the
  # default value, then modify it accordingly.  This presumes that the default
  # value may change based on the prefix.

  set(_GNUInstallDirs_CMAKE_INSTALL_FORCE_${var} "")
  if(NOT DEFINED CMAKE_INSTALL_${var})
    set(_GNUInstallDirs_CMAKE_INSTALL_DEFAULT_${var} 1 CACHE INTERNAL
      "CMAKE_INSTALL_${var} has default value")
  elseif(DEFINED _GNUInstallDirs_CMAKE_INSTALL_LAST_DEFAULT_${var} AND
    NOT "${_GNUInstallDirs_CMAKE_INSTALL_LAST_DEFAULT_${var}}" STREQUAL
      "${CMAKE_INSTALL_DEFAULT_${var}}" AND
    _GNUInstallDirs_CMAKE_INSTALL_DEFAULT_${var} AND
    "${_GNUInstallDirs_CMAKE_INSTALL_LAST_${var}}" STREQUAL
      "${CMAKE_INSTALL_${var}}")
    set(_GNUInstallDirs_CMAKE_INSTALL_FORCE_${var} "FORCE")
  endif()

  set(CMAKE_INSTALL_${var} "${CMAKE_INSTALL_DEFAULT_${var}}" CACHE PATH
    "${docstring} (Default: ${CMAKE_INSTALL_DEFAULT_${var}})"
    ${_GNUInstallDirs_CMAKE_INSTALL_FORCE_${var}})

  if(NOT "${CMAKE_INSTALL_${var}}" STREQUAL "${CMAKE_INSTALL_DEFAULT_${var}}")
    unset(_GNUInstallDirs_CMAKE_INSTALL_DEFAULT_${var} CACHE)
  endif()

  # Save for next run
  set(_GNUInstallDirs_CMAKE_INSTALL_LAST_${var} "${CMAKE_INSTALL_${var}}"
    CACHE INTERNAL "CMAKE_INSTALL_${var} during last run")
  set(_GNUInstallDirs_CMAKE_INSTALL_LAST_DEFAULT_${var}
    "${CMAKE_INSTALL_DEFAULT_${var}}" CACHE INTERNAL
    "CMAKE_INSTALL_DEFAULT_${var} during last run")
endmacro()

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_BINDIR)
  set(CMAKE_INSTALL_DEFAULT_BINDIR "bin")
endif()
GNUInstallDirs_set_install_dir(BINDIR
  "Directory into which user executables should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_SBINDIR)
  set(CMAKE_INSTALL_DEFAULT_SBINDIR "sbin")
endif()
GNUInstallDirs_set_install_dir(SBINDIR
  "Directory into which system admin executables should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_LIBEXECDIR)
  set(CMAKE_INSTALL_DEFAULT_LIBEXECDIR "libexec")
endif()
GNUInstallDirs_set_install_dir(LIBEXECDIR
  "Directory under which executables run by other programs should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_SYSCONFDIR)
  set(CMAKE_INSTALL_DEFAULT_SYSCONFDIR "etc")
endif()
GNUInstallDirs_set_install_dir(SYSCONFDIR
  "Directory into which machine-specific read-only ASCII data and configuration files should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_SHAREDSTATEDIR)
  set(CMAKE_INSTALL_DEFAULT_SHAREDSTATEDIR "com")
endif()
GNUInstallDirs_set_install_dir(SHAREDSTATEDIR
  "Directory into which architecture-independent run-time-modifiable data files should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_LOCALSTATEDIR)
  set(CMAKE_INSTALL_DEFAULT_LOCALSTATEDIR "var")
endif()
GNUInstallDirs_set_install_dir(LOCALSTATEDIR
  "Directory into which machine-specific run-time-modifiable data files should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_LIBDIR)
  set(CMAKE_INSTALL_DEFAULT_LIBDIR "lib")
  # Override this default 'lib' with 'lib64' iff:
  #  - we are on Linux system but NOT cross-compiling
  #  - we are NOT on debian
  #  - we are on a 64 bits system
  # reason is: amd64 ABI: http://www.x86-64.org/documentation/abi.pdf
  # For Debian with multiarch, use 'lib/${CMAKE_LIBRARY_ARCHITECTURE}' if
  # CMAKE_LIBRARY_ARCHITECTURE is set (which contains e.g. "i386-linux-gnu"
  # and CMAKE_INSTALL_PREFIX is "/usr"
  # See http://wiki.debian.org/Multiarch
  if(CMAKE_SYSTEM_NAME MATCHES "^(Linux|kFreeBSD|GNU)$"
      AND NOT CMAKE_CROSSCOMPILING)
    if (EXISTS "/etc/debian_version") # is this a debian system ?
      if(CMAKE_LIBRARY_ARCHITECTURE)
        if("${CMAKE_INSTALL_PREFIX}" MATCHES "^/usr/?$")
          set(CMAKE_INSTALL_DEFAULT_LIBDIR "lib/${CMAKE_LIBRARY_ARCHITECTURE}")
        endif()
      endif()
    else() # not debian, rely on CMAKE_SIZEOF_VOID_P:
      if(NOT DEFINED CMAKE_SIZEOF_VOID_P)
        message(AUTHOR_WARNING
          "Unable to determine default CMAKE_INSTALL_LIBDIR directory because no target architecture is known. "
          "Please enable at least one language before including GNUInstallDirs.")
      else()
        if("${CMAKE_SIZEOF_VOID_P}" EQUAL "8")
          set(CMAKE_INSTALL_DEFAULT_LIBDIR "lib64")
        endif()
      endif()
    endif()
  endif()
endif()
GNUInstallDirs_set_install_dir(LIBDIR
  "Directory into which object files and object code libraries should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_INCLUDEDIR)
  set(CMAKE_INSTALL_DEFAULT_INCLUDEDIR "include")
endif()
GNUInstallDirs_set_install_dir(INCLUDEDIR
  "Directory into which C header files should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_OLDINCLUDEDIR)
  set(CMAKE_INSTALL_DEFAULT_OLDINCLUDEDIR "/usr/include")
endif()
GNUInstallDirs_set_install_dir(OLDINCLUDEDIR
  PATH "Directory into which C header files for non-GCC compilers should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_DATAROOTDIR)
  set(CMAKE_INSTALL_DEFAULT_DATAROOTDIR "share")
endif()
GNUInstallDirs_set_install_dir(DATAROOTDIR
  "The root of the directory tree for read-only architecture-independent data files")

#-----------------------------------------------------------------------------
# Values whose defaults are relative to DATAROOTDIR.  Store empty values in
# the cache and store the defaults in local variables if the cache values are
# not set explicitly.  This auto-updates the defaults as DATAROOTDIR changes.

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_DATADIR)
  set(CMAKE_INSTALL_DEFAULT_DATADIR "<CMAKE_INSTALL_DATAROOTDIR>")
endif()
GNUInstallDirs_set_install_dir(DATADIR
  "The directory under which read-only architecture-independent data files should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_INFODIR)
  if(CMAKE_SYSTEM_NAME MATCHES "^(.*BSD|DragonFly)$")
    set(CMAKE_INSTALL_DEFAULT_INFODIR "info")
  else()
    set(CMAKE_INSTALL_DEFAULT_INFODIR "<CMAKE_INSTALL_DATAROOTDIR>/info")
  endif()
endif()
GNUInstallDirs_set_install_dir(INFODIR
  "The directory into which info documentation files should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_MANDIR)
  if(CMAKE_SYSTEM_NAME MATCHES "^(.*BSD|DragonFly)$")
    set(CMAKE_INSTALL_DEFAULT_MANDIR "man")
  else()
    set(CMAKE_INSTALL_DEFAULT_MANDIR "<CMAKE_INSTALL_DATAROOTDIR>/man")
  endif()
endif()
GNUInstallDirs_set_install_dir(MANDIR
  "The directory under which man pages should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_LOCALEDIR)
  set(CMAKE_INSTALL_DEFAULT_LOCALEDIR "<CMAKE_INSTALL_DATAROOTDIR>/locale")
endif()
GNUInstallDirs_set_install_dir(LOCALEDIR
  "The directory under which locale-specific message catalogs should be installed")

if(NOT DEFINED CMAKE_INSTALL_DEFAULT_DOCDIR)
  set(CMAKE_INSTALL_DEFAULT_DOCDIR "<CMAKE_INSTALL_DATAROOTDIR>/doc/${PROJECT_NAME}")
endif()
GNUInstallDirs_set_install_dir(DOCDIR
  "The directory into which documentation files (other than info files) should be installed")

#-----------------------------------------------------------------------------

mark_as_advanced(
  CMAKE_INSTALL_BINDIR
  CMAKE_INSTALL_SBINDIR
  CMAKE_INSTALL_LIBEXECDIR
  CMAKE_INSTALL_SYSCONFDIR
  CMAKE_INSTALL_SHAREDSTATEDIR
  CMAKE_INSTALL_LOCALSTATEDIR
  CMAKE_INSTALL_LIBDIR
  CMAKE_INSTALL_INCLUDEDIR
  CMAKE_INSTALL_OLDINCLUDEDIR
  CMAKE_INSTALL_DATAROOTDIR
  CMAKE_INSTALL_DATADIR
  CMAKE_INSTALL_INFODIR
  CMAKE_INSTALL_LOCALEDIR
  CMAKE_INSTALL_MANDIR
  CMAKE_INSTALL_DOCDIR
  )

macro(GNUInstallDirs_get_absolute_install_dir absvar var)
  string(REGEX REPLACE "[<>]" "@" ${var} "${${var}}")
  # Handle the specific case of an empty CMAKE_INSTALL_DATAROOTDIR
  if(NOT CMAKE_INSTALL_DATAROOTDIR AND
    ${var} MATCHES "\@CMAKE_INSTALL_DATAROOTDIR\@/")
    string(CONFIGURE "${${var}}" ${var} @ONLY)
    string(REGEX REPLACE "^/" "" ${var} "${${var}}")
  else()
    string(CONFIGURE "${${var}}" ${var} @ONLY)
  endif()
  if(NOT IS_ABSOLUTE "${${var}}")
    # Handle special cases:
    # - CMAKE_INSTALL_PREFIX == /
    # - CMAKE_INSTALL_PREFIX == /usr
    # - CMAKE_INSTALL_PREFIX == /opt/...
    if("${CMAKE_INSTALL_PREFIX}" STREQUAL "/")
      if("${dir}" STREQUAL "SYSCONFDIR" OR "${dir}" STREQUAL "LOCALSTATEDIR")
        set(${absvar} "/${${var}}")
      else()
        if (NOT "${${var}}" MATCHES "^usr/")
          set(${var} "usr/${${var}}")
        endif()
        set(${absvar} "/${${var}}")
      endif()
    elseif("${CMAKE_INSTALL_PREFIX}" MATCHES "^/usr/?$")
      if("${dir}" STREQUAL "SYSCONFDIR" OR "${dir}" STREQUAL "LOCALSTATEDIR")
        set(${absvar} "/${${var}}")
      else()
        set(${absvar} "${CMAKE_INSTALL_PREFIX}/${${var}}")
      endif()
    elseif("${CMAKE_INSTALL_PREFIX}" MATCHES "^/opt/.*")
      if("${dir}" STREQUAL "SYSCONFDIR" OR "${dir}" STREQUAL "LOCALSTATEDIR")
        set(${absvar} "/${${var}}${CMAKE_INSTALL_PREFIX}")
      else()
        set(${absvar} "${CMAKE_INSTALL_PREFIX}/${${var}}")
      endif()
    else()
      set(${absvar} "${CMAKE_INSTALL_PREFIX}/${${var}}")
    endif()
  else()
    set(${absvar} "${${var}}")
  endif()
  string(REGEX REPLACE "/$" "" ${absvar} "${${absvar}}")
endmacro()

# Result directories
#
foreach(dir
    BINDIR
    SBINDIR
    LIBEXECDIR
    SYSCONFDIR
    SHAREDSTATEDIR
    LOCALSTATEDIR
    LIBDIR
    INCLUDEDIR
    OLDINCLUDEDIR
    DATAROOTDIR
    DATADIR
    INFODIR
    LOCALEDIR
    MANDIR
    DOCDIR
    )
  GNUInstallDirs_get_absolute_install_dir(CMAKE_INSTALL_FULL_${dir} CMAKE_INSTALL_${dir})
endforeach()
