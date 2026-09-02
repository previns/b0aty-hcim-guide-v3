/*
 * Copyright (c) 2026, Previn <https://github.com/previns>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.b0atyguide.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import net.runelite.api.Point;

/**
 * A downward chevron floating above whatever the current step points at.
 *
 * <p>An outline alone is easy to lose in a crowd -- it follows the model's
 * silhouette, so a target standing among identical NPCs reads as just another
 * shape. An arrow sits clear of the scene and says "this one".
 */
final class TargetArrow
{
	private static final int WIDTH = 14;
	private static final int HEIGHT = 12;
	/** Vertical travel of the bob, in pixels. */
	private static final int BOB = 3;
	private static final int BOB_PERIOD_MS = 1200;

	private TargetArrow()
	{
	}

	/**
	 * @param anchor point just above the target, in canvas space
	 */
	static void draw(Graphics2D graphics, Point anchor, Color color)
	{
		draw(graphics, anchor, color, 1.0f);
	}

	/**
	 * @param anchor point just above the target, in canvas space
	 * @param scale  1.0 for the world; smaller for the minimap, where a
	 *               full-size chevron covers several tiles of map
	 */
	static void draw(Graphics2D graphics, Point anchor, Color color, float scale)
	{
		if (anchor == null)
		{
			return;
		}
		final int width = Math.max(4, Math.round(WIDTH * scale));
		final int height = Math.max(4, Math.round(HEIGHT * scale));

		// A slow bob draws the eye without the strobing that a fast blink causes.
		// Derived from the clock rather than a frame counter so it runs at the
		// same speed whatever the frame rate.
		final double phase = (System.currentTimeMillis() % BOB_PERIOD_MS) / (double) BOB_PERIOD_MS;
		final int offset = (int) Math.round(BOB * Math.sin(phase * 2 * Math.PI));

		final int x = anchor.getX();
		final int y = anchor.getY() + Math.round(offset * scale);

		final Polygon arrow = new Polygon(
			new int[]{x - width / 2, x + width / 2, x},
			new int[]{y - height, y - height, y},
			3);

		final Object priorHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(new Color(0, 0, 0, 130));
		graphics.fillPolygon(new Polygon(
			new int[]{x - width / 2 - 1, x + width / 2 + 1, x},
			new int[]{y - height - 1, y - height - 1, y + 2},
			3));

		graphics.setColor(opaque(color));
		graphics.fillPolygon(arrow);
		graphics.setColor(Color.WHITE);
		graphics.drawPolygon(arrow);

		if (priorHint != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, priorHint);
		}
	}

	/**
	 * The configured colour carries the alpha used for the model outline, which
	 * is deliberately soft. A translucent arrow disappears against bright
	 * scenery, so the arrow uses the same hue at full strength.
	 */
	private static Color opaque(Color color)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue());
	}
}
