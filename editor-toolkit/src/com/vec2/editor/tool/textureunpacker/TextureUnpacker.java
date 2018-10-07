/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.vec2.editor.tool.textureunpacker;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.vec2.editor.utils.MessageKey;
import com.vec2.editor.utils.Messager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/** Unpacks a texture atlas into individual image files.
 * @author Geert Konijnendijk
 * @author Nathan Sweet
 * @author Michael Bazos */
public class TextureUnpacker {
	private static final String DEFAULT_OUTPUT_PATH = "output";
	private static final int NINEPATCH_PADDING = 1;
	private static final String OUTPUT_TYPE = "png";
	private boolean quiet;


	/** Splits an atlas into seperate image and ninepatch files. */
	public void splitAtlas (TextureAtlasData atlas, String outputDir) {
		// create the output directory if it did not exist yet
		File outputDirFile = new File(outputDir);
		if (!outputDirFile.exists()) {
			outputDirFile.mkdirs();
			String string = String.format("Creating directory: %s", outputDirFile.getPath());
			if (!quiet) {
				System.out.println(string);
			}
			Messager.send(string,MessageKey.PROCESSING);
		}

		for (Page page : atlas.getPages()) {
			// load the image file belonging to this page as a Buffered Image
			File file = page.textureFile.file();
			if (!file.exists()) throw new RuntimeException("Unable to find atlas image: " + file.getAbsolutePath());
			BufferedImage img = null;
			try {
				img = ImageIO.read(file);
			} catch (IOException e) {
				printExceptionAndExit(e);
			}
			for (Region region : atlas.getRegions()) {
				String string = String.format("Processing image for %s: x[%s] y[%s] w[%s] h[%s], rotate[%s]",
						region.name, region.left, region.top, region.width, region.height, region.rotate);
				if (!quiet) {
					System.out.println(string);
				}
				Messager.send(string,MessageKey.PROCESSING);
				// check if the page this region is in is currently loaded in a Buffered Image
				if (region.page == page) {
					BufferedImage splitImage = null;
					String extension = null;

					// check if the region is a ninepatch or a normal image and delegate accordingly
					if (region.splits == null) {
						splitImage = extractImage(img, region, outputDirFile, 0);
						extension = OUTPUT_TYPE;
					} else {
						splitImage = extractNinePatch(img, region, outputDirFile);
						extension = String.format("9.%s", OUTPUT_TYPE);
					}

					// check if the parent directories of this image file exist and create them if not
					File imgOutput = new File(outputDirFile,
						String.format("%s.%s", region.index == -1 ? region.name : region.name + "_" + region.index, extension));
					File imgDir = imgOutput.getParentFile();
					if (!imgDir.exists()) {
						String string1 = String.format("Creating directory: %s", imgDir.getPath());
						if (!quiet) {
							System.out.println(string1);
						}
						Messager.send(string,MessageKey.PROCESSING);
						imgDir.mkdirs();
					}

					// save the image
					try {
						ImageIO.write(splitImage, OUTPUT_TYPE, imgOutput);
					} catch (Exception e) {
						printExceptionAndExit(e);
					}
				}
			}
		}
	}

	/** Extract an image from a texture atlas.
	 * @param page The image file related to the page the region is in
	 * @param region The region to extract
	 * @param outputDirFile The output directory
	 * @param padding padding (in pixels) to apply to the image
	 * @return The extracted image */
	private BufferedImage extractImage (BufferedImage page, Region region, File outputDirFile, int padding) {
		BufferedImage splitImage = null;

		// get the needed part of the page and rotate if needed
		if (region.rotate) {
			BufferedImage srcImage = page.getSubimage(region.left, region.top, region.height, region.width);
			splitImage = new BufferedImage(region.width, region.height, page.getType());

			AffineTransform transform = new AffineTransform();
			transform.rotate(Math.toRadians(90.0));
			transform.translate(0, -region.width);
			AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
			op.filter(srcImage, splitImage);
		} else {
			splitImage = page.getSubimage(region.left, region.top, region.width, region.height);
		}

		// draw the image to a bigger one if padding is needed
		if (padding > 0) {
			BufferedImage paddedImage = new BufferedImage(splitImage.getWidth() + padding * 2, splitImage.getHeight() + padding * 2,
				page.getType());
			Graphics2D g2 = paddedImage.createGraphics();
			g2.drawImage(splitImage, padding, padding, null);
			g2.dispose();
			return paddedImage;
		} else {
			return splitImage;
		}
	}

	/** Extract a ninepatch from a texture atlas, according to the android specification.
	 * @see <a href="http://developer.android.com/guide/topics/graphics/2d-graphics.html#nine-patch">ninepatch specification</a>
	 * @param page The image file related to the page the region is in
	 * @param region The region to extract */
	private BufferedImage extractNinePatch (BufferedImage page, Region region, File outputDirFile) {
		BufferedImage splitImage = extractImage(page, region, outputDirFile, NINEPATCH_PADDING);
		Graphics2D g2 = splitImage.createGraphics();
		g2.setColor(Color.BLACK);

		// Draw the four lines to save the ninepatch's padding and splits
		int startX = region.splits[0] + NINEPATCH_PADDING;
		int endX = region.width - region.splits[1] + NINEPATCH_PADDING - 1;
		int startY = region.splits[2] + NINEPATCH_PADDING;
		int endY = region.height - region.splits[3] + NINEPATCH_PADDING - 1;
		if (endX >= startX) g2.drawLine(startX, 0, endX, 0);
		if (endY >= startY) g2.drawLine(0, startY, 0, endY);
		if (region.pads != null) {
			int padStartX = region.pads[0] + NINEPATCH_PADDING;
			int padEndX = region.width - region.pads[1] + NINEPATCH_PADDING - 1;
			int padStartY = region.pads[2] + NINEPATCH_PADDING;
			int padEndY = region.height - region.pads[3] + NINEPATCH_PADDING - 1;
			g2.drawLine(padStartX, splitImage.getHeight() - 1, padEndX, splitImage.getHeight() - 1);
			g2.drawLine(splitImage.getWidth() - 1, padStartY, splitImage.getWidth() - 1, padEndY);
		}
		g2.dispose();

		return splitImage;
	}

	private void printExceptionAndExit (Exception e) {
		e.printStackTrace();
		System.exit(1);
	}

	public void setQuiet (boolean quiet) {
		this.quiet = quiet;
	}

	
	public static void exec(String atlasFile,String imageDir,String outputDir) {
	

		TextureUnpacker unpacker = new TextureUnpacker();			
		File atlasFileHandle = new File(atlasFile).getAbsoluteFile();
		if (!atlasFileHandle.exists()) throw new RuntimeException("Atlas file not found: " + atlasFileHandle.getAbsolutePath());
			String atlasParentPath = atlasFileHandle.getParentFile().getAbsolutePath();
		
		// Set the directory variables to a default when they weren't given in the variables
		if (imageDir == null) imageDir = atlasParentPath;
		if (outputDir == null) outputDir = (new File(atlasParentPath, DEFAULT_OUTPUT_PATH)).getAbsolutePath();
		
		// Opens the atlas file from the specified filename
		TextureAtlasData atlas = new TextureAtlasData(new FileHandle(atlasFile), new FileHandle(imageDir), false);
		try {
			unpacker.splitAtlas(atlas, outputDir);
		} catch (Exception e) {
			Messager.send("Processing Error\nPlease try again", MessageKey.ERROR);
		}
		
	}	
}
