#!/bin/awk -f
# scripts/options.awk - library build configuration control
#
# last changed in libpng version 1.6.11 - June 5, 2014
#
# Copyright (c) 1998-2014 Glenn Randers-Pehrson
#
# This code is released under the libpng license.
# For conditions of distribution and use, see the disclaimer
# and license in png.h

# The output of this script is written to the file given by
# the variable 'out'.  The script is run twice, once with
# an intermediate output file, 'options.tmp' then again on
# that file to produce the final output:
#
#  awk -f scripts/options.awk out=options.tmp scripts/options.dfa 1>&2
#  awk -f scripts/options.awk out=options.dfn options.tmp 1>&2
#
# Some options may be specified on the command line:
#
#  deb=1            Causes debugging to be output
#  logunsupported=1 Causes all options to be recorded in the output
#  everything=off   Causes all options to be disabled by default
#  everything=on    Causes all options to be enabled by default
#
# If awk fails on your platform, try nawk instead.
#
# These options may also be specified in the original input file (and
# are copied to the preprocessed file).

BEGIN{
   out=""                       # intermediate, preprocessed, file
   pre=-1                       # preprocess (first line)
   version="libpng version unknown" # version information
   version_file=""              # where to find the version
   err=0                        # in-line exit sets this
   # The following definitions prevent the C preprocessor noticing the lines
   # that will be in the final output file.  Some C preprocessors tokenise
   # the lines, for example by inserting spaces around operators, and all
   # C preprocessors notice lines that start with '#', most remove comments.
   # The technique adopted here is to make the final output lines into
   # C strings (enclosed in double quotes), preceded by PNG_DFN.  As a
   # consequence the output cannot contain a 'raw' double quote - instead put
   # @' in, this will be replaced by a single " afterward.  See the parser
   # script dfn.awk for more capabilities (not required here).  Note that if
   # you need a " in a 'setting' in pnglibconf.dfa it must also be @'!
   dq="@'"                      # For a single double quote
   start=" PNG_DFN \""          # Start stuff to output (can't contain a "!)
   end="\" "                    # End stuff to output
   subs="@\" "                  # Substitute start (substitute a C macro)
   sube=" \"@"                  # Substitute end
   comment=start "/*"           # Comment start
   cend="*/" end                # Comment end
   def=start "#define PNG_"     # Arbitrary define
   sup="_SUPPORTED" end         # end supported option
   und=comment "#undef PNG_"    # Unsupported option
   une="_SUPPORTED" cend        # end unsupported option
   error=start "ERROR:"         # error message, terminate with 'end'

   # Variables
   deb=0                        # debug - set on command line
   everything=""                # do not override defaults
   logunsupported=0             # write unsupported options too

   # Precreate arrays
   # for each option:
   option[""] = ""    # list of all options: default enabled/disabled
   done[""] = 1       # marks option as having been output
   requires[""] = ""  # requires by option
   iffs[""] = ""      # if by option
   enabledby[""] = "" # options that enable it by option
   sets[""] = ""      # settings set by each option
   setval[""] = ""    # value to set (indexed: 'option sets[option]')
   # for each setting:
   setting[""] = ""   # requires by setting
   defaults[""] = ""  # used for a defaulted value
   doneset[""] = 1    # marks setting as having been output
   r[""] = ""         # Temporary array

   # For decorating the output file
   protect = ""
}

# The output file must be specified before any input:
out == "" {
   print "out=output.file must be given on the command line"
   err = 1
   exit 1
}

# The very first line indicates whether we are reading pre-processed
# input or not, this must come *first* because 'PREPROCESSED' needs
# to be the very first line in the temporary file.
pre == -1{
   if ($0 == "PREPROCESSED") {
      pre = 0
      next
   } else {
      pre = 1
      print "PREPROCESSED" >out
      # And fall through to continue processing
   }
}

# While pre-processing if version is set to "search" look for a version string
# in the following file.
pre && version == "search" && version_file == ""{
   version_file = FILENAME
}

pre && version == "search" && version_file != FILENAME{
   print "version string not found in", version_file
   err = 1
   exit 1
}

pre && version == "search" && $0 ~ /^ \* libpng version/{
   version = substr($0, 4)
   print "version =", version >out
   next
}

pre && FILENAME == version_file{
   next
}

