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
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import java.awt.image.BufferedImage;
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

	private BufferedImage icon;
	private GuideWorldMapPoint placed;

	/** Put the marker where this step happens, or take it away. */
	public void setStep(Step step)
	{
		clear();
		if (!config.showWorldMapPoint() || step == null)
		{
			return;
		}

		final WorldPoint point = locate(step);
		if (point == null)
		{
			return;
		}

		if (icon == null)
		{
			final BufferedImage source =
				ImageUtil.loadImageResource(WorldMapMarker.class, GuideIcon.RESOURCE);
			if (source == null)
			{
				return;
			}
			icon = ImageUtil.resizeImage(source, GuideIcon.WORLD_MAP, GuideIcon.WORLD_MAP);
		}

		placed = new GuideWorldMapPoint(point, icon);
		placed.setName("B0aty Guide");
		placed.setTooltip(step.getText());
		manager.add(placed);
	}

	public void clear()
	{
		if (placed != null)
		{
			manager.remove(placed);
			placed = null;
		}
		// Belt and braces: a marker left behind by a crashed shutdown would
		// otherwise sit on the map for the rest of the session.
		manager.removeIf(p -> p instanceof GuideWorldMapPoint);
	}

	/**
	 * Where the step happens. The destination is preferred over the target: a
	 * step that says "Bank at Edgeville" wants the bank, not the banker who
	 * happens to be the extracted target.
	 */
	private WorldPoint locate(Step step)
	{
		final Destination destination = step.getDestination();
		if (destination != null && !destination.isAmbiguous())
		{
			final WorldPoint point = first(destination.getPoints());
			if (point != null)
			{
				return point;
			}
		}
		final Target target = step.getTarget();
		return target == null ? null : first(target.getPoints());
	}

	/**
	 * Several coordinates means the data could not say which one, so nothing is
	 * drawn. Marking the first would put a confident pin in a place the player
	 * may not be going.
	 */
	private static WorldPoint first(List<List<Integer>> points)
	{
		if (points.size() != 1)
		{
			return null;
		}
		final List<Integer> raw = points.get(0);
		return raw.size() < 3 ? null : new WorldPoint(raw.get(0), raw.get(1), raw.get(2));
	}
}
