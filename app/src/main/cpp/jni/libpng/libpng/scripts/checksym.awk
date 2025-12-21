#!/bin/awk -f
# Check a list of symbols against the master definition
# (official) list.  Arguments:
#
# awk -f checksym.awk official-def list-to-check
#
# Output is a file in the current directory called 'symbols.new',
# the value of the awk variable "of" (which can be changed on the
# command line if required.)  stdout holds error messages.  Error
# code indicates success or failure.
#
# NOTE: this is a pure, old fashioned, awk script.  It will
# work with any awk

BEGIN{
   err=0
   master=""        # master file
   official[1] = "" # defined symbols from master file
   symbol[1] = ""   # defined symbols from png.h
   removed[1] = ""  # removed symbols from png.h
   lasto = 0        # last ordinal value from png.h
   mastero = 0      # highest ordinal in master file
   symbolo = 0      # highest ordinal in png.h
   missing = "error"# log an error on missing symbols
   of="symbols.new" # default to a fixed name
}

# Read existing definitions from the master file (the first
# file on the command line.)  This must be a def file and it
# has definition lines (others are ignored) of the form:
#
#   symbol @ordinal
#
master == "" {
   master = FILENAME
}
FILENAME==master && NF==2 && $2~/^@/ && $1!~/^;/ {
   o=0+substr($2,2)
   if (o > 0) {
      if (official[o] == "") {
         official[o] = $1
         if (o > mastero) mastero = o
         next
      } else
         print master ": duplicated symbol:", official[o] ":", $0
   } else
      print master ": bad export line format:", $0
   err = 1
}
FILENAME==master && $1==";missing" && NF==2{
   # This allows the master file to control how missing symbols
   # are handled; symbols that aren't in either the master or
   # the new file.  Valid values are 'ignore', 'warning' and
   # 'error'
   missing = $2
}
FILENAME==master {
   next
}

# Read new definitions, these are free form but the lines must
# just be symbol definitions.  Lines will be commented out for
# 'removed' symbols, introduced in png.h using PNG_REMOVED rather
# than PNG_EXPORT.  Use symbols.dfn or pngwin.dfn to generate the
# input file.
#
#  symbol @ordinal   # two fields, exported symbol
#  ; symbol @ordinal # three fields, removed symbol
#  ; @ordinal        # two fields, the last ordinal
NF==2 && $1 == ";" && $2 ~ /^@[1-9][0-9]*$/ { # last ordinal
   o=0+substr($2,2)
   if (lasto == 0 || lasto == o)
      lasto=o
   else {
      print "png.h: duplicated last ordinal:", lasto, o
      err = 1
   }
   next
}
NF==3 && $1 == ";" && $3 ~ /^@[1-9][0-9]*$/ { # removed symbol
   o=0+substr($3,2)
   if (removed[o] == "" || removed[o] == $2) {
      removed[o] = $2
      if (o > symbolo) symbolo = o
   } else {
      print "png.h: duplicated removed symbol", o ": '" removed[o] "' != '" $2 "'"
      err = 1
   }
   next
}
NF==2 && $2 ~ /^@[1-9][0-9]*$/ { # exported symbol
   o=0+substr($2,2)
   if (symbol[o] == "" || symbol[o] == $1) {
      symbol[o] = $1
      if (o > symbolo) symbolo = o
   } else {
      print "png.h: duplicated symbol", o ": '" symbol[o] "' != '" $1 "'"
      err = 1
   }
}
{
   next # skip all other lines
}

# At the end check for symbols marked as both duplicated and removed
END{
   if (symbolo > lasto) {
      print "highest symbol ordinal in png.h,", symbolo ", exceeds last ordinal from png.h", lasto
      err = 1
   }
   if (mastero > lasto) {
      print "highest symbol ordinal in", master ",", mastero ", exceeds last ordinal from png.h", lasto
      err = 1
   }
   unexported=0
   # Add a standard header to symbols.new:
   print ";Version INSERT-VERSION-HERE" >of
   print ";--------------------------------------------------------------" >of
   print "; LIBPNG symbol list as a Win32 DEF file" >of
   print "; Contains all the symbols that can be exported from libpng" >of
   print ";--------------------------------------------------------------" >of
   print "LIBRARY" >of
   print "" >of
   print "EXPORTS" >of

   for (o=1; o<=lasto; ++o) {
      if (symbol[o] == "" && removed[o] == "") {
         if (unexported == 0) unexported = o
         if (official[o] == "") {
            # missing in export list too, so ok
            if (o < lasto) continue
         }
      }
      if (unexported != 0) {
         # Symbols in the .def but not in the new file are errors, but
         # the 'unexported' symbols aren't in either.  By default this
         # is an error too (see the setting of 'missing' at the start),
         # but this can be reset on the command line or by stuff in the
         # file - see the comments above.
         if (missing != "ignore") {
            if (o-1 > unexported)
               print "png.h:", missing ": missing symbols:", unexported "-" o-1
            else
               print "png.h:", missing ": missing symbol:", unexported
            if (missing != "warning")
               err = 1
         }
         unexported = 0
      }
      if (symbol[o] != "" && removed[o] != "") {
         print "png.h: symbol", o, "both exported as '" symbol[o] "' and removed as '" removed[o] "'"
         err = 1
      } else if (symbol[o] != official[o]) {
         # either the symbol is missing somewhere or it changed
         err = 1
         if (symbol[o] == "")
            print "png.h: symbol", o, "is exported as '" official[o] "' in", master
         else if (official[o] == "")
            print "png.h: exported symbol", o, "'" symbol[o] "' not present in", master
         else
            print "png.h: exported symbol", o, "'" symbol[o] "' exists as '" official[o] "' in", master
      }

      # Finally generate symbols.new
      if (symbol[o] != "")
         print " " symbol[o], "@" o > of
   }

   if (err != 0) {
      print "*** A new list is in", of, "***"
      exit 1
   }
}
