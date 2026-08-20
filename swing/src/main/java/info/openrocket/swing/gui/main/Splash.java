package info.openrocket.swing.gui.main;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.SplashScreen;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

import info.openrocket.core.util.BuildProperties;

/**
 * Helper methods for manipulating the Java runtime splash screen.
 * <p>
 * Notes:
 * SplashScreen.update() takes randomly between 4 and 500 ms to complete,
 * even after it has been called ~100 times (and therefore pre-compiled).
 * Therefore it cannot be relied upon to perform for example color fades.
 * Care should be taken to call update() only once or twice per second.
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class Splash {
	
	// The right edge of the text base line for the version string
	private static final int VERSION_POSITION_X = 617;
	private static final int VERSION_POSITION_Y = 150;
	private static final Font VERSION_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
	private static final Color VERSION_COLOR = Color.WHITE;

	private static final int WATERMARK_POSITION_X = 617;
	private static final int WATERMARK_POSITION_Y = 178;
	private static final Font WATERMARK_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	private static final Color WATERMARK_COLOR = new Color(255, 255, 255, 210);
	private static final String WATERMARK_TEXT =
			"Project Imperia is separate from OpenRocket, but based on it";
	
	
	/**
	 * Initialize the splash screen with additional information.  This method should
	 * be called as soon as reasonably possible during program startup.
	 * 
	 * @return	<code>true</code> if the splash screen could be successfully initialized
	 */
	public static boolean init() {
		// Get the splash screen
		SplashScreen s = getSplashScreen();
		if (s == null)
			return false;
		
		// Create graphics context and set antialiasing on
		Graphics2D g2 = s.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
				RenderingHints.VALUE_STROKE_NORMALIZE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING,
				RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		
		// Draw the version number and Project Imperia watermark
		drawVersionNumber(g2);
		drawWatermark(g2);
		
		// Update the splash screen
		s.update();
		return true;
	}
	
	
	
	private static void drawVersionNumber(Graphics2D g2) {
		String text = "Project Imperia V" + BuildProperties.getVersion();
		GlyphVector gv = VERSION_FONT.createGlyphVector(g2.getFontRenderContext(), text);
		
		Rectangle2D rect = gv.getVisualBounds();
		double width = rect.getWidth();
		
		g2.setColor(VERSION_COLOR);
		g2.drawGlyphVector(gv, (float) (VERSION_POSITION_X - width), VERSION_POSITION_Y);
		
	}

	private static void drawWatermark(Graphics2D g2) {
		GlyphVector gv = WATERMARK_FONT.createGlyphVector(g2.getFontRenderContext(), WATERMARK_TEXT);
		Rectangle2D rect = gv.getVisualBounds();
		double width = rect.getWidth();

		g2.setColor(WATERMARK_COLOR);
		g2.drawGlyphVector(gv, (float) (WATERMARK_POSITION_X - width), WATERMARK_POSITION_Y);
	}
	
	
	/**
	 * Return the current splash screen or <code>null</code> if not available or already closed.
	 * This method catches the possible exceptions and returns null if they occur.
	 * 
	 * @return	the current (visible) splash screen, or <code>null</code>.
	 */
	public static SplashScreen getSplashScreen() {
		try {
			SplashScreen splash = SplashScreen.getSplashScreen();
			if (splash != null && splash.isVisible()) {
				return splash;
			} else {
				return null;
			}
		} catch (RuntimeException e) {
			return null;
		}
	}
	
	
	
}
