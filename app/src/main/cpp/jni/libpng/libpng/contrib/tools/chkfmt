#!/bin/sh

# chkfmt
#
# COPYRIGHT: Written by John Cunningham Bowler, 2010.
# To the extent possible under law, the author has waived all copyright and
# related or neighboring rights to this work.  This work is published from:
# United States.
#
# Check the format of the source files in the current directory - checks for a
# line length of 80 characters max and no tab characters.
#
# Optionally arguments are files or directories to check.
#
# -v: output the long lines (makes fixing them easier)
# -e: spawn an editor for each file that needs a change ($EDITOR must be
#     defined).  When using -e the script MUST be run from an interactive
#     command line.
verbose=
edit=
vers=
test "$1" = "-v" && {
   shift
   verbose=yes
}
test "$1" = "-e" && {
   shift
   if test -n "$EDITOR"
   then
      edit=yes

      # Copy the standard streams for the editor
      exec 3>&0 4>&1 5>&2
   else
      echo "chkfmt -e: EDITOR must be defined" >&2
      exit 1
   fi
}

# Function to edit a single file - if the file isn't changed ask the user
# whether or not to continue.  This stuff only works if the script is run from
# the command line (otherwise, don't specify -e or you will be sorry).
doed(){
   cp "$file" "$file".orig
   "$EDITOR" "$file" 0>&3 1>&4 2>&5 3>&- 4>&- 5>&- || exit 1
   if cmp -s "$file".orig "$file"
   then
      rm "$file".orig
      echo -n "$file: file not changed, type anything to continue: " >&5
      read ans 0>&3
      test -n "$ans" || return 1
   fi
   return 0
}

# In beta versions the version string which appears in files can be a little
# long and cause spuriously overlong lines.  To avoid this substitute the version
# string with a 'standard' version a.b.cc before checking for long lines.
if test -r png.h
then
   vers="`sed -n -e \
   's/^#define PNG_LIBPNG_VER_STRING .\([0-9]\.[0-9]\.[0-9][0-9a-z]*\).$/\1/p' \
   png.h`"
   echo "chkfmt: checking version $vers"
fi
if test -z "$vers"
then
   echo "chkfmt: png.h not found, ignoring version number" >&2
fi

test -n "$1" || set -- .
find "$@" \( -type d \( -name '.git' -o -name '.libs' -o -name 'projects' \) \
   -prune \) -o \( -type f \
   ! -name '*.[oa]' ! -name '*.l[oa]' !  -name '*.png' ! -name '*.out' \
   ! -name '*.jpg' ! -name '*.patch' ! -name '*.obj' ! -name '*.exe' \
   ! -name '*.com' ! -name '*.tar.*' ! -name '*.zip' ! -name '*.ico' \
   ! -name '*.res' ! -name '*.rc' ! -name '*.mms' ! -name '*.rej' \
   ! -name '*.dsp' ! -name '*.orig' ! -name '*.dfn' ! -name '*.swp' \
   ! -name '~*' ! -name '*.3' \
   ! -name 'missing' ! -name 'mkinstalldirs' ! -name 'depcomp' \
   ! -name 'aclocal.m4' ! -name 'install-sh' ! -name 'Makefile.in' \
   ! -name 'ltmain.sh' ! -name 'config*' -print \) | {
   st=0
   while read file
   do
      case "$file" in
      *.mak|*[Mm]akefile.*|*[Mm]akefile)
         # Makefiles require tabs, dependency lines can be this long.
         check_tabs=
         line_length=100;;
      *.awk)
         # Includes literal tabs
         check_tabs=
         # The following is arbitrary
         line_length=132;;
      *contrib/*/*.[ch])
         check_tabs=yes
         line_length=96;;
      *)
         check_tabs=yes
         line_length=80;;
      esac

      # Note that vers can only contain 0-9, . and a-z
      if test -n "$vers"
      then
         sed -e "s/$vers/a.b.cc/g" "$file" >"$file".$$
      else
         cp "$file" "$file".$$
      fi
      splt="`fold -$line_length "$file".$$ | diff -c "$file".$$ -`"
      rm "$file".$$

      if test -n "$splt"
      then
         echo "$file: lines too long"
         st=1
         if test -n "$EDITOR" -a -n "$edit"
         then
            doed "$file" || exit 1
         elif test -n "$verbose"
         then
            echo "$splt"
         fi
      fi
      if test -n "$check_tabs"
      then
         tab="`tr -c -d '\t' <"$file"`"
         if test -n "$tab"
         then
            echo "$file: file contains tab characters"
            st=1
            if test -n "$EDITOR" -a -n "$edit"
            then
               doed "$file" || exit 1
            elif test -n "$verbose"
            then
               echo "$splt"
            fi
         fi
      fi
   done
   exit $st
}