# variable=value
#   Sets the given variable to the given value (the syntax is fairly
#   free form, except for deb (you are expected to understand how to
#   set the debug variable...)
#
#   This happens before the check on 'pre' below skips most of the
#   rest of the actions, so the variable settings happen during
#   preprocessing but are recorded in the END action too.  This
#   allows them to be set on the command line too.
$0 ~ /^[ 	]*version[ 	]*=/{
   sub(/^[  ]*version[  ]*=[  ]*/, "")
   version = $0
   next
}
$0 ~ /^[ 	]*everything[ 	=]*off[ 	]*$/{
   everything = "off"
   next
}
$0 ~ /^[ 	]*everything[ 	=]*on[ 	]*$/{
   everything = "on"
   next
}
$0 ~ /^[ 	]*logunsupported[ 	=]*0[ 	]*$/{
   logunsupported = 0
   next
}
$0 ~ /^[ 	]*logunsupported[ 	=]*1[ 	]*$/{
   logunsupported = 1
   next
}
$1 == "deb" && $2 == "=" && NF == 3{
   deb = $3
   next
}

# Preprocessing - this just copies the input file with lines
# that need preprocessing (just chunk at present) expanded
# The bare "pre" instead of "pre != 0" crashes under Sunos awk
pre && $1 != "chunk"{
   print >out
   next
}

# The first characters of the line determine how it is processed,
# leading spaces are ignored.  In general tokens that are not
# keywords are the names of options.  An option 'name' is
# controlled by the definition of the corresponding macros:
#
#   PNG_name_SUPPORTED    The option is turned on
#   PNG_NO_name
#   PNG_NO_name_SUPPORTED If the first macro is not defined
#                         either of these will turn the option off
#
# If none of these macros are defined the option is turned on, unless
# the keyword 'off' is given in a line relating to the option.  The
# keyword 'on' can also be given, but it will be ignored (since it is
# the default.)
#
# In the syntax below a 'name' is indicated by "NAME", other macro
# values are indicated by "MACRO", as with "NAME" the leading "PNG_"
# is omitted, but in this case the "NO_" prefix and the "_SUPPORTED"
# suffix are never used.
#
# Each line is introduced by a keyword - the first non-space characters
# on the line.  A line starting with a '#' is a comment - it is totally
# ignored.  Keywords are as follows, a NAME, is simply a macro name
# without the leading PNG_, PNG_NO_ or the trailing _SUPPORTED.

$1 ~ /^#/ || $0 ~ /^[ 	]*$/{
   next
}

# com <comment>
#   The whole line is placed in the output file as a comment with
#   the preceding 'com' removed
$1 == "com"{
   if (NF > 1) {
      # sub(/^[ 	]*com[ 	]*/, "")
      $1 = ""
      print comment $0, cend >out
   } else
      print start end >out
   next
}

# version
#   Inserts a version comment
$1 == "version" && NF == 1{
   if (version == "") {
      print "ERROR: no version string set"
      err = 1 # prevent END{} running
      exit 1
   }

   print comment, version, cend >out
   next
}

# file output input protect
#   Informational: the official name of the input file (without
#   make generated local directories), the official name of the
#   output file and, if required, a name to use in a protection
#   macro for the contents.
$1 == "file" && NF >= 2{
   print comment, $2, cend >out
   print comment, "Machine generated file: DO NOT EDIT", cend >out
   if (NF >= 3)
      print comment, "Derived from:", $3, cend >out
   protect = $4
   if (protect != "") {
      print start "#ifndef", protect end >out
      print start "#define", protect end >out
   }
   next
}

# option NAME ( (requires|enables|if) NAME* | on | off | disabled |
#                sets SETTING VALUE+ )*
#     
#   Declares an option 'NAME' and describes its default setting (disabled)
#   and its relationship to other options.  The option is disabled
#   unless *all* the options listed after 'requires' are set and at
#   least one of the options listed after 'if' is set.  If the
#   option is set then it turns on all the options listed after 'enables'.
#
#   Note that "enables" takes priority over the required/if/disabled/off
#   setting of the target option.
#
#   The definition file may list an option as 'disabled': off by default,
#   otherwise the option is enabled: on by default.  A later (and it must
#   be later) entry may turn an option on or off explicitly.

