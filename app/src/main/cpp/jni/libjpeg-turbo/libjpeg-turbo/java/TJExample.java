/*
 * Copyright (C)2011-2012, 2014-2015, 2017-2018 D. R. Commander.
 *                                              All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of the libjpeg-turbo Project nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS",
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This program demonstrates how to compress, decompress, and transform JPEG
 * images using the TurboJPEG Java API
 */

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.nio.*;
import javax.imageio.*;
import javax.swing.*;
import org.libjpegturbo.turbojpeg.*;


@SuppressWarnings("checkstyle:JavadocType")
class TJExample implements TJCustomFilter {

  static final String CLASS_NAME =
    new TJExample().getClass().getName();

  static final int DEFAULT_SUBSAMP = TJ.SAMP_444;
  static final int DEFAULT_QUALITY = 95;


  static final String[] SUBSAMP_NAME = {
    "4:4:4", "4:2:2", "4:2:0", "Grayscale", "4:4:0", "4:1:1"
  };

  static final String[] COLORSPACE_NAME = {
    "RGB", "YCbCr", "GRAY", "CMYK", "YCCK"
  };


  /* DCT filter example.  This produces a negative of the image. */

  @SuppressWarnings("checkstyle:JavadocMethod")
  public void customFilter(ShortBuffer coeffBuffer, Rectangle bufferRegion,
                           Rectangle planeRegion, int componentIndex,
                           int transformIndex, TJTransform transform)
                           throws TJException {
    for (int i = 0; i < bufferRegion.width * bufferRegion.height; i++) {
      coeffBuffer.put(i, (short)(-coeffBuffer.get(i)));
    }
  }


  static void usage() throws Exception {
    System.out.println("\nUSAGE: java [Java options] " + CLASS_NAME +
                       " <Input image> <Output image> [options]\n");

    System.out.println("Input and output images can be in any image format that the Java Image I/O");
    System.out.println("extensions understand.  If either filename ends in a .jpg extension, then");
    System.out.println("the TurboJPEG API will be used to compress or decompress the image.\n");

    System.out.println("Compression Options (used if the output image is a JPEG image)");
    System.out.println("--------------------------------------------------------------\n");

    System.out.println("-subsamp <444|422|420|gray> = Apply this level of chrominance subsampling when");
    System.out.println("     compressing the output image.  The default is to use the same level of");
    System.out.println("     subsampling as in the input image, if the input image is also a JPEG");
    System.out.println("     image, or to use grayscale if the input image is a grayscale non-JPEG");
    System.out.println("     image, or to use " +
                       SUBSAMP_NAME[DEFAULT_SUBSAMP] +
                       " subsampling otherwise.\n");

    System.out.println("-q <1-100> = Compress the output image with this JPEG quality level");
    System.out.println("     (default = " + DEFAULT_QUALITY + ").\n");

    System.out.println("Decompression Options (used if the input image is a JPEG image)");
    System.out.println("---------------------------------------------------------------\n");

    System.out.println("-scale M/N = Scale the input image by a factor of M/N when decompressing it.");
    System.out.print("(M/N = ");
    for (int i = 0; i < SCALING_FACTORS.length; i++) {
      System.out.print(SCALING_FACTORS[i].getNum() + "/" +
                       SCALING_FACTORS[i].getDenom());
      if (SCALING_FACTORS.length == 2 && i != SCALING_FACTORS.length - 1)
        System.out.print(" or ");
      else if (SCALING_FACTORS.length > 2) {
        if (i != SCALING_FACTORS.length - 1)
          System.out.print(", ");
        if (i == SCALING_FACTORS.length - 2)
          System.out.print("or ");
      }
    }
    System.out.println(")\n");

    System.out.println("-hflip, -vflip, -transpose, -transverse, -rot90, -rot180, -rot270 =");
    System.out.println("     Perform one of these lossless transform operations on the input image");
    System.out.println("     prior to decompressing it (these options are mutually exclusive.)\n");

    System.out.println("-grayscale = Perform lossless grayscale conversion on the input image prior");
    System.out.println("     to decompressing it (can be combined with the other transform operations");
    System.out.println("     above.)\n");

    System.out.println("-crop WxH+X+Y = Perform lossless cropping on the input image prior to");
    System.out.println("     decompressing it.  X and Y specify the upper left corner of the cropping");
    System.out.println("     region, and W and H specify the width and height of the cropping region.");
    System.out.println("     X and Y must be evenly divible by the MCU block size (8x8 if the input");
    System.out.println("     image was compressed using no subsampling or grayscale, 16x8 if it was");
    System.out.println("     compressed using 4:2:2 subsampling, or 16x16 if it was compressed using");
    System.out.println("     4:2:0 subsampling.)\n");

    System.out.println("General Options");
    System.out.println("---------------\n");

    System.out.println("-display = Display output image (Output filename need not be specified in this");
    System.out.println("     case.)\n");

    System.out.println("-fastupsample = Use the fastest chrominance upsampling algorithm available in");
    System.out.println("     the underlying codec.\n");

    System.out.println("-fastdct = Use the fastest DCT/IDCT algorithms available in the underlying");
    System.out.println("     codec.\n");

    System.out.println("-accuratedct = Use the most accurate DCT/IDCT algorithms available in the");
    System.out.println("     underlying codec.\n");

    System.exit(1);
  }


