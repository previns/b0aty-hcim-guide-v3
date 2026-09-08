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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Everything the plugin draws or ticks can be turned off here.
 *
 * <p>Grouped by what the reader is deciding rather than by when it was added:
 * what appears on the overlay, what is outlined in the world, what leads you
 * to it, then the shared colour. Positions are explicit because options
 * sharing one render in an arbitrary order.
 *
 * <p>The two hidden keys at the bottom hold saved progress, not settings.
 */
@ConfigGroup(B0atyGuideConfig.GROUP)
public interface B0atyGuideConfig extends Config
{
	String GROUP = "b0atyguide";

	@ConfigSection(
		name = "Highlights",
		description = "What the plugin draws in the game world",
		position = 10
	)
	String highlightSection = "highlights";

	@ConfigSection(
		name = "Panel",
		description = "How the side panel behaves",
		position = 20
	)
	String panelSection = "panel";

	@ConfigItem(
		keyName = "highlightNpcs",
		name = "Highlight NPCs",
		description = "Outline the NPC the current step names, when it is on screen",
		section = highlightSection,
		position = 2
	)
	default boolean highlightNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightBanks",
		name = "Highlight banks on banking steps",
		description =
			"On a step that sends you to a bank, outline every booth, chest and banker in "
				+ "reach. The guide names the bank, not which booth to use.",
		section = highlightSection,
		position = 4
	)
	default boolean highlightBanks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightObjects",
		name = "Highlight objects",
		description = "Outline the scenery the current step names, when it is on screen",
		section = highlightSection,
		position = 3
	)
	default boolean highlightObjects()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightUnconfirmed",
		name = "Highlight unconfirmed names",
		description =
			"Also highlight names the guide's wording implied but the wiki could not confirm. "
				+ "These are matched purely by name against what is on screen, so a wrong guess "
				+ "simply highlights nothing.",
		section = highlightSection,
		position = 10
	)
	default boolean highlightUnconfirmed()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightSpell",
		name = "Ring the spell to cast",
		description =
			"On a step like \"Teleport to Varrock\" or \"Home teleport to Lumbridge\", ring "
				+ "that spell in the spellbook. Only steps that plainly cast one -- a teletab "
				+ "or a jewellery teleport is an item, not a spell, and stays unmarked.",
		section = highlightSection,
		position = 5
	)
	default boolean highlightSpell()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightDialogue",
		name = "Ring the dialogue option to pick",
		description =
			"On a step that writes the answers as a sequence -- \"Talk to Father Aereck "
				+ "(3,1)\" -- colour the option that is due, in the highlight colour, so you "
				+ "do not have to count rows in the chat box.",
		section = highlightSection,
		position = 12
	)
	default boolean highlightDialogue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTargetArrow",
		name = "Arrow over the target",
		description = "Float an arrow above the current step's NPC or object, so it stands out in a crowd",
		section = highlightSection,
		position = 6
	)
	default boolean showTargetArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPath",
		name = "Path to the target",
		description =
			"Draw a walkable route along the ground to the current step's target. The line "
				+ "follows the client's own collision data, so it goes through doorways rather "
				+ "than walls, and stops at the edge of the loaded area.",
		section = highlightSection,
		position = 7
	)
	default boolean showPath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMinimapArrow",
		name = "Arrow on the minimap",
		description =
			"Mark the current step's target on the minimap, in the highlight colour. Replaces "
				+ "the game's flashing yellow hint arrow, which cannot be recoloured.",
		section = highlightSection,
		position = 8
	)
	default boolean showMinimapArrow()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "highlightColor",
		name = "Highlight colour",
		description = "Colour used to outline the current step's target",
		section = highlightSection,
		position = 11
	)
	default Color highlightColor()
	{
		return new Color(0, 200, 83, 180);
	}

	@ConfigItem(
		keyName = "showQuestSteps",
		name = "Show Quest Helper's next quest step",
		description =
			"On steps that name a quest, also show what Quest Helper says to do at your "
				+ "current progress. Dimmed and marked \"(varies)\" where Quest Helper would "
				+ "choose between branches, which this plugin does not evaluate.",
		section = highlightSection,
		position = 1
	)
	default boolean showQuestSteps()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showStepOverlay",
		name = "Show current step overlay",
		description = "Draw the current step over the game world, so the side panel can stay closed",
		section = highlightSection,
		position = 0
	)
	default boolean showStepOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWorldMapPoint",
		name = "Show world map marker",
		description = "Mark the current step's destination on the world map",
		section = highlightSection,
		position = 9
	)
	default boolean showWorldMapPoint()
	{
		return true;
	}

	@ConfigSection(
		name = "Bank",
		description = "What the plugin does at a bank",
		position = 15
	)
	String bankSection = "bank";

	@ConfigItem(
		keyName = "highlightWithdrawItems",
		name = "Ring items you still need",
		description =
			"In the bank and inventory, ring the items this bank's withdraw list asks for that "
				+ "you are not already carrying. Worn equipment counts as carried.",
		section = bankSection,
		position = 0
	)
	default boolean highlightWithdrawItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTeleportItem",
		name = "Ring the item you travel with",
		description =
			"On a step like \"Ardy Cloak -> CKS\", ring the item in your inventory. A category "
				+ "carries every tier, so whichever cloak you own is the one that lights up.",
		section = bankSection,
		position = 1
	)
	default boolean showTeleportItem()
	{
		return true;
	}

	@ConfigItem(
		keyName = "remindAboutSetup",
		name = "Remind me to check the setup image",
		description =
			"Once per bank, a chat line pointing at that bank's setup screenshot. The "
				+ "withdraw list only says what to take out; the screenshot also shows what "
				+ "you should already be wearing, which the guide's wording does not.",
		section = bankSection,
		position = 4
	)
	default boolean remindAboutSetup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightShopItems",
		name = "Ring what to buy in a shop",
		description =
			"With a shop open, ring the item the current step tells you to buy. Matched by "
				+ "item id, so a step whose items the plugin could not identify rings nothing "
				+ "rather than guessing at a name.",
		section = bankSection,
		position = 3
	)
	default boolean highlightShopItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightInterfaces",
		name = "Ring what to click in a menu",
		description =
			"With a quest interface open, ring the part of it the step is about -- the right "
				+ "tool on the cannon, the line of the multi-skill menu, the slot in a shop. "
				+ "Taken from Quest Helper's own marks, so a step it does not mark rings "
				+ "nothing rather than guessing.",
		section = bankSection,
		position = 4
	)
	default boolean highlightInterfaces()
	{
		return true;
	}

	@ConfigItem(
		keyName = "registerBankTags",
		name = "Bank tags for each bank",
		description =
			"Type \"bank150\" in the bank search to see the items that bank needs. The tags are "
				+ "computed from the guide, so your own bank tags are untouched and nothing is "
				+ "left behind if you uninstall. Needs the Bank Tags plugin enabled.",
		section = bankSection,
		position = 2
	)
	default boolean registerBankTags()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSectionImages",
		name = "Show bank setup images",
		description =
			"Load the guide's bank setup screenshots from the wiki's image host. "
				+ "Turning this off means the plugin makes no image requests.",
		section = panelSection,
		position = 2
	)
	default boolean showSectionImages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoTickQuests",
		name = "Auto-tick completed quests",
		description =
			"Tick steps tagged with a quest once the client reports that quest complete. "
				+ "Only quests the client can actually verify are ticked.",
		section = panelSection,
		position = 4
	)
	default boolean autoTickQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFeatures",
		name = "Show what the plugin can do",
		description =
			"A short list at the top of the panel covering the parts that are not "
				+ "discoverable -- searching a bank by number, and right-clicking a step.",
		section = panelSection,
		position = 0
	)
	default boolean showFeatures()
	{
		return true;
	}

	// Below 10 the step text stops being readable; above 22 a single step fills
	// the sidebar. The spinner is the only place a typed number reaches this.
	@Range(min = 10, max = 22)
	@ConfigItem(
		keyName = "fontSize",
		name = "Step text size",
		description = "Point size for step text in the side panel",
		section = panelSection,
		position = 1
	)
	default int fontSize()
	{
		return 14;
	}

	@ConfigItem(
		keyName = "autoTickArrival",
		name = "Auto-tick when you arrive",
		description =
			"Tick the step you are on once you reach where it sent you. Only steps that are "
				+ "nothing but travel -- \"head there and talk to someone\" still needs the talk.",
		section = panelSection,
		position = 9
	)
	default boolean autoTickArrival()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoTickAcquired",
		name = "Auto-tick when you have the items",
		description =
			"Tick the step you are on once you are carrying everything it told you to get. "
				+ "Only steps that plainly say to acquire something and whose items are all "
				+ "known, and only the step you are on -- never one further down the guide.",
		section = panelSection,
		position = 8
	)
	default boolean autoTickAcquired()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoTickSkills",
		name = "Auto-tick reached skill levels",
		description =
			"Tick a step that trains to a level once you reach it. Only steps whose target is "
				+ "a real level -- a boosted one is never reached, so those stay manual.",
		section = panelSection,
		position = 6
	)
	default boolean autoTickSkills()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoTickQuestProgress",
		name = "Auto-tick partial quest steps",
		description =
			"Tick a step that sends you into a quest once that quest moves forward. Only steps "
				+ "the guide tagged with a quest name, and only movement after you reach the "
				+ "step -- a quest you already finished ticks nothing.",
		section = panelSection,
		position = 7
	)
	default boolean autoTickQuestProgress()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoTickDiaries",
		name = "Auto-tick completed diary tasks",
		description =
			"Tick steps whose achievement-diary task the game reports complete. Only tasks "
				+ "with a known completion bit are ticked; the rest stay manual.",
		section = panelSection,
		position = 5
	)
	default boolean autoTickDiaries()
	{
		return true;
	}

	@ConfigItem(
		keyName = "collapseCompleted",
		name = "Collapse completed banks",
		description = "Fold a bank away once every step in it is ticked",
		section = panelSection,
		position = 3
	)
	default boolean collapseCompleted()
	{
		return true;
	}

	@ConfigItem(keyName = "progress", name = "", description = "", hidden = true)
	default String progress()
	{
		return "";
	}

	/**
	 * Steps the player ticked one at a time, as opposed to via "complete bank".
	 * Stored separately so un-completing a bank can keep them.
	 */
	@ConfigItem(keyName = "manualProgress", name = "", description = "", hidden = true)
	default String manualProgress()
	{
		return "";
	}
}
