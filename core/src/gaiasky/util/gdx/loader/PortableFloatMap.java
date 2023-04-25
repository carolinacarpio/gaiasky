/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.loader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class PortableFloatMap {

    /** The width of the image. Must be positive. */
    public int width;

    /** The height of the image. Must be positive. */
    public int height;

    /** The mode of the image, color/grayscale. Must not be {@code null}. */
    public Mode mode;

    /**
     * The pixels in the image, in row-major order from bottom to top. Must not be {@code null}. Grayscale images use 1 number per pixel.
     * Color images use 3 numbers per pixel, in RGB order. (These details correspond with the low-level file format.)
     * <p>For grayscale, the array length must equal (<var>width</var> &times; <var>height</var>);
     * for color it must equal (<var>width</var> &times; <var>height</var> &times; 3).
     * The width, height, and mode must not make the pixel array length exceed {@code Integer.MAX_VALUE}.</p>
     */
    public float[] pixels;

    /**
     * Indicates whether the image read was in big endian, or indicates whether to write the image
     * in big endian. Big endian is preferred for Java, while little endian is preferred for C/C++.
     */
    public boolean bigEndian;

    /**
     * Constructs a blank Portable FloatMap image.
     */
    public PortableFloatMap() {
        bigEndian = true;  // Arbitrary default
    }

    /**
     * Constructs a PFM image by reading from the specified file. All 5 fields are set to values from the file.
     *
     * @param file the file to read from
     *
     * @throws NullPointerException     if the file is {@code null}
     * @throws IllegalArgumentException if the file data does not represent a valid PFM file
     * @throws IOException              if an I/O exception occurred
     */
    public PortableFloatMap(File file) throws IOException {
        Objects.requireNonNull(file);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            read(in);
        }
    }

    /**
     * Constructs a PFM image by reading from the specified input stream. All 5 fields are set to values from the stream.
     * <p>The stream is not closed by this method. If successful, only the exact number of bytes for the image are read -
     * no extra bytes are read past the end of the image, so it is possible to continue reading from the stream for other purposes.
     * Otherwise if the file format is detected to be invalid or an I/O exception occurs, then an undetermined number of bytes will have been read.</p>
     *
     * @param in the input stream to read from
     *
     * @throws NullPointerException     if the stream is {@code null}
     * @throws IllegalArgumentException if the stream data does not represent a valid PFM file
     * @throws IOException              if an I/O exception occurred
     */
    public PortableFloatMap(InputStream in) throws IOException {
        Objects.requireNonNull(in);
        read(in);
    }



    /*---- Methods ----*/

    private static String readLine(InputStream in) throws IOException {
        byte[] buf = new byte[100];
        for (int i = 0; i < buf.length; i++) {
            int b = in.read();
            if (b == '\n' || b == -1)
                return new String(buf, 0, i, StandardCharsets.US_ASCII);
            else
                buf[i] = (byte) b;
        }
        throw new IllegalArgumentException("Line too long");
    }

    private void read(InputStream in) throws IOException {
        // Parse file magic header line
        String format = readLine(in);
        if (format.equals("PF"))
            mode = Mode.COLOR;
        else if (format.equals("Pf"))
            mode = Mode.GRAYSCALE;
        else
            throw new IllegalArgumentException("Unrecognized format: " + format);

        // Parse width and height line
        String[] tokens = readLine(in).split(" ", 2);
        if (tokens.length != 2)
            throw new IllegalArgumentException("Invalid dimensions");
        try {
            width = Integer.parseInt(tokens[0]);
            height = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("Width and height must be positive");

        // Parse endianness line
        double temp = Double.parseDouble(readLine(in));
        if (temp >= 0)
            bigEndian = true;
        else if (temp < 0)
            bigEndian = false;
        else
            throw new IllegalArgumentException("Unrecognized format: " + format);

        // Read float32 image pixel data
        DataInput din = new DataInputStream(in);
        pixels = new float[calcPixelArrayLength()];
        if (bigEndian) {
            for (int i = 0; i < pixels.length; i++)
                pixels[i] = din.readFloat();
        } else {
            for (int i = 0; i < pixels.length; i++)
                pixels[i] = Float.intBitsToFloat(Integer.reverseBytes(din.readInt()));
        }
    }

    /**
     * Writes this PFM image to the specified file.
     *
     * @param file the file to write to
     *
     * @throws NullPointerException     if the file or mode or pixel array is {@code null}
     * @throws IllegalStateException    if the width or height is zero/negative, or the pixel array is not exactly the expected length
     * @throws IllegalArgumentException if the width, height, and mode imply a pixel array length that exceeds {@code Integer.MAX_VALUE}
     * @throws IOException              if an I/O exception occurred
     */
    public void write(File file) throws IOException {
        Objects.requireNonNull(file);
        checkData();  // Check before opening file
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            write(out);
        }
    }

    /**
     * Writes this PFM image to the specified output stream.
     *
     * @param out the output stream to write to
     *
     * @throws NullPointerException     if the stream or mode or pixel array is {@code null}
     * @throws IllegalStateException    if the width or height is zero/negative, or the pixel array is not exactly the expected length
     * @throws IllegalArgumentException if the width, height, and mode imply a pixel array length that exceeds {@code Integer.MAX_VALUE}
     * @throws IOException              if an I/O exception occurred
     */
    public void write(OutputStream out) throws IOException {
        Objects.requireNonNull(out);
        checkData();

        // Write header text data. Must use Unix newlines, not universal style
        PrintWriter pout = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.US_ASCII));
        switch (mode) {
        case COLOR -> pout.print("PF\n");
        case GRAYSCALE -> pout.print("Pf\n");
        default -> throw new AssertionError();
        }
        pout.print(width + " " + height + "\n");
        pout.print((bigEndian ? "1.0" : "-1.0") + "\n");
        pout.flush();
        // Detach the PrintWriter stream

        // Write float32 image pixel data
        DataOutput dataOutput = new DataOutputStream(out);
        if (bigEndian) {
            for (float x : pixels)
                dataOutput.writeFloat(x);
        } else {
            for (float x : pixels)
                dataOutput.writeInt(Integer.reverseBytes(Float.floatToIntBits(x)));
        }
    }

    private void checkData() {
        if (width <= 0)
            throw new IllegalStateException("Width must be positive");
        if (height <= 0)
            throw new IllegalStateException("Height must be positive");
        Objects.requireNonNull(mode, "Mode not set");
        Objects.requireNonNull(pixels, "Pixel array not set");
        if (pixels.length != calcPixelArrayLength())
            throw new IllegalStateException("Pixel array length does not match width and height");
    }

    private int calcPixelArrayLength() {
        int channels = switch (mode) {
            case COLOR -> 3;
            case GRAYSCALE -> 1;
            default -> throw new AssertionError();
        };
        if (Integer.MAX_VALUE / width / height / channels == 0)
            throw new IllegalArgumentException("Dimensions are too large to make a pixel array");
        else
            return width * height * channels;  // Guaranteed to not overflow
    }

    public enum Mode {
        COLOR,
        GRAYSCALE
    }

}

