# Notices

This repo ships one data file, `src/main/resources/guide.json`, built in the
sibling `b0aty-guide-data` repo. Everything below describes what is inside it.

## Guide content

Step text, section structure and screenshot URLs originate from
[Guide:B0aty HCIM Guide V3](https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3)
on the Old School RuneScape Wiki, © B0aty and the wiki's contributors, licensed
under [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

Step text is reproduced verbatim and never rewritten. Screenshots are loaded
from their original host (i.ibb.co) and are not re-hosted.

Game IDs and `{{Map}}` coordinates in `guide.json` come from the same wiki's
infoboxes.

## Shops

Which shop sells which item, and who runs it, is read from the pages in
[Category:Shops](https://oldschool.runescape.wiki/wiki/Category:Shops) on the
Old School RuneScape Wiki -- the `owner` field of each `Infobox Shop` and its
`StoreLine` rows -- and item names are turned into ids through the wiki's own
[item mapping](https://prices.runescape.wiki/api/v1/osrs/mapping). Both are
wiki content, CC BY-NC-SA, and travel in `guide.json` as `step.sellers`.

## Quest Helper

Some coordinates, achievement-diary task bits and item-collection membership in
`guide.json` are derived from
[Quest Helper](https://github.com/Zoinkwiz/quest-helper), (c) 2020 Zoinkwiz,
BSD 2-Clause:

- `target.points` where `pointSource` is `quest-helper`
- `completion.varplayer` and `completion.bit` on diary steps
- the ids behind an `items[].collection`

**`guide.json` also carries Quest Helper's own step descriptions**, under
`questHelpers`, shown in the plugin as "Quest step". That is their prose, not
only ids and coordinates, and it is displayed as coming from Quest Helper. The
rest of the fields above are joined by numeric game ID and include no Quest
Helper text. See `b0aty-guide-data/NOTICE.md` for how each is extracted.

The generic cyclic-widget direction/count logic in `QuestHelperSteps` and
`InterfaceOverlay` is adapted from Quest Helper's Tribal Totem `PuzzleStep`,
Copyright (c) 2020 Zoinkwiz and Twinkle, BSD 2-Clause. Its targets, cycle length,
varbits and widget IDs are extracted into data rather than hardcoded in Java.

## Shortest Path

`PathFinder`'s movement rules are taken from
[Shortest Path](https://github.com/Skretzo/shortest-path), BSD 2-Clause --
specifically its `CollisionMap`:

- a diagonal step requires *both* ways round the corner to be clear, not one
- the tie-break for a target that cannot be reached: nearest, then shortest
  walk, then lowest x, then lowest y

The movement rules are re-expressed against the client's live collision flags
for the loaded scene. `open-transports.tsv` is a filtered copy of Shortest
Path's `transports.tsv`: adjacent, same-plane entries whose action begins
`Open` or `Slash`. It supplies an unambiguous crossing direction only when a
matching gate, door or web candidate is currently present in the live scene. The complete
prebuilt world collision map and all other transport data are not shipped.

## RuneLite

The numeric values behind `ItemID`, `NpcID`, `ObjectID` and `VarPlayerID`
constants are read from a compiled `runelite-api` jar during the data build.
[RuneLite](https://github.com/runelite/runelite), © 2016-2026 the RuneLite
authors, BSD 2-Clause.

`net.runelite.client.plugins.banktags` is used through its public API to
register bank tags; the plugin is a normal RuneLite dependency and none of its
source is vendored here.

## Code

Everything under `src/` is BSD 2-Clause. See LICENSE.
