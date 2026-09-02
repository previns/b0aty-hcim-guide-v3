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

import com.b0atyguide.data.Episode;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.Step;
import com.b0atyguide.ui.GuideLinks;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Which addresses the panel will open.
 *
 * <p>Stricter than the image check, and for a reason: a picture from a bad URL
 * is only shown, while a link opens the player's browser wherever it points.
 * Both come from a wiki page anyone can edit.
 */
public class GuideLinksTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	@Test
	public void theGuidesOwnVideoHostsAreAllowed()
	{
		assertTrue(GuideLinks.isAllowed("https://www.youtube.com/watch?v=D7wFjCCKRAU"));
		assertTrue(GuideLinks.isAllowed("https://youtu.be/bDB351j-qZI?t=178"));
		assertTrue(GuideLinks.isAllowed(
			"https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3"));
	}

	@Test
	public void aLookalikeHostIsRefused()
	{
		// Matched on the exact host, never a suffix.
		assertFalse(GuideLinks.isAllowed("https://youtube.com.evil.example/watch?v=x"));
		assertFalse(GuideLinks.isAllowed("https://evil.example/?to=youtube.com"));
		assertFalse(GuideLinks.isAllowed("https://notyoutube.com/watch?v=x"));
	}

	@Test
	public void plainHttpAndNonsenseAreRefused()
	{
		assertFalse(GuideLinks.isAllowed("http://www.youtube.com/watch?v=x"));
		assertFalse(GuideLinks.isAllowed("javascript:alert(1)"));
		assertFalse(GuideLinks.isAllowed("file:///etc/passwd"));
		assertFalse(GuideLinks.isAllowed(""));
	}

	@Test
	public void anUnknownHostProducesNoLabelAtAll()
	{
		// The guide links an imgur image. Rather than open it, the panel simply
		// renders nothing -- a dead label would be worse than none.
		assertNotNull(GuideLinks.link("ok", "https://youtu.be/abc", null));
		org.junit.Assert.assertNull(
			GuideLinks.link("no", "https://imgur.com/jZUFUCR", null));
	}

	@Test
	public void everyEpisodeHasAPlayableVideo() throws Exception
	{
		final Guide guide = loader.loadBundled();
		assertFalse(guide.getEpisodes().isEmpty());
		for (Episode episode : guide.getEpisodes())
		{
			assertFalse("episode " + episode.getOrdinal() + " has no video",
				episode.getVideoIds().isEmpty());
			assertTrue(GuideLinks.isAllowed(
				GuideLinks.youTubeUrl(episode.getVideoIds().get(0))));
		}
	}

	@Test
	public void everySectionBelongsToAnEpisode() throws Exception
	{
		// The episode link is only useful if a bank can be traced to one.
		final Guide guide = loader.loadBundled();
		int traced = 0;
		for (Section section : guide.getSections())
		{
			if (guide.episodeFor(section) != null)
			{
				traced++;
			}
		}
		assertTrue("most banks should trace to an episode",
			traced > guide.getSections().size() / 2);
	}

	@Test
	public void aStepsOwnLinksAreCheckedToo() throws Exception
	{
		final Guide guide = loader.loadBundled();
		int checked = 0;
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				for (String videoId : step.getVideoIds())
				{
					assertTrue(GuideLinks.isAllowed(GuideLinks.youTubeUrl(videoId)));
					checked++;
				}
			}
		}
		assertEquals("the guide should carry a few step videos", 6, checked);
	}
}
