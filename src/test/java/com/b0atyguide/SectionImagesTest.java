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

import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.ui.SectionImages;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Which image URLs the plugin is willing to request.
 *
 * <p>The links come from a wiki page anyone can edit, so the host is checked
 * rather than trusted. Without that an editor could point every player's client
 * at an address of their choosing -- which is a much worse outcome than a
 * missing screenshot.
 */
public class SectionImagesTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	@Test
	public void theGuidesOwnImagesAreAllowed()
	{
		assertTrue(SectionImages.isAllowed("https://i.ibb.co/np0HBqN/image.png"));
	}

	@Test
	public void anotherHostIsRefused()
	{
		assertFalse(SectionImages.isAllowed("https://example.com/image.png"));
		assertFalse(SectionImages.isAllowed("https://i.ibb.co.evil.example/image.png"));
		assertFalse(SectionImages.isAllowed("https://evil.example/?x=i.ibb.co"));
	}

	@Test
	public void plainHttpIsRefused()
	{
		// Downgrading to http would let anyone on the path swap the image.
		assertFalse(SectionImages.isAllowed("http://i.ibb.co/np0HBqN/image.png"));
	}

	@Test
	public void nonsenseIsRefusedRatherThanThrowing()
	{
		assertFalse(SectionImages.isAllowed("not a url"));
		assertFalse(SectionImages.isAllowed(""));
		assertFalse(SectionImages.isAllowed("file:///etc/passwd"));
	}

	@Test
	public void everyImageInTheShippedGuidePassesTheCheck() throws Exception
	{
		// If this fails, either the wiki gained a new image host or someone
		// edited a link to point elsewhere. Both want a human to look.
		final Guide guide = loader.loadBundled();
		int checked = 0;
		for (Section section : guide.getSections())
		{
			for (String url : section.getImageUrls())
			{
				assertTrue("unexpected image host: " + url, SectionImages.isAllowed(url));
				checked++;
			}
		}
		assertTrue("the guide should ship some screenshots", checked > 100);
	}

	@Test
	public void everyShippedImageHasARecordedHash() throws Exception
	{
		// A URL with no hash is refused at runtime, so a missing one is a
		// silently blank screenshot rather than a loud failure.
		final Guide guide = loader.loadBundled();
		int checked = 0;
		for (Section section : guide.getSections())
		{
			for (String url : section.getImageUrls())
			{
				final String hash = guide.imageHashFor(url);
				assertNotNull("no recorded hash for " + url, hash);
				assertEquals("sha256 should be 64 hex chars", 64, hash.length());
				checked++;
			}
		}
		assertTrue(checked > 100);
	}

	@Test
	public void anUnknownUrlHasNoHash() throws Exception
	{
		assertNull(loader.loadBundled().imageHashFor("https://i.ibb.co/nope/x.png"));
	}
}
