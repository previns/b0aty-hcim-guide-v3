# Architecture

## What this repo is

The RuneLite plugin half of the B0aty HCIM Guide project. It is a **renderer**
over `src/main/resources/guide.json`, which is built in the sibling
`b0aty-guide-data` repo from the OSRS Wiki.

Changing what the guide says never means changing Java. If you find yourself
writing guide content here, stop -- it belongs in the wiki, or in the data
repo's `curated/`.

## Build

```
./gradlew build          # compile + tests, no client needed
./gradlew test
./gradlew run            # dev client with this plugin side-loaded
./gradlew shadowJar      # an all-in-one jar, to hand someone a build
```

`build.gradle` follows the RuneLite plugin template, including the
`pluginMainClass` variable both tasks read -- the hub's instructions tell
authors to keep that in step with the test class name.

Because `build=standard` replaces `build.gradle` and `settings.gradle` at
submission, local build choices never reach the hub.

Compiled with `options.release.set(11)`. RuneLite targets Java 11 bytecode, and
`--release` -- rather than source/target -- stops a newer local JDK linking
against APIs the client will not have at runtime.

**There is no RuneLite checkout.** The client is an ordinary dependency from
`repo.runelite.net`, and `ExternalPluginManager.loadBuiltin()` registers this
plugin the way the plugin hub would. The `run` task and the IntelliJ
configuration in `.run/` both launch `B0atyGuidePluginTest.main`.

Both pass `-ea`. `loadBuiltin()` throws without assertions enabled, on purpose,
so any new run configuration needs it in the VM options too.

Neither passes `--debug`. That flag raises the *root* logger to DEBUG, which
buries this plugin's handful of log lines under every other plugin, the cache
loader and the net layer. `src/test/resources/logback-test.xml` puts
`com.b0atyguide` alone at DEBUG instead, so the console is either this plugin
talking or something the client meant to say. That file is test-only and must
stay that way: a plugin has no business dictating logging to its host, and a
test asserts nothing named `logback` exists under `src/main`.

In IntelliJ, open the **plugin folder** rather than its parent, or Gradle will
not find the project. Set both the project SDK and the Gradle JVM to JDK 11;
a mismatch between them is the usual cause of the IDE showing errors that
`./gradlew build` does not. The **RuneLite (dev client)** configuration in
`.run/` appears automatically on a fresh clone. `.idea/` is git-ignored, so
nothing machine-specific reaches the repo.

`repositories` deliberately omits `mavenLocal()`. A partial LWJGL 3.3.2 in a
developer's `~/.m2` -- every native classifier except one -- shadows Maven
Central for the whole module, and the runtime classpath then fails to resolve
with an error naming only `.m2`. Add `mavenLocal()` back only if you start
publishing a local RuneLite build.

## The safety rule this plugin exists to honour

**Match by name; trust IDs only when the wiki confirmed them.**

`guide.json` marks every target with a `confidence`:

| confidence | meaning | carries IDs? |
|---|---|---|
| `linked` | the guide author linked the wiki page | yes |
| `wiki-exact` | exact wiki page title | yes |
| `wiki-redirect` | reached via a wiki-authored redirect | yes |
| `inferred` | the extraction grammar guessed the name | **no** |
| `manual` | a human wrote it in `curated/overrides.yaml` | yes |

`SceneTracker` matches an `inferred` name against `npc.getName()` and
`ObjectComposition.getName()` in the loaded scene. When the guess is wrong it
matches nothing and nothing is drawn. **A bad extraction costs a missing
highlight, never a highlight on the wrong thing.**

Never synthesise an ID from a name in Java. Never present an `inferred` target
as though it were confirmed.

## What auto-ticks, and why only that

Three signals, all read from state the game sets:

1. **Quests** — `Quest.getState(client) == FINISHED`.
2. **Achievement diary tasks** — a bit in a per-region `VarPlayer`, carried in
   `step.completion` as `{varplayer, bit}`. The *numbers* travel in the data
   because Plugin Hub forbids reflection, so the client cannot resolve a
   constant name at runtime.
3. **Quest progress** (`QuestProgress`) — the route advances a quest a step at
   a time, so most of those steps never finish one and `getState` never reports
   them. The quest's own progress value moving *while that step is current* is
   the game saying it happened.

   Two guards make the third one honest, and both matter. It compares against a
   **baseline taken when the step became current**, never an absolute value --
   nothing here knows which value belongs to which step, and a player who
   finished the quest last week must see no tick. And it needs a real
   `[Quest Name]` **tag**, not a quest named in the step's prose: the guide
   mentions quests in passing constantly.

Everything else stays manual — roughly 2,850 steps. "Collect 3x Logs next to
the stairs" has no signal, and there is no honest way to infer one.

`isProvenComplete()` is the only place a step is ticked without the player.
Keep it to branches that read state the game sets. Do not add heuristics over
inventory, position or skill level: for 2,900 steps they will be plausible and
wrong, and a wrongly-ticked step silently skips work the player needed and is
discovered far too late.

The diary bit arithmetic is unit-tested, including that bit 31 does not
sign-extend — a `VarPlayer` is a signed int, and an arithmetic shift would tick
every task in a diary at once.

**The two signals may not share a code path.** Reading a `VarPlayer` is an array
lookup and is safe anywhere. `Quest.getState` runs a client script, and the
client refuses to run one from inside another. `VarbitChanged` is posted from
*inside* script execution, so a quest check there throws "scripts are not
reentrant", kills the client thread, and hangs the game on "Please wait" at
login with nothing in the panel to explain it. That shipped once.