$1 == "option" && NF >= 2{
   opt = $2
   sub(/,$/,"",opt)
   onoff = option[opt]  # records current (and the default is "", enabled)
   key = ""
   istart = 3
   do {
      if (istart == 1) {     # continuation line
         val = getline

         if (val != 1) { # error reading it
            if (val == 0)
               print "option", opt ": ERROR: missing continuation line"
            else
               print "option", opt ": ERROR: error reading continuation line"

            # This is a hard error
            err = 1 # prevent END{} running
            exit 1
         }
      }

      for (i=istart; i<=NF; ++i) {
         val=$(i)
         sub(/,$/,"",val)
         if (val == "on" || val == "off" || val == "disabled" || val =="enabled") {
            key = ""
            if (onoff != val) {
               # on or off can zap disabled or enabled:
               if (onoff == "" || (onoff == "disabled" || onoff == "enabled") &&
                   (val == "on" || val == "off")) {
                  # It's easy to mis-spell the option when turning it
                  # on or off, so warn about it here:
                  if (onoff == "" && (val == "on" || val == "off")) {
                     print "option", opt ": ERROR: turning unrecognized option", val
                     # For the moment error out - it is safer
                     err = 1 # prevent END{} running
                     exit 1
                  }
                  onoff = val
               } else {
                  # Print a message, otherwise the error
                  # below is incomprehensible
                  print "option", opt ": currently", onoff ": attempt to turn", val
                  break
               }
            }
         } else if (val == "requires" || val == "if" || val == "enables" || val =="sets") {
            key = val
         } else if (key == "requires") {
            requires[opt] = requires[opt] " " val
         } else if (key == "if") {
            iffs[opt] = iffs[opt] " " val
         } else if (key == "enables") {
            enabledby[val] = enabledby[val] " " opt
         } else if (key == "sets") {
            sets[opt] = sets[opt] " " val
            key = "setval"
            set = val
         } else if (key == "setval") {
            setval[opt " " set] = setval[opt " " set] " " val
         } else
            break # bad line format
      }

      istart = 1
   } while (i > NF && $0 ~ /,$/)

   if (i > NF) {
      # Set the option, defaulting to 'enabled'
      if (onoff == "") onoff = "enabled"
      option[opt] = onoff
      next
   }
   # Else fall through to the error handler
}

# chunk NAME [requires OPT] [enables LIST] [on|off|disabled]
#   Expands to the 'option' settings appropriate to the reading and
#   writing of an ancillary PNG chunk 'NAME':
#
#   option READ_NAME requires READ_ANCILLARY_CHUNKS [READ_OPT]
#   option READ_NAME enables NAME LIST
#   [option READ_NAME off]
#   option WRITE_NAME requires WRITE_ANCILLARY_CHUNKS [WRITE_OPT]
#   option WRITE_NAME enables NAME LIST
#   [option WRITE_NAME off]

pre != 0 && $1 == "chunk" && NF >= 2{
   # 'chunk' is handled on the first pass by writing appropriate
   # 'option' lines into the intermediate file.
   opt = $2
   sub(/,$/,"",opt)
   onoff = ""
   reqread = ""
   reqwrite = ""
   enables = ""
   req = 0
   istart = 3
   do {
      if (istart == 1) {     # continuation line
         val = getline

         if (val != 1) { # error reading it
            if (val == 0)
               print "chunk", opt ": ERROR: missing continuation line"
            else
               print "chunk", opt ": ERROR: error reading continuation line"

            # This is a hard error
            err = 1 # prevent END{} running
            exit 1
         }
      }

      # read the keywords/additional OPTS
      for (i=istart; i<=NF; ++i) {
         val = $(i)
         sub(/,$/,"",val)
         if (val == "on" || val == "off" || val == "disabled") {
            if (onoff != val) {
               if (onoff == "")
                  onoff = val
               else
                  break # on/off conflict
            }
            req = 0
         } else if (val == "requires")
            req = 1
         else if (val == "enables")
            req = 2
         else if (req == 1){
            reqread = reqread " READ_" val
            reqwrite = reqwrite " WRITE_" val
         } else if (req == 2)
            enables = enables " " val
         else
            break # bad line: handled below
      }

      istart = 1
   } while (i > NF && $0 ~ /,$/)

   if (i > NF) {
      # Output new 'option' lines to the intermediate file (out)
      print "option READ_" opt, "requires READ_ANCILLARY_CHUNKS" reqread, "enables", opt enables , onoff >out
      print "option WRITE_" opt, "requires WRITE_ANCILLARY_CHUNKS" reqwrite, "enables", opt enables, onoff >out
      next
   }
   # Else hit the error handler below - bad line format!
}