  public static void main(String[] argv) {

    try {

      TJScalingFactor scalingFactor = new TJScalingFactor(1, 1);
      int outSubsamp = -1, outQual = -1;
      TJTransform xform = new TJTransform();
      boolean display = false;
      int flags = 0;
      int width, height;
      String inFormat = "jpg", outFormat = "jpg";
      BufferedImage img = null;
      byte[] imgBuf = null;

      if (argv.length < 2)
        usage();

      if (argv[1].substring(0, 2).equalsIgnoreCase("-d"))
        display = true;

      /* Parse arguments. */
      for (int i = 2; i < argv.length; i++) {
        if (argv[i].length() < 2)
          continue;
        else if (argv[i].length() > 2 &&
                 argv[i].substring(0, 3).equalsIgnoreCase("-sc") &&
                 i < argv.length - 1) {
          int match = 0;
          String[] scaleArg = argv[++i].split("/");
          if (scaleArg.length == 2) {
            TJScalingFactor tempsf =
              new TJScalingFactor(Integer.parseInt(scaleArg[0]),
                                  Integer.parseInt(scaleArg[1]));
            for (int j = 0; j < SCALING_FACTORS.length; j++) {
              if (tempsf.equals(SCALING_FACTORS[j])) {
                scalingFactor = SCALING_FACTORS[j];
                match = 1;
                break;
              }
            }
          }
          if (match != 1)
            usage();
        } else if (argv[i].length() > 2 &&
                   argv[i].substring(0, 3).equalsIgnoreCase("-su") &&
                   i < argv.length - 1) {
          i++;
          if (argv[i].substring(0, 1).equalsIgnoreCase("g"))
            outSubsamp = TJ.SAMP_GRAY;
          else if (argv[i].equals("444"))
            outSubsamp = TJ.SAMP_444;
          else if (argv[i].equals("422"))
            outSubsamp = TJ.SAMP_422;
          else if (argv[i].equals("420"))
            outSubsamp = TJ.SAMP_420;
          else
            usage();
        } else if (argv[i].substring(0, 2).equalsIgnoreCase("-q") &&
                   i < argv.length - 1) {
          outQual = Integer.parseInt(argv[++i]);
          if (outQual < 1 || outQual > 100)
            usage();
        } else if (argv[i].substring(0, 2).equalsIgnoreCase("-g"))
          xform.options |= TJTransform.OPT_GRAY;
        else if (argv[i].equalsIgnoreCase("-hflip"))
          xform.op = TJTransform.OP_HFLIP;
        else if (argv[i].equalsIgnoreCase("-vflip"))
          xform.op = TJTransform.OP_VFLIP;
        else if (argv[i].equalsIgnoreCase("-transpose"))
          xform.op = TJTransform.OP_TRANSPOSE;
        else if (argv[i].equalsIgnoreCase("-transverse"))
          xform.op = TJTransform.OP_TRANSVERSE;
        else if (argv[i].equalsIgnoreCase("-rot90"))
          xform.op = TJTransform.OP_ROT90;
        else if (argv[i].equalsIgnoreCase("-rot180"))
          xform.op = TJTransform.OP_ROT180;
        else if (argv[i].equalsIgnoreCase("-rot270"))
          xform.op = TJTransform.OP_ROT270;
        else if (argv[i].equalsIgnoreCase("-custom"))
          xform.cf = new TJExample();
        else if (argv[i].length() > 2 &&
                 argv[i].substring(0, 2).equalsIgnoreCase("-c") &&
                 i < argv.length - 1) {
          String[] cropArg = argv[++i].split("[x\\+]");
          if (cropArg.length != 4)
            usage();
          xform.width = Integer.parseInt(cropArg[0]);
          xform.height = Integer.parseInt(cropArg[1]);
          xform.x = Integer.parseInt(cropArg[2]);
          xform.y = Integer.parseInt(cropArg[3]);
          if (xform.x < 0 || xform.y < 0 || xform.width < 1 ||
              xform.height < 1)
            usage();
          xform.options |= TJTransform.OPT_CROP;
        } else if (argv[i].substring(0, 2).equalsIgnoreCase("-d"))
          display = true;
        else if (argv[i].equalsIgnoreCase("-fastupsample")) {
          System.out.println("Using fast upsampling code");
          flags |= TJ.FLAG_FASTUPSAMPLE;
        } else if (argv[i].equalsIgnoreCase("-fastdct")) {
          System.out.println("Using fastest DCT/IDCT algorithm");
          flags |= TJ.FLAG_FASTDCT;
        } else if (argv[i].equalsIgnoreCase("-accuratedct")) {
          System.out.println("Using most accurate DCT/IDCT algorithm");
          flags |= TJ.FLAG_ACCURATEDCT;
        } else usage();
      }

      /* Determine input and output image formats based on file extensions. */
      String[] inFileTokens = argv[0].split("\\.");
      if (inFileTokens.length > 1)
        inFormat = inFileTokens[inFileTokens.length - 1];
      String[] outFileTokens;
      if (display)
        outFormat = "bmp";
      else {
        outFileTokens = argv[1].split("\\.");
        if (outFileTokens.length > 1)
          outFormat = outFileTokens[outFileTokens.length - 1];
      }

      if (inFormat.equalsIgnoreCase("jpg")) {
        /* Input image is a JPEG image.  Decompress and/or transform it. */
        boolean doTransform = (xform.op != TJTransform.OP_NONE ||
                               xform.options != 0 || xform.cf != null);

        /* Read the JPEG file into memory. */
        File jpegFile = new File(argv[0]);
        FileInputStream fis = new FileInputStream(jpegFile);
        int jpegSize = fis.available();
        if (jpegSize < 1) {
          System.out.println("Input file contains no data");
          System.exit(1);
        }
        byte[] jpegBuf = new byte[jpegSize];
        fis.read(jpegBuf);
        fis.close();

        TJDecompressor tjd;
        if (doTransform) {
          /* Transform it. */
          TJTransformer tjt = new TJTransformer(jpegBuf);
          TJTransform[] xforms = new TJTransform[1];
          xforms[0] = xform;
          xforms[0].options |= TJTransform.OPT_TRIM;
          TJDecompressor[] tjds = tjt.transform(xforms, 0);
          tjd = tjds[0];
          tjt.close();
        } else
          tjd = new TJDecompressor(jpegBuf);

        width = tjd.getWidth();
        height = tjd.getHeight();
        int inSubsamp = tjd.getSubsamp();
        int inColorspace = tjd.getColorspace();

        System.out.println((doTransform ? "Transformed" : "Input") +
                           " Image (jpg):  " + width + " x " + height +
                           " pixels, " + SUBSAMP_NAME[inSubsamp] +
                           " subsampling, " + COLORSPACE_NAME[inColorspace]);

        if (outFormat.equalsIgnoreCase("jpg") && doTransform &&
            scalingFactor.isOne() && outSubsamp < 0 && outQual < 0) {
          /* Input image has been transformed, and no re-compression options
             have been selected.  Write the transformed image to disk and
             exit. */
          File outFile = new File(argv[1]);
          FileOutputStream fos = new FileOutputStream(outFile);
          fos.write(tjd.getJPEGBuf(), 0, tjd.getJPEGSize());
          fos.close();
          System.exit(0);
        }

        /* Scaling and/or a non-JPEG output image format and/or compression
           options have been selected, so we need to decompress the
           input/transformed image. */
        width = scalingFactor.getScaled(width);
        height = scalingFactor.getScaled(height);
        if (outSubsamp < 0)
          outSubsamp = inSubsamp;

        if (!outFormat.equalsIgnoreCase("jpg"))
          img = tjd.decompress(width, height, BufferedImage.TYPE_INT_RGB,
                               flags);
        else
          imgBuf = tjd.decompress(width, 0, height, TJ.PF_BGRX, flags);
        tjd.close();
      } else {
        /* Input image is not a JPEG image.  Load it into memory. */
        img = ImageIO.read(new File(argv[0]));
        if (img == null)
          throw new Exception("Input image type not supported.");
        width = img.getWidth();
        height = img.getHeight();
        if (outSubsamp < 0) {
          if (img.getType() == BufferedImage.TYPE_BYTE_GRAY)
            outSubsamp = TJ.SAMP_GRAY;
          else
            outSubsamp = DEFAULT_SUBSAMP;
        }
        System.out.println("Input Image:  " + width + " x " + height +
                           " pixels");
      }
      System.gc();
      if (!display)
        System.out.print("Output Image (" + outFormat + "):  " + width +
                         " x " + height + " pixels");

      if (display) {
        /* Display the uncompressed image */
        ImageIcon icon = new ImageIcon(img);
        JLabel label = new JLabel(icon, JLabel.CENTER);
        JOptionPane.showMessageDialog(null, label, "Output Image",
                                      JOptionPane.PLAIN_MESSAGE);
      } else if (outFormat.equalsIgnoreCase("jpg")) {
        /* Output image format is JPEG.  Compress the uncompressed image. */
        if (outQual < 0)
          outQual = DEFAULT_QUALITY;
        System.out.println(", " + SUBSAMP_NAME[outSubsamp] +
                           " subsampling, quality = " + outQual);

        TJCompressor tjc = new TJCompressor();
        tjc.setSubsamp(outSubsamp);
        tjc.setJPEGQuality(outQual);
        if (img != null)
          tjc.setSourceImage(img, 0, 0, 0, 0);
        else
          tjc.setSourceImage(imgBuf, 0, 0, width, 0, height, TJ.PF_BGRX);
        byte[] jpegBuf = tjc.compress(flags);
        int jpegSize = tjc.getCompressedSize();
        tjc.close();

        /* Write the JPEG image to disk. */
        File outFile = new File(argv[1]);
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(jpegBuf, 0, jpegSize);
        fos.close();
      } else {
        /* Output image format is not JPEG.  Save the uncompressed image
           directly to disk. */
        System.out.print("\n");
        File outFile = new File(argv[1]);
        ImageIO.write(img, outFormat, outFile);
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
  }

  static final TJScalingFactor[] SCALING_FACTORS =
    TJ.getScalingFactors();
};
