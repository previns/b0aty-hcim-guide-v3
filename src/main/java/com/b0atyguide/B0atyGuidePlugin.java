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

import com.b0atyguide.bank.GuideBankTags;
import com.b0atyguide.bank.SetupReminder;
import com.b0atyguide.bank.ShopOverlay;
import com.b0atyguide.bank.WithdrawOverlay;
import com.b0atyguide.bank.WithdrawTracker;
import com.b0atyguide.data.Completion;
import com.b0atyguide.data.Guide;
import com.b0atyguide.data.GuideLoader;
import com.b0atyguide.data.Section;
import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.progress.HeldItems;
import java.util.List;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import com.b0atyguide.overlay.GroundItemTracker;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.Item;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.ItemContainer;
import javax.swing.SwingUtilities;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Tag;
import com.b0atyguide.overlay.ApproachTracker;
import com.b0atyguide.overlay.BankTracker;
import com.b0atyguide.overlay.CurrentStepOverlay;
import com.b0atyguide.overlay.DialogueHighlighter;
import com.b0atyguide.overlay.HighlightOverlay;
import com.b0atyguide.overlay.InterfaceOverlay;
import com.b0atyguide.overlay.MinimapOverlay;
import com.b0atyguide.overlay.PathOverlay;
import com.b0atyguide.overlay.GuideIcon;
import com.b0atyguide.overlay.SceneTracker;
import com.b0atyguide.overlay.SpellOverlay;
import com.b0atyguide.overlay.WorldMapMarker;
import com.b0atyguide.path.PathTracker;
import com.b0atyguide.path.RealPoint;
import com.b0atyguide.progress.AutoTick;
import com.b0atyguide.progress.Progress;
import com.b0atyguide.progress.QuestProgress;
import com.b0atyguide.ui.GuidePanel;
import com.b0atyguide.ui.SectionImages;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

