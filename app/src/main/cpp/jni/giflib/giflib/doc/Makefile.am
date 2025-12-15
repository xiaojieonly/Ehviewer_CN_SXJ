SUFFIXES = .xml .html .txt .asc .1

.xml.html:
	xmlto xhtml-nochunks $<

.xml.1:
	xmlto man $<

.xml.txt:
	xmlto txt $<

.asc.html:
	asciidoc $<

all: allhtml manpages

# The distinction between XMLMAN and XMLINTERNAL is because
# some pages shouldn't be installed as part of a binary package;
# they're just for test-pattern generators.
XMLMAN = \
	gif2rgb.xml \
	gifbuild.xml \
	gifclrmp.xml \
	gifecho.xml \
	giffix.xml \
	gifinto.xml \
	giflib.xml \
	giftext.xml \
	giftool.xml
XMLINTERNAL = \
	gifbg.xml \
	gifcolor.xml \
	gifhisto.xml \
	gifwedge.xml
XMLDOC = intro.xml gif_lib.xml
XMLALL = $(XMLMAN) $(XMLINTERNAL) $(XMLDOC)
TXTDOC = 00README gif87.txt gif89.txt lzgif.txt
SOURCES = $(XMLALL) $(TXTDOC)
SITEHTML = index.html

# Philosophical choice: the website gets the internal manual pages
allhtml: $(XMLALL:.xml=.html) $(XMLINTERNAL:.xml=.html)

manpages: $(XMLMAN:.xml=.1) $(XMLINTERNAL:.xml=.1)
man_MANS = $(XMLMAN:.xml=.1)

# Control what goes in the distribution tarball.
# We include all of the XML, and also generated manual pages 
# so people working from the distribution tarball won't need xmlto. 
EXTRA_DIST = $(SOURCES) $(XMLMAN:.xml=.1) $(XMLINTERNAL:.xml=.1)

# Prepare the website directory to deliver an update.
# ImageMagick and asciidoc are required.
website: allhtml
	rm -fr staging; mkdir staging; 
	cp -r $(XMLALL:.xml=.html) $(SITEHTML) *.txt whatsinagif staging
	convert ../pic/gifgrid.gif -resize 50x50 staging/giflib-logo.gif
	asciidoc - <../history.asc >staging/history.html

clean-local:
	rm -fr staging $(XMLALL:.xml=.html) $(XMLMAN:.xml=.1)
