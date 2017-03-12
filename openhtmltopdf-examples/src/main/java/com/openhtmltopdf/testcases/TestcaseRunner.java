package com.openhtmltopdf.testcases;

import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.TextDirection;
import com.openhtmltopdf.simple.Java2DRendererBuilder;
import com.openhtmltopdf.extend.FSObjectDrawer;
import com.openhtmltopdf.extend.OutputDevice;
import com.openhtmltopdf.extend.OutputDeviceGraphicsDrawer;
import com.openhtmltopdf.render.DefaultObjectDrawerFactory;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import com.openhtmltopdf.util.JDKXRLogger;
import com.openhtmltopdf.util.XRLog;
import com.openhtmltopdf.util.XRLogger;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.util.Charsets;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.logging.Level;

public class TestcaseRunner {

	/**
	 * Runs our set of manual test cases. You can specify an output directory
	 * with -DOUT_DIRECTORY=./output for example. Otherwise, the current working
	 * directory is used. Test cases must be placed in
	 * src/main/resources/testcases/
	 *
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		/*
		 * Note: The RepeatedTableSample optionally requires the font file
		 * NotoSans-Regular.ttf to be placed in the resources directory.
		 * 
		 * This sample demonstrates the failing repeated table header on each
		 * page.
		 */
		runTestCase("RepeatedTableSample");

		/*
		 * This sample demonstrates the -fs-pagebreak-min-height css property
		 */
		runTestCase("FSPageBreakMinHeightSample");

		runTestCase("color");
		runTestCase("background-color");
		runTestCase("background-image");
		runTestCase("invalid-url-background-image");
		runTestCase("text-align");
		runTestCase("font-family-built-in");
		runTestCase("form-controls");

		/*
		 * SVG samples
		 */
		runTestCase("svg-inline");

		/*
		 * Graphics2D Texterror Case
		 */
		runTestCase("moonbase");

		/*
		 * Custom Objects
		 */
		runTestCase("custom-objects");