/**
 * Follows the B0aty HCIM Guide V3 in game.
 *
 * <p>The plugin is a <b>renderer over one data file</b>. Every step, item id,
 * coordinate and quest reference lives in {@code guide.json}, which is built
 * from the OSRS Wiki in a separate repository. Nothing here decides what the
 * guide says.
 *
 * <p>Two rules are worth knowing before changing this class, because both are
 * invisible in the API and each has broken the plugin once:
 *
 * <ul>
 *   <li>{@code Quest.getState} runs a client script, and the client refuses to
 *       run one from inside another. {@code VarbitChanged} is posted from
 *       inside script execution, so the quest pass is deferred to
 *       {@code GameTick}.
 *   <li>{@code Client.getItemContainer} requires the client thread, and
 *       {@code startUp()} runs on the event thread. Reads of it are deferred.
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "B0aty HCIM Guide v3",
	description = "Follow the B0aty HCIM Guide V3 in game, synced from the OSRS Wiki",
	tags = {"ironman", "hcim", "guide", "quest", "skilling", "route", "b0aty", "checklist"}
)
public class B0atyGuidePlugin extends Plugin
{
	private static final String PROGRESS_KEY = "progress";
	private static final String MANUAL_KEY = "manualProgress";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	private Gson gson;

	@Inject
	private QuestProgress questProgress;

	@Inject
	private SectionImages sectionImages;

	@Inject
	private SceneTracker sceneTracker;

	@Inject
	private MinimapOverlay minimapOverlay;

	@Inject
	private PathOverlay pathOverlay;

	@Inject
	private SpellOverlay spellOverlay;

	@Inject
	private PathTracker pathTracker;

	@Inject
	private ApproachTracker approachTracker;

	@Inject
	private BankTracker bankTracker;

	@Inject
	private WorldMapMarker worldMapMarker;

	@Inject
	private DialogueHighlighter dialogueHighlighter;

	@Inject
	private GroundItemTracker groundItems;

	@Inject
	private ShopOverlay shopOverlay;

	@Inject
	private InterfaceOverlay interfaceOverlay;



	@Inject
	private SetupReminder setupReminder;

	/** The Quest Helper instruction the trackers were last pointed at. */
	private QuestHelperSteps.Instruction lastInstruction;

	/**
	 * Where the player was standing as of the last tick.
	 *
	 * <p>Read rather than {@link Client#getLocalPlayer()} because the step can be
	 * re-chosen from the panel, which runs on Swing's thread, and asking the
	 * client for the local player there is an assertion failure -- the same rule
	 * that already applies to item containers. Written on the client thread and
	 * volatile so the panel's thread sees it; a tick stale, which is far less
	 * than the distance that decides any of the branches it feeds.
	 */
	private volatile WorldPoint playerAt;

	/**
	 * The step the guide is on.
	 *
	 * <p>Held here rather than read back from {@link SceneTracker}, which is
	 * where it used to come from. That tracker is written on the client thread
	 * and the step is chosen on Swing's, so for the tick in between it still
	 * answered with the step before -- and the tick in between is exactly when
	 * the quest refresh runs. The observed result: the step advanced
	 * to "take Jug of Water", and ten milliseconds later the guidance went back
	 * to the shopkeeper from the step before it and stayed there, until the
	 * player un-ticked a step they had already done because the plugin was
	 * insisting on it.
	 */
	private volatile Step currentStep;

	/**
	 * How close counts as arrived.
	 *
	 * <p>Ten tiles: a destination is a building or a bank rather than a square,
	 * and the guide's coordinate is wherever the wiki put its map marker.
	 */
	private static final int ARRIVAL_TILES = 10;

	@Inject
	private GuideBankTags bankTags;

	@Inject
	private WithdrawTracker withdrawTracker;

	@Inject
	private WithdrawOverlay withdrawOverlay;

	@Inject
	private HighlightOverlay highlightOverlay;

	@Inject
	private CurrentStepOverlay currentStepOverlay;

	private GuidePanel panel;
	private NavigationButton navButton;
	private Guide guide;
	private Progress progress = new Progress();

	/**
	 * A varbit changed, so a quest may have completed -- but the check has to
	 * wait for a tick, because Quest.getState cannot run inside the script that
	 * posted the event.
	 */
	private boolean questCheckPending;

	@Provides
	B0atyGuideConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(B0atyGuideConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		guide = new GuideLoader(gson).loadBundled();
		// INFO, not DEBUG: this plugin's entire job is loading a data file, so
		// "which revision, how many steps" is the first thing anyone diagnosing
		// it needs, and it is one line per session.
		log.info("loaded guide revid {} ({} sections, {} steps, hash {})",
			guide.getSourceRevid(), guide.getSections().size(),
			guide.getStepCount(), guide.getContentHash());

		sectionImages.setExpectedHashes(guide::imageHashFor);
		loadProgress();

		panel = injector.getInstance(GuidePanel.class);
		panel.init(guide, progress, this::onStepToggled, this::onStepSelected,
			this::onSectionToggled, config::fontSize, config::collapseCompleted,
			sectionImages, config::showSectionImages,
			config::showFeatures, this::onFeaturesDismissed);

		// One 56x56 asset, resized per use: the sidebar wants a small square,
		// the world map wants something larger.
		final BufferedImage icon = ImageUtil.resizeImage(
			ImageUtil.loadImageResource(B0atyGuidePlugin.class, GuideIcon.RESOURCE),
			GuideIcon.SIDEBAR, GuideIcon.SIDEBAR);
		navButton = NavigationButton.builder()
			.tooltip("B0aty HCIM Guide v3")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(highlightOverlay);
		currentStepOverlay.setGuide(guide);
		overlayManager.add(currentStepOverlay);
		overlayManager.add(minimapOverlay);
		overlayManager.add(pathOverlay);
		overlayManager.add(withdrawOverlay);
		overlayManager.add(spellOverlay);
		overlayManager.add(shopOverlay);
		overlayManager.add(interfaceOverlay);
		if (config.registerBankTags())
		{
			bankTags.register(guide);
		}

		selectCurrentStep();
	}

	@Override
	protected void shutDown()
	{
		// startUp() can fail part way -- a bad guide.json throws before the nav
		// button exists -- and RuneLite calls shutDown() regardless. Removing a
		// null navigation throws inside ClientUI on the EDT, which surfaces as
		// an uncaught exception with none of our frames in the trace.
		overlayManager.remove(highlightOverlay);
		overlayManager.remove(currentStepOverlay);
		currentStepOverlay.setGuide(null);
		overlayManager.remove(minimapOverlay);
		overlayManager.remove(pathOverlay);
		overlayManager.remove(withdrawOverlay);
		overlayManager.remove(spellOverlay);
		overlayManager.remove(shopOverlay);
		overlayManager.remove(interfaceOverlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		releaseTrackers();
		if (panel != null)
		{
			panel.deinit();
		}
		panel = null;
		guide = null;
	}

	// --- progress ----------------------------------------------------------

	/**
	 * Progress is stored per RuneLite profile, so an alt keeps its own ticks
	 * without the plugin needing the account hash before login.
	 */
	private void loadProgress()
	{
		progress = new Progress(parseIds(config.progress()), parseIds(config.manualProgress()));

		final int migrated = progress.applyMigrations(guide.getMigrations());
		final int pruned = progress.prune(guide);
		if (migrated > 0 || pruned > 0)
		{
			log.debug("progress: {} step(s) migrated, {} dropped", migrated, pruned);
			saveProgress();
		}
	}

	private static Set<String> parseIds(String stored)
	{
		final Set<String> ids = new LinkedHashSet<>();
		if (stored != null && !stored.isEmpty())
		{
			ids.addAll(Arrays.asList(stored.split(",")));
			ids.remove("");
		}
		return ids;
	}

	private void saveProgress()
	{
		configManager.setConfiguration(
			B0atyGuideConfig.GROUP, PROGRESS_KEY, String.join(",", progress.completedIds()));
		configManager.setConfiguration(
			B0atyGuideConfig.GROUP, MANUAL_KEY, String.join(",", progress.manualIds()));
	}

	/**
	 * Tick or untick a whole bank.
	 *
	 * <p>Un-ticking keeps the steps the player ticked individually, so pressing
	 * this by accident never loses work.
	 */
	private void onSectionToggled(Section section, boolean complete)
	{
		if (progress.setSectionComplete(section, complete))
		{
			saveProgress();
		}
		selectCurrentStep();
	}

	private void onStepToggled(Step step, boolean complete)
	{
		progress.setComplete(step.getId(), complete);
		saveProgress();
		selectCurrentStep();
	}

	/**
	 * The player closed the feature list. Written to config so it stays closed,
	 * and findable again under Panel in the settings.
	 */
	private void onFeaturesDismissed(boolean show)
	{
		configManager.setConfiguration(
			B0atyGuideConfig.GROUP, "showFeatures", Boolean.toString(show));
	}

	private void onStepSelected(Step step)
	{
		currentStep = step;
		lastInstruction = instructionFor(step);
		sceneTracker.setStep(step, labelFor(step), lastInstruction);
		clientThread.invokeLater(() -> groundItems.setStep(step, lastInstruction));
		worldMapMarker.setStep(step, lastInstruction);
		pathTracker.clear();
	}

	/**
	 * What Quest Helper says to do on this step right now, or null.
	 *
	 * <p>Reads the quest's own progress value, which is a varbit or VarPlayer
	 * lookup and so safe from anywhere. The instruction changes as the quest
	 * advances, which is why it is re-read rather than resolved once when the
	 * step is selected.
	 */
	private QuestHelperSteps.Instruction instructionFor(Step step)
	{
		if (step == null || guide == null || !config.showQuestSteps()
			|| QuestProgress.explicitGoalSatisfied(guide, step, client::getVarbitValue,
				client::getVarpValue, withdrawTracker.carried()))
		{
			return null;
		}
		// Only where the step is the quest, or is nested inside one. The build
		// attaches a quest helper wherever the guide names a quest, and it names
		// them in passing all the time -- "Head to the Grand Tree" is a walk,
		// and answering it with "talk to King Narnode Shareen" sent players off
		// after an npc the guide was not asking for yet.
		//
		// A sub-step is the other way round: "Take 1 extra Rotten Apple" under
		// "Start Biohazard until you get the samples" is done while running
		// Biohazard, so that is the quest to show, not the one its own bracket
		// names.
		final String key = step.isQuestStep()
			? step.getQuestHelper() : step.getQuestContext();
		final QuestHelperSteps helper = key == null ? null : guide.getQuestHelpers().get(key);
		final QuestHelperSteps.Var var = helper == null ? null : helper.getVar();

		QuestHelperSteps.Instruction instruction = null;
		if (var != null)
		{
			instruction = helper.at(var.isVarbit()
				? client.getVarbitValue(var.getId())
				: client.getVarpValue(var.getId()));
		}
		if (instruction == null)
		{
			// A diary task has no progress value -- it is done or it is not --
			// so Quest Helper's step for it stands until the bit is set, and
			// setting that bit is already what ticks the guide's step off.
			instruction = guide.diaryTaskFor(step);
		}
		if (instruction == null)
		{
			return null;
		}

		// Where the player is standing and what they are carrying decide between
		// a conditional's branches, wherever Quest Helper's own condition was a
		// zone, an item, or both. Downstairs it is "climb the ladder"; upstairs
		// it is the cat the milk is for; and with the kitten in hand it is
		// "return the kitten to Gertrude's cat".
		// Reading a var is an array lookup, so this is safe from Swing's thread
		// as well as the client's -- the same reason the quest's own progress
		// value is read here rather than deferred.
		// Several branches can ask about scenery. Share one lazy index during
		// this evaluation only; the next call must see despawns and impostors.
		final SceneTracker.PresenceCheck presence = sceneTracker.conditionCheck();
		return instruction.now(playerAt, QuestProgress.guidanceInventory(step, withdrawTracker.carried()),
			new QuestHelperSteps.Vars()
			{
				@Override
				public int varbit(int id)
				{
					return client.getVarbitValue(id);
				}

				@Override
				public int varplayer(int id)
				{
					return client.getVarpValue(id);
				}

				@Override
				public boolean here(String kind, List<Integer> ids,
					List<List<Integer>> zone)
				{
					return presence.isPresent(kind, ids, zone);
				}

				@Override
				public boolean interfaceOpen(int id)
				{
					final Widget widget = client.getWidget(id);
					return widget != null && !widget.isHidden();
				}
			});
	}

	/**
	 * Re-point the trackers when the quest moves under the current step.
	 *
	 * <p>Without this the highlight stays on the NPC that started the quest
	 * while the game is waiting on the next one, which is most of a quest.
	 */
	private void refreshQuestInstruction()
	{
		final Step current = currentStep;
		if (current == null)
		{
			return;
		}
		final QuestHelperSteps.Instruction now = instructionFor(current);
		if (now == lastInstruction)
		{
			return;
		}
		lastInstruction = now;
		// The instruction moving can change what is on the floor to look for,
		// and where the map should be pointing.
		groundItems.setStep(current, now);
		worldMapMarker.setStep(current, now);
		sceneTracker.setStep(current, labelFor(current), now);
		pathTracker.clear();
	}

	/** "Bank 12" for the overlay heading, or null when the step is not in a section. */
	private String labelFor(Step step)
	{
		final Section section = progress.sectionOf(guide, step);
		return section == null ? null : section.getDisplayLabel();
	}

	private void selectCurrentStep()
	{
		final Step current = progress.firstIncompleteStep(guide);
		currentStep = current;
		lastInstruction = instructionFor(current);
		sceneTracker.setStep(current, labelFor(current), lastInstruction);
		clientThread.invokeLater(() -> groundItems.setStep(current, lastInstruction));
		worldMapMarker.setStep(current, lastInstruction);
		withdrawTracker.update(guide, current);
		setupReminder.onBankChanged(progress.sectionOf(guide, current));
		pathTracker.clear();
		if (panel != null)
		{
			panel.refresh(current);
		}
	}

	// --- auto-ticking ------------------------------------------------------

		/**
	 * Tick steps whose quest the client reports finished.
	 *
	 * <p><b>Never call this from inside a client script.</b> {@link
	 * Quest#getState} runs one, and the client refuses to run a script from
	 * within another -- it throws "scripts are not reentrant" and takes the
	 * client thread with it, which the player sees as the game hanging on
	 * "Please wait" at login. {@code VarbitChanged} in particular is posted
	 * from inside script execution; {@code GameTick} is not.
	 */
	private void syncQuestCompletion()
	{
		if (!config.autoTickQuests() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		boolean changed = false;
		// A quest can occur in many guide steps. getState runs a client script,
		// so ask once per quest in this pass, not once per occurrence in the
		// guide. The map is deliberately not retained across varbit changes.
		final Map<Quest, QuestState> states = new EnumMap<>(Quest.class);
		for (Section section : guide.getSections())
		{
			for (Step step : section.getSteps())
			{
				if (progress.isComplete(step.getId()))
				{
					continue;
				}
				// The step has to BE the quest. The guide names quests as the
				// reason for a step constantly -- "Buy 2x Bronze Med Helm
				// [Black Knights Fortress, Mournings End Pt II, Kings Ransom]"
				// -- and finishing any one of those would otherwise tick a step
				// whose helmets were never bought.
				final Tag tag = QuestProgress.canCompleteFromQuestState(step) ? step.getVerifiableQuest() : null;
				if (tag == null)
				{
					continue;
				}
				final Quest quest = questFor(tag.getConstant());
				if (quest != null
					&& states.computeIfAbsent(quest, q -> q.getState(client)) == QuestState.FINISHED)
				{
					progress.setComplete(step.getId(), true);
					changed = true;
				}
			}
		}

		if (changed)
		{
			saveProgress();
			selectCurrentStep();
		}
	}

	/**
	 * Tick the current step when the quest it names moves forward.
	 *
	 * <p>Reads varbits and varplayers only, so it is safe from inside a client
	 * script -- unlike the quest-state check, which runs one.
	 */
	private void syncQuestProgress()
	{
		if (!config.autoTickQuestProgress() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final Step current = progress.firstIncompleteStep(guide);
		// Which of Quest Helper's own steps is being shown right now, branches
		// and all. Re-read rather than taken from lastInstruction: a branch can
		// turn over between ticks without the step changing, and that turning
		// over is precisely the event this is watching for.
		final QuestHelperSteps.Instruction showing = instructionFor(current);
		final Integer panelNow = showing == null ? null : showing.getPanel();
		if (questProgress.hasAdvanced(guide, current, client::getVarbitValue,
			client::getVarpValue, panelNow, withdrawTracker.carried()))
		{
			progress.setComplete(current.getId(), true);
			questProgress.clear();
			saveProgress();
			selectCurrentStep();
			return;
		}
		questProgress.watch(guide, current, client::getVarbitValue, client::getVarpValue,
			panelNow);
	}

	/**
	 * Tick steps whose skill target has been reached.
	 *
	 * <p>Reads a level, which is a plain lookup -- safe from inside a client
	 * script, like the diary bits and unlike the quest state.
	 */
	private void syncSkillCompletion()
	{
		if (!config.autoTickSkills() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (AutoTick.completions(guide, progress, client::getVarpValue, this::realLevel,
			Completion.KIND_SKILL) > 0)
		{
			saveProgress();
			selectCurrentStep();
		}
	}

	/**
	 * A real skill level by name, or 0 when the guide names one this client
	 * does not have -- which only happens if the data was built against a
	 * newer RuneLite.
	 */
	private int realLevel(String skillName)
	{
		try
		{
			return client.getRealSkillLevel(Skill.valueOf(skillName));
		}
		catch (IllegalArgumentException e)
		{
			log.debug("unknown skill {}", skillName);
			return 0;
		}
	}

	/**
	 * Tick diary steps whose bit the game has set.
	 *
	 * <p>Safe from anywhere, including inside a running script: reading a
	 * VarPlayer is an array lookup.
	 */
	private void syncDiaryCompletion()
	{
		if (!config.autoTickDiaries() || guide == null
			|| client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (AutoTick.completions(guide, progress, client::getVarpValue, this::realLevel,
			Completion.KIND_DIARY) > 0)
		{
			saveProgress();
			selectCurrentStep();
		}
	}

	private Quest questFor(String constant)
	{
		try
		{
			return Quest.valueOf(constant);
		}
		catch (IllegalArgumentException e)
		{
			// The data was generated against a newer RuneLite than this build.
			// Not an error worth bothering the player about.
			log.debug("unknown quest constant {}", constant);
			return null;
		}
	}

	// --- events ------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		sceneTracker.onGameStateChanged(event);
		if (event.getGameState() == GameState.LOADING
			|| event.getGameState() == GameState.HOPPING)
		{
			// The scene is rebuilt, so every cached scan is stale.
			approachTracker.onSceneChanged();
			groundItems.onSceneChanged();
			bankTracker.onSceneChanged();
			pathTracker.onSceneChanged();
			pathTracker.clear();
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() ->
			{
				syncQuestCompletion();
				syncDiaryCompletion();
				syncSkillCompletion();
				syncQuestProgress();
			});
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// First of all: everything below decides using where the player is, and
		// the panel's thread reads this same snapshot rather than the client.
		playerAt = RealPoint.of(client, client.getLocalPlayer());

		// Order matters. Before the trackers, because a quest that moved
		// changes what they are looking for and doing it after would leave them
		// a tick behind; and the path and the arrows both ask the approach
		// tracker where they are really heading.
		dialogueHighlighter.onTick();
		refreshQuestInstruction();
		syncArrived();
		// Acquiring an explicit quest item need not move a varbit (Waterfall's
		// book is received before it is read). Check the fresh carried snapshot
		// each game tick as well as responding to quest progress events.
		syncQuestProgress();
		approachTracker.update();
		bankTracker.update(sceneTracker.getStep());
		pathTracker.update();
		// Deferred out of onVarbitChanged, which runs inside a client script
		// where Quest.getState is not allowed. A tick's delay is invisible.
		if (questCheckPending)
		{
			questCheckPending = false;
			syncQuestCompletion();
		}
	}

	/** A wanted item hit the floor -- the chicken's bones and feather. */
	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundItems.onItemSpawned(event);
	}

	/** Someone picked it up, which may or may not have been this player. */
	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItems.onItemDespawned(event);
	}

	/** What the player is carrying changed, so the missing list has too. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (guide != null)
		{
			withdrawTracker.update(guide, progress.firstIncompleteStep(guide));
			syncAcquired();
		}
	}

	/**
	 * Tick the current step once the player is standing where it sent them.
	 *
	 * <p>Only steps the build marked as pure travel, and only the current one.
	 * The radius is generous because a destination is a building or a bank, not
	 * a tile, and stopping a few squares short still means you got there.
	 */
	private void syncArrived()
	{
		if (!config.autoTickArrival() || guide == null)
		{
			return;
		}

		final Step current = progress.firstIncompleteStep(guide);
		final WorldPoint where = current == null ? null : current.getArrivesAt();
		final WorldPoint at = playerAt;
		if (where == null || at == null)
		{
			return;
		}

		if (at.getPlane() == where.getPlane() && at.distanceTo(where) <= ARRIVAL_TILES)
		{
			progress.setComplete(current.getId(), true);
			saveProgress();
			selectCurrentStep();
		}
	}

	/**
	 * Tick the current step once the player is holding what it told them to get.
	 *
	 * <p>Only the current step, deliberately. Sweeping the guide would tick any
	 * future step whose items you happen to be carrying -- a later "Withdraw:
	 * Coins" would complete itself the moment you picked up a coin, hundreds of
	 * banks early.
	 *
	 * <p>Reads the item containers, so it needs the client thread.
	 */
	private void syncAcquired()
	{
		if (!config.autoTickAcquired() || guide == null)
		{
			return;
		}

		final Step current = progress.firstIncompleteStep(guide);
		if (current == null || !current.isAcquires())
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			final Map<Integer, Integer> held = new HashMap<>();
			tally(held, client.getItemContainer(InventoryID.INV));
			tally(held, client.getItemContainer(InventoryID.WORN));
			// Inventory and equipment each post their own change, so this
			// arrives twice for one pickup. Ticking twice is harmless but it
			// saved twice and re-chose the step twice, and it made the log read
			// as though the guide had jumped two steps.
			if (HeldItems.satisfied(current, held) && !progress.isComplete(current.getId()))
			{
				progress.setComplete(current.getId(), true);
				saveProgress();
				SwingUtilities.invokeLater(this::selectCurrentStep);
			}
		});
	}

	/** Add a container's contents to the running count, worn equipment included. */
	private static void tally(Map<Integer, Integer> into, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() >= 0)
			{
				// Not "> 0". An empty slot is -1; zero is Dwarf remains, the
				// first item in the game, and excluding it meant nobody could
				// ever be holding one. Quest Helper's Dwarf Cannon branches on
				// exactly that item, so the guide kept saying "get the dwarf
				// remains at the top of the tower" to a player carrying them.
				into.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}
	}

	/** A level went up, which is the only thing that can finish a skill step. */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		syncSkillCompletion();
	}

	/**
	 * Diary bits, skill targets and quest vars all flip the moment the work is
	 * done, so waiting for the next login would leave a finished step un-ticked.
	 *
	 * <p>Quest completion is the exception: it is deferred to the next game tick
	 * because this runs inside a client script, where Quest.getState is not
	 * allowed.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		syncDiaryCompletion();
		syncSkillCompletion();
		syncQuestProgress();
		// Most doors replace their scene object when opened, but an impostor can
		// also change its actions in place when a varbit flips. The route's cached
		// Open/Slash scan has to be rebuilt on the next tick in either case.
		pathTracker.onSceneObjectChanged();
		questCheckPending = true;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		sceneTracker.onNpcSpawned(event);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		sceneTracker.onNpcDespawned(event);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		sceneTracker.onGameObjectSpawned(event);
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		sceneTracker.onGameObjectDespawned(event);
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		sceneTracker.onWallObjectSpawned(event);
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		sceneTracker.onGroundObjectSpawned(event);
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		pathTracker.onSceneObjectChanged();
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		pathTracker.onSceneObjectChanged();
	}

	/**
	 * Drops everything the trackers hold between sessions.
	 *
	 * <p>Each one caches references into the loaded scene -- NPCs, TileObjects,
	 * decoded images, quest baselines -- and a player can toggle the plugin off
	 * and on without the client restarting. Anything not released here is held
	 * for the rest of the session.
	 */
	private void releaseTrackers()
	{
		bankTags.clear();
		withdrawTracker.clear();
		withdrawOverlay.clear();
		pathTracker.clear();
		approachTracker.clear();
		bankTracker.clear();
		questProgress.clear();
		sectionImages.clear();
		worldMapMarker.clear();
		sceneTracker.clear();
		groundItems.clear();
		setupReminder.clear();
		dialogueHighlighter.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!B0atyGuideConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("registerBankTags".equals(event.getKey()))
		{
			if (config.registerBankTags())
			{
				bankTags.register(guide);
			}
			else
			{
				bankTags.clear();
			}
		}
		if (panel != null)
		{
			panel.refresh(progress.firstIncompleteStep(guide));
		}
	}
}
