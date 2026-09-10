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
import com.b0atyguide.data.Destination;
import com.b0atyguide.data.QuestHelperSteps;
import java.util.Collections;
import java.util.ArrayList;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

/**
 * Marks where the current step happens on the world map.
 *
 * <p>This is the only guidance that survives the target being nowhere near the
 * player: the outline needs the NPC loaded, and the hint arrow needs it inside
 * the scene. A bank on the other side of the map only shows up here.
 */
@Singleton
public class WorldMapMarker
{
	@Inject
	private WorldMapPointManager manager;

	@Inject
	private B0atyGuideConfig config;


	/** Thickness of the ring, and the padding that keeps it off the icon. */
	private static final int RING = 2;

	private BufferedImage icon;

	/** The colour {@link #icon} was drawn in, so a change to it is noticed. */
	private Color drawnIn;
	/** More than this on screen at once stops being guidance. */
	private static final int MAX_MARKERS = 6;

	private final List<GuideWorldMapPoint> placed = new ArrayList<>();

	/** The instruction the current step was chosen with, or null. */
	private QuestHelperSteps.Instruction chosen;

	/** Put the marker where this step happens, or take it away. */
	public void setStep(Step step)
	{
		setStep(step, null);
	}

	/**
	 * The step, and the Quest Helper instruction chosen for it.
	 *
	 * <p>Handed in rather than read back from {@link SceneTracker}: that tracker
	 * is written on the client thread and this runs on Swing's, so for the tick
	 * in between it still answers with the step before -- and the marker would
	 * sit on the previous step's tile.
	 */
	public void setStep(Step step, QuestHelperSteps.Instruction instruction)
	{
		chosen = instruction;
		clear();
		if (!config.showWorldMapPoint() || step == null)
		{
			return;
		}

		final List<WorldPoint> points = locate(step);
		if (points.isEmpty())
		{
			return;
		}

		final Color colour = config.highlightColor();
		if (icon == null || !colour.equals(drawnIn))
		{
			final BufferedImage source =
				ImageUtil.loadImageResource(WorldMapMarker.class, GuideIcon.RESOURCE);
			if (source == null)
			{
				return;
			}
			icon = ringed(
				ImageUtil.resizeImage(source, GuideIcon.WORLD_MAP, GuideIcon.WORLD_MAP),
				colour);
			drawnIn = colour;
		}

		// Several coordinates means the data could not say which one. Marking
		// them all is honest -- it is one of these -- where marking the first
		// would put a confident pin somewhere the player may not be going, and
		// marking none left 123 steps with no guidance at all.
		final boolean several = points.size() > 1;
		for (WorldPoint point : points)
		{
			final GuideWorldMapPoint marker = new GuideWorldMapPoint(point, icon);
			marker.setName("B0aty Guide");
			marker.setTooltip(several
				? step.getText() + " (one of " + points.size() + " possible places)"
				: step.getText());
			placed.add(marker);
			manager.add(marker);
		}
	}

	public void clear()
	{
		for (GuideWorldMapPoint marker : placed)
		{
			manager.remove(marker);
		}
		placed.clear();
		// Belt and braces: a marker left behind by a crashed shutdown would
		// otherwise sit on the map for the rest of the session.
		manager.removeIf(p -> p instanceof GuideWorldMapPoint);
	}

	/**
	 * Where the step happens. The destination is preferred over the target: a
	 * step that says "Bank at Edgeville" wants the bank, not the banker who
	 * happens to be the extracted target.
	 */
	private List<WorldPoint> locate(Step step)
	{
		// Quest Helper first, unless the step names something of its own. Its
		// coordinate is the tile this step of the quest is waiting on, and the
		// guide's destination is the town the step mentions -- but a step that
		// names an npc is about that npc, not about wherever the quest happens
		// to stand.
		final QuestHelperSteps.Instruction instruction = step.navigationInstruction(chosen);
		if (instruction != null)
		{
			final List<WorldPoint> exact =
				all(Collections.singletonList(instruction.getPoint()));
			if (!exact.isEmpty())
			{
				return exact;
			}
		}

		final Destination destination = step.getDestination();
		if (destination != null && !destination.isAmbiguous())
		{
			final List<WorldPoint> places = all(destination.getPoints());
			if (!places.isEmpty())
			{
				return places;
			}
		}

		final Target target = step.getTarget();
		final List<WorldPoint> fromTarget =
			target == null || target.isScattered()
				? Collections.emptyList() : all(target.getPoints());
		if (!fromTarget.isEmpty())
		{
			return fromTarget;
		}

		// Last, and only into silence: where the item lies on the ground. A step
		// that already knows where it is going has been answered above, so this
		// never argues with the guide -- it answers "Collect 2x Purple Dye when
		// passing", which named an item, a quantity, and no place at all.
		final List<List<Integer>> lying = new ArrayList<>();
		for (Step.Spawn spawn : step.getSpawns())
		{
			lying.addAll(spawn.getPoints());
		}
		return all(lying);
	}

	/**
	 * Every usable coordinate in the list, capped.
	 *
	 * <p>Capped because a name the wiki maps across a whole region would
	 * otherwise sprinkle the map, which reads as noise rather than guidance.
	 */
	private static List<WorldPoint> all(List<List<Integer>> points)
	{
		final List<WorldPoint> out = new ArrayList<>();
		for (List<Integer> raw : points)
		{
			if (raw != null && raw.size() >= 3)
			{
				out.add(new WorldPoint(raw.get(0), raw.get(1), raw.get(2)));
			}
			if (out.size() >= MAX_MARKERS)
			{
				break;
			}
		}
		return out;
	}

	/**
	 * The icon with a ring drawn round it, in the guide's own colour.
	 *
	 * <p>The world map is already crowded with the game's own icons -- banks,
	 * altars, shops, a hundred of them in a city -- and a small picture among
	 * them is not findable. A ring in a colour nothing else on the map uses is,
	 * and it is the same colour as everything else this plugin draws.
	 *
	 * <p>Drawn into a larger image rather than over the existing one, so the
	 * icon itself is not cropped by its own border.
	 */
	private static BufferedImage ringed(BufferedImage icon, Color colour)
	{
		final int size = icon.getWidth() + RING * 4;
		final BufferedImage out =
			new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D graphics = out.createGraphics();
		graphics.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// A dark disc behind it, so the ring reads against pale coastline and
		// dark dungeon alike.
		graphics.setColor(new Color(0, 0, 0, 150));
		graphics.fillOval(0, 0, size - 1, size - 1);

		graphics.drawImage(icon, RING * 2, RING * 2, null);

		graphics.setColor(colour);
		graphics.setStroke(new BasicStroke(RING));
		graphics.drawOval(RING / 2, RING / 2, size - RING - 1, size - RING - 1);
		graphics.dispose();
		return out;
	}

}
