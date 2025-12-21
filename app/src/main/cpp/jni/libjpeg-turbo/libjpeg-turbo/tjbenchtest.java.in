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
OUTDIR=`mktemp -d /tmp/__tjbenchtest_java_output.XXXXXX`
EXEDIR=@CMAKE_CURRENT_BINARY_DIR@
JAVA="@Java_JAVA_EXECUTABLE@"
JAVAARGS="-cp $EXEDIR/java/turbojpeg.jar -Djava.library.path=$EXEDIR"
BMPARG=
NSARG=
YUVARG=
PROGARG=

if [ -d $OUTDIR ]; then
	rm -rf $OUTDIR
fi
mkdir -p $OUTDIR

while [ $# -gt 0 ]; do
	case "$1" in
	-yuv)
		NSARG=-nosmooth
		YUVARG=-yuv

# NOTE: The combination of tjEncodeYUV*() and tjCompressFromYUV*() does not
# always produce bitwise-identical results to tjCompress*() if subsampling is
# enabled.  In both cases, if the image width or height are not evenly
# divisible by the MCU width/height, then the bottom and/or right edge are
# expanded.  However, the libjpeg code performs this expansion prior to
# downsampling, and TurboJPEG performs it in tjCompressFromYUV*(), which is
# after downsampling.  Thus, the two will agree only if the width/height along
# each downsampled dimension is an odd number or is evenly divisible by the MCU
# width/height.  This disagreement basically amounts to a round-off error, but
# there is no easy way around it, so for now, we just test the only image that
# works.  (NOTE: nightshot_iso_100 does not suffer from the above issue, but
# it suffers from an unrelated problem whereby the combination of
# tjDecompressToYUV*() and tjDecodeYUV*() do not produce bitwise-identical
# results to tjDecompress*() if decompression scaling is enabled.  This latter
# phenomenon is not yet fully understood but is also believed to be some sort
# of round-off error.)
		IMAGES="vgl_6548_0026a.bmp"
		;;
	-progressive)
		PROGARG=-progressive
		;;
	esac
	shift
done

exec >$EXEDIR/tjbenchtest-java$YUVARG$PROGARG.log

