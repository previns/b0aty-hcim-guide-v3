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
import com.b0atyguide.data.Spell;
import com.b0atyguide.data.Step;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Rings the spell a step says to cast.
 *
 * <p>"Teleport to Varrock" names where to go and not how, because on the
 * standard spellbook there is one answer -- so the plugin can point at it,
 * which is the spellbook half of what the inventory highlight already does for
 * a cloak or a necklace.
 *
 * <p>Drawn only while that spell is actually on screen. A hidden widget has
 * stale bounds, and drawing from them paints a box over whatever is there now.
 */
public class SpellOverlay extends Overlay
{
	private static final int PADDING = 2;

	@Inject
	private Client client;

	@Inject
	private SceneTracker tracker;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	public SpellOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		// The spellbook is a widget, so anything under this layer is hidden by
		// it rather than drawn on it.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightSpell())
		{
			return null;
		}

		final Step step = tracker.getStep();
		final Spell spell = step == null ? null : step.getSpell();
		if (spell == null || !spell.isUsable())
		{
			return null;
		}

		final Widget widget = client.getWidget(spell.getWidget());
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		final Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.isEmpty())
		{
			return null;
		}

		Ring.draw(graphics, bounds, config.highlightColor(), PADDING);
		return null;
	}
}
