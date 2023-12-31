/*
    Copyright © 2021, 2022 Stephen R Chadfield.

    This file is part of Shogi Explorer.

    Shogi Explorer is free software: you can redistribute it and/or modify it under the terms of the 
    GNU General Public License as published by the Free Software Foundation, either version 3 
    of the License, or (at your option) any later version.

    Shogi Explorer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
    See the GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along with Shogi Explorer. 
    If not, see <https://www.gnu.org/licenses/>.
 */
package com.chadfield.shogiexplorer.utils;

import java.awt.Image;
import java.awt.image.BaseMultiResolutionImage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import com.chadfield.shogiexplorer.objects.Coordinate;
import com.chadfield.shogiexplorer.objects.Dimension;
import com.chadfield.shogiexplorer.objects.ImageCache;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

public class ImageUtils {

    private static final String OS = System.getProperty("os.name").toLowerCase();
    public static final boolean IS_WINDOWS = (OS.contains("win"));
    public static final boolean IS_MAC = (OS.contains("mac"));
    public static final boolean IS_LINUX = (OS.contains("nux"));

    private ImageUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static JLabel getPieceLabelForKoma(Image image, Coordinate boardCoord, Dimension offset, Coordinate imageLocation) {
        JLabel pieceLabel = new JLabel(new ImageIcon(image));
        pieceLabel.setBounds(
                imageLocation.getX() + (boardCoord.getX() * MathUtils.KOMA_X + offset.getWidth()),
                imageLocation.getY() + (boardCoord.getY() * MathUtils.KOMA_Y + offset.getHeight()),
                MathUtils.KOMA_X,
                MathUtils.KOMA_Y);
        return pieceLabel;
    }

    public static JLabel getPieceLabelForKoma(Image image, Coordinate boardCoord, Dimension offset, Coordinate imageLocation, double scale) {
        JLabel pieceLabel = new JLabel(new ImageIcon(image));
        pieceLabel.setBounds(
                (int) Math.round((imageLocation.getX() + (boardCoord.getX() * MathUtils.KOMA_X + offset.getWidth())) * scale),
                (int) Math.round((imageLocation.getY() + (boardCoord.getY() * MathUtils.KOMA_Y + offset.getHeight())) * scale),
                (int) Math.round(MathUtils.KOMA_X * scale),
                (int) Math.round(MathUtils.KOMA_Y * scale));
        return pieceLabel;
    }

    public static JLabel getTextLabelForBan(Coordinate boardCoord, Dimension offset, Coordinate imageLocation, String text, double scale) {
        JLabel numberLabel = new JLabel(text);
        numberLabel.setBounds(
                (int) Math.round((imageLocation.getX() + (boardCoord.getX() * MathUtils.KOMA_X + offset.getWidth())) * scale),
                (int) Math.round((imageLocation.getY() + (boardCoord.getY() * MathUtils.KOMA_Y + offset.getHeight())) * scale),
                (int) Math.round(MathUtils.KOMA_X * scale),
                (int) Math.round(MathUtils.KOMA_Y * scale));
        Font labelFont = numberLabel.getFont();
        if (IS_LINUX) {
            numberLabel.setFont(new Font("Mincho", Font.BOLD, labelFont.getSize()));
        } else {
            numberLabel.setFont(new Font(labelFont.getName(), Font.BOLD, labelFont.getSize()));
        }
        return numberLabel;
    }