So: `VarbitChanged` ticks diaries and sets `questCheckPending`; `GameTick`,
which is posted outside script execution, does the quest pass. Before calling
any client API from an event handler, check whether that event fires inside a
script.

**The sibling rule: some client calls require the client thread.**
`client.getItemContainer` asserts it, and `startUp()` and everything the panel
calls run on the **event** thread -- so a container read reached from there
throws, `startUp()` fails, and RuneLite disables the plugin on the spot. That
shipped too. `WithdrawTracker.update` defers its whole body with
`clientThread.invokeLater`, which is right from either side.

Neither rule is visible in the API. `getVarpValue` and `getItemContainer` look
equally harmless and only one needs the client thread; `Quest.getState` and
`getVarbitValue` look equally harmless and only one runs a script. Check before
adding a call, not after.

## Layout

```
data/       Gson POJOs + GuideLoader (validates, refuses bad input loudly)
progress/   Progress: the completed-step set, migrations, pruning
ui/         GuidePanel -> FeaturesPanel + SectionPanel -> StepRow
bank/       GuideBankTags (a virtual tag per bank), WithdrawTracker +
            WithdrawOverlay (what you are still missing)
path/       PathFinder (BFS over collision flags) + PathTracker
overlay/    SceneTracker (runtime matching), ApproachTracker (the way up),
            and the things drawn from them:
              HighlightOverlay    model outline + TargetArrow chevron
              CurrentStepOverlay  the step itself, over the game
              MinimapOverlay      the same chevron, or a rim arrow when off-map
              PathOverlay         the walkable route along the ground
              WorldMapMarker      the only guidance that survives distance
```

**A requirement is met by any one of its ids, and an unresolved one is never
missing.** "Pickaxe" carries every pickaxe, so owning the worst tier counts.
"Combat gear" has no ids by design -- flagging it would put a warning on the
overlay that no amount of banking can clear.

**Bank tags are virtual.** `TagManager.registerTag` takes a predicate, so
membership is computed from `guide.json` and nothing is written to the player's
config. Never switch to writing `item_<id>` keys: it would touch their own bank
tags, survive uninstall, and need a migration on every wiki edit.

**The minimap arrow is drawn, not borrowed.** `client.setHintArrow` is one call
and comes with the minimap for free, but it is a flashing yellow the player
cannot change, it clashes with a plugin whose visual language is one
configurable colour, and it is a single shared slot quests also want. Do not go
back to it.

**Stairs: the quest's answer first, the game's second.** When the target sits
on another plane, the path, minimap arrow and highlight all retarget to the way
up. `ApproachTracker` tries two sources in order:

1. `step.approach` — the staircase the Quest Helper for that quest actually
   uses, joined at build time. Trusted only when the object is really in the
   scene, matched by id.
2. The nearest object the game says can be climbed, via
   `ObjectComposition.getActions()`. Never a list of names: that is a guess
   that silently misses whatever it forgot.

The fallback exists because most steps name no quest, but it picks the nearest
and is wrong in Lumbridge castle, where several staircases sit within a few
tiles. Either way it is drawn smaller and without a chevron -- it is the route,
not the destination.

**The path is walked, not drawn straight.** `PathFinder` is breadth-first over
the client's own collision flags, so the line goes through doorways. A straight
line is far easier and is what most guides draw, but it points confidently
through buildings and over rivers, which is worse than drawing nothing. It is
pure and unit-tested on hand-drawn maps, because a line through a wall is
invisible in a test of the drawing.

**One icon asset, resized per use.** `GuideIcon` holds the resource path and
both sizes. A missing image resource loads as `null` rather than throwing, so
the button renders blank with nothing in the log -- `IconTest` catches that.

`GuideLoader` and `Progress` are pure and fully unit-tested. Everything that
needs a live client is verified by hand in the dev client -- say so plainly
rather than mocking the client and proving nothing.

## Progress and migrations

Progress is a set of step ids in RuneLite config, per profile.

On load: apply `guide.migrations` (old id -> new id, followed transitively so a
player returning after several releases lands on the current id), then prune ids
the guide no longer contains. Both are tested, including the cycle case -- a
loop in the map must not hang startup.

Step ids are stable across wiki edits that only add link markup. They change
when the wording changes, which is exactly when the migration map earns its
keep.

## Panel performance

236 sections, ~2,900 steps. Do not build every row up front. `SectionPanel`
builds its `StepRow`s the first time it is expanded, and `GuidePanel` renders at
most 60 sections, leaving the rest to the search box.

## Non-negotiable rules

1. **Step text is verbatim.** Never rewrite, clarify or summarise. If it reads
   badly the fix is a wiki edit.
2. **Degrade silently.** Most steps have no target, no items and no
   destination. An empty render pass is the normal case.
3. **Never block startup on the network.** `SectionImages` is the only thing
   that makes a request, off-thread, and only once a bank is expanded -- a
   player who opens no banks makes no requests. Every failure is silent and
   logged at debug: a broken screenshot is not worth an error in front of
   someone mid-route.

   The image host is **checked, not trusted**. The links come from a wiki page
   anyone can edit, so an unchecked URL would let an editor point every
   player's client wherever they liked. https and `i.ibb.co` only,
   test-enforced against the shipped file.
4. **No reflection, no native code.** Plugin Hub review restricts both.

## Attribution

Guide content and screenshots are © B0aty and OSRS Wiki contributors,
CC BY-NC-SA; screenshots are hotlinked, never re-hosted. Code is BSD 2-Clause.
Keep `NOTICE.md` accurate.
