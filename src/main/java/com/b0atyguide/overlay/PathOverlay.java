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

import com.b0atyguide.B0atyGuideConfig;
import com.b0atyguide.path.PathTracker;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the walkable route to the current target along the ground.
 *
 * <p>The line follows the path the player can actually walk, not the straight
 * line to the target -- see {@link com.b0atyguide.path.PathFinder} for why that
 * distinction is the whole point.
 */
public class PathOverlay extends Overlay
{
	private static final int WIDTH = 3;
	/** Beyond this the line is dense clutter rather than guidance. */
	private static final int MAX_TILES_DRAWN = 60;
	private static final Stroke PATH_STROKE =
		new BasicStroke(WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private final FadePalette palette = new FadePalette();

	@Inject
	private Client client;

	@Inject
	private PathTracker pathTracker;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	public PathOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showPath())
		{
			return null;
		}

		final List<java.awt.Point> path = pathTracker.getPath();
		if (path.size() < 2)
		{
			return null;
		}

		final Object prior = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		final Stroke priorStroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setStroke(PATH_STROKE);

		final Color colour = config.highlightColor();
		final int drawn = Math.min(path.size(), MAX_TILES_DRAWN);
		final Color[] colours = palette.forPath(colour, drawn);
		Point previous = canvas(path.get(0));

		for (int i = 1; i < drawn; i++)
		{
			final Point current = canvas(path.get(i));
			if (previous != null && current != null)
			{
				// Fade with distance so the near end, which is the bit being
				// walked next, reads strongest.
				graphics.setColor(colours[i]);
				graphics.drawLine(previous.getX(), previous.getY(),
					current.getX(), current.getY());
			}
			previous = current;
		}

		graphics.setStroke(priorStroke);
		if (prior != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prior);
		}
		return null;
	}

	/** Centre of a scene tile on screen, or null when it is off camera. */
	private Point canvas(java.awt.Point tile)
	{
		final LocalPoint local = LocalPoint.fromScene(
			tile.x, tile.y, client.getTopLevelWorldView());
		return local == null ? null : Perspective.localToCanvas(client, local, 0);
	}

	private static Color fade(Color colour, int index, int total)
	{
		final float ratio = 1f - (index / (float) total) * 0.6f;
		return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(),
			Math.max(40, Math.round(220 * ratio)));
	}

	/**
	 * The gradient depends on path length and configured colour, not the camera.
	 * Keeping only the current palette avoids up to 59 new Color objects per
	 * frame without retaining old paths or caching stale screen projections.
	 */
	static final class FadePalette
	{
		private Color colour;
		private Color[] colours = new Color[0];

		Color[] forPath(Color requested, int length)
		{
			if (!requested.equals(colour) || colours.length != length)
			{
				colour = requested;
				colours = new Color[length];
				for (int i = 0; i < length; i++)
				{
					colours[i] = fade(requested, i, length);
				}
			}
			return colours;
		}
	}
}
