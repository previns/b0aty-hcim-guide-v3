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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Rings a rectangle in the highlight colour: a translucent wash inside, a solid
 * border just outside it.
 *
 * <p>Used for the two things the plugin marks in an interface rather than the
 * world -- an item to withdraw and a spell to cast. They are unrelated
 * features, but a player should not have to learn two different marks, so they
 * share the drawing and differ only in how far the border sits from the icon.
 */
public final class Ring
{
	/** Enough wash to read at a glance without hiding the item underneath. */
	private static final int FILL_ALPHA = 60;

	private Ring()
	{
	}

	/**
	 * @param padding how far outside {@code bounds} the border sits; inventory
	 *                icons sit closer together than spell icons do
	 */
	public static void draw(Graphics2D graphics, Rectangle bounds, Color colour, int padding)
	{
		graphics.setColor(
			new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), FILL_ALPHA));
		graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics.setColor(colour);
		graphics.drawRect(
			bounds.x - padding, bounds.y - padding,
			bounds.width + padding, bounds.height + padding);
	}
}
