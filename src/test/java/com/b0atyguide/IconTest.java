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
package com.b0atyguide;

import com.b0atyguide.overlay.GuideIcon;
import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The icon resource has to exist and survive resizing.
 *
 * <p>A missing image resource fails at runtime as a null, not an exception, so
 * the sidebar button would simply render blank with nothing in the log. Cheap
 * to catch here instead.
 */
public class IconTest
{
	@Test
	public void theIconResourceLoads()
	{
		final BufferedImage source =
			ImageUtil.loadImageResource(B0atyGuidePlugin.class, GuideIcon.RESOURCE);
		assertNotNull("missing " + GuideIcon.RESOURCE, source);
		assertTrue(source.getWidth() > 0);
		assertTrue(source.getHeight() > 0);
	}

	@Test
	public void theIconHasAnAlphaChannel()
	{
		// It sits on the sidebar and on the world map, both of which show
		// through. A flat RGB image would draw a square block of background.
		final BufferedImage source =
			ImageUtil.loadImageResource(B0atyGuideConfig.class, GuideIcon.RESOURCE);
		assertNotNull(source);
		assertTrue("icon should carry transparency",
			source.getColorModel().hasAlpha());
	}

	@Test
	public void bothSizesResizeCleanly()
	{
		final BufferedImage source =
			ImageUtil.loadImageResource(B0atyGuidePlugin.class, GuideIcon.RESOURCE);
		assertNotNull(source);

		final BufferedImage sidebar =
			ImageUtil.resizeImage(source, GuideIcon.SIDEBAR, GuideIcon.SIDEBAR);
		assertEquals(GuideIcon.SIDEBAR, sidebar.getWidth());
		assertEquals(GuideIcon.SIDEBAR, sidebar.getHeight());

		final BufferedImage map =
			ImageUtil.resizeImage(source, GuideIcon.WORLD_MAP, GuideIcon.WORLD_MAP);
		assertEquals(GuideIcon.WORLD_MAP, map.getWidth());
		assertEquals(GuideIcon.WORLD_MAP, map.getHeight());
	}

	@Test
	public void theWorldMapIconIsTheLargerOne()
	{
		assertTrue("the map marker must be findable when zoomed out",
			GuideIcon.WORLD_MAP > GuideIcon.SIDEBAR);
	}

	@Test
	public void runeLitesWikiIconIsOnTheClasspath()
	{
		// Borrowed from another plugin's resources rather than bundled, so it
		// can move between client versions. The panel copes with null, but if
		// this starts failing the link has quietly lost its icon.
		final BufferedImage icon = ImageUtil.loadImageResource(
			B0atyGuidePlugin.class, "/net/runelite/client/plugins/info/wiki_icon.png");
		assertNotNull("RuneLite's wiki icon moved; the link falls back to text", icon);
		assertTrue(icon.getWidth() > 0 && icon.getHeight() > 0);
	}
}