		/* Add additional test cases here. */
	}

	/**
	 * Will throw an exception if a SEVERE or WARNING message is logged.
	 *
	 * @param testCaseFile
	 * @throws Exception
	 */
	public static void runTestWithoutOutput(String testCaseFile) throws Exception {
		runTestWithoutOutput(testCaseFile, false);
	}

	/**
	 * Will silently let ALL log messages through.
	 *
	 * @param testCaseFile
	 * @throws Exception
	 */
	public static void runTestWithoutOutputAndAllowWarnings(String testCaseFile) throws Exception {
		runTestWithoutOutput(testCaseFile, true);
	}

	private static void runTestWithoutOutput(String testCaseFile, boolean allowWarnings) throws Exception {
		System.out.println("Trying to run: " + testCaseFile);

		byte[] htmlBytes = IOUtils
				.toByteArray(TestcaseRunner.class.getResourceAsStream("/testcases/" + testCaseFile + ".html"));
		String html = new String(htmlBytes, Charsets.UTF_8);
		OutputStream outputStream = new ByteArrayOutputStream(4096);

		// We wan't to throw if we get a warning or severe log message.
		final XRLogger delegate = new JDKXRLogger();
		final java.util.List<RuntimeException> warnings = new ArrayList<RuntimeException>();
		XRLog.setLoggerImpl(new XRLogger() {
			@Override
			public void setLevel(String logger, Level level) {
			}

			@Override
			public void log(String where, Level level, String msg, Throwable th) {
				if (level.equals(Level.WARNING) || level.equals(Level.SEVERE)) {
					warnings.add(new RuntimeException(where + ": " + msg, th));
				}
				delegate.log(where, level, msg, th);
			}

			@Override
			public void log(String where, Level level, String msg) {
				if (level.equals(Level.WARNING) || level.equals(Level.SEVERE)) {
					warnings.add(new RuntimeException(where + ": " + msg));
				}
				delegate.log(where, level, msg);
			}
		});

		renderPDF(html, outputStream);

		if (!warnings.isEmpty() && !allowWarnings) {
			throw warnings.get(0);
		}
	}

	private static void renderPDF(String html, OutputStream outputStream) throws Exception {
		try {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
			builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
			builder.defaultTextDirection(TextDirection.LTR);
			builder.useSVGDrawer(new BatikSVGDrawer());

			DefaultObjectDrawerFactory objectDrawerFactory = new DefaultObjectDrawerFactory();
			objectDrawerFactory.registerDrawer("custom/binary-tree", new SampleObjectDrawerBinaryTree());
			builder.useObjectDrawerFactory(objectDrawerFactory);

			builder.withHtmlContent(html, TestcaseRunner.class.getResource("/testcases/").toString());
			builder.toStream(outputStream);
			builder.run();
		} finally {
			outputStream.close();
		}
	}

	private static void renderPNG(String html, OutputStream outputStream) throws IOException {
		Java2DRendererBuilder builder = new Java2DRendererBuilder();
		builder.useSVGDrawer(new BatikSVGDrawer());
		builder.withHtmlContent(html, TestcaseRunner.class.getResource("/testcases/").toString());
		BufferedImage image = builder.build().renderToImage(512, BufferedImage.TYPE_3BYTE_BGR);
		ImageIO.write(image, "PNG", outputStream);
		outputStream.close();
	}

	public static void runTestCase(String testCaseFile) throws Exception {
		byte[] htmlBytes = IOUtils
				.toByteArray(TestcaseRunner.class.getResourceAsStream("/testcases/" + testCaseFile + ".html"));
		String html = new String(htmlBytes, Charsets.UTF_8);
		String outDir = System.getProperty("OUT_DIRECTORY", ".");
		String testCaseOutputFile = outDir + "/" + testCaseFile + ".pdf";
		String testCaseOutputPNGFile = outDir + "/" + testCaseFile + ".png";
		FileOutputStream outputStream = new FileOutputStream(testCaseOutputFile);
		renderPDF(html, outputStream);
		System.out.println("Wrote " + testCaseOutputFile);

		FileOutputStream outputStreamPNG = new FileOutputStream(testCaseOutputPNGFile);
		renderPNG(html, outputStreamPNG);
	}

	public static class SampleObjectDrawerBinaryTree implements FSObjectDrawer {
		int fanout;
		int angle;

		@Override
		public void drawObject(Element e, double x, double y, final double width, final double height,
				OutputDevice outputDevice, RenderingContext ctx, final int dotsPerPixel) {
			final int depth = Integer.parseInt(e.getAttribute("data-depth"));
			fanout = Integer.parseInt(e.getAttribute("data-fanout"));
			angle = Integer.parseInt(e.getAttribute("data-angle"));

			outputDevice.drawWithGraphics((float) x, (float) y, (float) width / dotsPerPixel,
					(float) height / dotsPerPixel, new OutputDeviceGraphicsDrawer() {
						@Override
						public void render(Graphics2D graphics2D) {
							double realWidth = width / dotsPerPixel;
							double realHeight = height / dotsPerPixel;

							renderTree(graphics2D, realWidth / 2f, realHeight, realHeight / depth, -90, depth);
						}
					});
		}

		private void renderTree(Graphics2D gfx, double x, double y, double len, double angleDeg, int depth) {
			double rad = angleDeg * Math.PI / 180f;
			double xTarget = x + Math.cos(rad) * len;
			double yTarget = y + Math.sin(rad) * len;
			gfx.setStroke(new BasicStroke(2f));
			gfx.setColor(new Color(255 / depth, 128, 128));
			gfx.draw(new Line2D.Double(x, y, xTarget, yTarget));

			if (depth > 1) {
				double childAngle = angleDeg - (((fanout - 1) * angle) / 2f);
				for (int i = 0; i < fanout; i++) {
					renderTree(gfx, xTarget, yTarget, len * 0.95, childAngle, depth - 1);
					childAngle += angle;
				}
			}
		}
	}
}