    public static BaseMultiResolutionImage loadPNGImageFromResources(String imageName) {
        BufferedImage image1 = null;
        BufferedImage image2 = null;
        BufferedImage image3 = null;
        BufferedImage image4 = null;

        try {
            image1 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + ".png"));
            image2 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "@1.25x.png"));
            image3 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "@1.5x.png"));
            image4 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "@2x.png"));
        } catch (IOException ex) {
            Logger.getLogger(ImageUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        BaseMultiResolutionImage mri = new BaseMultiResolutionImage(image1, image2, image3, image4);
        return (mri);

    }

    public static BaseMultiResolutionImage loadSVGImageFromResources(String imageName, Dimension imageDimension, double scale) {
        BufferedImage image1 = null;
        BufferedImage image2 = null;
        BufferedImage image3 = null;
        BufferedImage image4 = null;

        try {
            InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(imageName + ".svg");
            image1 = transcodeSVGToBufferedImage(inputStream, imageDimension.getWidth() * scale);
            inputStream.close();
            inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(imageName + ".svg");
            image2 = transcodeSVGToBufferedImage(inputStream, (imageDimension.getWidth() * 1.25) * scale);
            inputStream.close();
            inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(imageName + ".svg");
            image3 = transcodeSVGToBufferedImage(inputStream, (imageDimension.getWidth() * 1.5) * scale);
            inputStream.close();
            inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(imageName + ".svg");
            image4 = transcodeSVGToBufferedImage(inputStream, (imageDimension.getWidth() * 2.0) * scale);
            inputStream.close();
        } catch (TranscoderException | IOException ex) {
            Logger.getLogger(ImageUtils.class.getName()).log(Level.SEVERE, null, ex);
        }

        BaseMultiResolutionImage mri = new BaseMultiResolutionImage(image1, image2, image3, image4);
        return (mri);
    }

    public static BufferedImage transcodeSVGToBufferedImage(InputStream inputStream, double width) throws TranscoderException {
        // Create a PNG transcoder.
        PNGTranscoder t = new PNGTranscoder();

        // Set the transcoding hints.
        t.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, (float) width);

        try {
            // Create the transcoder input.
            TranscoderInput input = new TranscoderInput(inputStream);

            // Create the transcoder output.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            TranscoderOutput output = new TranscoderOutput(outputStream);

            // Save the image.
            t.transcode(input, output);

            // Flush and close the stream.
            outputStream.flush();
            outputStream.close();

            // Convert the byte stream into an image.
            byte[] imgData = outputStream.toByteArray();
            return ImageIO.read(new ByteArrayInputStream(imgData));

        } catch (IOException | TranscoderException ex) {
            Logger.getLogger(ImageUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static Image loadIconImageFromResources(String imageName) {
        Image image1 = null;
        Image image2 = null;
        try {
            image1 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_128x128.png"));
            image2 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_256x256.png"));
        } catch (IOException ex) {
            Logger.getLogger(ImageUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        BaseMultiResolutionImage mri = new BaseMultiResolutionImage(image1, image2);
        return (mri);
    }

    public static Image loadTaskbarImageFromResources(String imageName) {
        Image image1 = null;
        Image image2 = null;
        Image image3 = null;
        Image image4 = null;
        Image image5 = null;
        Image image6 = null;
        try {
            image1 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_16x16.png"));
            image2 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_32x32.png"));
            image3 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_64x64.png"));
            image4 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_128x128.png"));
            image5 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_256x256.png"));
            image6 = ImageIO.read(ClassLoader.getSystemClassLoader().getResource(imageName + "_512x512.png"));

        } catch (IOException ex) {
            Logger.getLogger(ImageUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        BaseMultiResolutionImage mri = new BaseMultiResolutionImage(image1, image2, image3, image4, image5, image6);
        return (mri);
    }

    public static void drawLabel(JPanel boardPanel, Coordinate imageCoordinate, Dimension imageDimension, Coordinate offset, Color color, double scale) {
        JLabel imageLabel = new JLabel();
        imageLabel.setBounds(
                (int) Math.round((offset.getX() + imageCoordinate.getX()) * scale),
                (int) Math.round((offset.getY() + imageCoordinate.getY()) * scale),
                (int) Math.round((imageDimension.getWidth()) * scale),
                (int) Math.round((imageDimension.getHeight()) * scale));
        imageLabel.setBackground(color);
        imageLabel.setOpaque(true);
        boardPanel.add(imageLabel);
    }

    public static void drawPNGImage(ImageCache imageCache, JPanel boardPanel, String imageName, Coordinate imageCoordinate, Dimension imageDimension, Coordinate offset, double scale) {
        BaseMultiResolutionImage imageFile = imageCache.getImage(imageName + scale);
        if (imageFile == null) {
            imageFile = loadPNGImageFromResources(imageName);
            imageCache.putImage(imageName + scale, imageFile);
        }
        JLabel imageLabel = new JLabel(new ImageIcon(imageFile));
        imageLabel.setBounds(
                (int) Math.round((offset.getX() + imageCoordinate.getX()) * scale),
                (int) Math.round((offset.getY() + imageCoordinate.getY()) * scale),
                (int) Math.round((imageDimension.getWidth()) * scale),
                (int) Math.round((imageDimension.getHeight()) * scale));
        boardPanel.add(imageLabel);
    }

    public static void drawImage(ImageCache imageCache, JPanel boardPanel, String imageName, Coordinate imageCoordinate, Dimension imageDimension, Coordinate offset, double scale) {
        BaseMultiResolutionImage imageFile = imageCache.getImage(imageName + scale);
        if (imageFile == null) {
            imageFile = loadSVGImageFromResources(imageName, imageDimension, scale);
            imageCache.putImage(imageName + scale, imageFile);
        }
        JLabel imageLabel = new JLabel(new ImageIcon(imageFile));
        imageLabel.setBounds(
                (int) Math.round((offset.getX() + imageCoordinate.getX()) * scale),
                (int) Math.round((offset.getY() + imageCoordinate.getY()) * scale),
                (int) Math.round((imageDimension.getWidth()) * scale),
                (int) Math.round((imageDimension.getHeight()) * scale));
        boardPanel.add(imageLabel);
    }
}
