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
import com.google.gson.Gson;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * How current the shipped guide claims to be.
 *
 * <p>Updates are manual, so the panel tells the player when the wiki page was
 * last edited. That date has to be real and has to be the revision's, not the
 * build's -- a build date would claim freshness the content does not have.
 */
public class RevisionDateTest
{
	private final GuideLoader loader = new GuideLoader(new Gson());

	@Test
	public void theGuideKnowsWhenItsSourceWasEdited() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final String iso = guide.getSourceRevisionAt();
		assertNotNull("the panel has nothing to show without this", iso);
		// Parses, which is what the panel needs; an unreadable date is hidden.
		final Instant revision = Instant.parse(iso);
		assertTrue("a revision cannot predate the game",
			revision.isAfter(Instant.parse("2013-01-01T00:00:00Z")));
	}

	@Test
	public void theRevisionIsNotInTheFuture() throws Exception
	{
		final Guide guide = loader.loadBundled();
		final Instant revision = Instant.parse(guide.getSourceRevisionAt());
		assertTrue("a future date means a clock or a parsing problem",
			revision.isBefore(Instant.now().plus(1, ChronoUnit.DAYS)));
	}

	@Test
	public void theRevisionIdIsRecordedToo() throws Exception
	{
		// The tooltip cites it, and it is how a maintainer finds the exact
		// wiki version a report came from.
		assertTrue(loader.loadBundled().getSourceRevid() > 0);
	}
}
