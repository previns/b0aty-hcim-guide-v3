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
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Target;
import com.b0atyguide.path.PathTracker;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;
import java.awt.Dimension;
import java.awt.Polygon;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Actor;
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

	/** How far above an object's tile the item sprite sits. */
	private static final int ICON_HEIGHT = 120;

	/** The route's arrow is smaller than the destination's, on purpose. */
	private static final float APPROACH_ARROW_SCALE = 0.75f;

	@Inject
	private SceneTracker tracker;

	@Inject
	private Client client;

	@Inject
	private PathTracker pathTracker;

	@Inject
	private GroundItemTracker groundItems;

	@Inject
	private ApproachTracker approachTracker;

	@Inject
	private BankTracker bankTracker;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	private ModelOutlineRenderer outlineRenderer;

	@Inject
	private ItemManager itemManager;


	@Inject
	public HighlightOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		// The ground items belong in this test. Without them the overlay left
		// before drawing anything on exactly the steps where a thing on the
		// floor is the whole point: "Collect 2x Planks" resolves to an item and
		// no npc and no scenery, so the scene tracker wants nothing, and the
		// planks two tiles away were never outlined.
		// Everything this overlay can draw has to be in this test. Twice now a
		// feature was added below it and left unreachable: the ground items,
		// and then the destination tile -- 1,939 instructions carry a
		// coordinate and no ids, which is exactly when the scene tracker wants
		// nothing and exactly when the tile is the only guidance there is.
		final QuestHelperSteps.Instruction instruction = tracker.getNavigationInstruction();
		final boolean somewhereToMark = instruction != null
			&& (!instruction.getTiles().isEmpty() || instruction.getPoint().size() >= 3);
		if (!tracker.isTracking() && !bankTracker.isTracking() && !somewhereToMark
			&& groundItems.getTiles().isEmpty() && approachTracker.getApproach() == null
			&& pathTracker.getEntrance() == null)
		{
			return null;
		}

		drawApproach(graphics);
		drawBanks(graphics);
		drawGroundItems(graphics);
		drawTarget(graphics);
		drawEntrance(graphics);
		drawDestinationTile(graphics);
		return null;
	}

	/**
	 * The tile Quest Helper is pointing at, when there is nothing to outline.
	 *
	 * <p>A fifth of its steps name a place rather than a thing -- "pick up the
	 * blanket in the room to the south", the safespot to stand on, the square to
	 * walk to -- and Quest Helper marks the tile for every one of them. This
	 * drew a line towards it and then nothing at the end, so the player arrived
	 * in the right room and the plugin fell silent.
	 *
	 * <p>Only when nothing else is marked. With an npc or a piece of scenery
	 * outlined, the tile underneath it is noise.
	 */
	private void drawDestinationTile(Graphics2D graphics)
	{
		if (!tracker.getNpcs().isEmpty() || !tracker.getObjects().isEmpty())
		{
			return;
		}
		final QuestHelperSteps.Instruction instruction = tracker.getNavigationInstruction();
		if (instruction == null)
		{
			return;
		}
		// The tiles Quest Helper marks itself come first and are always drawn:
		// a safespot is the instruction, not a hint about where it is.
		for (List<Integer> marked : instruction.getTiles())
		{
			markTile(graphics, marked, "marked tile");
		}
		if (instruction.getTiles().isEmpty())
		{
			markTile(graphics, instruction.getPoint(), "destination tile");
		}
	}

	/** One tile of the scene, outlined and washed with the highlight colour. */
	private void markTile(Graphics2D graphics, List<Integer> at, String what)
	{
		if (at.size() < 3)
		{
			return;
		}
		final WorldPoint tile = new WorldPoint(at.get(0), at.get(1), at.get(2));
		if (tile.getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return;
		}
		final LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), tile);
		if (local == null)
		{
			return;
		}
		final Polygon poly = Perspective.getCanvasTilePoly(client, local);
		if (poly == null)
		{
			return;
		}
		final Color colour = config.highlightColor();
		graphics.setColor(colour);
		graphics.drawPolygon(poly);
		graphics.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40));
		graphics.fillPolygon(poly);
	}

	/**
	 * The gate the route stops at, when the destination is walled off.
	 *
	 * <p>A line that ends at a fence says "walk into this fence". The route now
	 * ends at the way in instead, and this marks which one -- a paddock has
	 * four sides and the gate is on one of them.
	 *
	 * <p>Drawn like the approach rather than like a target: it is the thing to
	 * open on the way, not the thing being looked for.
	 */
	private void drawEntrance(Graphics2D graphics)
	{
		final WorldPoint at = pathTracker.getEntrance();
		if (at == null)
		{
			return;
		}
		// The object standing on that tile, when one is loaded: its clickbox is
		// what the player has to click. The tile itself otherwise, which is
		// still the right square to walk to.
		// Resolve the actionable object when the route changes, not by walking
		// all 104 x 104 scene tiles every rendered frame. Its projected clickbox
		// still belongs here: the camera can move without a game tick occurring.
		final TileObject object = pathTracker.getEntranceObject();
		final Shape hull = object == null ? null : object.getClickbox();
		if (hull != null)
		{
			OverlayUtil.renderPolygon(graphics, hull, config.highlightColor());
		}
		else
		{
			markTile(graphics, Arrays.asList(at.getX(), at.getY(), at.getPlane()),
				"the way in");
		}
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

	/**
	 * Tiles holding something the step says to pick up.
	 *
	 * <p>Drawn as a tile outline rather than a model outline: a ground item has
	 * no model to outline until it is rendered, and the tile is what the player
	 * clicks anyway.
	 */
	private void drawGroundItems(Graphics2D graphics)
	{
		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		final Color colour = config.highlightColor();
		final Color fill = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 40);
		for (WorldPoint at : groundItems.getTiles())
		{
			final LocalPoint local_ = LocalPoint.fromWorld(client.getTopLevelWorldView(), at);
			if (local_ == null)
			{
				continue;
			}
			final Polygon poly = Perspective.getCanvasTilePoly(client, local_);
			if (poly == null)
			{
				continue;
			}
			graphics.setColor(colour);
			graphics.drawPolygon(poly);
			graphics.setColor(fill);
			graphics.fillPolygon(poly);
		}
	}

	/** What the current step actually points at. */
	private void drawTarget(Graphics2D graphics)
	{
		final Step step = tracker.getStep();
		final Target target = step == null ? null : step.getTarget();
		final QuestHelperSteps.Instruction instruction = tracker.getNavigationInstruction();
		final boolean fromQuest = instruction != null && !instruction.getIds().isEmpty();

		// A name the wiki could not confirm is opt-in. It is still safe -- it
		// matches nothing when wrong -- but some players would rather see only
		// highlights that are known-good.
		//
		// Quest Helper's instruction is not subject to that: it is an id from
		// its own source, not a name this pipeline guessed. Requiring a target
		// here meant a step whose only guidance was the quest -- "continue
		// Gertrude's Cat" -- outlined nothing, however well the scene matched.
		if (!fromQuest && tracker.getActiveTravel() == null
			&& (target == null || (!target.isWikiBacked() && !config.highlightUnconfirmed())))
		{
			return;
		}

		// One instruction uses the same icon on every matched entity. Ask the
		// image cache once, and do not project an icon position when none exists.
		final BufferedImage sprite = instruction == null || !instruction.hasIcon()
			? null : itemManager.getImage(instruction.getIcon());

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
				drawIcon(graphics, sprite, npc);
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
				if (sprite != null)
				{
					drawIcon(graphics, sprite, object.getCanvasLocation(ICON_HEIGHT));
				}
			}
		}
	}

	/**
	 * The item Quest Helper draws on an npc, positioned above its head.
	 *
	 * <p>The sprite is fetched first and the position asked for second, because
	 * {@code getCanvasImageLocation} needs the image to know how wide it is --
	 * and it dereferences it. Asking for the position first meant looking up
	 * item 0's sprite once per npc per frame, and passing null into that call
	 * when the item manager had nothing to give.
	 */
	private void drawIcon(Graphics2D graphics, BufferedImage sprite, Actor actor)
	{
		if (sprite == null)
		{
			return;
		}
		final Point at = actor.getCanvasImageLocation(sprite, actor.getLogicalHeight());
		if (at != null)
		{
			graphics.drawImage(sprite, at.getX(), at.getY(), null);
		}
	}

	/**
	 * The item to use on this thing, drawn over it.
	 *
	 * <p>Quest Helper's clearest instruction, and the one hardest to say in
	 * words: a picture of what to click with, on what to click it on. "Use a
	 * bucket of milk on Gertrude's cat" is a sentence; the bucket drawn on the
	 * cat is not something the player has to read.
	 *
	 * <p>Silent when the sprite is not cached yet -- {@code getImage} loads
	 * asynchronously and returns null until it is, which is a frame, not an
	 * error.
	 */
	private void drawIcon(Graphics2D graphics, BufferedImage sprite, Point at)
	{
		if (sprite == null || at == null)
		{
			return;
		}
		graphics.drawImage(sprite, at.getX(), at.getY(), null);
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
