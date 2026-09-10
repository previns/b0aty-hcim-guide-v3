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
import com.b0atyguide.bank.WithdrawTracker;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Teleport;
import com.b0atyguide.data.Target;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows the current step over the game, so the guide can be followed without
 * the side panel open.
 *
 * <p>The active instruction plus explicitly associated quest side tasks;
 * never an arbitrary preview of the next few guide steps.
 */
public class CurrentStepOverlay extends OverlayPanel
{
	private static final int WIDTH = 220;
	/** Only a few, so the overlay stays a hint rather than a report. */
	private static final int MAX_UNIDENTIFIED_SHOWN = 3;

	/** A quest can want a dozen things; the overlay is a hint, not a list. */
	private static final int MAX_QUEST_ITEMS_SHOWN = 4;

	/** A bank not yet visited is missing everything; the list has to stop somewhere. */
	private static final int MAX_MISSING_SHOWN = 5;

	private final SceneTracker tracker;
	private final B0atyGuideConfig config;
	private final Client client;
	private final WithdrawTracker withdrawTracker;

	/** Set once the guide is loaded; null before that and after shutdown. */
	private Guide guide;
	private final WrappedText stepLines = new WrappedText();
	private final WrappedText questLines = new WrappedText();
	private final WrappedText needsLines = new WrappedText();
	private QuestHelperSteps.Instruction needsFor;
	private String needsText = "";
	private List<Step> sideTasks = Collections.emptyList();
	private final WrappedText sideTaskLines = new WrappedText();
	private String sideTaskText = "";
	private String sideTasksFor;

	public void setSideTasks(Step current, List<Step> tasks)
	{
		sideTasksFor = current == null ? null : current.getId();
		if (!sideTasks.equals(tasks))
		{
			sideTasks = new ArrayList<>(tasks);
			List<String> text = new ArrayList<>();
			for (Step task : tasks) { text.add(task.getText()); }
			sideTaskText = String.join("\n", text);
		}
	}

