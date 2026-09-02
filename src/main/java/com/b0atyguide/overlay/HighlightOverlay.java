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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Draws the current step's target where it stands.
 *
 * <p>Everything here degrades to nothing. Most steps have no highlightable
 * target at all, and of those that do, most are off screen most of the time --
 * so an empty render pass is the normal case, not an error.
 */
public class HighlightOverlay extends Overlay
{
	private static final int OUTLINE_WIDTH = 2;
	private static final int OUTLINE_FEATHER = 4;
	/** How far above the target's own height the arrow floats, in world units. */
	private static final int ARROW_GAP = 30;

	/** The route's arrow is smaller than the destination's, on purpose. */
	private static final float APPROACH_ARROW_SCALE = 0.75f;

	@Inject
	private SceneTracker tracker;

	@Inject
	private ApproachTracker approachTracker;

	@Inject
	private BankTracker bankTracker;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	private ModelOutlineRenderer outlineRenderer;

	@Inject
	public HighlightOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!tracker.isTracking() && !bankTracker.isTracking())
		{
			return null;
		}

		drawApproach(graphics);
		drawBanks(graphics);
		drawTarget(graphics);
		return null;
	}

	/**
	 * The way up, when the target is on another floor.
	 *
	 * <p>Drawn smaller and without a chevron so it does not read as the thing
	 * being looked for: it is the route, not the destination.
	 */
	private void drawApproach(Graphics2D graphics)
	{
		final TileObject approach = approachTracker.getApproach();
		if (approach == null)
		{
			return;
		}
		final Shape hull = approach.getClickbox();
		if (hull != null)
		{
			OverlayUtil.renderPolygon(graphics, hull, config.highlightColor());
		}
		TargetArrow.draw(graphics, approach.getCanvasLocation(ARROW_GAP),
			config.highlightColor(), APPROACH_ARROW_SCALE);
	}

	/**
	 * Every bank in reach, while the step is a trip to one.
	 *
	 * <p>Deliberately outside the target checks below: a banking step usually
	 * has no target at all, because "Bank at Draynor" resolves to a place.
	 */
	private void drawBanks(Graphics2D graphics)
	{
		if (!config.highlightBanks())
		{
			return;
		}
		for (NPC banker : bankTracker.getNpcs())
		{
			outlineRenderer.drawOutline(
				banker, OUTLINE_WIDTH, config.highlightColor(), OUTLINE_FEATHER);
		}
		for (TileObject booth : bankTracker.getObjects())
		{
			drawObject(graphics, booth);
		}
	}

	/** What the current step actually points at. */
	private void drawTarget(Graphics2D graphics)
	{
		final Step step = tracker.getStep();
		final Target target = step == null ? null : step.getTarget();

		// A name the wiki could not confirm is opt-in. It is still safe -- it
		// matches nothing when wrong -- but some players would rather see only
		// highlights that are known-good.
		if (target == null || (!target.isWikiBacked() && !config.highlightUnconfirmed()))
		{
			return;
		}

		if (config.highlightNpcs())
		{
			for (NPC npc : tracker.getNpcs())
			{
				outlineRenderer.drawOutline(
					npc, OUTLINE_WIDTH, config.highlightColor(), OUTLINE_FEATHER);
				if (config.showTargetArrow())
				{
					// An empty string still measures a text box, which is what
					// positions the point; the arrow is drawn, not written.
					TargetArrow.draw(
						graphics,
						npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() + ARROW_GAP),
						config.highlightColor());
				}
			}
		}

		if (config.highlightObjects())
		{
			for (TileObject object : tracker.getObjects())
			{
				drawObject(graphics, object);
				if (config.showTargetArrow())
				{
					TargetArrow.draw(
						graphics, object.getCanvasLocation(ARROW_GAP), config.highlightColor());
				}
			}
		}
	}

	private void drawObject(Graphics2D graphics, TileObject object)
	{
		final Shape hull = object.getClickbox();
		if (hull != null)
		{
			OverlayUtil.renderPolygon(graphics, hull, config.highlightColor());
			return;
		}
		// Some scenery has no clickbox; fall back to a marker on its tile.
		final Point canvas = object.getCanvasLocation();
		if (canvas != null)
		{
			OverlayUtil.renderTextLocation(graphics, canvas, "*", config.highlightColor());
		}
	}
}