# Standard tests
for image in $IMAGES; do

	cp $IMGDIR/$image $OUTDIR
	basename=`basename $image .bmp`
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -grayscale -outfile $OUTDIR/${basename}_GRAY_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -sample 2x2 -outfile $OUTDIR/${basename}_420_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -sample 2x1 -outfile $OUTDIR/${basename}_422_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -sample 1x1 -outfile $OUTDIR/${basename}_444_fast_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -grayscale -outfile $OUTDIR/${basename}_GRAY_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -sample 2x2 -outfile $OUTDIR/${basename}_420_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -sample 2x1 -outfile $OUTDIR/${basename}_422_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -sample 1x1 -outfile $OUTDIR/${basename}_444_accurate_cjpeg.jpg $IMGDIR/${basename}.bmp
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
	for dct in accurate fast; do
		runme "$JAVA" $JAVAARGS TJBench $OUTDIR/$image 95 -rgb -quiet -benchtime 0.01 -warmup 0 -${dct}dct $YUVARG $PROGARG
		for samp in GRAY 420 422 444; do
			runme cmp $OUTDIR/${basename}_${samp}_Q95.jpg $OUTDIR/${basename}_${samp}_${dct}_cjpeg.jpg
		done
	done

	for dct in fast accurate default; do
		dctarg=-${dct}dct
		if [ "${dct}" = "default" ]; then
			dctarg=
		fi

		# Tiled compression & decompression
		runme "$JAVA" $JAVAARGS TJBench $OUTDIR/$image 95 -rgb -tile -quiet -benchtime 0.01 -warmup 0 ${dctarg} $YUVARG $PROGARG
		for samp in GRAY 444; do
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp -i 54:54 $i $OUTDIR/${basename}_${samp}_${dct}_djpeg.bmp
				rm $i
			done
		done
		runme "$JAVA" $JAVAARGS TJBench $OUTDIR/$image 95 -rgb -tile -quiet -benchtime 0.01 -warmup 0 -fastupsample ${dctarg} $YUVARG $PROGARG
		for samp in 420 422; do
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp -i 54:54 $i $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.bmp
				rm $i
			done
		done

		# Tiled decompression
		for samp in GRAY 444; do
			runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -tile -quiet -benchtime 0.01 -warmup 0 ${dctarg} $YUVARG $PROGARG
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp -i 54:54 $i $OUTDIR/${basename}_${samp}_${dct}_djpeg.bmp
				rm $i
			done
		done
		for samp in 420 422; do
			runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -tile -quiet -benchtime 0.01 -warmup 0 -fastupsample ${dctarg} $YUVARG $PROGARG
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp $i -i 54:54 $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.bmp
				rm $i
			done
		done
	done

	# Scaled decompression
	for scale in 2_1 15_8 7_4 13_8 3_2 11_8 5_4 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8; do
		scalearg=`echo $scale | sed 's/\_/\//g'`
		for samp in GRAY 420 422 444; do
			runme $EXEDIR/djpeg -rgb -scale ${scalearg} $NSARG -bmp -outfile $OUTDIR/${basename}_${samp}_${scale}_djpeg.bmp $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
			runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -scale ${scalearg} -quiet -benchtime 0.01 -warmup 0 $YUVARG $PROGARG
			runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_Q95_${scale}.bmp $OUTDIR/${basename}_${samp}_${scale}_djpeg.bmp
			rm $OUTDIR/${basename}_${samp}_Q95_${scale}.bmp
		done
	done

	# Transforms
	for samp in GRAY 420 422 444; do
		runme $EXEDIR/jpegtran -flip horizontal -trim -outfile $OUTDIR/${basename}_${samp}_hflip_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
		runme $EXEDIR/jpegtran -flip vertical -trim -outfile $OUTDIR/${basename}_${samp}_vflip_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
		runme $EXEDIR/jpegtran -transpose -trim -outfile $OUTDIR/${basename}_${samp}_transpose_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
		runme $EXEDIR/jpegtran -transverse -trim -outfile $OUTDIR/${basename}_${samp}_transverse_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
		runme $EXEDIR/jpegtran -rotate 90 -trim -outfile $OUTDIR/${basename}_${samp}_rot90_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
		runme $EXEDIR/jpegtran -rotate 180 -trim -outfile $OUTDIR/${basename}_${samp}_rot180_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
		runme $EXEDIR/jpegtran -rotate 270 -trim -outfile $OUTDIR/${basename}_${samp}_rot270_jpegtran.jpg $OUTDIR/${basename}_${samp}_Q95.jpg
	done
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444; do
			runme $EXEDIR/djpeg -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -$xform -tile -quiet -benchtime 0.01 -warmup 0 $YUVARG $PROGARG
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp -i 54:54 $i $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp
				rm $i
			done
		done
		for samp in 420 422; do
			runme $EXEDIR/djpeg -nosmooth -rgb -bmp -outfile $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -$xform -tile -quiet -benchtime 0.01 -warmup 0 -fastupsample $YUVARG $PROGARG
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp -i 54:54 $i $OUTDIR/${basename}_${samp}_${xform}_jpegtran.bmp
				rm $i
			done
		done
	done

	# Grayscale transform
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444 422 420; do
			runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -$xform -tile -quiet -benchtime 0.01 -warmup 0 -grayscale $YUVARG $PROGARG
			for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].bmp \
				$OUTDIR/${basename}_${samp}_Q95_full.bmp; do
				runme cmp -i 54:54 $i $OUTDIR/${basename}_GRAY_${xform}_jpegtran.bmp
				rm $i
			done
		done
	done

	# Transforms with scaling
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444 422 420; do
			for scale in 2_1 15_8 7_4 13_8 3_2 11_8 5_4 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8; do
				scalearg=`echo $scale | sed 's/\_/\//g'`
				runme $EXEDIR/djpeg -rgb -scale ${scalearg} $NSARG -bmp -outfile $OUTDIR/${basename}_${samp}_${xform}_${scale}_jpegtran.bmp $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
				runme "$JAVA" $JAVAARGS TJBench $OUTDIR/${basename}_${samp}_Q95.jpg -$xform -scale ${scalearg} -quiet -benchtime 0.01 -warmup 0 $YUVARG $PROGARG
				runme cmp -i 54:54 $OUTDIR/${basename}_${samp}_Q95_${scale}.bmp $OUTDIR/${basename}_${samp}_${xform}_${scale}_jpegtran.bmp
				rm $OUTDIR/${basename}_${samp}_Q95_${scale}.bmp
			done
		done
	done

done

echo SUCCESS!
