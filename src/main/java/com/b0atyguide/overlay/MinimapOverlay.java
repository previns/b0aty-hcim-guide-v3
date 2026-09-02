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
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Marks the current step's target on the minimap, in the same green as the
 * world highlight.
 *
 * <p>This replaces the game's own hint arrow. That arrow was one call and came
 * with the minimap for free, but it is a flashing yellow the player cannot
 * change, it clashes with a plugin whose whole visual language is one
 * configurable colour, and it is a single shared slot that quests also want.
 * Drawing it ourselves costs an overlay and gives up nothing.
 */
public class MinimapOverlay extends Overlay
{
	/**
	 * The minimap packs the whole scene into ~150px, so a world-sized chevron
	 * would blanket several tiles of it.
	 */
	private static final float SCALE = 0.65f;

	/** Beyond this the target is off the minimap and localToMinimap returns null. */
	private static final int MAX_DISTANCE = 6400;

	/** Size of the rim pointer, and how far inside the rim it sits. */
	private static final int EDGE_ARROW = 10;
	private static final int EDGE_INSET = 8;

	@Inject
	private Client client;

	@Inject
	private SceneTracker tracker;

	@Inject
	private ApproachTracker approachTracker;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	public MinimapOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		// The minimap is a widget, so anything drawn under this layer is hidden
		// by it rather than on it.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showMinimapArrow() || !tracker.isTracking())
		{
			return null;
		}

		final Step step = tracker.getStep();
		final Target target = step == null ? null : step.getTarget();
		if (target == null || (!target.isWikiBacked() && !config.highlightUnconfirmed()))
		{
			return null;
		}

		final Point point = minimapPoint(target);
		if (point != null)
		{
			TargetArrow.draw(graphics, point, config.highlightColor(), SCALE);
			return null;
		}

		// Off the minimap. Pinning an arrow to the rim in the target's
		// direction is the one piece of guidance that still works at any
		// distance -- it is what the game's own hint arrow does, and losing it
		// was the cost of dropping that arrow.
		drawEdgeMarker(graphics, target);
		return null;
	}

	/**
	 * An arrow on the rim of the minimap, pointing the way to a target too far
	 * away to place on it.
	 */
	private void drawEdgeMarker(Graphics2D graphics, Target target)
	{
		final Widget minimap = minimapDrawArea();
		if (minimap == null)
		{
			return;
		}

		final WorldPoint destination = firstPoint(target);
		final Player local = client.getLocalPlayer();
		if (destination == null || local == null)
		{
			return;
		}

		final WorldPoint from = local.getWorldLocation();
		final double dx = destination.getX() - from.getX();
		final double dy = destination.getY() - from.getY();
		if (dx == 0 && dy == 0)
		{
			return;
		}

		// The minimap rotates with the camera, so the bearing has to be taken
		// in camera space or the arrow points north when the player faces east.
		final double camera = client.getCameraYaw() * (Math.PI * 2d / 2048d);
		final double bearing = Math.atan2(dx, dy) - camera;

		final Rectangle bounds = minimap.getBounds();
		final int cx = bounds.x + bounds.width / 2;
		final int cy = bounds.y + bounds.height / 2;
		final int radius = Math.min(bounds.width, bounds.height) / 2 - EDGE_INSET;

		final int x = cx + (int) Math.round(Math.sin(bearing) * radius);
		final int y = cy - (int) Math.round(Math.cos(bearing) * radius);

		drawPointer(graphics, x, y, bearing);
	}

	/** A filled triangle pointing outward along the bearing. */
	private void drawPointer(Graphics2D graphics, int x, int y, double bearing)
	{
		final Object prior = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		final AffineTransform priorTransform = graphics.getTransform();
		graphics.translate(x, y);
		// Screen y grows downward, so the rotation is negated against the
		// compass bearing.
		graphics.rotate(-bearing);

		final Polygon pointer = new Polygon(
			new int[]{0, -EDGE_ARROW / 2, EDGE_ARROW / 2},
			new int[]{-EDGE_ARROW, EDGE_ARROW / 2, EDGE_ARROW / 2},
			3);
		graphics.setColor(new Color(0, 0, 0, 140));
		graphics.drawPolygon(pointer);
		graphics.setColor(config.highlightColor());
		graphics.fillPolygon(pointer);
		graphics.setColor(Color.WHITE);
		graphics.drawPolygon(pointer);

		graphics.setTransform(priorTransform);
		if (prior != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, prior);
		}
	}

	/**
	 * The minimap lives at a different widget id in each viewport layout, and
	 * the inactive ones still resolve -- as hidden widgets with stale bounds.
	 * Taking the first non-hidden one is what keeps the arrow on the minimap
	 * after the player switches between fixed and resizable.
	 */
	private Widget minimapDrawArea()
	{
		final int[] candidates = {
			InterfaceID.Toplevel.MINIMAP,
			InterfaceID.ToplevelOsrsStretch.MINIMAP,
			InterfaceID.ToplevelPreEoc.MINIMAP,
		};
		for (int id : candidates)
		{
			final Widget widget = client.getWidget(id);
			if (widget != null && !widget.isHidden())
			{
				return widget;
			}
		}
		return null;
	}

	private WorldPoint firstPoint(Target target)
	{
		for (List<Integer> raw : target.getPoints())
		{
			if (raw != null && raw.size() >= 3)
			{
				return new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
			}
		}
		return null;
	}

	/**
	 * Where to draw. A loaded NPC or object wins, because it is where the thing
	 * actually is; the wiki coordinate is the fallback for something not yet in
	 * the scene.
	 */
	private Point minimapPoint(Target target)
	{
		// Point at the stairs, not at a spot on this floor under a target that
		// is actually above it.
		final TileObject approach = approachTracker.getApproach();
		if (approach != null)
		{
			return approach.getMinimapLocation();
		}

		final List<NPC> npcs = tracker.getNpcs();
		if (!npcs.isEmpty())
		{
			return npcs.get(0).getMinimapLocation();
		}

		final List<TileObject> objects = tracker.getObjects();
		if (!objects.isEmpty())
		{
			return objects.get(0).getMinimapLocation();
		}

		final WorldView view = client.getTopLevelWorldView();
		for (List<Integer> raw : target.getPoints())
		{
			if (raw == null || raw.size() < 3 || raw.get(2) != view.getPlane())
			{
				continue;
			}
			final WorldPoint world = new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
			final LocalPoint local = LocalPoint.fromWorld(view, world);
			if (local != null)
			{
				// Returns null once the point falls outside the minimap, which
				// is the clipping we would otherwise have to do by hand.
				return Perspective.localToMinimap(client, local, MAX_DISTANCE);
			}
		}
		return null;
	}
}
