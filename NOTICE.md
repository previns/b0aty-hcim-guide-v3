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
