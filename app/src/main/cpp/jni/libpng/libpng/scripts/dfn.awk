#!/bin/awk -f
# scripts/dfn.awk - process a .dfn file
#
# last changed in libpng version 1.5.19 - August 21, 2014
#
# Copyright (c) 2013-2014 Glenn Randers-Pehrson
#
# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h

# The output of this script is written to the file given by
# the variable 'out', which should be set on the command line.
# Error messages are printed to stdout and if any are printed
# the script will exit with error code 1.

BEGIN{
   out="/dev/null"       # as a flag
   out_count=0           # count of output lines
   err=0                 # set if an error occurred
   sort=0                # sort the output
   array[""]=""
}

# The output file must be specified before any input:
NR==1 && out == "/dev/null" {
   print "out=output.file must be given on the command line"
   # but continue without setting the error code; this allows the
   # script to be checked easily
}

# Output can be sorted; two lines are recognized
$1 == "PNG_DFN_START_SORT"{
   sort=0+$2
   next
}

$1 ~ /^PNG_DFN_END_SORT/{
   # Do a very simple, slow, sort; notice that blank lines won't be
   # output by this
   for (entry in array) {
      while (array[entry] != "") {
         key = entry
         value = array[key]
         array[key] = ""

         for (alt in array) {
            if (array[alt] != "" && alt < key) {
               array[key] = value
               value = array[alt]
               key = alt
               array[alt] = ""
            }
         }

         print value >out
      }
   }
   sort=0
   next
}

/^[^"]*PNG_DFN *".*"[^"]*$/{
   # A definition line, apparently correctly formatted; extract the
   # definition then replace any doubled "" that remain with a single
   # double quote.  Notice that the original doubled double quotes
   # may have been split by tokenization
   #
   # Sometimes GCC splits the PNG_DFN lines; we know this has happened
   # if the quotes aren't closed and must read another line.  In this
   # case it is essential to reject lines that start with '#' because those
   # are introduced #line directives.
   orig=$0
   line=$0
   lineno=FNR
   if (lineno == "") lineno=NR

   if (sub(/^[^"]*PNG_DFN *"/,"",line) != 1) {
	print "line", lineno ": processing failed:"
	print orig
	err=1
       next
   } else {
	++out_count
   }

   # Now examine quotes within the value:
   #
   #   @" - delete this and any following spaces
   #   "@ - delete this and any preceding spaces
   #   @' - replace this by a double quote
   #
   # This allows macro substitution by the C compiler thus:
   #
   #   #define first_name John
   #   #define last_name Smith
   #
   #	PNG_DFN"#define name @'@" first_name "@ @" last_name "@@'"
   #
   # Might get C preprocessed to:
   #
   #   PNG_DFN "#define foo @'@" John "@ @" Smith "@@'"
   #
   # Which this script reduces to:
   #
   #	#define name "John Smith"
   #
   while (1) {
      # While there is an @" remove it and the next "@
      if (line ~ /@"/) {
         if (line ~ /@".*"@/) {
            # Do this special case first to avoid swallowing extra spaces
            # before or after the @ stuff:
            if (!sub(/@" *"@/, "", line)) {
               # Ok, do it in pieces - there has to be a non-space between the
               # two.  NOTE: really weird things happen if a leading @" is
               # lost - the code will error out below (I believe).
               if (!sub(/@" */, "", line) || !sub(/ *"@/, "", line)) {
                  print "line", lineno, ": internal error:", orig
                  exit 1
               }
            }
         }

         # There is no matching "@.  Assume a split line
         else while (1) {
            if (getline nextline) {
               # If the line starts with '#' it is a preprocesor line directive
               # from cc -E; skip it:
               if (nextline !~ /^#/) {
                  line = line " " nextline
                  break
               }
            } else {
               # This is end-of-input - probably a missing "@ on the first line:
               print "line", lineno ": unbalanced @\" ... \"@ pair"
               err=1
               next
            }
         }

         # Keep going until all the @" have gone
         continue
      }

      # Attempt to remove a trailing " (not preceded by '@') - if this can
      # be done, stop now; if not assume a split line again
      if (sub(/"[^"]*$/, "", line))
         break

      # Read another line
      while (1) {
         if (getline nextline) {
            if (nextline !~ /^#/) {
               line = line " " nextline
               # Go back to stripping @" "@ pairs
               break
            }
         } else {
            print "line", lineno ": unterminated PNG_DFN string"
            err=1
            next
         }
      }
   }

   # Put any needed double quotes in (at the end, because these would otherwise
   # interfere with the processing above.)
   gsub(/@'/,"\"", line)

   # Remove any trailing spaces (not really required, but for
   # editorial consistency
   sub(/ *$/, "", line)

   # Remove trailing CR
   sub(/$/, "", line)

   if (sort) {
      if (split(line, parts) < sort) {
         print "line", lineno ": missing sort field:", line
         err=1
      } else
         array[parts[sort]] = line
   }

   else
      print line >out
   next
}

/PNG_DFN/{
   print "line", NR, "incorrectly formatted PNG_DFN line:"
   print $0
   err = 1
}

END{
   if (out_count > 0 || err > 0)
	exit err

   print "no definition lines found"
   exit 1
}