# setting MACRO ( requires MACRO* )* [ default VALUE ]
#   Behaves in a similar way to 'option' without looking for NO_ or
#   _SUPPORTED; the macro is enabled if it is defined so long as all
#   the 'requires' macros are also defined.  The definitions may be
#   empty, an error will be issued if the 'requires' macros are
#   *not* defined.  If given the 'default' value is used if the
#   macro is not defined.  The default value will be re-tokenised.
#   (BTW: this is somewhat restrictive, it mainly exists for the
#   support of non-standard configurations and numeric parameters,
#   see the uses in scripts/options.dat

$1 == "setting" && (NF == 2 || NF >= 3 && ($3 == "requires" || $3 == "default")){
   reqs = ""
   deflt = ""
   isdef = 0
   key = ""
   for (i=3; i<=NF; ++i)
      if ($(i) == "requires" || $(i) == "default") {
         key = $(i)
         if (key == "default") isdef = 1
      } else if (key == "requires")
         reqs = reqs " " $(i)
      else if (key == "default")
         deflt = deflt " " $(i)
      else
         break # Format error, handled below

   setting[$2] = reqs
   # NOTE: this overwrites a previous value silently
   if (isdef && deflt == "")
      deflt = " " # as a flag to force output
   defaults[$2] = deflt
   next
}

# The order of the dependency lines (option, chunk, setting) is irrelevant
# - the 'enables', 'requires' and 'if' settings will be used to determine
# the correct order in the output and the final values in pnglibconf.h are
# not order dependent.  'requires' and 'if' entries take precedence over
# 'enables' from other options; if an option requires another option it
# won't be set regardless of any options that enable it unless the other
# option is also enabled.
#
# Similarly 'enables' trumps a NO_ definition in CFLAGS or pngusr.h
#
# For simplicity cycles in the definitions are regarded as errors,
# even if they are not ambiguous.
# A given NAME can be specified in as many 'option' lines as required, the
# definitions are additive.

# For backwards compatibility equivalent macros may be listed thus:
#
# = [NO_]NAME MACRO
#   Makes -DMACRO equivalent to -DPNG_NO_NAME or -DPNG_NAME_SUPPORTED
#   as appropriate.
#
# The definition is injected into the C compiler input when encountered
# in the second pass (so all these definitions appear *after* the @
# lines!)
#
# 'NAME' is as above, but 'MACRO' is the full text of the equivalent
# old, deprecated, macro.

$1 == "=" && NF == 3{
   print "#ifdef PNG_" $3 >out
   if ($2 ~ /^NO_/)
      print "#   define PNG_" $2 >out
   else
      print "#   define PNG_" $2 "_SUPPORTED" >out
   print "#endif" >out
   next
}

# Lines may be injected into the C compiler input by preceding them
# with an "@" character.  The line is copied with just the leading
# @ removed.

$1 ~ /^@/{
   # sub(/^[ 	]*@/, "")
   $1 = substr($1, 2)
   print >out
   next
}

# Check for unrecognized lines, because of the preprocessing chunk
# format errors will be detected on the first pass independent of
# any other format errors.
{
   print "options.awk: bad line (" NR "):", $0
   err = 1 # prevent END{} running
   exit 1
}

# For checking purposes names that start with "ok_" or "fail_" are
# not output to pnglibconf.h and must be either enabled or disabled
# respectively for the build to succeed.  This allows interdependencies
# between options of the form "at least one of" or "at most one of"
# to be checked.  For example:
#
# option FLOATING_POINT enables ok_math
# option FIXED_POINT enables ok_math
#   This ensures that at least one of FLOATING_POINT and FIXED_POINT
#   must be set for the build to succeed.
#
# option fail_math requires FLOATING_POINT FIXED_POINT
#   This means the build will fail if *both* FLOATING_POINT and
#   FIXED_POINT are set (this is an example; in fact both are allowed.)
#
# If all these options were given the build would require exactly one
# of the names to be enabled.

