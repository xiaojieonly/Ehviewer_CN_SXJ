#!/bin/bash

set -u
set -e
trap onexit INT
trap onexit TERM
trap onexit EXIT

onexit()
{
	if [ -d $OUTDIR ]; then
		rm -rf $OUTDIR
	fi
}

runme()
{
	echo \*\*\* $*
	"$@"
}

IMAGES="vgl_5674_0098.bmp vgl_6434_0018a.bmp vgl_6548_0026a.bmp nightshot_iso_100.bmp"
IMGDIR=@CMAKE_CURRENT_SOURCE_DIR@/testimages
OUTDIR=`mktemp -d /tmp/__tjexampletest_java_output.XXXXXX`
EXEDIR=@CMAKE_CURRENT_BINARY_DIR@
JAVA="@Java_JAVA_EXECUTABLE@"
JAVAARGS="-cp $EXEDIR/java/turbojpeg.jar -Djava.library.path=$EXEDIR"

if [ -d $OUTDIR ]; then
	rm -rf $OUTDIR
fi
mkdir -p $OUTDIR

exec >$EXEDIR/tjexampletest-java.log

for image in $IMAGES; do

	cp $IMGDIR/$image $OUTDIR
	basename=`basename $image .bmp`
	runme $EXEDIR/cjpeg -quality 95 -dct fast -grayscale -outfile $OUTDIR/${basename}_GRAY_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct fast -sample 2x2 -outfile $OUTDIR/${basename}_420_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct fast -sample 2x1 -outfile $OUTDIR/${basename}_422_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct fast -sample 1x1 -outfile $OUTDIR/${basename}_444_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int -grayscale -outfile $OUTDIR/${basename}_GRAY_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int -sample 2x2 -outfile $OUTDIR/${basename}_420_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int -sample 2x1 -outfile $OUTDIR/${basename}_422_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int -sample 1x1 -outfile $OUTDIR/${basename}_444_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	for samp in GRAY 420 422 444; do
		runme $EXEDIR/djpeg -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_default_djpeg.bmp $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct fast -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_fast_djpeg.bmp $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct int -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_accurate_djpeg.bmp $OUTDIR/${basename}_${samp}_accurate_cjpeg.jpg
	done
	for samp in 420 422; do
		runme $EXEDIR/djpeg -nosmooth -bmp -outfile $OUTDIR/${basename}_${samp}_default_nosmooth_djpeg.bmp $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct fast -nosmooth -bmp -outfile $OUTDIR/${basename}_${samp}_fast_nosmooth_djpeg.bmp $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct int -nosmooth -bmp -outfile $OUTDIR/${basename}_${samp}_accurate_nosmooth_djpeg.bmp $OUTDIR/${basename}_${samp}_accurate_cjpeg.jpg
	done

	# Compression
	for dct in fast accurate; do
		for samp in GRAY 420 422 444; do
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/$image $OUTDIR/${basename}_${samp}_${dct}.jpg -q 95 -subsamp ${samp} -${dct}dct
			runme cmp $OUTDIR/${basename}_${samp}_${dct}.jpg $OUTDIR/${basename}_${samp}_${dct}_cjpeg.jpg
		done
	done

	# Decompression
	for dct in fast accurate default; do
		srcdct=${dct}
		dctarg=-${dct}dct
		if [ "${dct}" = "default" ]; then
			srcdct=fast
			dctarg=
		fi
		for samp in GRAY 420 422 444; do
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_${srcdct}.jpg $OUTDIR/${basename}_${samp}_${dct}.bmp ${dctarg}
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${dct}.bmp $OUTDIR/${basename}_${samp}_${dct}_djpeg.bmp
			rm $OUTDIR/${basename}_${samp}_${dct}.bmp
		done
		for samp in 420 422; do
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_${srcdct}.jpg $OUTDIR/${basename}_${samp}_${dct}_nosmooth.bmp -fastupsample ${dctarg}
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${dct}_nosmooth.bmp $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.bmp
			rm $OUTDIR/${basename}_${samp}_${dct}_nosmooth.bmp
		done
	done

	# Scaled decompression
	for scale in 2_1 15_8 7_4 13_8 3_2 11_8 5_4 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8; do
		scalearg=`echo $scale | sed 's/\_/\//g'`
		for samp in GRAY 420 422 444; do
			runme $EXEDIR/djpeg -rgb -bmp -scale ${scalearg} -outfile $OUTDIR/${basename}_${samp}_${scale}_djpeg.bmp $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${scale}.bmp -scale ${scalearg}
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${scale}.bmp $OUTDIR/${basename}_${samp}_${scale}_djpeg.bmp
			rm $OUTDIR/${basename}_${samp}_${scale}.bmp
		done
	done

	# Transforms
	for samp in GRAY 420 422 444; do
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -flip horizontal -trim -outfile $OUTDIR/${basename}_${samp}_hflip_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -flip vertical -trim -outfile $OUTDIR/${basename}_${samp}_vflip_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -transpose -trim -outfile $OUTDIR/${basename}_${samp}_transpose_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -transverse -trim -outfile $OUTDIR/${basename}_${samp}_transverse_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -rotate 90 -trim -outfile $OUTDIR/${basename}_${samp}_rot90_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -rotate 180 -trim -outfile $OUTDIR/${basename}_${samp}_rot180_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
		runme $EXEDIR/jpegtran -crop 70x60+16+16 -rotate 270 -trim -outfile $OUTDIR/${basename}_${samp}_rot270_jpegtran.jpg $OUTDIR/${basename}_${samp}_fast.jpg
	done
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 420 422 444; do
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${xform}.jpg -$xform -crop 70x60+16+16
			runme cmp $OUTDIR/${basename}_${samp}_${xform}.jpg $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme $EXEDIR/djpeg -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${xform}.bmp -$xform -crop 70x60+16+16
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${xform}.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp
			rm $OUTDIR/${basename}_${samp}_${xform}.bmp
		done
		for samp in 420 422; do
			runme $EXEDIR/djpeg -nosmooth -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${xform}.bmp -$xform -crop 70x60+16+16 -fastupsample
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${xform}.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp
			rm $OUTDIR/${basename}_${samp}_${xform}.bmp
		done
	done

	# Grayscale transform
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444 422 420; do
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${xform}.jpg -$xform -grayscale -crop 70x60+16+16
			runme cmp $OUTDIR/${basename}_${samp}_${xform}.jpg $OUTDIR/${basename}_GRAY_${xform}_jpegtran.jpg
			runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${xform}.bmp -$xform -grayscale -crop 70x60+16+16
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${xform}.bmp $OUTDIR/${basename}_GRAY_${xform}_jpegtran.bmp
			rm $OUTDIR/${basename}_${samp}_${xform}.bmp
		done
	done

	# Transforms with scaling
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444 422 420; do
			for scale in 2_1 15_8 7_4 13_8 3_2 11_8 5_4 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8; do
				scalearg=`echo $scale | sed 's/\_/\//g'`
				runme $EXEDIR/djpeg -rgb -bmp -scale ${scalearg} -outfile $OUTDIR/${basename}_${samp}_${xform}_${scale}_jpegtran.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
				runme "$JAVA" $JAVAARGS TJExample $OUTDIR/${basename}_${samp}_fast.jpg $OUTDIR/${basename}_${samp}_${xform}_${scale}.bmp -$xform -scale ${scalearg} -crop 70x60+16+16
				runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_${xform}_${scale}.bmp $OUTDIR/${basename}_${samp}_${xform}_${scale}_jpegtran.bmp
				rm $OUTDIR/${basename}_${samp}_${xform}_${scale}.bmp
			done
		done
	done

done

echo SUCCESS!
