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
	$*
}

EXT=bmp
IMAGES="vgl_5674_0098.${EXT} vgl_6434_0018a.${EXT} vgl_6548_0026a.${EXT} nightshot_iso_100.${EXT}"
IMGDIR=@CMAKE_CURRENT_SOURCE_DIR@/testimages
OUTDIR=`mktemp -d /tmp/__tjbenchtest_output.XXXXXX`
EXEDIR=@CMAKE_CURRENT_BINARY_DIR@
BMPARG=
NSARG=
YUVARG=
ALLOC=0
ALLOCARG=
PROGARG=
if [ "$EXT" = "bmp" ]; then BMPARG=-bmp; fi

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
		IMAGES="vgl_6548_0026a.${EXT}"
		;;
	-alloc)
		ALLOCARG=-alloc
		ALLOC=1
		;;
	-progressive)
		PROGARG=-progressive
		;;
	esac
	shift
done

exec >$EXEDIR/tjbenchtest$YUVARG$ALLOCARG$PROGARG.log

# Standard tests
for image in $IMAGES; do

	cp $IMGDIR/$image $OUTDIR
	basename=`basename $image .${EXT}`
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -grayscale -outfile $OUTDIR/${basename}_GRAY_fast_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -sample 2x2 -outfile $OUTDIR/${basename}_420_fast_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -sample 2x1 -outfile $OUTDIR/${basename}_422_fast_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct fast $PROGARG -sample 1x1 -outfile $OUTDIR/${basename}_444_fast_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -grayscale -outfile $OUTDIR/${basename}_GRAY_accurate_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -sample 2x2 -outfile $OUTDIR/${basename}_420_accurate_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -sample 2x1 -outfile $OUTDIR/${basename}_422_accurate_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	runme $EXEDIR/cjpeg -quality 95 -dct int $PROGARG -sample 1x1 -outfile $OUTDIR/${basename}_444_accurate_cjpeg.jpg $IMGDIR/${basename}.${EXT}
	for samp in GRAY 420 422 444; do
		runme $EXEDIR/djpeg -rgb $NSARG $BMPARG -outfile $OUTDIR/${basename}_${samp}_default_djpeg.${EXT} $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct fast -rgb $NSARG $BMPARG -outfile $OUTDIR/${basename}_${samp}_fast_djpeg.${EXT} $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct int -rgb $NSARG $BMPARG -outfile $OUTDIR/${basename}_${samp}_accurate_djpeg.${EXT} $OUTDIR/${basename}_${samp}_accurate_cjpeg.jpg
	done
	for samp in 420 422; do
		runme $EXEDIR/djpeg -nosmooth $BMPARG -outfile $OUTDIR/${basename}_${samp}_default_nosmooth_djpeg.${EXT} $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct fast -nosmooth $BMPARG -outfile $OUTDIR/${basename}_${samp}_fast_nosmooth_djpeg.${EXT} $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
		runme $EXEDIR/djpeg -dct int -nosmooth $BMPARG -outfile $OUTDIR/${basename}_${samp}_accurate_nosmooth_djpeg.${EXT} $OUTDIR/${basename}_${samp}_accurate_cjpeg.jpg
	done

	# Compression
	for dct in accurate fast; do
		runme $EXEDIR/tjbench $OUTDIR/$image 95 -rgb -quiet -benchtime 0.01 -warmup 0 -${dct}dct $YUVARG $ALLOCARG $PROGARG
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
		runme $EXEDIR/tjbench $OUTDIR/$image 95 -rgb -tile -quiet -benchtime 0.01 -warmup 0 ${dctarg} $YUVARG $ALLOCARG $PROGARG
		for samp in GRAY 444; do
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_${samp}_${dct}_djpeg.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_${samp}_${dct}_djpeg.${EXT}
					rm $i
				done
			fi
		done
		runme $EXEDIR/tjbench $OUTDIR/$image 95 -rgb -tile -quiet -benchtime 0.01 -warmup 0 -fastupsample ${dctarg} $YUVARG $ALLOCARG $PROGARG
		for samp in 420 422; do
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.${EXT}
					rm $i
				done
			fi
		done

		# Tiled decompression
		for samp in GRAY 444; do
			runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -tile -quiet -benchtime 0.01 -warmup 0 ${dctarg} $YUVARG $ALLOCARG $PROGARG
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_${samp}_${dct}_djpeg.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_${samp}_${dct}_djpeg.${EXT}
					rm $i
				done
			fi
		done
		for samp in 420 422; do
			runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -tile -quiet -benchtime 0.01 -warmup 0 -fastupsample ${dctarg} $YUVARG $ALLOCARG $PROGARG
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_${samp}_${dct}_nosmooth_djpeg.${EXT}
					rm $i
				done
			fi
		done
	done

	# Scaled decompression
	for scale in 2_1 15_8 7_4 13_8 3_2 11_8 5_4 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8; do
		scalearg=`echo $scale | sed 's/\_/\//g'`
		for samp in GRAY 420 422 444; do
			runme $EXEDIR/djpeg -rgb -scale ${scalearg} $NSARG $BMPARG -outfile $OUTDIR/${basename}_${samp}_${scale}_djpeg.${EXT} $OUTDIR/${basename}_${samp}_fast_cjpeg.jpg
			runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -scale ${scalearg} -quiet -benchtime 0.01 -warmup 0 $YUVARG $ALLOCARG $PROGARG
			runme cmp $OUTDIR/${basename}_${samp}_Q95_${scale}.${EXT} $OUTDIR/${basename}_${samp}_${scale}_djpeg.${EXT}
			rm $OUTDIR/${basename}_${samp}_Q95_${scale}.${EXT}
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
			runme $EXEDIR/djpeg -rgb $BMPARG -outfile $OUTDIR/${basename}_${samp}_${xform}_jpegtran.${EXT} $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -$xform -tile -quiet -benchtime 0.01 -warmup 0 $YUVARG $ALLOCARG $PROGARG
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_${samp}_${xform}_jpegtran.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_${samp}_${xform}_jpegtran.${EXT}
					rm $i
				done
			fi
		done
		for samp in 420 422; do
			runme $EXEDIR/djpeg -nosmooth -rgb $BMPARG -outfile $OUTDIR/${basename}_${samp}_${xform}_jpegtran.${EXT} $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
			runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -$xform -tile -quiet -benchtime 0.01 -warmup 0 -fastupsample $YUVARG $ALLOCARG $PROGARG
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_${samp}_${xform}_jpegtran.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_${samp}_${xform}_jpegtran.${EXT}
					rm $i
				done
			fi
		done
	done

	# Grayscale transform
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444 422 420; do
			runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -$xform -tile -quiet -benchtime 0.01 -warmup 0 -grayscale $YUVARG $ALLOCARG $PROGARG
			if [ $ALLOC = 1 ]; then
				runme cmp $OUTDIR/${basename}_${samp}_Q95_full.${EXT} $OUTDIR/${basename}_GRAY_${xform}_jpegtran.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_full.${EXT}
			else
				for i in $OUTDIR/${basename}_${samp}_Q95_[0-9]*[0-9]x[0-9]*[0-9].${EXT} \
					$OUTDIR/${basename}_${samp}_Q95_full.${EXT}; do
					runme cmp $i $OUTDIR/${basename}_GRAY_${xform}_jpegtran.${EXT}
					rm $i
				done
			fi
		done
	done

	# Transforms with scaling
	for xform in hflip vflip transpose transverse rot90 rot180 rot270; do
		for samp in GRAY 444 422 420; do
			for scale in 2_1 15_8 7_4 13_8 3_2 11_8 5_4 9_8 7_8 3_4 5_8 1_2 3_8 1_4 1_8; do
				scalearg=`echo $scale | sed 's/\_/\//g'`
				runme $EXEDIR/djpeg -rgb -scale ${scalearg} $NSARG $BMPARG -outfile $OUTDIR/${basename}_${samp}_${xform}_${scale}_jpegtran.${EXT} $OUTDIR/${basename}_${samp}_${xform}_jpegtran.jpg
				runme $EXEDIR/tjbench $OUTDIR/${basename}_${samp}_Q95.jpg $BMPARG -$xform -scale ${scalearg} -quiet -benchtime 0.01 -warmup 0 $YUVARG $ALLOCARG $PROGARG
				runme cmp $OUTDIR/${basename}_${samp}_Q95_${scale}.${EXT} $OUTDIR/${basename}_${samp}_${xform}_${scale}_jpegtran.${EXT}
				rm $OUTDIR/${basename}_${samp}_Q95_${scale}.${EXT}
			done
		done
	done

done

echo SUCCESS!
