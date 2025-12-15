#!/bin/sh
#
# Copyright (c) 2013 John Cunningham Bowler
#
# Last changed in libpng 1.6.0 [February 14, 2013]
#
# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h
#
# Generate a set of PNG test images.  The images are generated in a
# sub-directory called 'tests' by default, however a command line argument will
# change that name.  The generation requires a built version of makepng in the
# current directory.
#
usage(){
   exec >&2
   echo "$0 [<directory>]"
   echo '  Generate a set of PNG test files in "directory" ("tests" by default)'
   exit 1
}

mp="$PWD/makepng"
test -x "$mp" || {
   exec >&2
   echo "$0: the 'makepng' program must exist"
   echo "  in the directory within which this program:"
   echo "    $mp"
   echo "  is executed"
   usage
}

# Just one argument: the directory
testdir="tests"
test $# -gt 1 && {
   testdir="$1"
   shift
}
test $# -eq 0 || usage

# Take care not to clobber something
if test -e "$testdir"
then
   test -d "$testdir" || usage
else
   # mkdir -p isn't portable, so do the following
   mkdir "$testdir" 2>/dev/null || mkdir -p "$testdir" || usage
fi

# This fails in a very satisfactory way if it's not accessible
cd "$testdir"
:>"test$$.png" || {
   exec >&2
   echo "$testdir: directory not writable"
   usage
}
rm "test$$.png" || {
   exec >&2
   echo "$testdir: you have create but not write privileges here."
   echo "  This is unexpected.  You have a spurion; "'"'"test$$.png"'"'"."
   echo "  You need to remove this yourself.  Try a different directory."
   exit 1
}

# Now call makepng ($mp) to create every file we can think of with a
# reasonable name
doit(){
   for gamma in "" --sRGB --linear --1.8
   do
      case "$gamma" in
         "")
            gname=;;
         --sRGB)
            gname="-srgb";;
         --linear)
            gname="-lin";;
         --1.8)
            gname="-18";;
         *)
            gname="-$gamma";;
      esac
      "$mp" $gamma "$1" "$2" "test-$1-$2$gname.png"
   done
}
#
for ct in gray palette
do
   for bd in 1 2 4 8
   do
      doit "$ct" "$bd"
   done
done
#
doit "gray" "16"
#
for ct in gray-alpha rgb rgb-alpha
do
   for bd in 8 16
   do
      doit "$ct" "$bd"
   done
done