END{
   # END{} gets run on an exit (a traditional awk feature)
   if (err) exit 1

   if (pre) {
      # Record the final value of the variables
      print "deb =", deb >out
      if (everything != "") {
         print "everything =", everything >out
      }
      print "logunsupported =", logunsupported >out
      exit 0
   }

   # Do the options first (allowing options to set settings).  The dependency
   # tree is thus:
   #
   #   name     >     name
   #   name requires  name
   #   name if        name
   #   name enabledby name
   #
   # First build a list 'tree' by option of all the things on which
   # it depends.
   print "" >out
   print "/* OPTIONS */" >out
   print comment, "options", cend >out
   for (opt in enabledby) tree[opt] = 1  # may not be explicit options
   for (opt in option) if (opt != "") {
      o = option[opt]
      # option should always be one of the following values
      if (o != "on" && o != "off" && o != "disabled" && o != "enabled") {
         print "internal option error (" o ")"
         exit 1
      }
      tree[opt] = ""   # so unlisted options marked
   }
   for (opt in tree) if (opt != "") {
      if (tree[opt] == 1) {
         tree[opt] = ""
         if (option[opt] != "") {
            print "internal error (1)"
            exit 1
         }
         # Macros only listed in 'enables' remain off unless
         # one of the enabling macros is on.
         option[opt] = "disabled"
      }

      split("", list) # clear 'list'
      # Now add every requires, iffs or enabledby entry to 'list'
      # so that we can add a unique list of requirements to tree[i]
      split(requires[opt] iffs[opt] enabledby[opt], r)
      for (i in r) list[r[i]] = 1
      for (i in list) tree[opt] = tree[opt] " " i
   }

   # print the tree for extreme debugging
   if (deb > 2) for (i in tree) if (i != "") print i, "depends-on" tree[i]

   # Ok, now check all options marked explicitly 'on' or 'off':
   #
   # If an option[opt] is 'on' then turn on all requires[opt]
   # If an option[opt] is 'off' then turn off all enabledby[opt]
   #
   # Error out if we have to turn 'on' to an 'off' option or vice versa.
   npending = 0
   for (opt in option) if (opt != "") {
      if (option[opt] == "on" || option[opt] == "off") {
         pending[++npending] = opt
      }
   }

   err = 0 # set on error
   while (npending > 0) {
      opt = pending[npending--]
      if (option[opt] == "on") {
         nreqs = split(requires[opt], r)
         for (j=1; j<=nreqs; ++j) {
            if (option[r[j]] == "off") {
               print "option", opt, "turned on, but requirement", r[j], "is turned off"
               err = 1
            } else if (option[r[j]] != "on") {
               option[r[j]] = "on"
               pending[++npending] = r[j]
            }
         }
      } else {
         if (option[opt] != "off") {
            print "internal error (2)"
            exit 1
         }
         nreqs = split(enabledby[opt], r)
         for (j=1; j<=nreqs; ++j) {
            if (option[r[j]] == "on") {
               print "option", opt, "turned off, but enabled by", r[j], "which is turned on"
               err = 1
            } else if (option[r[j]] != "off") {
               option[r[j]] = "off"
               pending[++npending] = r[j]
            }
         }
      }
   }
   if (err) exit 1

   # Sort options:
   print "PNG_DFN_START_SORT 2" >out

   # option[i] is now the complete list of all the tokens we may
   # need to output, go through it as above, depth first.
   finished = 0
   while (!finished) {
      finished = 1
      movement = 0 # done nothing
      for (i in option) if (!done[i]) {
         nreqs = split(tree[i], r)
         if (nreqs > 0) {
            for (j=1; j<=nreqs; ++j) if (!done[r[j]]) {
               break
            }
            if (j<=nreqs) {
               finished = 0
               continue  # next option
            }
         }

         # All the requirements have been processed, output
         # this option.  An option is _SUPPORTED if:
         #
         # all 'requires' are _SUPPORTED AND
         # at least one of the 'if' options are _SUPPORTED AND
         # EITHER:
         #   The name is _SUPPORTED (on the command line)
         # OR:
         #   an 'enabledby' is _SUPPORTED
         # OR:
         #   NO_name is not defined AND
         #   the option is not disabled; an option is disabled if:
         #    option == off
         #    option == disabled && everything != on
         #    option == "" && everything == off
         if (deb) print "option", i
         print "" >out
         print "/* option:", i, option[i] >out
         print " *   requires:  " requires[i] >out
         print " *   if:        " iffs[i] >out
         print " *   enabled-by:" enabledby[i] >out
         print " *   sets:      " sets[i], "*/" >out
         print "#undef PNG_on" >out
         print "#define PNG_on 1" >out

         # requires
         nreqs = split(requires[i], r)
         for (j=1; j<=nreqs; ++j) {
            print "#ifndef PNG_" r[j] "_SUPPORTED" >out
            print "#   undef PNG_on /*!" r[j] "*/" >out
            # This error appears in the final output if something
            # was switched 'on' but the processing above to force
            # the requires did not work
            if (option[i] == "on") {
               print error, i, "requires", r[j] end >out
            }
            print "#endif" >out
         }

         # if
         have_ifs = 0
         nreqs = split(iffs[i], r)
         print "#undef PNG_no_if" >out
         if (nreqs > 0) {
            have_ifs = 1
            print "/* if" iffs[i], "*/" >out
            print "#define PNG_no_if 1" >out
            for (j=1; j<=nreqs; ++j) {
               print "#ifdef PNG_" r[j] "_SUPPORTED" >out
               print "#   undef PNG_no_if /*" r[j] "*/" >out
               print "#endif" >out
            }
            print "#ifdef PNG_no_if /*missing if*/" >out
            print "#   undef PNG_on" >out
            # There is no checking above for this, because we
            # don't know which 'if' to choose, so whine about
            # it here:
            if (option[i] == "on") {
               print error, i, "needs one of:", iffs[i] end >out
            }
            print "#endif" >out
         }

         print "#ifdef PNG_on /*requires, if*/" >out
         # enables
         print "#   undef PNG_not_enabled" >out
         print "#   define PNG_not_enabled 1" >out
         print "   /* enabled by" enabledby[i], "*/" >out
         nreqs = split(enabledby[i], r)
         for (j=1; j<=nreqs; ++j) {
            print "#ifdef PNG_" r[j] "_SUPPORTED" >out
            print "#   undef PNG_not_enabled /*" r[j] "*/" >out
            # Oops, probably not intended (should be factored
            # out by the checks above).
            if (option[i] == "off") {
               print error, i, "enabled by:", r[j] end >out
            }
            print "#endif" >out
         }

         print "#   ifndef PNG_" i "_SUPPORTED /*!command line*/" >out
         print "#    ifdef PNG_not_enabled /*!enabled*/" >out
         # 'have_ifs' here means that everything = "off" still allows an 'if' on
         # an otherwise enabled option to turn it on; otherwise the 'if'
         # handling is effectively disabled by 'everything = off'
         if (option[i] == "off" || option[i] == "disabled" && everything != "on" || option[i] == "enabled" && everything == "off" && !have_ifs) {
            print "#      undef PNG_on /*default off*/" >out
         } else {
            print "#      ifdef PNG_NO_" i >out
            print "#       undef PNG_on /*turned off*/" >out
            print "#      endif" >out
            print "#      ifdef PNG_NO_" i "_SUPPORTED" >out
            print "#       undef PNG_on /*turned off*/" >out
            print "#      endif" >out
         }
         print "#    endif /*!enabled*/" >out
         print "#    ifdef PNG_on" >out
         # The _SUPPORTED macro must be defined so that dependent
         # options output later work.
         print "#      define PNG_" i "_SUPPORTED" >out
         print "#    endif" >out
         print "#   endif /*!command line*/" >out
         # If PNG_on is still set the option should be defined in
         # pnglibconf.h
         print "#   ifdef PNG_on" >out
         if (i ~ /^fail_/) {
            print error, i, "is on: enabled by:" iffs[i] enabledby[i] ", requires" requires[i] end >out
         } else if (i !~ /^ok_/) {
            print def i sup >out
            # Supported option, set required settings
            nreqs = split(sets[i], r)
            for (j=1; j<=nreqs; ++j) {
               print "#    ifdef PNG_set_" r[j] >out
               # Some other option has already set a value:
               print error, i, "sets", r[j] ": duplicate setting" end >out
               print error, "   previous value: " end "PNG_set_" r[j] >out
               print "#    else" >out
               # Else set the default: note that this won't accept arbitrary
               # values, the setval string must be acceptable to all the C
               # compilers we use.  That means it must be VERY simple; a number,
               # a name or a string.
               print "#     define PNG_set_" r[j], setval[i " " r[j]] >out
               print "#    endif" >out
            }
         }
         print "#   endif /* definition */" >out
         print "#endif /*requires, if*/" >out
         if (logunsupported || i ~ /^ok_/) {
            print "#ifndef  PNG_on" >out
            if (logunsupported) {
               print und i une >out
            }
            if (i ~ /^ok_/) {
               print error, i, "not enabled: requires:" requires[i] ", enabled by:" iffs[i] enabledby[i] end >out
            }
            print "#endif" >out
         }

         done[i] = 1
         ++movement
      }

      if (!finished && !movement) {
         print "option: loop or missing option in dependency tree, cannot process:"
         for (i in option) if (!done[i]) {
            print "  option", i, "depends on" tree[i], "needs:"
            nreqs = split(tree[i], r)
            if (nreqs > 0) for (j=1; j<=nreqs; ++j) if (!done[r[j]]) {
               print "   " r[j]
            }
         }
         exit 1
      }
   }
   print "PNG_DFN_END_SORT" >out
   print comment, "end of options", cend >out

   # Do the 'setting' values second, the algorithm the standard
   # tree walk (O(1)) done in an O(2) while/for loop; iterations
   # settings x depth, outputting the deepest required macros
   # first.
   print "" >out
   print "/* SETTINGS */" >out
   print comment, "settings", cend >out
   # Sort (in dfn.awk) on field 2, the setting name
   print "PNG_DFN_START_SORT 2" >out
   finished = 0
   while (!finished) {
      finished = 1
      movement = 0 # done nothing
      for (i in setting) if (!doneset[i]) {
         nreqs = split(setting[i], r)
         if (nreqs > 0) {
            # By default assume the requires values are options, but if there
            # is no option with that name check for a setting
            for (j=1; j<=nreqs; ++j) if (option[r[j]] == "" && !doneset[r[j]]) {
               break
            }
            if (j<=nreqs) {
               finished = 0
               continue # try a different setting
            }
         }

         # All the requirements have been processed, output
         # this setting.
         if (deb) print "setting", i
         deflt = defaults[i]
         # Remove any spurious trailing spaces
         sub(/ *$/,"",deflt)
         # A leading @ means leave it unquoted so the preprocessor
         # can substitute the build time value
         if (deflt ~ /^ @/)
            deflt = " " subs substr(deflt, 3) sube
         print "" >out
         print "/* setting: ", i >out
         print " *   requires:" setting[i] >out
         print " *   default: ", defaults[i] deflt, "*/" >out
         for (j=1; j<=nreqs; ++j) {
            if (option[r[j]] != "")
               print "#ifndef PNG_" r[j] "_SUPPORTED" >out
            else
               print "#ifndef PNG_" r[j] >out
            print error, i, "requires", r[j] end >out
            print "# endif" >out
         }
         # The precedence is:
         #
         #  1) External definition; trumps:
         #  2) Option 'sets' value; trumps:
         #  3) Setting 'default'
         #
         print "#ifdef PNG_" i >out
         # PNG_<i> is defined, so substitute the value:
         print def i, subs "PNG_" i sube end >out
         print "#else /* use default */" >out
         print "# ifdef PNG_set_" i >out
         # Value from an option 'sets' argument
         print def i, subs "PNG_set_" i sube end >out
         # This is so that subsequent tests on the setting work:
         print "#  define PNG_" i, "1" >out
         if (defaults[i] != "") {
            print "# else /*default*/" >out
            print def i deflt end >out
            print "#  define PNG_" i, "1" >out
         }
         print "# endif /* defaults */" >out
         print "#endif /* setting", i, "*/" >out

         doneset[i] = 1
         ++movement
      }

      if (!finished && !movement) {
         print "setting: loop or missing setting in 'requires', cannot process:"
         for (i in setting) if (!doneset[i]) {
            print "  setting", i, "requires" setting[i]
         }
         exit 1
      }
   }
   print "PNG_DFN_END_SORT" >out
   print comment, "end of settings", cend >out

   # Regular end - everything looks ok
   if (protect != "") {
      print start "#endif", "/*", protect, "*/" end >out
   }
}