	@Inject
	CurrentStepOverlay(SceneTracker tracker, B0atyGuideConfig config, Client client,
		WithdrawTracker withdrawTracker)
	{
		this.tracker = tracker;
		this.config = config;
		this.client = client;
		this.withdrawTracker = withdrawTracker;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(WIDTH, 0));
	}

	public void setGuide(Guide guide)
	{
		this.guide = guide;
		needsFor = null;
		needsText = "";
		stepLines.clear();
		questLines.clear();
		needsLines.clear();
		setSideTasks(null, Collections.emptyList());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStepOverlay())
		{
			return null;
		}

		final Step step = tracker.getStep();
		if (step == null)
		{
			return null;
		}

		final String heading = tracker.getSectionLabel();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(heading == null ? "B0aty Guide" : heading)
			.color(config.highlightColor())
			.build());

		// LineComponent does not wrap, and step text routinely runs past the
		// panel width, so the wrapping is done here against the real font.
		for (String line : stepLines.get(step.getText(), graphics.getFontMetrics(), WIDTH - 14))
		{
			panelComponent.getChildren().add(
				LineComponent.builder().left(line).build());
		}

		final Target target = step.getTarget();
		if (target != null && target.getName() != null)
		{
			final boolean visible = onScreen();
			panelComponent.getChildren().add(LineComponent.builder()
				.left(visible ? "On screen" : "Looking for")
				.right(target.getName())
				.leftColor(Color.GRAY)
				.rightColor(visible ? config.highlightColor() : Color.LIGHT_GRAY)
				.build());
		}

		renderTeleport(step);
		renderMissingItems(graphics);
		renderQuestItems(graphics);
		renderUnidentified(graphics);
		renderQuestStep(graphics, step);
		if (step.getId().equals(sideTasksFor) && !sideTaskText.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Along the way")
				.leftColor(config.highlightColor()).build());
			for (String line : sideTaskLines.get(sideTaskText, graphics.getFontMetrics(), WIDTH - 14))
			{
				panelComponent.getChildren().add(LineComponent.builder().left(line).build());
			}
		}
		return super.render(graphics);
	}

	/**
	 * How the step says to travel: "Ardy Cloak", "Fairy Ring".
	 *
	 * <p>Shown even when it is not an item -- a fairy ring or a minecart is
	 * still the answer to "how do I get there", which the destination alone
	 * does not say.
	 */
	private void renderTeleport(Step step)
	{
		final Teleport teleport = step.getTeleport();
		if (teleport == null || teleport.getVia().isEmpty())
		{
			return;
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Travel by")
			.right(teleport.getVia())
			.leftColor(Color.GRAY)
			.rightColor(teleport.isItem() ? config.highlightColor() : Color.LIGHT_GRAY)
			.build());
	}

	/**
	 * What this bank's withdraw list asks for that is not being carried.
	 *
	 * <p>Capped, because a bank you have not visited yet is missing all of it
	 * and a twenty-line overlay is worse than none.
	 */
	private void renderMissingItems(Graphics2D graphics)
	{
		if (!config.highlightWithdrawItems())
		{
			return;
		}
		final List<ItemRef> missing = withdrawTracker.getMissing();
		if (missing.isEmpty())
		{
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Still need")
			.right(Integer.toString(missing.size()))
			.leftColor(Color.GRAY)
			.rightColor(Color.GRAY)
			.build());

		final int shown = Math.min(missing.size(), MAX_MISSING_SHOWN);
		for (int i = 0; i < shown; i++)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(missing.get(i).getName())
				.leftColor(Color.ORANGE)
				.build());
		}
		if (missing.size() > shown)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("and " + (missing.size() - shown) + " more")
				.leftColor(Color.GRAY)
				.build());
		}
	}

	/**
	 * What the quest on this step asks the player to bring.
	 *
	 * <p>Separate from the bank's own list: that says what this bank asks for,
	 * while this is what the quest itself needs, and a player who read only the
	 * withdraw line arrives without it.
	 */
	private void renderQuestItems(Graphics2D graphics)
	{
		final List<String> wanted = withdrawTracker.getQuestItems();
		if (wanted.isEmpty())
		{
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Quest needs")
			.right(Integer.toString(wanted.size()))
			.leftColor(Color.GRAY)
			.rightColor(Color.GRAY)
			.build());
		for (int i = 0; i < Math.min(wanted.size(), MAX_QUEST_ITEMS_SHOWN); i++)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(wanted.get(i))
				.leftColor(Color.ORANGE)
				.build());
		}
	}

	/**
	 * Names this bank asks for that the plugin could not identify.
	 *
	 * <p>Said out loud rather than left silent. Most are category words the
	 * guide uses deliberately -- "Combat gear" is not an item and never will
	 * be -- but from the player's side an unidentified name and a broken
	 * plugin look identical: nothing lights up either way.
	 */
	private void renderUnidentified(Graphics2D graphics)
	{
		final List<String> unknown = withdrawTracker.getUnidentified();
		if (unknown.isEmpty())
		{
			return;
		}

		final int shown = Math.min(unknown.size(), MAX_UNIDENTIFIED_SHOWN);
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Not identified")
			.right(Integer.toString(unknown.size()))
			.leftColor(Color.GRAY)
			.rightColor(Color.GRAY)
			.build());
		for (int i = 0; i < shown; i++)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(unknown.get(i))
				.leftColor(Color.GRAY)
				.build());
		}
	}

	/**
	 * What Quest Helper would say for this quest at the player's current
	 * progress.
	 *
	 * <p>The guide advances quests a step at a time in passing, so the step
	 * text alone ("Start X Marks the Spot on Veos") does not say what to do
	 * next once you are two steps in. The quest's own progress value does.
	 */
	private void renderQuestStep(Graphics2D graphics, Step step)
	{
		if (!config.showQuestSteps() || guide == null)
		{
			return;
		}

		// The tracker's, not a second lookup of its own. Resolving the quest
		// value here as well meant this panel missed everything the plugin had
		// already worked out -- which zone branch applies, and the diary tasks,
		// which have no progress value to look up at all.
		final QuestHelperSteps.Instruction instruction = tracker.getInstruction();
		if (instruction == null || instruction.getText().isEmpty())
		{
			// Twenty-one of Quest Helper's slots are a place with no sentence
			// attached. They still drive the highlight and the path; heading a
			// panel section with nothing under it is not worth the room.
			return;
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Quest step")
			.right(instruction.isConditional() ? "(varies)" : "")
			.leftColor(Color.GRAY)
			.rightColor(Color.GRAY)
			.build());

		// A conditional default is Quest Helper's fallback branch, not the one
		// its conditions would pick, so it is dimmed rather than shown in the
		// same weight as an instruction we can stand behind.
		final Color colour = instruction.isConditional() ? Color.LIGHT_GRAY : Color.WHITE;
		for (String line : questLines.get(instruction.getText(), graphics.getFontMetrics(), WIDTH - 14))
		{
			panelComponent.getChildren().add(
				LineComponent.builder().left(line).leftColor(colour).build());
		}

		// What this step of the quest needs in hand, the way Quest Helper lists
		// it. Per step, not per quest: the rest of the shopping list is not
		// what the player needs at this moment, and showing it all is how the
		// panel stops being read.
		// Instructions are loaded guide data; selecting a different branch gives
		// a different instruction. The same shopping sentence needs no rebuild
		// each frame while that instruction remains current.
		if (instruction != needsFor)
		{
			needsFor = instruction;
			final String needs = needed(instruction);
			needsText = needs.isEmpty() ? "" : "Needs: " + needs;
		}
		if (!needsText.isEmpty())
		{
			for (String line : needsLines.get(needsText, graphics.getFontMetrics(), WIDTH - 14))
			{
				panelComponent.getChildren().add(
					LineComponent.builder().left(line).leftColor(Color.GRAY).build());
			}
		}
	}

	/** "2 x Bucket of milk, Doogle leaves", or empty when the step needs nothing. */
	private static String needed(QuestHelperSteps.Instruction instruction)
	{
		final StringBuilder line = new StringBuilder();
		for (QuestHelperSteps.Need need : instruction.getItems())
		{
			if (line.length() > 0)
			{
				line.append(", ");
			}
			if (need.getCount() > 1)
			{
				line.append(need.getCount()).append(" x ");
			}
			line.append(need.getName());
		}
		return line.toString();
	}

	private boolean onScreen()
	{
		return !tracker.getNpcs().isEmpty() || !tracker.getObjects().isEmpty();
	}

	/**
	 * One bounded cache per paragraph, not a growing cache of guide history.
	 * Wrapping measures every candidate line, which was repeated every frame
	 * despite text normally staying unchanged for minutes. Font and render
	 * context are part of the key: a UI scale or font change must still reflow.
	 */
	static final class WrappedText
	{
		private String text;
		private Font font;
		private FontRenderContext context;
		private int width;
		private List<String> lines;

		void clear()
		{
			text = null;
			font = null;
			context = null;
			lines = null;
		}

		List<String> get(String requested, FontMetrics metrics, int requestedWidth)
		{
			if (!requested.equals(text) || !metrics.getFont().equals(font)
				|| !metrics.getFontRenderContext().equals(context) || width != requestedWidth)
			{
				text = requested;
				font = metrics.getFont();
				context = metrics.getFontRenderContext();
				width = requestedWidth;
				lines = wrap(requested, metrics, requestedWidth);
			}
			return lines;
		}
	}

	/** Greedy word wrap. Words longer than the line get their own line rather than truncating. */
	private static List<String> wrap(String text, FontMetrics metrics, int width)
	{
		final List<String> lines = new ArrayList<>();
		final StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			final String candidate = line.length() == 0 ? word : line + " " + word;
			if (metrics.stringWidth(candidate) > width && line.length() > 0)
			{
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
			else
			{
				line.setLength(0);
				line.append(candidate);
			}
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
	}
}
