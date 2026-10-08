package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.IBuilderProcess;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1282;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1542;
import net.minecraft.class_1548;
import net.minecraft.class_1569;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1747;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2661;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_2771;
import net.minecraft.class_3481;
import net.minecraft.class_418;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_7923;
import net.minecraft.class_9290;
import net.minecraft.class_9334;
import net.minecraft.class_2902.class_2903;

public class FarmBuilder extends Module {
   private static FarmBuilder instance;
   private final SettingGroup sgSource = this.settings.createGroup("Schematic");
   private final SettingGroup sgShop = this.settings.createGroup("Auction House");
   private final SettingGroup sgGui = this.settings.createGroup("AH GUI");
   private final SettingGroup sgBuild = this.settings.createGroup("Build");
   private final SettingGroup sgGear = this.settings.createGroup("Gear & Combat");
   private final SettingGroup sgStealth = this.settings.createGroup("Stealth");
   private final SettingGroup sgPanel = this.settings.createGroup("Status Panel");
   private final SettingGroup sgStorage = this.settings.createGroup("Storage & Extras");
   private final Setting<Boolean> outsideChest = this.sgBuild.add(new Builder().name("outside-supply-chest")
      .description("Create or reuse a supply chest outside the farm before working, and take needed materials from it.").defaultValue(true).build());
   private final Setting<Boolean> logoutWhenDone = this.sgBuild.add(new Builder().name("log-out-when-done")
      .description("When every block of the farm is placed and checked, log out of the server and keep auto-reconnect off. Not used when blocks were skipped.")
      .defaultValue(true).build());
   private final Setting<Boolean> stashBeforeBuild = this.sgStorage.add(new Builder().name("stash-before-build")
      .description("Once everything the farm needs is gathered or bought, store it all in the outside supply chest, then take it out in batches while building. A death before the build no longer loses the materials.")
      .defaultValue(true).visible(this.outsideChest::get).build());
   private final Setting<Integer> withdrawReserve = this.sgStorage.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("withdraw-reserve-slots").description("Free inventory slots left open when taking a batch of materials out of the chest.")
      .defaultValue(4).range(1, 20).sliderRange(1, 12).visible(this.outsideChest::get).build());
   private final Setting<Boolean> vaultValuables = this.sgStorage.add(new Builder().name("vault-valuables")
      .description("Lock valuables (diamonds, netherite, ancient debris, enchanted books, shulker boxes...) that the job does not need into the outside chest, so a death cannot take them.")
      .defaultValue(true).visible(this.outsideChest::get).build());
   private final Setting<Boolean> priceBook = this.sgStorage.add(new Builder().name("price-book")
      .description("Remember the unit prices seen on the AH per server and skip listings far above the usual price (the item is made instead when make-if-not-on-ah is on).")
      .defaultValue(true).build());
   private final Setting<Double> priceLimit = this.sgStorage.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
      .name("price-book-limit").description("Skip a listing when its unit price is above this multiple of the usual (median) price.")
      .defaultValue(2.0).range(1.2, 10.0).sliderRange(1.2, 5.0).visible(this.priceBook::get).build());
   private final Setting<String> webhookUrl = this.sgStorage.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
      .name("discord-webhook").description("Discord webhook URL. Farm Builder posts when it finishes, stops, dies, is attacked, finds the chest tampered with, and at build milestones. Empty = off.")
      .defaultValue("").build());
   private final Setting<Boolean> webhookCoords = this.sgStorage.add(new Builder().name("webhook-include-location")
      .description("Append your coordinates to webhook messages. Off by default so a leaked webhook does not reveal your base.")
      .defaultValue(false).visible(() -> !this.webhookUrl.get().isBlank()).build());
   private final Setting<Boolean> dropJunkOutside = this.sgBuild.add(new Builder().name("drop-junk-outside")
      .description("When crafting or building runs out of slots, drop unneeded junk at a random spot beside the outside chest.").defaultValue(true).build());
   private final Setting<Boolean> buildWhereLooking = this.sgBuild.add(new Builder()
      .name("build-where-looking").description("On a new Start Farm, save the block you aim at as the build corner. Resume keeps the saved job.")
      .defaultValue(false).build());
   private final Setting<Integer> pickDistance = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("pick-distance").description("Maximum distance in blocks when choosing a build surface by looking.")
      .defaultValue(64).range(4, 128).sliderRange(4, 128).build());
   private final Setting<Boolean> buildAtCoordinates = this.sgBuild.add(new Builder()
      .name("build-at-coordinates").description("Use the exact build origin below instead of offsets or automatic site selection.")
      .defaultValue(false).build());
   private final Setting<Integer> buildX = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("build-x").description("Exact X of the schematic origin.").defaultValue(0).range(-29999984, 29999984)
      .sliderRange(-1000, 1000).visible(this.buildAtCoordinates::get).build());
   private final Setting<Integer> buildY = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("build-y").description("Exact Y of the schematic origin (the lowest schematic layer).").defaultValue(64).range(-2032, 2031)
      .sliderRange(-64, 319).visible(this.buildAtCoordinates::get).build());
   private final Setting<Integer> buildZ = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("build-z").description("Exact Z of the schematic origin.").defaultValue(0).range(-29999984, 29999984)
      .sliderRange(-1000, 1000).visible(this.buildAtCoordinates::get).build());
   private final Setting<Boolean> replaceTools = this.sgGear.add(new Builder()
      .name("replace-tools").description("Mine, smelt and craft replacement pickaxes, axes and shovels when the ones used by this job break or wear out.")
      .defaultValue(true).build());
   private final Setting<Integer> toolDurability = this.sgGear.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("replace-tool-percent").description("Replace tools below this percentage of durability. Keeps a spare before item-saver stops mining.")
      .defaultValue(8).range(8, 50).sliderRange(8, 25).visible(this.replaceTools::get).build());
   private final Setting<Boolean> mineBesideFarm = this.sgBuild.add(new Builder()
      .name("mine-beside-farm").description("Enter ore mines from beside the farm and descend there before gathering ores.")
      .defaultValue(true).build());
   private final Setting<Integer> mineMargin = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("mine-distance-from-farm").description("Gap between the east edge of the farm and the mining entry.")
      .defaultValue(8).range(4, 64).sliderRange(4, 32).visible(this.mineBesideFarm::get).build());
   private final Setting<Boolean> fastHomeReturn = this.sgBuild.add(new Builder()
      .name("fast-home-return").description("Try the home command before walking back after mining or replacing tools. Walk if the command fails.")
      .defaultValue(true).build());
   private final Setting<String> gatherHomeCommand = this.sgBuild.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
      .name("gather-home-cmd").description("Teleport here after gathering, making gear or replacing tools. Empty allows walking back instead.")
      .defaultValue("/home 1").build());
   private final Setting<Integer> homeMinDistance = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("home-min-distance").description("Use the home command when returning from at least this many blocks away.")
      .defaultValue(16).range(7, 20000).sliderRange(7, 256).visible(this.fastHomeReturn::get).build());
   private final Setting<Integer> homeWaitSeconds = this.sgBuild.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("home-wait-seconds").description("Allow time for a server teleport countdown before falling back to walking.")
      .defaultValue(10).range(3, 60).sliderRange(3, 30).visible(this.fastHomeReturn::get).build());
   private final Setting<Boolean> showPanel = this.sgPanel
      .add(
         new Builder()
            .name("show-panel")
            .description("On-screen panel showing what it's doing, what it's looking for and how far the build is.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> compactPanel = this.sgPanel
      .add(
         new Builder()
            .name("compact-panel")
            .description("Only the header, current activity and main progress bar.")
            .defaultValue(false)
            .visible(this.showPanel::get)
            .build()
      );
   private final Setting<Integer> panelX = this.sgPanel
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("panel-x")
            .description("Panel distance from the left edge.")
            .defaultValue(4)
            .range(0, 2000)
            .sliderRange(0, 600)
            .visible(this.showPanel::get)
            .build()
      );
   private final Setting<Integer> panelY = this.sgPanel
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("panel-y")
            .description("Panel distance from the top edge.")
            .defaultValue(40)
            .range(0, 2000)
            .sliderRange(0, 400)
            .visible(this.showPanel::get)
            .build()
      );
   private final Setting<Farm> farm = this.sgSource.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<Farm>()
      .name("farm").description("Which farm to build. Custom uses schematic-url or file-name.").defaultValue(Farm.SweetBerries).build());
   private final Setting<String> url = this.sgSource
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("schematic-url")
            .description("Direct download link to a .litematic / .schem / .schematic farm. Leave empty to use a local file.")
            .defaultValue("")
            .visible(() -> this.farm.get() == FarmBuilder.Farm.Custom)
            .build()
      );
   private final Setting<String> fileName = this.sgSource
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("file-name")
            .description("File inside .minecraft/schematics/. The download is saved under this name too; empty = name from the URL.")
            .defaultValue("")
            .visible(() -> this.farm.get() == FarmBuilder.Farm.Custom)
            .build()
      );
   private final Setting<Integer> ahMissMemory = this.sgShop.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("ah-miss-memory")
      .description("Minutes to remember that the AH didn't have an item. Meanwhile it gathers that item instead of checking the AH again between batches. 0 = always check the AH.")
      .defaultValue(30).range(0, 1440).sliderRange(0, 180).build());
   private final Setting<Boolean> useAh = this.sgShop
      .add(
         new Builder()
            .name("use-ah")
            .description("Start buys missing stuff on the AH. Off = Start crafts/mines everything itself, like Get Items.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> buy = this.sgShop
      .add(new Builder().name("buy-missing").description("Buy whatever the farm needs that isn't in your inventory.").defaultValue(true).build());
   private final Setting<String> ahCommand = this.sgShop
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("ah-search-command")
            .description(
               "Command that opens the AH search. {item} becomes the item name. If the server rejects it, /ah search {item} and plain /ah are tried automatically."
            )
            .defaultValue("/ah {item}")
            .build()
      );
   private final Setting<Double> maxUnitPrice = this.sgShop
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("max-price-each")
            .description("Skip listings above this price per single item. 0 = no limit.")
            .defaultValue(200.0)
            .min(0.0)
            .sliderMax(10000.0)
            .build()
      );
   private final Setting<Double> budget = this.sgShop
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("budget")
            .description("Total money this run is allowed to spend. 0 = no limit.")
            .defaultValue(0.0)
            .min(0.0)
            .sliderMax(1.0E7)
            .build()
      );
   private final Setting<Integer> refreshTries = this.sgShop
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("ah-refreshes")
            .description("How many times to hit refresh when no good listing shows up before trying to gather the item instead.")
            .defaultValue(1)
            .min(0)
            .sliderMax(30)
            .build()
      );
   private final Setting<Integer> maxPages = this.sgShop
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("ah-pages")
            .description("How many extra AH pages to flip through before hitting refresh. Plain /ah (no search) checks 4x this.")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .build()
      );
   private final Setting<Boolean> gather = this.sgShop
      .add(
         new Builder()
            .name("make-if-not-on-ah")
            .description("When the AH doesn't have something, make it from scratch instead (wood -> tools -> mine -> smelt -> craft).")
            .defaultValue(true)
            .build()
      );
   private final Setting<String> priceKeyword = this.sgGui
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("price-label")
            .description("Word in front of the price on AH listings (Price, Cost...). Not the price itself. Lines with a $ are used if this isn't found.")
            .defaultValue("price")
            .build()
      );
   private final Setting<String> refreshKeyword = this.sgGui
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("refresh-keyword")
            .description("Name of the refresh button contains this.")
            .defaultValue("refresh")
            .build()
      );
   private final Setting<AuctionUi.PriceFormat> priceFormat = this.sgGui.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<AuctionUi.PriceFormat>()
      .name("price-number-format").description("Recognize English 1,234.50 or German 1.234,50 listing prices.").defaultValue(AuctionUi.PriceFormat.Auto).build());
   private final Setting<Integer> controlClick = this.sgGui.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("control-click-button").description("Mouse button for refresh, next-page and confirm: 0 left, 1 right.").defaultValue(0).range(0,1).sliderRange(0,1).build());
   private final Setting<Integer> listingClick = this.sgGui.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("listing-click-button").description("Mouse button for buying a listing: 0 left, 1 right.").defaultValue(0).range(0,1).sliderRange(0,1).build());
   private final Setting<String> nextKeyword = this.sgGui.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
      .name("next-page-keyword").description("Custom next-page label; English and German labels are also recognized.").defaultValue("next").build());
   private final Setting<Integer> refreshSlot = this.sgGui.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("refresh-slot").description("Server menu slot for Refresh. -1 detects it from labels; Inspect AH prints slot IDs.").defaultValue(-1).range(-1,255).sliderRange(-1,53).build());
   private final Setting<Integer> nextSlot = this.sgGui.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("next-page-slot").description("Server menu slot for Next Page. -1 detects it from labels.").defaultValue(-1).range(-1,255).sliderRange(-1,53).build());
   private final Setting<Integer> confirmSlot = this.sgGui.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("confirm-slot").description("Confirmation menu slot. -1 detects it from labels; only used in a confirmation screen.").defaultValue(-1).range(-1,255).sliderRange(-1,53).build());
   private final Setting<Integer> deliveryTimeout = this.sgGui.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
      .name("delivery-timeout").description("Ticks to wait for bought items to reach inventory before reporting the failed purchase.").defaultValue(200).range(40,1200).sliderRange(40,600).build());
   private final Setting<String> confirmKeyword = this.sgGui
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("confirm-keyword")
            .description("Name of the confirm button contains this.")
            .defaultValue("confirm")
            .build()
      );
   private final Setting<Integer> clickDelay = this.sgGui
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("click-delay")
            .description("Ticks to wait before each click in the AH.")
            .defaultValue(6)
            .min(1)
            .sliderMax(40)
            .build()
      );
   private final Setting<Integer> refreshDelay = this.sgGui
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("refresh-delay")
            .description("Ticks to wait after hitting refresh.")
            .defaultValue(20)
            .min(1)
            .sliderMax(100)
            .build()
      );
   private final Setting<Integer> guiTimeout = this.sgGui
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("gui-timeout")
            .description("Ticks to wait for a menu to open before giving up on it.")
            .defaultValue(80)
            .min(10)
            .sliderMax(200)
            .build()
      );
   private final Setting<Boolean> autoSite = this.sgBuild
      .add(
         new Builder()
            .name("auto-site")
            .description("Find a flat, empty spot nearby for the farm (no water, nobody's builds) instead of using your position.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> siteRadius = this.sgBuild
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("site-radius")
            .description("How far around you to look for a build spot.")
            .defaultValue(64)
            .range(16, 160)
            .sliderRange(16, 128)
            .visible(this.autoSite::get)
            .build()
      );
   private final Setting<Integer> offsetX = this.sgBuild
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("offset-x")
            .description("Build origin offset from your feet.")
            .defaultValue(1)
            .sliderRange(-32, 32)
            .build()
      );
   private final Setting<Integer> offsetY = this.sgBuild
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("offset-y")
            .description("Build origin offset from your feet.")
            .defaultValue(0)
            .sliderRange(-32, 32)
            .build()
      );
   private final Setting<Integer> offsetZ = this.sgBuild
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("offset-z")
            .description("Build origin offset from your feet.")
            .defaultValue(1)
            .sliderRange(-32, 32)
            .build()
      );
   private final Setting<Boolean> clearSiteFirst = this.sgBuild.add(new Builder().name("clear-site-first")
      .description("On a fresh site, break the natural blocks (grass, dirt, stone, trees, plants...) inside the farm's area before building. Stops instead if it finds a chest or anything player-made there.")
      .defaultValue(true).build());
   private final Setting<Boolean> breakWrong = this.sgBuild
      .add(
         new Builder()
            .name("break-wrong-blocks")
            .description("Let Baritone clear terrain that's in the way. It never breaks blocks it already placed correctly.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> gearUp = this.sgGear
      .add(new Builder().name("gear-up").description("Prepare full diamond armor and a sword before farming, and replace missing gear.").defaultValue(true).build());
   private final Setting<Boolean> mineGear = this.sgGear.add(new Builder().name("mine-diamond-gear")
      .description("Gather and craft missing diamond armor, sword and tools beside the base before building.")
      .defaultValue(true).visible(this.gearUp::get).build());
   private final Setting<Boolean> pauseMenus = this.sgBuild.add(new Builder().name("pause-in-menus")
      .description("Pause movement and farming while your inventory, chat or a menu is open. Resume on closing it.")
      .defaultValue(true).build());
   private final Setting<Boolean> gearTools = this.sgGear
      .add(
         new Builder()
            .name("buy-tools")
            .description("Also buy a diamond pickaxe, axe and shovel for mining.")
            .defaultValue(true)
            .visible(this.gearUp::get)
            .build()
      );
   private final Setting<Double> gearPrice = this.sgGear
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("max-gear-price")
            .description("Max price for one piece of gear. 0 = no limit.")
            .defaultValue(2000.0)
            .min(0.0)
            .sliderMax(100000.0)
            .visible(this.gearUp::get)
            .build()
      );
   private final Setting<Crafter.OreFinding> oreFinding = this.sgGear.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<Crafter.OreFinding>()
      .name("ore-finding").description("How ores are located while gathering. Legit explores and strip mines at the ore's normal Y level.")
      .defaultValue(Crafter.OreFinding.Auto).build());
   private final Setting<Boolean> nativeOreScan = this.sgGear.add(new Builder().name("native-baritone-ore-scan")
      .description("Use Baritone's native known-ore scan and route planning first for all ores, then exploration if needed.").defaultValue(true).build());
   private final Setting<Boolean> diamondScanFirst = this.sgGear.add(new Builder()
      .name("diamond-pickaxe-scan-first").description("Scan known diamond ores first when crafting a diamond pickaxe, then explore if none are found.").defaultValue(true).build());
   private final Setting<Integer> mineStall = this.sgGear
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("give-up-after")
            .description("Seconds of mining with nothing found before skipping that item (ores get 3x this).")
            .defaultValue(90)
            .range(15, 900)
            .sliderRange(30, 300)
            .build()
      );
   private final Setting<Boolean> pauseNearPlayers = this.sgStealth
      .add(
         new Builder()
            .name("pause-near-players")
            .description("Freeze everything while another player (not a Meteor friend) is close, carry on when they leave.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> playerRadius = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("player-radius")
            .description("How close a player has to be to pause.")
            .defaultValue(32)
            .range(8, 128)
            .sliderRange(8, 64)
            .visible(this.pauseNearPlayers::get)
            .build()
      );
   private final Setting<Boolean> takeBreaks = this.sgStealth
      .add(new Builder().name("take-breaks").description("Stop now and then for a bit and look around, like a person would.").defaultValue(true).build());
   private final Setting<Integer> workMin = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("work-minutes-min")
            .description("Shortest stretch of work between breaks.")
            .defaultValue(8)
            .range(1, 120)
            .sliderRange(1, 60)
            .visible(this.takeBreaks::get)
            .build()
      );
   private final Setting<Integer> workMax = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("work-minutes-max")
            .description("Longest stretch of work between breaks.")
            .defaultValue(20)
            .range(1, 180)
            .sliderRange(1, 90)
            .visible(this.takeBreaks::get)
            .build()
      );
   private final Setting<Integer> breakMin = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("break-seconds-min")
            .description("Shortest break.")
            .defaultValue(20)
            .range(5, 900)
            .sliderRange(5, 300)
            .visible(this.takeBreaks::get)
            .build()
      );
   private final Setting<Integer> breakMax = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("break-seconds-max")
            .description("Longest break.")
            .defaultValue(90)
            .range(5, 1800)
            .sliderRange(5, 600)
            .visible(this.takeBreaks::get)
            .build()
      );
   private final Setting<String> joinCommand = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("join-command")
            .description("Sent after rejoining, before carrying on (for servers that put you in a lobby first, e.g. /server survival). Empty = none.")
            .defaultValue("")
            .build()
      );
   private final Setting<Boolean> leaveIfPlayerHits = this.sgStealth
      .add(
         new Builder()
            .name("leave-if-player-attacks")
            .description("Log out right away if another player (not a Meteor friend) hits you, instead of losing your gear overnight.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> maxWalkBack = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-walk-back")
            .description("After a rejoin or respawn, walk back to the farm if it's at most this many blocks away.")
            .defaultValue(2000)
            .range(16, 20000)
            .sliderRange(100, 5000)
            .build()
      );
   private final Setting<String> setHomeCommand = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("sethome-cmd")
            .description("Sent when it arrives at the build site (before building), so it can teleport back later. Empty = don't.")
            .defaultValue("/sethome 1")
            .build()
      );
   private final Setting<String> homeCommand = this.sgStealth
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("home-cmd")
            .description("Teleport back when the farm is too far to walk to (after respawning at spawn, other dimension).")
            .defaultValue("/home 1")
            .build()
      );
   private static final class_1792[] ARMOR_PIECES = new class_1792[]{
      class_1802.field_8805, class_1802.field_8058, class_1802.field_8348, class_1802.field_8285
   };
   private static final class_1792[] TOOLS = new class_1792[]{class_1802.field_8377, class_1802.field_8556, class_1802.field_8250};
   private static final class_1792[] SWORDS = new class_1792[]{class_1802.field_22022, class_1802.field_8802};
   private static final Map<class_1792, class_1792> UPGRADE = Map.ofEntries(
      Map.entry(class_1802.field_8805, class_1802.field_22027),
      Map.entry(class_1802.field_8058, class_1802.field_22028),
      Map.entry(class_1802.field_8348, class_1802.field_22029),
      Map.entry(class_1802.field_8285, class_1802.field_22030),
      Map.entry(class_1802.field_8802, class_1802.field_22022),
      Map.entry(class_1802.field_8377, class_1802.field_22024),
      Map.entry(class_1802.field_8556, class_1802.field_22025),
      Map.entry(class_1802.field_8250, class_1802.field_22023)
   );
   private static final Map<class_1792, class_1304> ARMOR_SLOT = Map.ofEntries(
      Map.entry(class_1802.field_8805, class_1304.field_6169),
      Map.entry(class_1802.field_22027, class_1304.field_6169),
      Map.entry(class_1802.field_8058, class_1304.field_6174),
      Map.entry(class_1802.field_22028, class_1304.field_6174),
      Map.entry(class_1802.field_8348, class_1304.field_6172),
      Map.entry(class_1802.field_22029, class_1304.field_6172),
      Map.entry(class_1802.field_8285, class_1304.field_6166),
      Map.entry(class_1802.field_22030, class_1304.field_6166)
   );
   private final Random random = new Random();
   private boolean gearAhDone;
   private boolean menuPaused;
   private boolean suppressJobSave;
   private boolean explicitStart;
   private String jobServer = "";
   private boolean gearPreparing;
   private Crafter.Job reconnectCraft;
   private int nextGearCheck;
   private boolean pausedForPlayer;
   private int playerClearChecks;
   private boolean onBreak;
   private int breakEnd;
   private int nextBreak;
   private boolean baritonePaused;
   private int equipCloseTick = -1;
   private final Crafter crafter = new Crafter();
   private final Combat combat = new Combat(this.settings);
   private final Survival survival = new Survival(this.settings, this.combat);
   private final Movement movement = new Movement(this.settings);
   private final Reconnect reconnect = new Reconnect(this.settings);
   private final Overnight overnight = new Overnight(this.settings);
   private String sessionStopReason = "";
   private static final File LOCATION_FILE = new File(MeteorClient.FOLDER, "farm-builder-location.txt");
   private static final File SUPPLY_FILE = new File(MeteorClient.FOLDER, "farm-builder-supply.txt");
   private static final File MEMORY_FILE = new File(MeteorClient.FOLDER, "farm-builder-memory.txt");
   private final FarmMemory memory = new FarmMemory(MEMORY_FILE.toPath());
   private boolean missFromMemory;
   private FarmLocation supply;
   private boolean supplyLoaded;
   private static final File JOB_BACKUP_FILE = new File(MeteorClient.FOLDER, "farm-builder-job-backup.txt");
   private static final File JOB_FILE = new File(MeteorClient.FOLDER, "farm-builder-job.txt");
   private boolean pendingResume;
   private boolean resumeFresh;
   private String jobDim;
   private boolean returnWalking;
   private int returnSettleUntil;
   private int homeTries;
   private int homeNext;
   private boolean gatheringReturn;
   private boolean gearDoneReturning;
   private boolean depositReturning;
   private boolean storageReturning;
   private class_2338 lastReturnPosition;
   private String lastReturnDimension;
   private int worldSeenAt = -1;
   private float lastHealth = -1.0F;
   private int lastMenuEscape = -1000;
   private int totalBlocks;
   private int startTick;
   private int rejoins;
   private int threatSince = -1;
   private int lastPlanTick = -1000;
   private class_2338 planOrigin;
   private IStaticSchematic planSchematic;
   private final Deque<Integer> errorTicks = new ArrayDeque<>();
   private int lastActivity;
   private FarmBuilder.State lastActivityState;
   private int lastActivityHash;
   private class_2338 lastActivityPos;
   private final HudPanel hud = new HudPanel();
   private final SettingsVault vault = new SettingsVault(this.settings, new File(MeteorClient.FOLDER, "farm-builder-settings.txt"));
   private HudPanel.Model hudModel;
   private int panelCacheTick = -100;
   private boolean threatCache;
   private int threatCacheTick = -100;
   private boolean hadWorld;
   private boolean arriveThenLoad;
   private final Depot depot = new Depot();
   private boolean depotForItems;
   private boolean storageReady;
   private boolean storageCrafting;
   private boolean storageSettingUp;
   private boolean stockpiled;
   private boolean madeSomething;
   private final Map<class_1792, Integer> stockLedger = new LinkedHashMap<>();
   private final Map<class_1792, Integer> stashBefore = new HashMap<>();
   private static final File STOCK_FILE = new File(MeteorClient.FOLDER, "farm-builder-stock.txt");
   private int depotKind;
   private int nextStash;
   private int nextVault;
   private int nextDiscover;
   private int withdrawBefore;
   private final Set<class_1792> withdrawItems = new HashSet<>();
   private final Map<class_1792, Integer> pending = new LinkedHashMap<>();
   private final BuildForecast forecast = new BuildForecast();
   private final PriceBook prices = new PriceBook();
   private String pricesFor = "";
   private final Set<class_1792> priceNoted = new HashSet<>();
   private final Notifier notifier = new Notifier();
   private int deathsNotified;
   private int tamperAlerts;
   private int milestone;
   private Crafter.Job junkCraft;
   private State junkResumeState;
   private class_2338 junkSpot;
   private int junkSince;
   private class_2338 junkReturn;
   private boolean junkLeaving;
   private final DropLedger drops = new DropLedger();
   private final AfkSupport afk = new AfkSupport(this.settings);
   private boolean afkHeld;
   private class_2338 chunkWaitTarget;
   private int emptyDeposits;
   private int nextWithdraw;
   private final Set<class_1792> itemsSkipped = new HashSet<>();
   private final Deque<String> events = new ArrayDeque<>();
   private static final File OVERNIGHT_FILE = new File(MeteorClient.FOLDER, "farm-builder-overnight.txt");
   private static final File REPORT_FILE = new File(MeteorClient.FOLDER, "farm-builder-report.txt");
   private class_1297 pickupTarget;
   private int pickupUntil;
   private int nextPickupCheck;
   private boolean rejoinPending;
   private int rejoinAt = -1;
   private boolean joinCmdSent;
   private boolean craftBatch;
   private final Set<class_1792> lastBatch = new HashSet<>();
   private final Boat boat = new Boat();
   private boolean homeSet;
   private boolean inBuildSettings;
   private final List<class_2248> protectedBlocks = new ArrayList<>();
   private FarmBuilder.Mode mode = FarmBuilder.Mode.FULL;
   private FarmBuilder.Mode pendingMode = FarmBuilder.Mode.FULL;
   private FarmBuilder.State state = FarmBuilder.State.IDLE;
   private int wait;
   private int timeout;
   private volatile IStaticSchematic schematic;
   private volatile File schematicFile;
   private volatile String loadError;
   private class_2338 origin;
   private final Map<class_1792, Integer> required = new LinkedHashMap<>();
   private final Map<class_1792, Integer> shopping = new LinkedHashMap<>();
   private final Set<class_1792> unbuyable = new HashSet<>();
   private final Set<class_1792> gatherTried = new HashSet<>();
   private int tickCount;
   private class_1792 fallbackItem;
   private int pauseResumes;
   private class_1792 current;
   private int refreshes;
   private int cmdVariant;
   private boolean cmdRejected;
   private int pages;
   private int lastSyncId = -1;
   private double pendingPrice;
   private double spent;
   private final dev.rex.stealth.StealthPacing pacing = new dev.rex.stealth.StealthPacing(this.settings);
   private boolean buildStarted;
   private boolean clearing;
   private int clearStartTick;
   private int clearAttempts;
   private int buildRestarts;
   private boolean siteChosen;
   private boolean safeBuild;
   private int bestCorrect = -1;
   private int lastGainTick;
   private int nextProgressCheck;
   private int buildTicks;
   private final Map<String, class_1792> observedTools = new LinkedHashMap<>();
   private final Map<String, class_1792> replacementTargets = new LinkedHashMap<>();
   private Crafter.Job interruptedCraft;
   private boolean replacementReturning;
   private int nextToolCheck;
   private class_2338 replacementEntry;
   private int replacementEntrySince;
   private int pendingInventoryCount;
   private boolean purchaseConfirmed;
   private boolean confirmClicked;
   private int purchaseDeadline;
   private String purchaseRejected = "";
   private AuctionUi.Action navigationAction;
   private long navigationBefore;
   private int navigationDeadline;
   private int navigationSettle;
   private int navigationRetries;
   private boolean nextUnavailable;
   private boolean refreshUnavailable;
   private final Set<Long> seenAhPages = new HashSet<>();
   private boolean jobSaveFailed;
   private List<String> auctionSnapshot = List.of();
   private String auctionSnapshotTitle = "";
   private int auctionSnapshotTicks;
   private Crafter.Job savedCraftJob;
   private boolean savedCraftRepair;
   private volatile long loadGeneration;
   private Thread loaderThread;

   public FarmBuilder() {
      super(dev.rex.stealth.Spooky.category(), "farm-builder", "Downloads a farm, buys what's missing on the AH, then builds it.");
      instance = this;
      this.runInMainMenu = true;
      MeteorClient.EVENT_BUS.subscribe(this.vault);
      this.crafter.setRoomMaker(this::makeCraftRoom);
      this.crafter.setLocalStorage(() -> !this.outsideChest.get());
      this.crafter.setDiamondScanFirst(() -> this.diamondScanFirst.get());
      this.crafter.setNativeOreScan(() -> this.nativeOreScan.get());
      this.crafter.setMiningProfile(this::travelSettings);
      this.survival.setFoodReserve(this.overnight::foodReserve);
      this.survival.setDiscardListener((item, at) -> this.drops.discarded(item, at, System.currentTimeMillis()));
      this.crafter.setNoPlace(this::inFootprint);
      this.movement.setNoBreak(this::inFootprint);
      this.survival.setNoBreak(this::inFootprint);
      this.crafter.setKeep(this::keepItem);
      this.depot.setNoPlace(this::inFootprint);
      this.crafter.setMineAnchor(() -> this.mineBesideFarm.get() && this.siteChosen ? this.miningEntry() : null);
      MeteorClient.EVENT_BUS.subscribe(new FarmBuilder.AutoResume());
      this.crafter
         .setMineCeiling(
            () -> this.mc.field_1687 == null ? 2031 : this.mc.field_1687.method_31600()
         );
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WVerticalList col = theme.verticalList();
      title(theme, col, "☠ Farm Builder — Halloween Edition");
      col.add(theme.label("Status: " + (this.isActive() ? this.activity() : "idle")));

      section(theme, col, "Build");
      WHorizontalList run = theme.horizontalList();
      WButton start = run.add(theme.button("Start Farm")).widget();
      start.action = () -> this.launch(FarmBuilder.Mode.FULL);
      WButton resume = run.add(theme.button("Resume Saved Farm")).widget();
      resume.action = () -> {
         if (this.isActive()) {
            this.toggle();
         }

         this.explicitStart = false;
         this.pendingResume = true;
         this.toggle();
      };
      WButton stop = run.add(theme.button("Stop")).widget();
      stop.action = () -> {
         this.clearJob();
         if (this.isActive()) {
            this.toggle();
         }
      };
      col.add(run);
      WHorizontalList location = theme.horizontalList();
      WButton here = location.add(theme.button("Set Build Here")).widget();
      here.action = () -> {
         if (this.mc.field_1724 != null && this.mc.field_1687 != null)
            this.setBuildPlace(this.mc.field_1724.method_24515(), false);
      };
      WButton looking = location.add(theme.button("Set Build Where I'm Looking")).widget();
      looking.action = () -> this.pickBuildPlace(false);
      WButton saved = location.add(theme.button("Use Saved Place")).widget();
      saved.action = () -> {
         try {
            FarmLocation place = FarmLocation.read(LOCATION_FILE.toPath());
            if (!place.matches(this.currentDim(), this.currentServer())) {
               this.error("Saved place belongs to another server or dimension. Join that place first.");
               return;
            }
            this.setBuildPlace(place.origin(), false);
         } catch (Exception e) { this.error("No readable saved build place. Pick a ground block first."); }
      };
      col.add(location);
      col.add(theme.label("Aim at ground, save the corner, then Start Farm. The farm extends toward +X and +Z."));

      section(theme, col, "Supply chest");
      WHorizontalList supplyRow = theme.horizontalList();
      WButton supplyHere = supplyRow.add(theme.button("Set Supply Chest Here")).widget();
      supplyHere.action = this::setSupplySpot;
      WButton supplyAuto = supplyRow.add(theme.button("Auto Supply Spot")).widget();
      supplyAuto.action = () -> {
         this.supply = null;
         this.supplyLoaded = true;
         if (SUPPLY_FILE.delete() || !SUPPLY_FILE.exists()) this.info("Supply chest spot cleared; it goes 4 blocks outside the farm corner again.");
      };
      col.add(supplyRow);
      FarmLocation spot = this.mc.field_1687 == null ? null : this.supplySpot();
      col.add(theme.label(spot == null ? "Supply chest: automatic (4 blocks outside the farm corner)"
         : "Supply chest: " + spot.origin().method_10263() + " " + spot.origin().method_10264() + " " + spot.origin().method_10260()));

      section(theme, col, "Memory");
      WHorizontalList memRow = theme.horizontalList();
      WButton memShow = memRow.add(theme.button("Show Memory")).widget();
      memShow.action = this::printMemory;
      WButton memForget = memRow.add(theme.button("Forget AH Misses")).widget();
      memForget.action = () -> {
         this.memory.forgetAhMisses();
         this.info("Forgot which items the AH didn't have; it will check the AH again.");
      };
      col.add(memRow);
      List<String> misses = this.mc.field_1687 == null ? List.of()
         : this.memory.recentMisses(this.currentServer(), System.currentTimeMillis(), Math.max(1, this.ahMissMemory.get()) * 60000L);
      col.add(theme.label(misses.isEmpty() ? "Not on this AH: nothing remembered"
         : "Not on this AH (gathering instead): " + String.join(", ", misses.subList(0, Math.min(4, misses.size()))) + (misses.size() > 4 ? " ..." : "")));
      for (String e : this.memory.recentEvents(3)) col.add(theme.label(e.length() > 90 ? e.substring(0, 87) + "..." : e));
      col.add(theme.label("Saved in " + MEMORY_FILE.getName() + " in your Meteor folder."));

      section(theme, col, "Gather without building");
      col.add(theme.label("No money? These craft everything from scratch (no AH):"));
      WHorizontalList broke = theme.horizontalList();
      WButton gear = broke.add(theme.button("Get Gear")).widget();
      gear.action = () -> this.launch(FarmBuilder.Mode.GEAR);
      WButton items = broke.add(theme.button("Get Items")).widget();
      items.action = () -> this.launch(FarmBuilder.Mode.ITEMS);
      col.add(broke);

      section(theme, col, "Info & tools");
      WHorizontalList info = theme.horizontalList();
      WButton list = info.add(theme.button("Material List")).widget();
      list.action = this::printMaterials;
      WButton report = info.add(theme.button("Report")).widget();
      report.action = this::printReport;
      WButton overnightStatus = info.add(theme.button("Overnight Status")).widget();
      overnightStatus.action = () -> {
         this.writeOvernightHeartbeat(this.activity());
         this.printReport();
         this.info("Overnight snapshot: %s", OVERNIGHT_FILE.getPath());
      };
      col.add(info);
      WHorizontalList tools = theme.horizontalList();
      WButton vision = tools.add(theme.button("Prepare Manual Xray")).widget();
      vision.action = () -> { ManualXray.prepare(); this.info("All ore types configured. Enable Meteor Xray yourself when wanted."); };
      WButton inspect = tools.add(theme.button("Inspect AH Controls")).widget();
      inspect.action = this::inspectAuction;
      col.add(tools);
      col.add(theme.label("For custom AH menus: inspect the slots, then set refresh-slot and next-page-slot in AH GUI."));
      return col;
   }

   // Meteor's separator and title-label calls are not used elsewhere in this addon; if a Meteor build lacks
   // them, fall back to a plain label instead of breaking the settings screen.
   private static void section(GuiTheme theme, WVerticalList col, String name) {
      try {
         col.add((WWidget) (Object) theme.horizontalSeparator(name));
      } catch (Throwable t) {
         col.add(theme.label("— " + name + " —"));
      }
   }

   private static void title(GuiTheme theme, WVerticalList col, String text) {
      try {
         col.add(theme.label(text, true));
      } catch (Throwable t) {
         col.add(theme.label(text));
      }
   }

   private boolean pickBuildPlace(boolean keepLookMode) {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         this.error("Join a world before choosing a build place.");
         return false;
      }
      net.minecraft.class_239 hit = this.mc.field_1724.method_5745(this.pickDistance.get(), 1.0F, false);
      if (!(hit instanceof net.minecraft.class_3965 block) || hit.method_17783() != net.minecraft.class_239.class_240.field_1332) {
         this.error("Aim at a ground block within %d blocks, then choose the place again.", this.pickDistance.get());
         return false;
      }
      try { return this.setBuildPlace(FarmLocation.above(block.method_17777()), keepLookMode); }
      catch (IllegalArgumentException e) { this.error("%s", e.getMessage()); return false; }
   }

   private boolean setBuildPlace(class_2338 p, boolean keepLookMode) {
      try {
         FarmLocation place = new FarmLocation(p, this.currentDim(), this.currentServer());
         if (!this.mc.field_1687.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) {
            this.error("That build location isn't loaded. Move closer and pick it again.");
            return false;
         }
         place.save(LOCATION_FILE.toPath());
         this.buildX.set(p.method_10263());
         this.buildY.set(p.method_10264());
         this.buildZ.set(p.method_10260());
         this.buildAtCoordinates.set(true);
         this.buildWhereLooking.set(keepLookMode);
         Modules.get().save();
         this.info("Saved build corner %d %d %d in %s. Start Farm uses it; Resume keeps the existing job.",
            p.method_10263(), p.method_10264(), p.method_10260(), place.dimension());
         return true;
      } catch (Exception e) {
         this.error("Couldn't save build place: %s", e.getMessage());
         return false;
      }
   }

   private String currentServer() {
      net.minecraft.class_642 server = this.mc.method_1558();
      return server == null || server.field_3761 == null ? "" : server.field_3761.trim().toLowerCase(Locale.ROOT);
   }

   private boolean jobRunning() {
      try {
         return Boolean.parseBoolean(FarmJob.recover(JOB_FILE.toPath(), JOB_BACKUP_FILE.toPath()).getOrDefault("running", "false"));
      } catch (Exception var2) {
         return false;
      }
   }

   private void launch(FarmBuilder.Mode m) {
      if (this.isActive()) {
         this.toggle();
      }

      this.pendingResume = false;
      this.explicitStart = true;
      this.pendingMode = m;
      this.toggle();
   }

   @Override
   public void onActivate() {
      this.pacing.onActivate();
      this.afk.resetWorld();
      this.afkHeld = false;
      this.chunkWaitTarget = null;
      this.overnight.start(System.currentTimeMillis());
      this.sessionStopReason = "";
      this.savedCraftJob = null;
      this.savedCraftRepair = false;
      this.suppressJobSave = false;
      if (!this.explicitStart && !this.pendingResume && this.jobRunning()) this.pendingResume = true;
      if (!this.pendingResume && this.pendingMode != Mode.GEAR && this.buildWhereLooking.get()
         && this.mc.field_1724 != null && this.mc.field_1687 != null && !this.pickBuildPlace(true)) {
         this.suppressJobSave = true;
         this.toggle();
         return;
      }
      this.cancelLoader();
      this.menuPaused = false;
      this.storageReady = false;
      this.storageCrafting = false;
      this.storageSettingUp = false;
      this.stockpiled = false;
      this.madeSomething = false;
      this.depotKind = 0;
      this.nextStash = 0;
      this.nextVault = 0;
      this.nextDiscover = 0;
      this.nextWithdraw = 0;
      this.withdrawItems.clear();
      this.pending.clear();
      this.forecast.reset();
      this.priceNoted.clear();
      this.milestone = 0;
      this.junkCraft = null;
      this.junkSpot = null;
      this.junkLeaving = false;
      this.gearPreparing = false;
      this.reconnectCraft = null;
      this.nextGearCheck = 0;
      this.navigationAction = null;
      this.nextUnavailable = false;
      this.refreshUnavailable = false;
      this.seenAhPages.clear();
      this.confirmClicked = false;
      this.purchaseRejected = "";
      this.observedTools.clear();
      this.replacementTargets.clear();
      this.interruptedCraft = null;
      this.replacementReturning = false;
      this.gatheringReturn = false;
      this.gearDoneReturning = false;
      this.depositReturning = false;
      this.storageReturning = false;
      this.arriveThenLoad = false;
      this.lastReturnPosition = null;
      this.nextToolCheck = 0;
      this.craftBatch = false;
      this.lastBatch.clear();
      this.errorTicks.clear();
      this.lastHealth = -1.0F;
      this.worldSeenAt = -1;
      this.rejoinPending = false;
      this.rejoinAt = -1;
      this.joinCmdSent = false;
      this.hadWorld = false;
      this.buildTicks = 0;
      this.nextProgressCheck = 0;
      this.lastGainTick = 0;
      this.lastPlanTick = -1000;
      this.planOrigin = null;
      this.planSchematic = null;
      this.combat.reset();
      this.survival.reset();
      this.deathsNotified = this.survival.deaths();
      this.movement.reset();
      this.reconnect.reset();
      if (this.mc.field_1724 == null) {
         this.state = FarmBuilder.State.WAIT_WORLD;
      } else {
         this.explicitStart = false;
         this.schematic = null;
         this.schematicFile = null;
         this.loadError = null;
         this.required.clear();
         this.shopping.clear();
         this.unbuyable.clear();
         this.gatherTried.clear();
         this.fallbackItem = null;
         this.gearAhDone = false;
         this.startTick = this.tickCount;
         this.rejoins = 0;
         this.homeSet = false;
         this.boat.reset();
         if (!this.pendingResume) {
            this.movement.setCautious(false);
            this.depot.clear();
            this.stockLedger.clear();
            this.itemsSkipped.clear();
            this.events.clear();
         }

         this.totalBlocks = 0;
         this.siteChosen = false;
         this.safeBuild = false;
         this.clearing = false;
         this.clearAttempts = 0;
         this.bestCorrect = -1;
         this.pausedForPlayer = false;
         this.onBreak = false;
         this.baritonePaused = false;
         this.equipCloseTick = -1;
         this.scheduleBreak();
         this.pauseResumes = 0;
         this.cmdVariant = 0;
         this.current = null;
         this.spent = 0.0;
         this.buildStarted = false;
         this.buildRestarts = 0;
         this.wait = 0;
         this.origin = this.buildAtCoordinates.get()
            ? new class_2338(this.buildX.get(), this.buildY.get(), this.buildZ.get())
            : this.mc.field_1724.method_24515().method_10069(this.offsetX.get(), this.offsetY.get(), this.offsetZ.get());
         this.mode = this.pendingMode;
         this.pendingMode = FarmBuilder.Mode.FULL;
         this.jobDim = this.currentDim();
         this.jobServer = this.currentServer();
         if (!this.pendingResume && this.buildAtCoordinates.get() && LOCATION_FILE.isFile()) {
            try {
               FarmLocation place = FarmLocation.read(LOCATION_FILE.toPath());
               if (place.origin().equals(this.origin) && !place.matches(this.jobDim, this.jobServer)) {
                  this.suppressJobSave = true;
                  this.error("Selected build place belongs to another server or dimension. Pick a place in this world.");
                  this.toggle();
                  return;
               }
            } catch (Exception e) {
               this.suppressJobSave = true;
               this.error("Saved location is damaged. Choose Set Build Here or Set Build Where I'm Looking to replace it.");
               this.toggle();
               return;
            }
         }
         this.movement.applyBaritoneSettings();
         this.reconnect.setArmed(true);
         if (this.pendingResume) {
            this.pendingResume = false;
            if (!this.loadJob()) {
               this.suppressJobSave = true;
               this.error("Saved job is missing, damaged, or belongs to another server. It was kept unchanged.");
               this.toggle();
            } else {
               this.siteChosen = true;
               this.info("Resuming the saved farm location and loading its protection before travel.");
               if (this.mode == Mode.GEAR) this.comeBack(false, true);
               else this.beginWork();
            }
         } else {
            this.beginWork();
         }
      }
   }

   private void beginWork() {
      this.saveJob();
      if (this.mode != FarmBuilder.Mode.GEAR) {
         this.state = FarmBuilder.State.LOADING;
         this.schematic = null;
         this.loadError = null;
         this.cancelLoader();
         long generation = this.loadGeneration;
         String link = this.url.get().trim();
         String name = this.fileName.get().trim();
         Farm preset = this.farm.get();
         this.loaderThread = new Thread(() -> this.load(generation, preset, link, name), "farm-builder-loader");
         this.loaderThread.setDaemon(true);
         this.loaderThread.start();
         this.info("Loading schematic, origin %d %d %d.", this.origin.method_10263(), this.origin.method_10264(), this.origin.method_10260());
      } else {
         this.siteChosen = true;
         Map<class_1792, Integer> goals = new LinkedHashMap<>();
         List<class_1792> order = new ArrayList<>(List.of(class_1802.field_8377));

         for (class_1792 g : this.gearList()) {
            if (!order.contains(g)) {
               order.add(g);
            }
         }

         for (class_1792 gx : order) {
            goals.put(gx, 1);
         }

         if (this.savedCraftJob != null) {
            this.crafter.resume(this.savedCraftJob);
            this.savedCraftJob = null;
         } else this.crafter.start(goals, this::hasGear, this.oreFinding.get(), this.mineStall.get());
         this.state = FarmBuilder.State.CRAFT;
         this.info("Getting diamond gear the hard way: wood -> stone -> iron -> diamonds.");
      }
   }

   @Override
   public void onDeactivate() {
      this.vault.saveIfChanged();
      this.pacing.onDeactivate();
      this.explicitStart = false;
      this.pendingResume = false;
      this.pendingMode = FarmBuilder.Mode.FULL;
      this.cancelLoader();
      IBaritone b = baritone();
      if (b != null) {
         b.getPathingBehavior().cancelEverything();
      }

      if (this.state == FarmBuilder.State.CRAFT || this.state == FarmBuilder.State.TOOL_REPAIR) {
         this.crafter.stop();
      }

      if (this.baritonePaused && b != null) {
         b.getCommandManager().execute("resume");
      }

      this.baritonePaused = false;
      this.combat.reset();
      this.survival.reset();
      this.movement.reset();
      this.boat.reset();
      this.reconnect.setArmed(false);
      Look.releaseMovementKeys();
      if (!this.suppressJobSave && JOB_FILE.isFile() && this.origin != null) {
         this.saveJob(this.rejoinPending || this.mc.field_1687 == null && this.hadWorld);
      }

      this.depot.cancel();
      this.savePriceBook();
      this.notifier.shutdown();
      if (this.mc.field_1724 != null) {
         this.writeReport();
      }

      if (this.isShopScreen()) {
         this.mc.field_1724.method_7346();
      }

      this.writeOvernightHeartbeat(this.sessionStopReason.isEmpty() ? "Stopped; job retained for Resume unless completed or cleared" : "Stopped: " + this.sessionStopReason);
      this.state = FarmBuilder.State.IDLE;
      this.movement.restoreBaritoneSettings();
   }

   @Override
   public String getInfoString() {
      return switch (this.state) {
         case LOADING -> "loading";
         case PLAN -> "planning";
         default -> null;
         case SHOP_OPEN, SHOP_WAIT_GUI, SHOP_SCAN, SHOP_WAIT_UPDATE, SHOP_WAIT_CONFIRM, SHOP_AFTER_BUY -> this.current == null
            ? "shopping"
            : "buying " + this.current.method_63680().getString();
         case BUILD -> "building";
         case DONE -> "done";
      };
   }

   private synchronized void cancelLoader() {
      this.loadGeneration++;
      if (this.loaderThread != null) this.loaderThread.interrupt();
      this.loaderThread = null;
   }

   private void load(long generation, Farm preset, String link, String name) {
      java.nio.file.Path temporary = null;
      try {
         File dir = new File(this.mc.field_1697, "schematics");
         Files.createDirectories(dir.toPath());
         File file;
         File input;
         if (preset != Farm.Custom) {
            file = FarmFiles.resolve(dir, "farm-builder-" + preset.resource);
            temporary = Files.createTempFile(dir.toPath(), "farm-load-", ".litematic");
            try (InputStream in = FarmBuilder.class.getResourceAsStream("/farms/" + preset.resource)) {
               if (in == null) throw new IllegalStateException("The built-in farm is missing from the jar.");
               Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            input = temporary.toFile();
         } else if (!link.isEmpty()) {
            if (name.isEmpty()) name = nameFromUrl(link);
            file = FarmFiles.resolve(dir, name);
            String extension = name.substring(name.lastIndexOf('.'));
            temporary = Files.createTempFile(dir.toPath(), "farm-load-", extension);
            download(link, temporary.toFile());
            input = temporary.toFile();
         } else {
            if (name.isEmpty()) throw new IllegalStateException("Set schematic-url or file-name first.");
            file = FarmFiles.resolve(dir, name);
            input = file;
            if (!file.isFile()) throw new IllegalStateException("No file at " + file.getPath());
         }
         Optional<ISchematicFormat> format = BaritoneAPI.getProvider().getSchematicSystem().getByFile(input);
         if (format.isEmpty()) throw new IllegalStateException("Baritone can't read " + file.getName() + ".");
         IStaticSchematic parsed;
         try (InputStream in = new FileInputStream(input)) {
            parsed = format.get().parse(in);
         }
         long volume = parsed == null ? 0 : (long) parsed.widthX() * parsed.heightY() * parsed.lengthZ();
         if (parsed == null || parsed.widthX() <= 0 || parsed.heightY() <= 0 || parsed.lengthZ() <= 0 || volume > 4000000L)
            throw new IllegalStateException("Schematic dimensions are empty or exceed 4 million blocks.");
         synchronized (this) {
            if (generation != this.loadGeneration || Thread.currentThread().isInterrupted()) return;
            if (temporary != null) {
               Files.createDirectories(file.toPath().getParent());
               FarmFiles.move(temporary, file.toPath());
               temporary = null;
            }
            this.schematicFile = file;
            this.schematic = parsed;
         }
      } catch (Exception e) {
         synchronized (this) {
            if (generation == this.loadGeneration && !Thread.currentThread().isInterrupted())
               this.loadError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
         }
      } finally {
         if (temporary != null) {
            try { Files.deleteIfExists(temporary); }
            catch (Exception e) { MeteorClient.LOG.debug("[Farm Builder] Temporary schematic cleanup failed", e); }
         }
      }
   }

   private static String nameFromUrl(String link) {
      return FarmFiles.nameFromUrl(link);
   }

   private static void download(String link, File target) throws Exception {
      FarmFiles.download(link, target);
   }

   private static Entry<class_1792, Integer> cost(class_2680 s) {
      if (s != null && !s.method_26215()) {
         class_2248 block = s.method_26204();
         if (block != class_2246.field_10382 && block != class_2246.field_10164) {
            if (s.method_28498(class_2741.field_12533) && s.method_11654(class_2741.field_12533) == class_2756.field_12609) {
               return null;
            } else if (s.method_28498(class_2741.field_12483) && s.method_11654(class_2741.field_12483) == class_2742.field_12560) {
               return null;
            } else {
               class_1792 item = block.method_8389();
               if (item == class_1802.field_8162) {
                  return null;
               } else {
                  int count = s.method_28498(class_2741.field_12485) && s.method_11654(class_2741.field_12485) == class_2771.field_12682 ? 2 : 1;
                  return Map.entry(item, count);
               }
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private void plan() {
      if (this.tickCount - this.lastPlanTick >= 40 || this.planOrigin != this.origin || this.schematic != this.planSchematic) {
         this.planNow();
      }
   }

   private void planNow() {
      this.lastPlanTick = this.tickCount;
      this.planOrigin = this.origin;
      this.planSchematic = this.schematic;
      IStaticSchematic s = this.schematic;
      this.required.clear();
      this.shopping.clear();

      for (int x = 0; x < s.widthX(); x++) {
         for (int y = 0; y < s.heightY(); y++) {
            for (int z = 0; z < s.lengthZ(); z++) {
               class_2680 want = s.getDirect(x, y, z);
               Entry<class_1792, Integer> c = cost(want);
               if (c != null) {
                  class_2680 have = this.mc.field_1687.method_8320(this.origin.method_10069(x, y, z));
                  if (!FarmLogic.matches(have, want)) {
                     int amount = c.getValue();
                     Entry<class_1792, Integer> existing = cost(have);
                     if (existing != null && existing.getKey() == c.getKey() && amount > existing.getValue()) amount -= existing.getValue();
                     this.required.merge(c.getKey(), amount, Integer::sum);
                  }
               }
            }
         }
      }

      if (this.gearUp.get() && !this.gearAhDone) {
         for (class_1792 g : this.gearList()) {
            if (!this.hasGear(g)) {
               this.shopping.put(g, 1);
            }
         }
      }

      this.pending.clear();
      for (Entry<class_1792, Integer> e : this.required.entrySet()) {
         int missing = e.getValue() - this.countInInventory(e.getKey());
         if (missing > 0) {
            this.pending.put(e.getKey(), missing);
            // What is already waiting in the outside chest must not be bought or mined again.
            int toGet = missing - this.depot.stored(e.getKey());
            if (toGet > 0) this.shopping.put(e.getKey(), toGet);
         }
      }
   }

   private void tickCraft() {
      Crafter.Status result = this.crafter.tick();
      if (result != Crafter.Status.RUNNING && this.mode == Mode.FULL) this.madeSomething = true;
      if (this.storageCrafting && result != Crafter.Status.RUNNING) {
         this.storageCrafting = false;
         if (result == Crafter.Status.FAILED || this.countInInventory(class_1802.field_8106) <= 0) {
            this.giveUp("Couldn't craft the outside supply chest. Bring a chest and Resume Saved Farm.");
            return;
         }
         this.storageSettingUp = true;
         if (this.origin != null && !this.mc.field_1724.method_24515().method_19771(this.origin, 6.0) && !this.gatherHomeCommand.get().isBlank()) {
            this.beginGatherReturn(false, false);
            this.storageReturning = true;
            return;
         }
         this.depot.ensure(this.depotAnchor());
         this.state = State.DEPOT;
         return;
      }
      if (this.gearPreparing && result != Crafter.Status.RUNNING) {
         this.gearPreparing = false;
         if (result == Crafter.Status.FAILED || this.gearList().stream().anyMatch(item -> !this.hasGear(item))) {
            this.giveUp("Couldn't finish diamond gear preparation. The farm was saved for Resume.");
            return;
         }
      }
      switch (result) {
         case RUNNING:
         default:
            break;
         case FAILED:
            boolean full = this.crafter.failReason != null && this.crafter.failReason.startsWith("Inventory is full");
            if (this.craftBatch) {
               this.craftBatch = false;
               if (!full) {
                  this.warning("%s", this.crafter.failReason);
                  this.unbuyable.addAll(this.lastBatch);
               }

               this.buildStarted = false;
               this.state = full ? FarmBuilder.State.BUILD : FarmBuilder.State.PLAN;
               return;
            }

            if (full && this.mode == FarmBuilder.Mode.ITEMS) {
               this.itemsSkipped.addAll(this.crafter.skipped);
               this.startDeposit();
               return;
            }

            if (this.fallbackItem != null) {
               this.warning("%s", this.crafter.failReason);
               this.unbuyable.add(this.fallbackItem);
               this.fallbackItem = null;
               this.buildStarted = false;
               this.state = FarmBuilder.State.PLAN;
               return;
            }

            this.error(this.crafter.failReason);
            this.toggle();
            break;
         case DONE:
            if (this.craftBatch) {
               this.craftBatch = false;
               this.unbuyable.addAll(this.crafter.skipped);
               this.buildStarted = false;
               if (!this.returnAfterCraft()) this.state = FarmBuilder.State.PLAN;
               return;
            }

            if (this.fallbackItem != null) {
               this.unbuyable.addAll(this.crafter.skipped);
               this.fallbackItem = null;
               this.buildStarted = false;
               if (!this.returnAfterCraft()) this.state = FarmBuilder.State.PLAN;
               return;
            }

            if (!this.crafter.skipped.isEmpty()) {
               this.warning("Couldn't make: %s", this.crafter.skipped.stream().map(i -> i.method_63680().getString()).reduce((a, b) -> a + ", " + b).orElse(""));
            }

            if (this.mode == FarmBuilder.Mode.FULL) {
               this.unbuyable.addAll(this.crafter.skipped);
               if (!this.returnAfterCraft()) this.state = FarmBuilder.State.BUILD;
               return;
            }

            if (this.mode == FarmBuilder.Mode.ITEMS) {
               this.itemsSkipped.addAll(this.crafter.skipped);
               this.startDeposit();
               return;
            }

            if (!this.gatherHomeCommand.get().isBlank()) this.beginGatherReturn(true, false);
            else this.finishGear();
      }
   }

   private List<class_1792> gearList() {
      List<class_1792> out = new ArrayList<>(List.of(class_1802.field_8802));
      out.addAll(List.of(ARMOR_PIECES));
      if (this.gearTools.get()) {
         out.addAll(List.of(TOOLS));
      }

      return out;
   }

   private boolean isGear(class_1792 item) {
      return UPGRADE.containsKey(item);
   }

   private boolean hasGear(class_1792 item) {
      class_1792 better = UPGRADE.get(item);
      for (int i = 0; i < 36; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if ((stack.method_7909() == item || stack.method_7909() == better) && FarmLogic.usableGear(stack)) return true;
      }
      class_1304 slot = ARMOR_SLOT.get(item);
      if (slot == null) return false;
      class_1799 worn = this.mc.field_1724.method_6118(slot);
      return (worn.method_7909() == item || worn.method_7909() == better) && FarmLogic.usableGear(worn);
   }

   private int countInInventory(class_1792 item) {
      class_1661 inv = this.mc.field_1724.method_31548();
      int n = 0;

      for (int i = 0; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         if (st.method_7909() == item) {
            n += st.method_7947();
         }
      }

      class_1799 off = this.mc.field_1724.method_6079();
      if (off.method_7909() == item) {
         n += off.method_7947();
      }

      return n;
   }

   private boolean inventoryFull() {
      return this.mc.field_1724.method_31548().method_7376() == -1;
   }

   private void printMaterials() {
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         if (this.schematic == null) {
            this.info("Start the module once so the schematic gets loaded.");
         } else {
            if (this.origin == null) {
               this.origin = this.mc.field_1724.method_24515().method_10069(this.offsetX.get(), this.offsetY.get(), this.offsetZ.get());
            }

            this.planNow();
            if (this.required.isEmpty()) {
               this.info("Nothing left to place.");
            } else {
               this.info("Materials still to place (have / need):");

               for (Entry<class_1792, Integer> e : this.required.entrySet()) {
                  ChatUtils.info("  %s: %d / %d", e.getKey().method_63680().getString(), this.countInInventory(e.getKey()), e.getValue());
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(Post event) {
      try {
         if (this.mc.field_1724 != null && this.mc.field_1687 != null && this.state != FarmBuilder.State.WAIT_WORLD) {
            this.pacing.tick();
         }
         this.tickInner();
      } catch (Throwable var3) {
         this.onTickError(var3);
      }
   }

   private void onTickError(Throwable t) {
      MeteorClient.LOG.error("[Farm Builder] tick failed", t);
      this.errorTicks.addLast(this.tickCount);

      while (!this.errorTicks.isEmpty() && this.tickCount - this.errorTicks.peekFirst() > 6000) {
         this.errorTicks.removeFirst();
      }

      try {
         if (this.errorTicks.size() > 8) {
            this.giveUp("Kept hitting errors (" + t + ").");
            return;
         }

         this.note("Recovered from an error (%s), restarting the current step.", t.getClass().getSimpleName());
         this.recover();
      } catch (Throwable var3) {
      }
   }

   private boolean checkFrozen() {
      if (this.tickCount % 100 != 0) {
         return false;
      } else {
         int hash = 1;

         for (int i = 0; i < 36; i++) {
            class_1799 st = this.mc.field_1724.method_31548().method_5438(i);
            hash = 31 * hash + (st.method_7960() ? 0 : st.method_7909().hashCode() + st.method_7947());
         }

         class_2338 pos = this.mc.field_1724.method_24515();
         boolean waitingOnPurpose = this.onBreak
            || this.pausedForPlayer
            || Look.playerScreenOpen()
            || this.state == FarmBuilder.State.IDLE
            || this.state == FarmBuilder.State.DONE
            || this.state == FarmBuilder.State.CRAFT && this.crafter.isSmeltWaiting();
         if (!waitingOnPurpose
            && this.state == this.lastActivityState
            && hash == this.lastActivityHash
            && this.lastActivityPos != null
            && pos.method_19771(this.lastActivityPos, 3.0)) {
            if (this.tickCount - this.lastActivity < 12000) {
               return false;
            } else {
               this.note("Nothing happened for 10 minutes (stuck in %s), restarting that step.", this.state.name().toLowerCase());
               this.recover();
               return true;
            }
         } else {
            this.lastActivity = this.tickCount;
            this.lastActivityState = this.state;
            this.lastActivityHash = hash;
            this.lastActivityPos = pos;
            return false;
         }
      }
   }

   private void recover() {
      try {
         Look.releaseMovementKeys();
         Look.stopMining();
         if (this.mc.field_1755 instanceof class_465 || this.mc.field_1755 != null && !(this.mc.field_1755 instanceof class_418)) {
            Look.closeScreen();
         }

         this.combat.reset();
         this.survival.reset();
         this.movement.reset();
         this.depot.cancel();
         this.boat.reset();
         this.pickupTarget = null;
         IBaritone b = baritone();
         if (b != null) {
            if (this.baritonePaused) {
               b.getCommandManager().execute("resume");
            }

            b.getPathingBehavior().cancelEverything();
         }

         this.baritonePaused = false;
         this.lastActivity = this.tickCount;
         if (this.mc.field_1724 != null && (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) && this.crafter.isOreMining()) {
            this.crafter.reissue();
            return;
         }
         if (this.mc.field_1724 != null
            && this.state != FarmBuilder.State.IDLE
            && this.state != FarmBuilder.State.DONE
            && this.state != FarmBuilder.State.WAIT_WORLD
            && this.state != FarmBuilder.State.LOADING) {
            this.keepCurrentCraft();
            this.comeBack(false, false);
         }
      } catch (Throwable var2) {
      }
   }

   private void tickInner() {
      long now = System.currentTimeMillis();
      if (this.overnight.expired(now)) {
         this.giveUp("Overnight session time limit reached; saved for Resume.");
         return;
      }
      if (this.overnight.reportDue(now)) {
         this.writeReport();
         this.writeOvernightHeartbeat(this.activity());
      }
      this.reconnect.tick();
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         Look.tickScreens();
         if (this.pauseMenus.get() && Look.playerScreenOpen()) {
            if (!this.menuPaused) {
               this.menuPaused = true;
               this.saveJob();
               this.setBaritonePaused(true);
            }
            Look.releaseMovementKeys();
            Look.stopMining();
            return;
         }
         if (this.menuPaused) {
            this.afk.serverPulse(now);
            this.menuPaused = false;
            this.setBaritonePaused(false);
            this.movement.reset();
            this.movement.applyBaritoneSettings();
         }
      }
      if (this.mc.field_1724 != null && this.mc.field_1687 != null) {
         this.drops.world(this.currentServer() + "|" + this.currentDim());
         boolean replannable = this.state == State.BUILD || this.state == State.CRAFT || this.state == State.TOOL_REPAIR
            || this.state == State.TOOL_APPROACH || this.state == State.JUNK_TRIP || this.state == State.DEPOT;
         if (this.afk.teleported(this.mc.field_1724.method_24515(), replannable)) {
            this.chunkWaitTarget = null;
            this.afk.chunk(now, true);
            this.saveJob();
            this.escapeMenu();
            this.movement.reset();
            this.reissue();
            this.note("Unexpected teleport detected; replanning the saved work from the current position.");
         }
         if (this.tickAfkHold(now)) return;
         this.hadWorld = true;
         this.tickCount++;
         this.drainDepotAlerts();
         this.rememberTools();
         if (this.state == FarmBuilder.State.WAIT_WORLD) {
            if (this.worldSeenAt < 0) {
               this.worldSeenAt = this.tickCount;
            }

            if (this.tickCount - this.worldSeenAt > 100) this.onActivate();
         } else if (this.state != FarmBuilder.State.IDLE) {
            if (this.survival.handleDeathScreen()) {
               this.combat.reset();
               if (this.survival.deaths() > this.deathsNotified) {
                  this.deathsNotified = this.survival.deaths();
                  this.ping("Died (" + this.deathsNotified + " so far).");
               }
            } else if (this.survival.tooManyDeaths()) {
               this.giveUp("Died " + this.survival.deaths() + " times, stopping.");
            } else {
               if (this.survival.consumeRespawned()) {
                  this.keepCurrentCraft();
                  this.comeBack(true, false);
               }

               this.reconnect.consumeRejoined();
               if (this.rejoinPending) {
                  if (this.rejoinAt < 0) {
                     this.rejoinAt = this.tickCount + 20;
                     Look.releaseMovementKeys();
                     this.combat.reset();
                  }

                  if (this.tickCount < this.rejoinAt) {
                     return;
                  }

                  if (!this.joinCmdSent && !this.joinCommand.get().isBlank()) {
                     this.joinCmdSent = true;
                     ChatUtils.sendPlayerMsg(this.joinCommand.get().trim());
                     this.rejoinAt = this.tickCount + 100;
                     return;
                  }

                  this.rejoinPending = false;
                  this.rejoinAt = -1;
                  this.joinCmdSent = false;
                  this.rejoins++;
                  String reason = this.reconnect.lastKickReason();
                  this.note("Back in the game after a disconnect%s, carrying on.", reason.isBlank() ? "" : " (" + reason.trim() + ")");
                  this.comeBack(false, false);
                  if (this.reconnect.consumeSuspicious()) {
                     this.movement.setCautious(true);
                     this.movement.applyBaritoneSettings();
                     this.note("That kick looked like an anticheat kick. Switching to careful mode (no parkour, shorter reach).");
                  }
               }

               if (this.equipCloseTick > 0 && this.tickCount >= this.equipCloseTick) {
                  if (this.mc.field_1755 instanceof class_490) {
                     this.mc.field_1724.method_7346();
                  }

                  this.equipCloseTick = -1;
               }

               if (this.equipCloseTick > 0) {
                  this.setBaritonePaused(true);
                  return;
               }

               boolean shopping = this.state.name().startsWith("SHOP_");
               if (!Look.playerScreenOpen() || shopping || this.mc.field_1755 instanceof class_490 && this.equipCloseTick > 0) {
                  boolean working = this.state != FarmBuilder.State.LOADING && this.state != FarmBuilder.State.DONE;
                  if (working) {
                     if (this.checkPlayerAttack()) {
                        return;
                     }

                     if (this.tickCount - this.threatCacheTick >= 4) {
                        this.threatCache = this.combat.threatIncoming();
                        this.threatCacheTick = this.tickCount;
                     }

                     boolean threat = this.threatCache;
                     if (!threat) {
                        this.threatSince = -1;
                     } else if (this.threatSince < 0) {
                        this.threatSince = this.tickCount;
                     } else if (this.tickCount - this.threatSince > 300 && !this.combat.isFighting()) {
                        threat = false;
                     }

                     if (this.mc.field_1755 instanceof class_465 && (threat || this.inDanger())) {
                        this.escapeMenu();
                     }

                     boolean survivalBusy = this.survival.tick();
                     if (this.survival.consumeWorkResume()) this.reissue();
                     boolean fightingNow = (this.mc.field_1755 == null || this.combat.ownsScreen()) && this.combat.tick();
                     if (this.combat.consumeFleeEnded()) {
                        this.reissue();
                     }

                     if (fightingNow || threat || survivalBusy) this.movement.suspend();

                     // Deliberate pauses (player nearby, break, fight, inventory wait) are not a freeze:
                     // keep the freeze timer fresh so the first check after a long pause doesn't misfire.
                     if (this.tickSafety(survivalBusy)) {
                        this.lastActivity = this.tickCount;
                        return;
                     }

                     if (fightingNow || threat || survivalBusy) {
                        this.lastActivity = this.tickCount;
                        if (this.survival.isBreathing()) this.tickDigTool();
                        return;
                     }

                     if (!fightingNow && !survivalBusy && this.tickInventoryWait()) {
                        this.lastActivity = this.tickCount;
                        return;
                     }
                     if (!fightingNow && !survivalBusy && this.mc.field_1755 == null && this.startJunkTrip()) return;

                     if (this.movement.needsPause()) {
                        this.tickDigTool();
                        if (this.equipCloseTick > 0) return;
                     }
                     boolean clearingWater = this.movement.needsPause();
                     Movement.Verdict verdict = this.movement.tick(this.expectProgress());
                     if (clearingWater || this.movement.needsPause()) this.setBaritonePaused(this.movement.needsPause());
                     if (verdict == Movement.Verdict.GIVE_UP) {
                        this.giveUp("Stuck for good: " + this.movement.lastReason() + ".");
                        return;
                     }

                     if (verdict == Movement.Verdict.REISSUE) {
                        this.reissue();
                     }

                     if (verdict == Movement.Verdict.RECOVERING) {
                        return;
                     }

                     if (fightingNow || threat || survivalBusy && !this.survival.needsBaritone()) {
                        return;
                     }

                     if ((this.state == State.BUILD || this.state == State.PLAN) && this.startJunkTrip()) return;
                     if (this.startGearPreparation()) return;
                     if (this.startToolReplacement()) return;
                     if (!this.state.name().startsWith("SHOP_") && this.tickPickup()) {
                        return;
                     }

                     // Not while shopping: equipping a just-bought armor piece would hide it from the purchase check.
                     if (this.mc.field_1755 == null && !this.state.name().startsWith("SHOP_")
                        && ((this.state != State.CRAFT && this.state != State.TOOL_REPAIR) || !this.crafter.isClicking())) {
                        this.tickDigTool();
                        if (this.equipCloseTick > 0) return;
                        this.tickEquip();
                        if (this.equipCloseTick > 0) return;
                     }
                  }

                  if (this.tickCount % 100 == 0) {
                     this.saveJob();
                  }

                  if (!this.checkFrozen()) {
                     boolean buildPhase = this.state == FarmBuilder.State.BUILD;
                     if (buildPhase != this.inBuildSettings || !buildPhase && this.tickCount % 200 == 0) {
                        if (buildPhase) {
                           this.configureBaritone();
                        } else {
                           this.travelSettings();
                        }

                        this.inBuildSettings = buildPhase;
                     }
                     if (!buildPhase && this.tickCount % 20 == 0) this.travelSettings();

                     if (buildPhase) this.saveFarmHome();

                     if (this.wait > 0) {
                        this.wait--;
                     } else {
                        switch (this.state) {
                           case RETURNING:
                              this.tickReturning();
                              break;
                           case DEPOT:
                              this.tickDepot();
                              break;
                           case LOADING:
                              this.tickLoading();
                              break;
                           case PLAN:
                              this.tickPlan();
                              break;
                           case CRAFT:
                              this.tickCraft();
                              break;
                           case JUNK_TRIP:
                              this.tickJunkTrip();
                              break;
                           case TOOL_APPROACH:
                              this.tickToolApproach();
                              break;
                           case TOOL_REPAIR:
                              this.tickToolRepair();
                              break;
                           case SHOP_OPEN:
                              this.tickShopOpen();
                              break;
                           case SHOP_WAIT_GUI:
                              this.tickShopWaitGui();
                              break;
                           case SHOP_SCAN:
                              this.tickShopScan();
                              break;
                           case SHOP_WAIT_UPDATE:
                              this.tickShopWaitUpdate();
                              break;
                           case SHOP_WAIT_CONFIRM:
                              this.tickShopWaitConfirm();
                              break;
                           case SHOP_AFTER_BUY:
                              this.tickShopAfterBuy();
                              break;
                           case BUILD:
                              this.tickBuild();
                              break;
                           case DONE:
                              Map<class_1792, Integer> skipped = this.skippedNeeds();
                              // Re-count the world before the job is cleared: only a farm with every block placed counts as complete.
                              boolean complete = this.mode == FarmBuilder.Mode.FULL && skipped.isEmpty()
                                 && this.totalBlocks > 0 && this.countCorrect() >= this.totalBlocks;
                              this.clearJob();
                              this.note("Farm finished.");
                              this.ping(skipped.isEmpty() ? "Farm finished. Spent " + money(this.spent) + "." : "Farm built except " + describe(skipped) + ". Spent " + money(this.spent) + ".");
                              if (skipped.isEmpty()) {
                                 this.info("Farm finished. Spent %s.", money(this.spent));
                              } else {
                                 this.warning("Farm built except %s (couldn't buy or mine them). Spent %s.", describe(skipped), money(this.spent));
                              }

                              if (complete && this.logoutWhenDone.get()) {
                                 this.info("Every block is placed. Logging out.");
                                 this.logOut("Farm finished, all blocks placed. Logged out.");
                              } else {
                                 this.toggle();
                              }
                        }
                     }
                  }
               } else {
                  this.setBaritonePaused(true);
               }
            }
         }
      } else {
         if (this.hadWorld && this.state != FarmBuilder.State.IDLE && this.state != FarmBuilder.State.WAIT_WORLD && this.state != FarmBuilder.State.DONE) {
            if (!this.rejoinPending) {
               if (this.state == State.JUNK_TRIP && this.junkCraft != null) {
                  this.reconnectCraft = this.junkCraft;
                  this.junkCraft = null;
               }
               if (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) {
                  this.reconnectCraft = this.crafter.snapshot();
                  this.crafter.stop();
               }
               this.saveJob(true);
               Look.releaseMovementKeys();
               Look.stopMining();
            }
            this.rejoinPending = true;
         }

         this.hadWorld = false;
      }
   }

   @EventHandler
   private void onServerPulse(meteordevelopment.meteorclient.events.packets.PacketEvent.Receive event) {
      if (AfkSupport.worldUpdate(event.packet)) this.afk.serverPulse(System.currentTimeMillis());
   }

   private boolean tickAfkHold(long now) {
      boolean canHold = this.state != State.IDLE && this.state != State.DONE && this.state != State.LOADING && this.state != State.WAIT_WORLD
         && !this.mc.field_1724.method_29504() && !(this.mc.field_1755 instanceof class_418);
      String hold = null;
      if (canHold && this.afk.lagging(now)) {
         if (this.afk.lagExpired(now)) { this.giveUp("No server world updates for two minutes; saved for Resume."); return true; }
         hold = "Server updates stopped; pausing actions until the server responds.";
      } else if (canHold) {
         class_2338 next = this.mc.field_1724.method_24515();
         IBaritone b = baritone();
         var executor = b == null ? null : b.getPathingBehavior().getCurrent();
         if (executor != null && executor.getPosition() >= 0 && executor.getPosition() < executor.getPath().movements().size())
            next = executor.getPath().movements().get(executor.getPosition()).getDest();
         if (this.chunkWaitTarget != null) next = this.chunkWaitTarget;
         boolean loaded = this.mc.field_1687.method_8393(next.method_10263() >> 4, next.method_10260() >> 4);
         this.chunkWaitTarget = loaded ? null : next.method_10062();
         AfkSupport.Chunk chunk = this.afk.chunk(now, loaded);
         if (chunk == AfkSupport.Chunk.STOP) { this.giveUp("Current route chunk did not load after three waits; saved for Resume."); return true; }
         if (chunk == AfkSupport.Chunk.REPLAN) {
            this.chunkWaitTarget = null;
            this.setBaritonePaused(false);
            this.afkHeld = false;
            this.movement.reset();
            this.reissue();
            return true;
         }
         if (chunk == AfkSupport.Chunk.WAIT) hold = "Waiting for the next movement's chunk to load.";
      }
      if (hold != null) {
         if (!this.afkHeld) { this.afkHeld = true; this.saveJob(); this.note("%s", hold); }
         this.setBaritonePaused(true);
         Look.releaseMovementKeys();
         Look.stopMining();
         return true;
      }
      if (this.afkHeld) {
         this.afkHeld = false;
         this.setBaritonePaused(false);
         this.movement.reset();
         this.reissue();
      }
      return false;
   }

   private void tickLoading() {
      if (this.loadError != null) {
         this.error(this.loadError);
         this.toggle();
      } else if (this.schematic != null) {
         IStaticSchematic s = this.schematic;
         if (!new FarmLocation(this.origin, this.jobDim, this.jobServer).fits(s.widthX(), s.heightY(), s.lengthZ(),
            this.mc.field_1687.method_31607(), this.mc.field_1687.method_31605())) {
            this.error("The farm would extend outside this dimension's build height or supported coordinates. Pick another corner.");
            this.toggle();
            return;
         }
         this.info("Loaded %s (%dx%dx%d).", this.schematicFile.getName(), s.widthX(), s.heightY(), s.lengthZ());
         this.totalBlocks = 0;
         Set<class_2248> farmBlocks = new HashSet<>();

         for (int x = 0; x < s.widthX(); x++) {
            for (int y = 0; y < s.heightY(); y++) {
               for (int z = 0; z < s.lengthZ(); z++) {
                  class_2680 want = s.getDirect(x, y, z);
                  if (!want.method_26215() && cost(want) != null) {
                     this.totalBlocks++;
                  }

                  if (!want.method_26215() && !isNatural(want.method_26204().method_9564()) && want.method_26227().method_15769()) {
                     farmBlocks.add(want.method_26204());
                  }
               }
            }
         }

         this.protectedBlocks.clear();
         this.protectedBlocks.addAll(farmBlocks);
         if (!this.siteChosen) {
            this.siteChosen = true;
            if (this.autoSite.get() && !this.buildAtCoordinates.get()) {
               class_2338 site = this.findSite(s.widthX(), s.heightY(), s.lengthZ());
               if (site != null) {
                  this.origin = site;
                  this.note(
                     "Building at %d %d %d (%d blocks away, flattest empty spot nearby).",
                     site.method_10263(),
                     site.method_10264(),
                     site.method_10260(),
                     (int)Math.sqrt(this.mc.field_1724.method_24515().method_10262(site))
                  );
               } else {
                  this.warning("No flat empty spot within %d blocks, building next to you instead.", this.siteRadius.get());
               }
            }

            if (!new FarmLocation(this.origin, this.jobDim, this.jobServer).fits(s.widthX(), s.heightY(), s.lengthZ(),
               this.mc.field_1687.method_31607(), this.mc.field_1687.method_31605())) {
               this.giveUp("Selected site cannot fit the whole farm. Choose another corner.");
               return;
            }
            this.saveJob();
            if (this.mode != FarmBuilder.Mode.GEAR && this.mc.field_1724.method_24515().method_10262(this.origin) > 36.0) {
               this.arriveThenLoad = true;
               this.comeBack(false, false);
               this.returnSettleUntil = this.tickCount;
               return;
            }
         }

         if (this.mode != Mode.GEAR && this.mc.field_1724.method_24515().method_10262(this.origin) > 36.0) {
            this.arriveThenLoad = true;
            this.comeBack(false, false);
            this.returnSettleUntil = this.tickCount;
            return;
         }
         if (!this.homeSet && this.mode != Mode.GEAR && !this.setHomeCommand.get().isBlank()) {
            if (!this.atHomeAnchor()) {
               this.arriveThenLoad = true;
               this.comeBack(false, false);
               this.returnSettleUntil = this.tickCount;
               return;
            }
            this.saveFarmHome();
         }

         if (this.savedCraftJob != null) {
            // The storage setup below is skipped when a saved craft resumes; find the existing chests now so
            // stash-before-build and the vault keep working this session.
            if (this.mode == Mode.FULL && this.outsideChest.get() && !this.storageReady && !this.storageCrafting) {
               this.depot.discover(this.depotAnchor());
               this.storageReady = this.depot.chestCount() > 0;
            }
            this.crafter.resume(this.savedCraftJob);
            this.savedCraftJob = null;
            this.state = this.savedCraftRepair ? State.TOOL_REPAIR : State.CRAFT;
            this.savedCraftRepair = false;
            return;
         }
         if (this.mode == Mode.FULL && this.outsideChest.get() && !this.storageReady) {
            this.depot.discover(this.depotAnchor());
            if (this.depot.chestCount() == 0 && this.countInInventory(class_1802.field_8106) == 0) {
               this.storageCrafting = true;
               this.crafter.start(Map.of(class_1802.field_8106, 1), item -> false, this.oreFinding.get(), this.mineStall.get());
               this.state = State.CRAFT;
            } else {
               this.storageSettingUp = true;
               this.depot.ensure(this.depotAnchor());
               this.state = State.DEPOT;
            }
            return;
         }
         if (this.mode == FarmBuilder.Mode.FULL && (this.mineGear.get() || !this.useAh.get())) {
            Map<class_1792, Integer> gear = new LinkedHashMap<>();
            if (this.gearUp.get()) {
               for (class_1792 g : this.gearList()) {
                  if (!this.hasGear(g)) {
                     gear.put(g, 1);
                  }
               }
            }

            if (!gear.isEmpty()) {
               this.gearPreparing = true;
               this.craftBatch = true;
               this.crafter.start(gear, i -> this.isGear(i) && this.hasGear(i), this.oreFinding.get(), this.mineStall.get());
               this.state = FarmBuilder.State.CRAFT;
            } else {
               this.state = FarmBuilder.State.PLAN;
            }
         } else if (this.mode == FarmBuilder.Mode.ITEMS) {
            this.planNow();
            this.startItemsGathering();
         } else {
            this.state = FarmBuilder.State.PLAN;
         }
      }
   }

   private void tickPlan() {
      this.plan();
      this.shopping.keySet().removeAll(this.unbuyable);
      if (this.tryStockpile() || this.tryVault()) return;
      if (!this.tryWithdraw()) {
         if (this.nothingLeftToGet()) {
            this.state = FarmBuilder.State.DONE;
         } else {
            if (this.buy.get() && this.useAh.get() && !this.shopping.isEmpty() && !this.inventoryFull()) {
               this.info("Need to buy %d item types.", this.shopping.size());
               this.current = null;
               this.state = FarmBuilder.State.SHOP_OPEN;
            } else {
               this.state = FarmBuilder.State.BUILD;
            }
         }
      }
   }

   private void tickShopOpen() {
      if (this.current == null || !this.shopping.containsKey(this.current)) {
         this.current = this.pickShopItem();
         this.refreshes = 0;
         this.pages = 0;
         this.nextUnavailable = false;
         this.refreshUnavailable = false;
         this.seenAhPages.clear();
      }

      if (this.current != null && this.knownMissing(this.current)) {
         // Remembered: this AH had none of it recently. Gather it now instead of re-checking the AH every batch.
         this.info("Remembered the AH had no %s recently; gathering it instead.", this.current.method_63680().getString());
         this.missFromMemory = true;
         this.giveUpOnCurrent();
         this.missFromMemory = false;
         return;
      }

      if (this.current == null) {
         this.closeShop();
         this.state = FarmBuilder.State.BUILD;
      } else {
         if (this.isShopScreen()) {
            this.mc.field_1724.method_7346();
         }

         List<String> cmds = this.commandVariants();
         if (this.cmdVariant >= cmds.size()) {
            this.error("None of the AH commands work on this server. Set ah-search-command to whatever opens the AH here.");
            this.toggle();
         } else {
            String query = this.current.method_63680().getString();
            this.cmdRejected = false;
            Look.expectScreen(this.guiTimeout.get() + 40);
            ChatUtils.sendPlayerMsg(cmds.get(this.cmdVariant).replace("{item}", query));
            this.timeout = this.guiTimeout.get();
            this.state = FarmBuilder.State.SHOP_WAIT_GUI;
         }
      }
   }

   private List<String> commandVariants() {
      LinkedHashSet<String> out = new LinkedHashSet<>();
      String own = this.ahCommand.get().trim();
      if (!own.isEmpty()) {
         out.add(own.startsWith("/") ? own : "/" + own);
      }

      out.add("/ah {item}");
      out.add("/ah search {item}");
      out.add("/auctionhouse search {item}");
      out.add("/ah");
      return new ArrayList<>(out);
   }

   private boolean browsing() {
      List<String> cmds = this.commandVariants();
      return this.cmdVariant < cmds.size() && !cmds.get(this.cmdVariant).contains("{item}");
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      if (this.state == State.SHOP_WAIT_CONFIRM || this.state == State.SHOP_AFTER_BUY) {
         String message = AuctionUi.normalize(event.getMessage().getString());
         if (List.of("already sold", "expired", "not enough", "insufficient", "purchase failed", "verkauft", "abgelaufen", "nicht genug", "fehlgeschlagen").stream().anyMatch(message::contains)) this.purchaseRejected = event.getMessage().getString();
      }
      if (this.state == FarmBuilder.State.SHOP_WAIT_GUI) {
         String msg = event.getMessage().getString().toLowerCase(Locale.ROOT);
         if (msg.contains("incorrect argument") || msg.contains("unknown command") || msg.contains("usage:") || msg.contains("unknown or incomplete")) {
            this.cmdRejected = true;
         }
      }
   }

   private void tickShopWaitGui() {
      if (this.isShopScreen()) {
         this.wait = this.humanDelay(this.clickDelay.get());
         this.state = FarmBuilder.State.SHOP_SCAN;
      } else {
         if (this.cmdRejected || --this.timeout <= 0) {
            List<String> cmds = this.commandVariants();
            if (this.cmdVariant + 1 < cmds.size()) {
               this.cmdVariant++;
               this.info("AH command rejected, trying %s", cmds.get(this.cmdVariant).replace("{item}", "<item>"));
               this.state = FarmBuilder.State.SHOP_OPEN;
               return;
            }

            this.warning("AH didn't open for %s, skipping it.", this.current.method_63680().getString());
            this.giveUpOnCurrent();
         }
      }
   }

   private void tickShopScan() {
      if (!this.isShopScreen()) {
         this.state = FarmBuilder.State.SHOP_OPEN;
      } else {
         class_1703 handler = this.mc.field_1724.field_7512;
         if (this.current == null || this.shopping.getOrDefault(this.current, 0) <= 0) { this.state = State.SHOP_OPEN; return; }
         boolean freshPage = this.seenAhPages.add(this.auctionFingerprint(handler));
         int need = this.shopping.getOrDefault(this.current, 0);
         boolean useBook = this.priceBook.get() && !this.isGear(this.current);
         String itemId = class_7923.field_41178.method_10221(this.current).method_12832();
         if (useBook) this.ensurePriceBook();
         boolean bookSkipped = false;
         class_1735 best = null;
         double bestUnit = Double.MAX_VALUE;
         double bestPrice = 0.0;

         for (class_1735 slot : handler.field_7761) {
            if (!(slot.field_7871 instanceof class_1661)) {
               class_1799 st = slot.method_7677();
               // Worn-out gear never counts as owned, so buying it would only lead to buying it again.
               if (!st.method_7960() && st.method_7909() == this.current && FarmLogic.usableGear(st)) {
                  double price = this.priceOf(st);
                  if (Double.isFinite(price) && price > 0.0) {
                     double unit = price / st.method_7947();
                     if (useBook && freshPage) this.prices.record(itemId, unit);
                     double cap = this.isGear(this.current) ? this.gearPrice.get() : this.maxUnitPrice.get();
                     boolean overBook = useBook && this.prices.tooExpensive(itemId, unit, this.priceLimit.get());
                     if (overBook) bookSkipped = true;
                     if ((!(cap > 0.0) || !(unit > cap)) && (!(this.budget.get() > 0.0) || !(this.spent + price > this.budget.get())) && !overBook) {
                        double score = st.method_7947() > need ? unit * 1.15 : unit;
                        if (score < bestUnit) {
                           bestUnit = score;
                           best = slot;
                           bestPrice = price;
                        }
                     }
                  }
               }
            }
         }

         if (useBook && this.prices.takeDirty()) this.savePriceBook();
         if (bookSkipped && best == null && this.priceNoted.add(this.current)) {
            this.note("%s listings are far above the usual price (about %s each); skipping them.", this.current.method_63680().getString(), money(this.prices.fair(itemId)));
         }
         if (best != null) {
            this.pendingPrice = bestPrice;
            this.pendingInventoryCount = this.countInInventory(this.current);
            this.purchaseConfirmed = false;
            this.confirmClicked = false;
            this.purchaseRejected = "";
            this.purchaseDeadline = this.tickCount + this.deliveryTimeout.get();
            this.lastSyncId = handler.field_7763;
            this.click(handler, best.field_7874, this.listingClick.get());
            this.timeout = this.guiTimeout.get();
            this.wait = this.humanDelay(this.clickDelay.get());
            this.state = FarmBuilder.State.SHOP_WAIT_CONFIRM;
         } else {
            if (!this.nextUnavailable && this.pages < this.maxPages.get() * (this.browsing() ? 4 : 1)) {
               class_1735 next = this.findAuctionControl(handler, AuctionUi.Action.NEXT);
               if (next != null) { this.navigateAuction(handler, next, AuctionUi.Action.NEXT); return; }
               this.nextUnavailable = true;
            }
            if (this.refreshes < this.refreshTries.get()) {
               class_1735 refresh = this.refreshUnavailable ? null : this.findAuctionControl(handler, AuctionUi.Action.REFRESH);
               if (refresh != null) { this.navigateAuction(handler, refresh, AuctionUi.Action.REFRESH); return; }
               this.refreshes++;
               this.pages = 0;
               this.nextUnavailable = false;
               this.seenAhPages.clear();
               this.note("AH refresh control unavailable; reopening the search to refresh listings. Inspect AH Controls can show custom slots.");
               this.state = State.SHOP_OPEN;
               return;
            }

            this.warning("No affordable %s on the AH.", this.current.method_63680().getString());
            this.giveUpOnCurrent();
         }
      }
   }

   private void tickShopWaitConfirm() {
      if (this.current == null) { this.state = State.PLAN; return; }
      if (this.countInInventory(this.current) > this.pendingInventoryCount || !this.purchaseRejected.isBlank()) {
         this.state = State.SHOP_AFTER_BUY;
         return;
      }
      if (this.isShopScreen() && !this.confirmClicked) {
         class_1703 handler = this.mc.field_1724.field_7512;
         String title = this.mc.field_1755.method_25440().getString();
         class_1735 candidate = this.findAuctionControl(handler, AuctionUi.Action.CONFIRM);
         boolean selected = handler.field_7761.stream().anyMatch(slot -> !(slot.field_7871 instanceof class_1661) && slot.method_7677().method_7909() == this.current);
         boolean cancel = handler.field_7761.stream().anyMatch(slot -> !(slot.field_7871 instanceof class_1661)
            && (AuctionUi.words(AuctionUi.text(slot.method_7677()), "cancel") || AuctionUi.words(AuctionUi.text(slot.method_7677()), "abbrechen")));
         boolean titled = AuctionUi.words(title, this.confirmKeyword.get()) || AuctionUi.words(title, "purchase")
            || AuctionUi.words(title, "bestatigen") || AuctionUi.words(title, "bestaetigen");
         boolean confirmMenu = AuctionUi.confirmContext(titled, candidate != null, cancel, selected, handler.field_7763 != this.lastSyncId, this.confirmSlot.get() >= 0);
         class_1735 confirm = confirmMenu ? candidate : null;
         if (confirm != null) {
            this.click(handler, confirm.field_7874);
            this.confirmClicked = true;
            this.purchaseDeadline = this.tickCount + this.deliveryTimeout.get();
            this.wait = this.humanDelay(this.clickDelay.get());
            this.state = State.SHOP_AFTER_BUY;
            return;
         }
      }
      if (this.tickCount >= this.purchaseDeadline) this.state = State.SHOP_AFTER_BUY;
   }

   private void tickShopAfterBuy() {
      if (this.current == null) { this.state = State.PLAN; return; }
      boolean arrived = this.countInInventory(this.current) > this.pendingInventoryCount;
      if (!arrived && this.purchaseRejected.isBlank() && this.tickCount < this.purchaseDeadline) return;
      if (!arrived) {
         this.closeShop();
         if (!this.purchaseRejected.isBlank()) {
            this.warning("AH rejected the purchase: %s. Trying material gathering instead.", this.purchaseRejected);
            this.giveUpOnCurrent();
         } else {
            this.giveUp("The AH purchase didn't reach inventory. Check the server's collection/delivery menu, then Resume Saved Farm.");
         }
         return;
      }
      if (!this.purchaseConfirmed) this.spent += this.pendingPrice;
      this.purchaseConfirmed = true;
      this.info("Received %s for %s (budget used %s).", this.current.method_63680().getString(), money(this.pendingPrice), money(this.spent));
      this.saveJob();
      this.planNow();
      this.shopping.keySet().removeAll(this.unbuyable);
      this.pages = 0;
      this.nextUnavailable = false;
      this.refreshUnavailable = false;
      this.seenAhPages.clear();
      if (this.inventoryFull() || this.shopping.isEmpty()) {
         this.closeShop();
         this.state = State.BUILD;
      } else this.state = State.SHOP_OPEN;
   }

   private void navigateAuction(class_1703 handler, class_1735 control, AuctionUi.Action action) {
      this.navigationAction = action;
      this.navigationBefore = this.auctionFingerprint(handler);
      this.navigationRetries = 0;
      this.navigationSettle = this.tickCount + this.refreshDelay.get();
      this.navigationDeadline = this.tickCount + Math.max(this.guiTimeout.get(), this.refreshDelay.get() + 40);
      this.click(handler, control.field_7874);
      this.state = State.SHOP_WAIT_UPDATE;
   }

   private void tickShopWaitUpdate() {
      if (!this.isShopScreen()) {
         if (this.tickCount >= this.navigationDeadline) this.state = State.SHOP_OPEN;
         return;
      }
      if (this.tickCount < this.navigationSettle) return;
      class_1703 handler = this.mc.field_1724.field_7512;
      long fingerprint = this.auctionFingerprint(handler);
      if (fingerprint != this.navigationBefore) {
         if (this.navigationAction == AuctionUi.Action.NEXT) {
            if (this.seenAhPages.contains(fingerprint)) this.nextUnavailable = true;
            else this.pages++;
         } else {
            this.refreshes++;
            this.pages = 0;
            this.nextUnavailable = false;
            this.seenAhPages.clear();
         }
         this.state = State.SHOP_SCAN;
         return;
      }
      if (this.tickCount < this.navigationDeadline) return;
      class_1735 control = this.findAuctionControl(handler, this.navigationAction);
      if (this.navigationRetries++ == 0 && control != null) {
         this.click(handler, control.field_7874);
         this.navigationDeadline = this.tickCount + this.guiTimeout.get();
         this.navigationSettle = this.tickCount + this.refreshDelay.get();
         return;
      }
      if (this.navigationAction == AuctionUi.Action.NEXT) this.nextUnavailable = true;
      else this.refreshUnavailable = true;
      this.note("AH %s produced no visible update. Trying the next recovery step.", this.navigationAction.name().toLowerCase(Locale.ROOT));
      this.state = State.SHOP_SCAN;
   }

   private class_1735 findAuctionControl(class_1703 handler, AuctionUi.Action action) {
      List<AuctionUi.Control> controls = new ArrayList<>();
      for (class_1735 slot : handler.field_7761) {
         class_1799 stack = slot.method_7677();
         if (!stack.method_7960()) controls.add(new AuctionUi.Control(slot.field_7874, AuctionUi.text(stack),
            slot.field_7871 instanceof class_1661, this.priceOf(stack) > 0));
      }
      String keyword = switch (action) { case NEXT -> this.nextKeyword.get(); case REFRESH -> this.refreshKeyword.get(); case CONFIRM -> this.confirmKeyword.get(); };
      int override = switch (action) { case NEXT -> this.nextSlot.get(); case REFRESH -> this.refreshSlot.get(); case CONFIRM -> this.confirmSlot.get(); };
      int id = AuctionUi.choose(controls, action, keyword, override);
      return id < 0 ? null : handler.method_7611(id);
   }

   private long auctionFingerprint(class_1703 handler) {
      long hash = AuctionUi.stableText(this.mc.field_1755.method_25440().getString()).hashCode();
      for (class_1735 slot : handler.field_7761) {
         if (!(slot.field_7871 instanceof class_1661)) {
            class_1799 stack = slot.method_7677();
            hash = hash * 31 + slot.field_7874;
            hash = hash * 31 + stack.method_7947();
            hash = hash * 31 + class_7923.field_41178.method_10221(stack.method_7909()).hashCode();
            hash = hash * 31 + AuctionUi.stableText(AuctionUi.text(stack)).hashCode();
         }
      }
      return hash;
   }

   private void captureAuction() {
      if (this.mc.field_1724 == null || !this.isShopScreen()) return;
      List<String> lines = new ArrayList<>();
      for (class_1735 slot : this.mc.field_1724.field_7512.field_7761) {
         if (!(slot.field_7871 instanceof class_1661) && !slot.method_7677().method_7960()) {
            String text = AuctionUi.text(slot.method_7677()).replace('\n', ' ');
            lines.add("Slot " + slot.field_7874 + ": " + (text.length() > 150 ? text.substring(0,150) : text));
         }
      }
      this.auctionSnapshot = lines;
      this.auctionSnapshotTitle = this.mc.field_1755.method_25440().getString();
   }

   private void inspectAuction() {
      this.captureAuction();
      if (this.auctionSnapshot.isEmpty()) {
         this.error("Open the AH once, then reopen Farm Builder settings and press Inspect AH Controls.");
         return;
      }
      this.info("Last container: %s. AH GUI overrides use these slot IDs:", this.auctionSnapshotTitle);
      for (String line : this.auctionSnapshot) this.info("%s", line);
   }

   private static String itemId(class_1792 item) {
      return String.valueOf(class_7923.field_41178.method_10221(item));
   }

   private boolean knownMissing(class_1792 item) {
      int minutes = this.ahMissMemory.get();
      return minutes > 0 && this.gather.get() && !this.isGear(item)
         && this.memory.recentlyMissed(this.currentServer(), itemId(item), System.currentTimeMillis(), minutes * 60000L);
   }

   /** Items that may be on the AH come first; remembered misses only once nothing else is left to buy. */
   private class_1792 pickShopItem() {
      class_1792 firstMissed = null;
      for (class_1792 i : this.shopping.keySet()) {
         if (this.unbuyable.contains(i)) continue;
         if (this.knownMissing(i)) {
            if (firstMissed == null) firstMissed = i;
            continue;
         }
         return i;
      }
      return firstMissed;
   }

   private void printMemory() {
      this.info("Farm Builder memory (%s):", MEMORY_FILE.getName());
      try {
         FarmLocation place = FarmLocation.read(LOCATION_FILE.toPath());
         this.info("  Farm spot: %d %d %d (%s)", place.origin().method_10263(), place.origin().method_10264(), place.origin().method_10260(), place.dimension());
      } catch (Exception e) {
         this.info("  Farm spot: none saved");
      }
      FarmLocation spot = this.mc.field_1687 == null ? null : this.supplySpot();
      this.info("  Supply chest: %s", spot == null ? "automatic" : spot.origin().method_10263() + " " + spot.origin().method_10264() + " " + spot.origin().method_10260());
      this.info("  Known chests: %d", this.depot.chestCount());
      List<String> misses = this.memory.recentMisses(this.currentServer(), System.currentTimeMillis(), Math.max(1, this.ahMissMemory.get()) * 60000L);
      this.info("  Not on this AH: %s", misses.isEmpty() ? "nothing remembered" : String.join(", ", misses));
      for (String e : this.memory.recentEvents(10)) this.info("  %s", e);
   }

   private void giveUpOnCurrent() {
      class_1792 item = this.current;
      if (item != null && !this.missFromMemory) this.memory.ahMissed(this.currentServer(), itemId(item), System.currentTimeMillis());
      this.shopping.remove(item);
      this.current = null;
      this.closeShop();
      if (this.gather.get() && this.isGear(item)) {
         this.gearAhDone = true;
         this.shopping.keySet().removeIf(this::isGear);
         Map<class_1792, Integer> goals = new LinkedHashMap<>();
         List<class_1792> order = new ArrayList<>(List.of(class_1802.field_8377));

         for (class_1792 g : this.gearList()) {
            if (!order.contains(g)) {
               order.add(g);
            }
         }

         for (class_1792 gx : order) {
            if (this.gearList().contains(gx) && !this.hasGear(gx)) {
               goals.put(gx, 1);
            }
         }

         this.fallbackItem = item;
         this.crafter.start(goals, i -> this.isGear(i) && this.hasGear(i), this.oreFinding.get(), this.mineStall.get());
         this.info("Gear isn't on the AH, crafting the full set myself.");
         this.state = FarmBuilder.State.CRAFT;
      } else if (this.gather.get() && !this.unbuyable.contains(item)) {
         // Every AH miss starts another batch: big farms need more than one 64-item batch, and a gather that
         // actually fails already marks the item unbuyable in tickCraft, so this cannot loop forever.
         this.gatherTried.add(item);
         int target = Math.min(this.required.getOrDefault(item, 1), this.countInInventory(item) + 64);
         this.fallbackItem = item;
         this.crafter.start(Map.of(item, target), i -> this.isGear(i) && this.hasGear(i), this.oreFinding.get(), this.mineStall.get());
         this.info("No %s on the AH, making it myself.", item.method_63680().getString());
         this.state = FarmBuilder.State.CRAFT;
      } else {
         this.unbuyable.add(item);
         this.state = this.shopping.isEmpty() ? FarmBuilder.State.BUILD : FarmBuilder.State.SHOP_OPEN;
      }
   }

   private void tickDigTool() {
      boolean clearingWater = this.movement.needsPause() || this.survival.isBreathing();
      if (this.mc.field_1755 != null || this.combat.isFighting() || this.baritonePaused && !clearingWater) return;
      IBaritone b = baritone();
      if (b == null || !b.getInputOverrideHandler().isInputForcedDown(baritone.api.utils.input.Input.CLICK_LEFT)
         && !(clearingWater && this.mc.field_1761 != null && this.mc.field_1761.method_2923())) return;
      if (!(this.mc.field_1765 instanceof net.minecraft.class_3965 hit)) return;
      class_2680 block = this.mc.field_1687.method_8320(hit.method_17777());
      if (block.method_26215()) return;
      class_1799[] inventory = new class_1799[36];
      for (int i = 0; i < inventory.length; i++) inventory[i] = this.mc.field_1724.method_31548().method_5438(i);
      int slot = FarmLogic.bestDigTool(inventory, block, this.mc.field_1724.method_31548().method_67532());
      if (slot < 0) return;
      if (slot < 9) {
         meteordevelopment.meteorclient.utils.player.InvUtils.swap(slot, false);
      } else {
         if (!this.mc.field_1724.field_7498.method_34255().method_7960()) return;
         this.setBaritonePaused(true);
         Look.stopMining();
         Look.releaseMovementKeys();
         if (Look.openInventoryIfClosed() && this.mc.field_1724.field_7498.method_34255().method_7960()) {
            Look.clickSwapToHotbar(slot, 6);
            this.equipCloseTick = this.tickCount + 4;
         }
      }
   }

   private void tickEquip() {
      if (this.gearUp.get() && this.tickCount % 20 == 0 && !this.combat.isFighting()) {
         if (!(this.mc.field_1724.method_18798().method_37268() > 0.003)) {
            class_1661 inv = this.mc.field_1724.method_31548();

            for (int i = 0; i < 36; i++) {
               class_1304 slot = ARMOR_SLOT.get(inv.method_5438(i).method_7909());
               class_1792 candidate = inv.method_5438(i).method_7909();
               class_1799 worn = slot == null ? class_1799.field_8037 : this.mc.field_1724.method_6118(slot);
               if (slot != null && FarmLogic.preferArmor(inv.method_5438(i), worn)) {
                  this.setBaritonePaused(true);
                  Look.openInventoryIfClosed();
                  if (!this.mc.field_1724.field_7498.method_34255().method_7960()) return;
                  int armorSlot = slot == class_1304.field_6169 ? 5 : slot == class_1304.field_6174 ? 6
                     : slot == class_1304.field_6172 ? 7 : 8;
                  meteordevelopment.meteorclient.utils.player.InvUtils.move().from(i).toId(armorSlot);
                  this.equipCloseTick = this.tickCount + 4 + this.random.nextInt(8);
                  return;
               }
            }
         }
      }
   }

   private static String toolFamily(class_1792 item) { return FarmLogic.toolFamily(item); }

   private static int toolTier(class_1792 item) { return FarmLogic.toolTier(item); }

   private void rememberTools() {
      for (int i = 0; i < 36; i++) {
         class_1799 stack = this.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_7960()) continue;
         String family = toolFamily(stack.method_7909());
         if (family != null) this.observedTools.merge(family, stack.method_7909(),
            (a, b) -> toolTier(a) >= toolTier(b) ? a : b);
      }
   }

   private boolean usableTool(class_1799 stack, String family) {
      return FarmLogic.usableTool(stack, family, this.toolDurability.get());
   }

   private boolean hasUsableTool(String family) {
      for (int i = 0; i < 36; i++)
         if (this.usableTool(this.mc.field_1724.method_31548().method_5438(i), family)) return true;
      return this.usableTool(this.mc.field_1724.method_6079(), family);
   }

   private class_2338 miningEntry() {
      if (this.origin == null || this.mc.field_1687 == null) return null;
      int width = this.schematic == null ? 16 : this.schematic.widthX();
      int length = this.schematic == null ? 16 : this.schematic.lengthZ();
      int x = this.origin.method_10263() + width + this.mineMargin.get();
      int z = this.origin.method_10260() + length / 2;
      int y = this.mc.field_1687.method_8624(class_2903.field_13197, x, z);
      if (y <= this.mc.field_1687.method_31607()) y = this.origin.method_10264();
      return new class_2338(x, y, z);
   }

   private boolean startGearPreparation() {
      if (!this.gearUp.get() || !this.mineGear.get() || this.mode != Mode.FULL || this.schematic == null
         || !(this.state == State.PLAN || this.state == State.BUILD) || this.tickCount < this.nextGearCheck
         || this.mc.field_1755 != null) return false;
      this.nextGearCheck = this.tickCount + 100;
      Map<class_1792, Integer> missing = new LinkedHashMap<>();
      for (class_1792 item : this.gearList()) if (!this.hasGear(item)) missing.put(item, 1);
      if (missing.isEmpty()) return false;
      IBaritone b = baritone();
      if (b != null) b.getPathingBehavior().cancelEverything();
      this.buildStarted = false;
      this.travelSettings();
      this.gearPreparing = true;
      this.craftBatch = true;
      this.lastBatch.clear();
      this.crafter.start(missing, this::hasGear, this.oreFinding.get(), this.mineStall.get());
      this.state = State.CRAFT;
      this.saveJob();
      this.note("Preparing missing diamond gear using the mining entry beside the base.");
      return true;
   }

   private boolean startToolReplacement() {
      if (!this.replaceTools.get() || this.tickCount < this.nextToolCheck || this.replacementReturning
         || !(this.state == State.BUILD || this.state == State.CRAFT && this.crafter.isWalking())
         || this.mc.field_1755 != null || this.schematic == null || this.origin == null) return false;
      this.nextToolCheck = this.tickCount + 20;
      this.replacementTargets.clear();
      for (Entry<String, class_1792> observed : this.observedTools.entrySet()) {
         if (this.hasUsableTool(observed.getKey())) continue;
         class_1792 target = observed.getValue();
         if (!Crafter.knows(target)) target = switch (observed.getKey()) {
            case "pickaxe" -> class_1802.field_8377;
            case "axe" -> class_1802.field_8556;
            default -> class_1802.field_8250;
         };
         this.replacementTargets.put(observed.getKey(), target);
      }
      if (this.replacementTargets.isEmpty()) return false;
      this.interruptedCraft = this.state == State.CRAFT ? this.crafter.snapshot() : null;
      this.crafter.stop();
      Look.stopMining();
      Look.releaseMovementKeys();
      this.buildStarted = false;
      this.pickupTarget = null;
      this.travelSettings();
      this.replacementEntry = this.interruptedCraft != null ? this.mc.field_1724.method_24515()
         : this.mineBesideFarm.get() ? this.miningEntry() : this.mc.field_1724.method_24515();
      this.replacementEntrySince = this.tickCount;
      this.state = State.TOOL_APPROACH;
      this.note("Tool worn out or broken: %s. Replacing tools without abandoning the current gathering trip.", String.join(", ", this.replacementTargets.keySet()));
      return true;
   }

   private void tickToolApproach() {
      IBaritone b = baritone();
      if (b == null) { this.giveUp("Baritone isn't loaded for tool replacement."); return; }
      if (this.mc.field_1724.method_24515().method_19771(this.replacementEntry, 5.0)) {
         b.getPathingBehavior().cancelEverything();
         Map<class_1792, Integer> goals = new LinkedHashMap<>();
         for (class_1792 item : this.replacementTargets.values()) goals.put(item, this.countInInventory(item) + 1);
         this.crafter.start(goals, item -> false, this.oreFinding.get(), this.mineStall.get());
         this.state = State.TOOL_REPAIR;
      } else if (this.tickCount - this.replacementEntrySince > 2400) {
         this.giveUp("Couldn't reach the mining entry beside the farm for tool replacement.");
      } else if (!b.getCustomGoalProcess().isActive() && this.tickCount % 20 == 0) {
         b.getCustomGoalProcess().setGoalAndPath(new GoalNear(this.replacementEntry, 3));
      }
   }

   private void tickToolRepair() {
      Crafter.Status result = this.crafter.tick();
      if (result == Crafter.Status.RUNNING) return;
      if (result == Crafter.Status.FAILED) {
         this.giveUp("Tool replacement failed: " + this.crafter.failReason);
         return;
      }
      for (String family : this.replacementTargets.keySet()) {
         if (!this.hasUsableTool(family)) { this.giveUp("Couldn't gather the materials for a replacement " + family + "."); return; }
      }
      if (this.interruptedCraft != null) {
         Crafter.Job job = this.interruptedCraft;
         this.interruptedCraft = null;
         this.replacementTargets.clear();
         this.nextToolCheck = this.tickCount + 100;
         this.crafter.resume(job);
         this.state = State.CRAFT;
         this.saveJob();
         this.note("Replacement tools crafted. Continuing the interrupted gathering trip.");
         return;
      }
      this.note("Replacement tools crafted. Returning to the farm.");
      this.replacementReturning = true;
      this.beginGatherReturn(false, false);
   }

   private boolean returnAfterCraft() {
      if (this.mode == Mode.FULL && this.origin != null && !this.mc.field_1724.method_24515().method_19771(this.origin, 6.0)) {
         this.beginGatherReturn(false, false);
         return true;
      }
      return false;
   }

   private void beginGatherReturn(boolean gearDone, boolean deposit) {
      this.comeBack(false, false);
      this.gatheringReturn = true;
      this.gearDoneReturning = gearDone;
      this.depositReturning = deposit;
      this.returnSettleUntil = this.tickCount;
      this.saveJob();
   }

   private void finishGear() {
      this.note("Gear done.");
      this.clearJob();
      this.toggle();
   }

   private int humanDelay(int base) {
      return base + this.random.nextInt(Math.max(1, base / 2 + 2));
   }

   private boolean tickInventoryWait() {
      boolean work = this.state == State.BUILD || this.state == State.PLAN || this.state == State.CRAFT || this.state == State.TOOL_REPAIR;
      IBaritone currentBaritone = baritone();
      boolean placing = this.state == State.BUILD && this.buildStarted && currentBaritone != null
         && currentBaritone.getBuilderProcess().isActive() && !currentBaritone.getBuilderProcess().isPaused();
      boolean canMerge = placing || (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) && this.crafter.canContinueWithFullInventory();
      if (!this.overnight.waitEnabled() || !work || canMerge) {
         if (this.overnight.waitingInventory()) {
            this.overnight.clearInventoryWait();
            this.setBaritonePaused(false);
            this.reissue();
         }
         return false;
      }
      int slots = this.freeInventorySlots();
      if (this.overnight.waitingInventory() && slots >= 3) {
         this.overnight.clearInventoryWait();
         this.setBaritonePaused(false);
         this.movement.reset();
         this.reissue();
         this.note("Inventory space is available; resuming the saved work.");
         return false;
      }
      if (!this.overnight.waitingInventory() && (slots != 0 || this.survival.hasDiscardableJunk(this::keepItem))) return false;
      long now = System.currentTimeMillis();
      if (!this.overnight.waitingInventory()) {
         this.overnight.waitInventory(now);
         this.note("Inventory is full of required items; waiting for three free slots instead of abandoning the job.");
         this.saveJob();
      }
      this.setBaritonePaused(true);
      Look.releaseMovementKeys();
      Look.stopMining();
      if (this.overnight.inventoryExpired(now)) this.giveUp("Inventory-space waiting limit reached. Empty some slots and Resume Saved Farm.");
      return true;
   }

   private void writeOvernightHeartbeat(String activity) {
      try {
         this.overnight.report(OVERNIGHT_FILE.toPath(), System.currentTimeMillis(), activity, this.reconnect.attempts(),
            this.survival.foodCount(), this.mc.field_1724 == null ? -1 : this.freeInventorySlots(), this.crafter.miningStatus());
      } catch (Exception e) { MeteorClient.LOG.warn("[Farm Builder] Overnight status could not be written", e); }
   }

   private boolean tickSafety(boolean survivalBusy) {
      if (this.pauseNearPlayers.get() && this.tickCount % 10 == 0) {
         boolean near = this.playerNearby();
         if (near) {
            this.playerClearChecks = 0;
            if (!this.pausedForPlayer) {
               this.pausedForPlayer = true;
               this.info("Player nearby, pausing.");
            }
         } else if (this.pausedForPlayer && ++this.playerClearChecks >= 10) {
            this.pausedForPlayer = false;
            this.info("Clear, carrying on.");
         }
      }

      boolean busyClicking = (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) && this.crafter.isClicking();
      if (this.takeBreaks.get()
         && !this.onBreak
         && this.tickCount >= this.nextBreak
         && this.mc.field_1755 == null
         && !busyClicking
         && !this.combat.isFighting()
         && !survivalBusy) {
         this.onBreak = true;
         int secs = randomBetween(this.breakMin.get(), Math.max(this.breakMin.get(), this.breakMax.get()));
         this.breakEnd = this.tickCount + secs * 20;
         this.info("Short break (%ds).", secs);
      }

      if (this.onBreak && this.tickCount >= this.breakEnd) {
         this.onBreak = false;
         this.scheduleBreak();
      }

      boolean idle = this.pausedForPlayer || this.onBreak;
      boolean holdBaritone = idle || this.overnight.waitingInventory() || this.state.name().startsWith("SHOP_") || this.combat.isFighting() && !this.combat.needsBaritone() || survivalBusy && !this.survival.needsBaritone();
      this.setBaritonePaused(this.movement.needsPause()
         || holdBaritone && !this.movement.isRecovering() && !this.survival.needsBaritone() && !this.combat.needsBaritone());
      if (idle) {
         Look.idleLook(this.random);
      }

      return idle;
   }

   private boolean playerNearby() {
      double r = this.playerRadius.get().intValue();

      for (class_1657 p : this.mc.field_1687.method_18456()) {
         if (p != this.mc.field_1724 && !p.method_7325() && !Friends.get().isFriend(p) && this.mc.field_1724.method_5739(p) <= r) {
            return true;
         }
      }

      return false;
   }

   private void scheduleBreak() {
      int mins = randomBetween(this.workMin.get(), Math.max(this.workMin.get(), this.workMax.get()));
      this.nextBreak = this.tickCount + mins * 60 * 20;
   }

   private void setBaritonePaused(boolean pause) {
      if (pause != this.baritonePaused) {
         IBaritone b = baritone();
         if (b != null) {
            b.getCommandManager().execute(pause ? "pause" : "resume");
            this.baritonePaused = pause;
         }
      }
   }

   private static int randomBetween(int min, int max) {
      return min + (int)(Math.random() * (max - min + 1));
   }

   private boolean expectProgress() {
      if (!this.baritonePaused && this.mc.field_1755 == null) {
         IBaritone b = baritone();

         return switch (this.state) {
            case RETURNING -> this.returnWalking;
            case DEPOT -> this.depot.isWalking();
            case CRAFT, TOOL_REPAIR -> this.crafter.isWalking();
            case TOOL_APPROACH, JUNK_TRIP -> baritone() != null && baritone().getCustomGoalProcess().isActive();
            case BUILD -> this.buildStarted && b != null && b.getBuilderProcess().isActive() && !b.getBuilderProcess().isPaused();
            default -> false;
         };
      } else {
         return false;
      }
   }

   @EventHandler
   private void onRender(Render2DEvent event) {
      if (this.showPanel.get() && this.mc.field_1724 != null && !this.mc.field_1690.field_1842) {
         if (this.hudModel == null || this.tickCount - this.panelCacheTick >= 10 || this.tickCount < this.panelCacheTick) {
            this.hudModel = this.dashModel();
            this.panelCacheTick = this.tickCount;
         }

         this.hud.render(event.drawContext, this.mc.field_1772, this.panelX.get(), this.panelY.get(), this.hudModel, this.compactPanel.get());
      }
   }

   private static String xyz(class_2338 p) {
      return p.method_10263() + " " + p.method_10264() + " " + p.method_10260();
   }

   private String badge() {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) return "OFFLINE";
      if (this.overnight.waitingInventory()) return "WAITING";
      if (this.state == FarmBuilder.State.WAIT_WORLD) return "LOADING";
      if (Look.playerScreenOpen() && !this.state.name().startsWith("SHOP_") || this.pausedForPlayer || this.onBreak) return "PAUSED";
      if (this.combat.status() != null) return "COMBAT";
      if (this.survival.status() != null) return "SURVIVAL";
      if (this.movement.isRecovering()) return "UNSTUCK";
      if (this.state.name().startsWith("SHOP_")) return "SHOPPING";
      return switch (this.state) {
         case RETURNING -> "TRAVEL";
         case DEPOT -> "STORAGE";
         case JUNK_TRIP -> "CLEANUP";
         case LOADING, PLAN -> "PLANNING";
         case CRAFT, TOOL_REPAIR, TOOL_APPROACH -> "GATHERING";
         case BUILD -> "BUILDING";
         case DONE -> "DONE";
         default -> "IDLE";
      };
   }

   /** Everything the on-screen dashboard shows, rebuilt twice a second. */
   private HudPanel.Model dashModel() {
      HudPanel.Model m = new HudPanel.Model();
      m.badge = this.badge();
      m.badgeColor = HudPanel.badgeColor(m.badge);
      m.activity = this.activity();
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         m.subtitle = "Halloween Edition";
         if (this.origin != null) m.row("Site", xyz(this.origin));
         return m;
      }

      m.subtitle = switch (this.mode) {
         case ITEMS -> "Gathering items";
         case GEAR -> "Gathering gear";
         default -> "Building " + this.farm.get();
      };

      if (this.totalBlocks > 0 && this.mode == FarmBuilder.Mode.FULL) {
         int done = Math.max(0, Math.min(this.bestCorrect, this.totalBlocks));
         m.bar("Build" + (this.safeBuild ? " (place-only)" : ""), done + "/" + this.totalBlocks + " " + done * 100 / this.totalBlocks + "%",
            done / (float) this.totalBlocks, HudPanel.ORANGE);
      }
      if (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) m.row("Mining", this.crafter.miningStatus());
      if (this.baritonePaused) m.row("Paused", this.afkHeld ? "server/chunk wait" : this.equipCloseTick > 0 ? "tool inventory" : this.onBreak ? "scheduled break" : this.pausedForPlayer ? "player nearby" : "safety/menu/recovery");
      m.row("Needs", this.lookingFor());
      if (this.totalBlocks > 0 && this.state == FarmBuilder.State.BUILD) m.row("Pace", this.forecast.line(Math.max(0, this.bestCorrect), this.totalBlocks));
      if (this.depot.chestCount() > 0) {
         int n = this.depot.totals().values().stream().mapToInt(Integer::intValue).sum();
         m.row("Storage", n + " items in " + this.depot.chestCount() + " chest" + (this.depot.chestCount() == 1 ? "" : "s") + (this.stockpiled ? ", stockpiled" : ""));
         if (!this.stockLedger.isEmpty()) m.row("Made", describe(this.stockLedger));
         if (this.tamperAlerts > 0) m.row("Alerts", this.tamperAlerts + " chest tripwire alert" + (this.tamperAlerts == 1 ? "" : "s"));
      }
      if (this.origin != null) {
         int dist = (int) Math.sqrt(this.mc.field_1724.method_24515().method_10262(this.origin));
         m.row("Site", xyz(this.origin) + "  (" + dist + "m)");
      }

      int mins = Math.max(0, this.tickCount - this.startTick) / 1200;
      int used = 0;
      for (int i = 0; i < 36; i++) {
         if (!this.mc.field_1724.method_31548().method_5438(i).method_7960()) used++;
      }
      m.chip("HP " + Math.round(this.mc.field_1724.method_6032()));
      m.chip("Food " + this.mc.field_1724.method_7344().method_7586());
      m.chip("Free " + (36 - used));
      m.chip("Up " + mins / 60 + "h" + mins % 60 + "m");
      if (this.combat.spotted() > 0) m.chip("Mobs " + this.combat.spotted());
      if (this.survival.deaths() > 0) m.chip("Deaths " + this.survival.deaths());
      if (this.rejoins > 0) m.chip("Rejoins " + this.rejoins);
      if (this.spent > 0) m.chip("Spent " + money(this.spent));
      return m;
   }

   private List<String> statusLines() {
      List<String> out = new ArrayList<>();

      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         out.add("Farm Builder | disconnected");
         out.add(this.reconnect.status());
         out.add("Saved site: " + this.origin);
         return out;
      }

      String job = switch (this.mode) {
         case ITEMS -> "Get Items";
         case GEAR -> "Get Gear";
         default -> "Build " + this.farm.get();
      };
      out.add("Farm Builder | " + job);
      out.add("Doing: " + this.activity());
      var feet = this.mc.field_1724.method_24515();
      out.add("Player: " + feet.method_10263() + " " + feet.method_10264() + " " + feet.method_10260());
      if (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) out.add("Mining: " + this.crafter.miningStatus());
      if (this.baritonePaused) out.add("Movement paused: " + (this.afkHeld ? "server/chunk wait" : this.equipCloseTick > 0 ? "tool inventory" : this.onBreak ? "scheduled break" : this.pausedForPlayer ? "player nearby" : "safety/menu/recovery"));
      String looking = this.lookingFor();
      if (looking != null) {
         out.add("Looking for: " + looking);
      }

      if (this.totalBlocks > 0 && (this.state == FarmBuilder.State.BUILD || this.state == FarmBuilder.State.PLAN || this.mode == FarmBuilder.Mode.FULL)) {
         int done = Math.max(0, this.bestCorrect);
         out.add("Build: " + done + "/" + this.totalBlocks + " blocks (" + done * 100 / this.totalBlocks + "%)" + (this.safeBuild ? " place-only" : ""));
         String forecastLine = this.forecast.line(done, this.totalBlocks);
         if (forecastLine != null && this.state == FarmBuilder.State.BUILD) out.add(forecastLine);
      }

      if (this.depot.chestCount() > 0) {
         int n = this.depot.totals().values().stream().mapToInt(Integer::intValue).sum();
         out.add("Stored at farm: " + n + " items in " + this.depot.chestCount() + " chest" + (this.depot.chestCount() == 1 ? "" : "s")
            + (this.stockpiled ? " (materials stockpiled)" : ""));
         int vaulted = this.depot.totals().entrySet().stream()
            .filter(en -> VaultPolicy.isValuable(class_7923.field_41178.method_10221(en.getKey()).method_12832()) && !this.required.containsKey(en.getKey()))
            .mapToInt(Entry::getValue).sum();
         if (!this.stockLedger.isEmpty()) out.add("Made and stored: " + describe(this.stockLedger));
         if (vaulted > 0 || this.tamperAlerts > 0) {
            out.add("Vault: " + vaulted + " valuables locked | Chest tripwire alerts: " + this.tamperAlerts);
         }
      }

      if (this.origin != null) {
         int dist = (int)Math.sqrt(this.mc.field_1724.method_24515().method_10262(this.origin));
         out.add("Site: " + this.origin.method_10263() + " " + this.origin.method_10264() + " " + this.origin.method_10260() + " (" + dist + "m)");
      }

      out.add(
         "Mobs spotted: "
            + this.combat.spotted()
            + " | HP "
            + (int)this.mc.field_1724.method_6032()
            + " | Food "
            + this.mc.field_1724.method_7344().method_7586()
      );
      int mins = Math.max(0, this.tickCount - this.startTick) / 1200;
      out.add("Deaths " + this.survival.deaths() + " | Rejoins " + this.rejoins + " | Spent " + money(this.spent) + " | " + mins / 60 + "h" + mins % 60 + "m");
      return out;
   }

   private String activity() {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) return "Disconnected; " + this.reconnect.status();
      if (this.overnight.waitingInventory()) return this.overnight.hold();
      if (this.state == FarmBuilder.State.WAIT_WORLD) {
         return "Waiting for the world to load";
      } else if (Look.playerScreenOpen() && !this.state.name().startsWith("SHOP_")) {
         return "Paused (you have a menu open)";
      } else if (this.pausedForPlayer) {
         return "Paused (player nearby)";
      } else if (this.onBreak) {
         return "Short break (" + Math.max(0, (this.breakEnd - this.tickCount) / 20) + "s)";
      } else {
         String c = this.combat.status();
         if (c != null) {
            return c;
         } else {
            String sv = this.survival.status();
            if (sv != null) {
               return sv;
            } else if (this.movement.isRecovering()) {
               return "Getting unstuck: " + this.movement.lastReason();
            } else if (this.pickupTarget != null) {
               return "Picking up " + ((class_1542)this.pickupTarget).method_6983().method_7964().getString();
            } else {
               return switch (this.state) {
                  case RETURNING -> this.boat.isActive()
                     ? this.boat.status()
                     : (this.tickCount < this.returnSettleUntil ? "Settling in after (re)joining"
                        : this.gatheringReturn && !this.gatherHomeCommand.get().isBlank() ? "Returning with " + this.gatherHomeCommand.get() : "Walking back to the farm");
                  case DEPOT -> this.depot.status() == null ? "Storage" : this.depot.status();
                  case LOADING -> "Loading the schematic";
                  case PLAN -> "Working out what's missing";
                  case CRAFT -> this.crafter.status();
                  case TOOL_REPAIR -> "Replacing tools: " + this.crafter.status();
                  case TOOL_APPROACH -> "Going to the mining entry beside the farm";
                  case SHOP_OPEN, SHOP_WAIT_GUI -> "Opening the AH";
                  case SHOP_SCAN, SHOP_WAIT_UPDATE -> "Checking AH listings (page " + (this.pages + 1) + ")";
                  case SHOP_WAIT_CONFIRM, SHOP_AFTER_BUY -> "Buying";
                  case BUILD -> this.buildStarted ? "Building with Baritone" : "Starting the build";
                  case DONE -> "Done";
                  default -> "Idle";
               };
            }
         }
      }
   }

   private String lookingFor() {
      switch (this.state) {
         case RETURNING:
            return "the farm";
         case DEPOT:
         case LOADING:
         default:
            return null;
         case PLAN:
         case BUILD:
            if (this.shopping.isEmpty()) {
               return null;
            }

            StringBuilder sb = new StringBuilder();
            int n = 0;

            for (Entry<class_1792, Integer> e : this.shopping.entrySet()) {
               if (!this.unbuyable.contains(e.getKey())) {
                  if (n++ == 3) {
                     sb.append(", ...");
                     break;
                  }

                  if (sb.length() > 0) {
                     sb.append(", ");
                  }

                  sb.append(e.getValue()).append("x ").append(e.getKey().method_63680().getString());
               }
            }

            return sb.length() == 0 ? null : sb.toString();
         case CRAFT:
            return this.crafter.goalStatus();
         case SHOP_OPEN:
         case SHOP_WAIT_GUI:
         case SHOP_SCAN:
         case SHOP_WAIT_UPDATE:
         case SHOP_WAIT_CONFIRM:
         case SHOP_AFTER_BUY:
            return this.current == null ? null : this.current.method_63680().getString() + " on the AH";
      }
   }

   private void startItemsGathering() {
      Map<class_1792, Integer> goals = new LinkedHashMap<>();
      List<Entry<class_1792, Integer>> mats = new ArrayList<>(this.required.entrySet());
      mats.sort(Comparator.comparingInt(ex -> Crafter.knows((class_1792)ex.getKey()) ? 0 : 1));

      for (Entry<class_1792, Integer> e : mats) {
         if (!this.itemsSkipped.contains(e.getKey())) {
            int left = e.getValue() - this.depot.stored(e.getKey());
            if (left > 0) {
               goals.put(e.getKey(), Math.min(left, 4096)); // saved jobs reject goals above 4096
            }
         }
      }

      if (goals.isEmpty()) {
         this.note("Get Items done: everything I could get is in the chests at the farm. Hit Start to build.");
         this.clearJob();
         this.toggle();
      } else {
         this.crafter.start(goals, i -> this.isGear(i) && this.hasGear(i), this.oreFinding.get(), this.mineStall.get());
         this.info("Getting %d kinds of items by mining and crafting.", goals.size());
         this.state = FarmBuilder.State.CRAFT;
      }
   }

   private int freeInventorySlots() {
      int count = 0;
      for (int i = 0; i < 36; i++) if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) count++;
      return count;
   }

   private boolean makeCraftRoom() {
      if (!this.dropJunkOutside.get() || this.origin == null || !this.siteChosen)
         return this.survival.makeRoom(3, this::keepItem);
      if (this.freeInventorySlots() >= 3) return true;
      if (!this.survival.hasDiscardableJunk(this::keepItem)) return true;
      if (!this.startJunkTrip()) return true;
      return false;
   }

   private boolean startJunkTrip() {
      if (!this.dropJunkOutside.get() || this.origin == null || !this.siteChosen || this.schematic == null
         || this.movement.isRecovering()
         || this.freeInventorySlots() >= this.afk.headroom() || !this.survival.hasDiscardableJunk(this::keepItem)
         || this.crafter.isClicking() && (this.state == State.CRAFT || this.state == State.TOOL_REPAIR)
         || !(this.state == State.CRAFT || this.state == State.TOOL_REPAIR || this.state == State.BUILD || this.state == State.PLAN)) return false;
      class_2338 spot = this.chooseJunkSpot();
      if (spot == null) {
         if (this.inFootprint(this.mc.field_1724.method_24515())) {
            this.giveUp("No safe outside junk spot is reachable nearby. Clear inventory space and Resume Saved Farm.");
            return true;
         }
         this.setBaritonePaused(true);
         Look.releaseMovementKeys();
         Look.stopMining();
         if (this.survival.makeRoom(Math.max(6, this.afk.headroom()), this::keepItem)) this.setBaritonePaused(false);
         return true;
      }
      this.junkResumeState = this.state;
      this.junkCraft = this.state == State.CRAFT || this.state == State.TOOL_REPAIR ? this.crafter.snapshot() : null;
      this.junkReturn = this.mc.field_1724.method_24515().method_10062();
      this.junkLeaving = false;
      this.junkSpot = spot;
      this.crafter.stop();
      this.junkSince = this.tickCount;
      this.buildStarted = false;
      this.travelSettings();
      this.state = State.JUNK_TRIP;
      return true;
   }

   private void tickJunkTrip() {
      IBaritone b = baritone();
      if (this.tickCount - this.junkSince > 2400 || b == null) {
         this.giveUp("Couldn't reach the outside junk drop spot. Clear some inventory slots and Resume Saved Farm.");
         return;
      }
      if (this.junkLeaving) {
         if (!JunkLogic.returned(this.mc.field_1724.method_24515(), this.junkReturn, this.junkSpot)) {
            if (!b.getCustomGoalProcess().isActive()) b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.junkReturn));
            return;
         }
         b.getPathingBehavior().cancelEverything();
         this.state = this.junkResumeState;
         if (this.junkCraft != null) { this.crafter.resume(this.junkCraft); this.junkCraft = null; }
         this.junkSpot = null;
         this.junkLeaving = false;
         this.reissue();
         return;
      }
      if (!this.mc.field_1724.method_24515().method_19771(this.junkSpot, 2.0)) {
         if (!b.getCustomGoalProcess().isActive()) b.getCustomGoalProcess().setGoalAndPath(new GoalNear(this.junkSpot, 1));
         return;
      }
      b.getPathingBehavior().cancelEverything();
      Look.releaseMovementKeys();
      double dx = this.junkSpot.method_10263() - this.junkReturn.method_10263();
      double dz = this.junkSpot.method_10260() - this.junkReturn.method_10260();
      float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
      if (!Look.turnTowards(yaw, -10, 15)) return;
      if (!this.survival.makeRoom(Math.max(6, this.afk.headroom()), this::keepItem)) return;
      this.junkLeaving = true;
      this.setBaritonePaused(false);
      b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(this.junkReturn));
   }

   private class_2338 chooseJunkSpot() {
      class_2338 feet = this.mc.field_1724.method_24515();
      long now = System.currentTimeMillis();
      IBaritone b = baritone();
      var goal = b == null ? null : b.getPathingBehavior().getGoal();
      for (int distance : new int[]{3,6,10,14}) {
         class_2338 best = null;
         double bestScore = Double.NEGATIVE_INFINITY;
         for (int[] direction : new int[][]{{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}}) {
            class_2338 p = feet.method_10069(direction[0] * distance, 0, direction[1] * distance);
            if (!JunkLogic.localSpot(feet, p) || !this.mc.field_1687.method_8393(p.method_10263() >> 4,p.method_10260() >> 4) || this.inFootprint(p) || this.drops.crowded(p,now)) continue;
            if (this.mc.field_1687.method_8311(p) && this.mc.field_1687.method_8311(p.method_10084())
               && this.mc.field_1687.method_8316(p.method_10074()).method_15769()
               && !this.mc.field_1687.method_8320(p.method_10074()).method_26220(this.mc.field_1687,p.method_10074()).method_1110()) {
               double score = goal == null ? 0 : goal.heuristic(p.method_10263(),p.method_10264(),p.method_10260());
               if (best == null || score > bestScore) { best = p; bestScore = score; }
            }
         }
         if (best != null) return best;
      }
      if (feet.method_10264() < this.origin.method_10264() - 2 || !FarmLogic.nearFarm(feet, this.origin,
         this.schematic.widthX(), this.schematic.heightY(), this.schematic.lengthZ())) return null;
      class_2338 fallback = this.drops.nextSpot(this.origin);
      if (!this.mc.field_1687.method_8393(fallback.method_10263() >> 4, fallback.method_10260() >> 4)) return null;
      int y = this.mc.field_1687.method_8624(class_2903.field_13197, fallback.method_10263(), fallback.method_10260());
      return new class_2338(fallback.method_10263(),y,fallback.method_10260());
   }

   private void startDeposit() {
      if (this.origin != null) {
         if (!this.mc.field_1724.method_24515().method_19771(this.origin, 6.0) && !this.gatherHomeCommand.get().isBlank()) {
            this.beginGatherReturn(false, true);
            return;
         }
         this.depotForItems = true;
         this.depotKind = 0;
         this.depot.deposit(this.depotAnchor(), this.required::containsKey);
         this.state = FarmBuilder.State.DEPOT;
      }
   }

   /** Turns this module off and leaves the server; Meteor's AutoReconnect is left off so it stays logged out. */
   private void logOut(String why) {
      this.reconnect.setArmed(false);
      if (this.isActive()) this.toggle();
      try {
         meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect meteor = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
         if (meteor != null && meteor.isActive()) meteor.toggle();
      } catch (Throwable ignored) {
      }
      if (this.mc.field_1724 != null) this.mc.field_1724.field_3944.method_52781(new class_2661(class_2561.method_43470("[Farm Builder] " + why)));
   }

   private FarmLocation supplySpot() {
      if (!this.supplyLoaded) {
         this.supplyLoaded = true;
         try {
            this.supply = SUPPLY_FILE.isFile() ? FarmLocation.read(SUPPLY_FILE.toPath()) : null;
         } catch (Exception e) {
            this.supply = null;
         }
      }
      return this.supply != null && this.supply.matches(this.currentDim(), this.currentServer()) ? this.supply : null;
   }

   private void setSupplySpot() {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         this.error("Join a world before choosing the supply chest spot.");
         return;
      }
      class_2338 feet = this.mc.field_1724.method_24515().method_10062();
      if (this.inFootprint(feet)) {
         this.error("That spot is inside the farm. Stand outside the farm and press it again.");
         return;
      }
      try {
         FarmLocation spot = new FarmLocation(feet, this.currentDim(), this.currentServer());
         spot.save(SUPPLY_FILE.toPath());
         this.supply = spot;
         this.supplyLoaded = true;
         this.info("Supply chest spot saved at %d %d %d. Chests are placed and found around here for every farm.",
            feet.method_10263(), feet.method_10264(), feet.method_10260());
      } catch (Exception e) {
         this.error("Couldn't save the supply chest spot: %s", e.getMessage());
      }
   }

   private class_2338 depotAnchor() {
      FarmLocation spot = this.supplySpot();
      if (spot != null) return spot.origin();
      int x = this.origin.method_10263() - 4;
      int z = this.origin.method_10260() - 4;
      int y = this.mc.field_1687.method_8393(x >> 4, z >> 4) ? this.mc.field_1687.method_8624(class_2903.field_13197, x, z) : this.origin.method_10264();
      return new class_2338(x, y, z);
   }

   private boolean nearFarm(double radius) {
      return this.origin != null && this.mc.field_1724 != null && java.util.Objects.equals(this.jobDim, this.currentDim())
         && this.mc.field_1724.method_24515().method_19771(this.origin, radius);
   }

   private int heldCount(Set<class_1792> items) {
      int n = 0;
      for (class_1792 item : items) n += this.countInInventory(item);
      return n;
   }

   private boolean holdsRequired() {
      for (int i = 0; i < 36; i++) {
         class_1799 st = this.mc.field_1724.method_31548().method_5438(i);
         if (!st.method_7960() && this.required.containsKey(st.method_7909())) return true;
      }
      return false;
   }

   /**
    * Locks the farm materials in the outside chest: once everything is gathered, whenever the inventory is nearly full,
    * and right after a crafting trip so nothing the bot made is carried around (or lost on a death). The builder takes it
    * back out in batches.
    */
   private boolean tryStockpile() {
      if (!this.stashBeforeBuild.get() || !this.outsideChest.get() || !this.storageReady
         || this.mode != Mode.FULL || this.origin == null || this.required.isEmpty() || this.tickCount < this.nextStash
         || this.depot.chestCount() == 0 || !this.nearFarm(64.0)) return false;
      boolean made = this.madeSomething;
      boolean everything = !this.stockpiled && this.shopping.isEmpty();
      boolean overflow = !this.shopping.isEmpty() && this.freeInventorySlots() <= 2;
      if (!everything && !overflow && !made) return false;
      if (everything && !overflow && !made && this.totalBlocks > 0 && this.countCorrect() * 20 > this.totalBlocks) {
         this.stockpiled = true;
         return false;
      }
      if (!this.holdsRequired()) {
         this.madeSomething = false;
         if (everything) this.stockpiled = true;
         return false;
      }
      this.madeSomething = false;
      this.stashBefore.clear();
      for (class_1792 item : this.required.keySet()) {
         int n = this.countInInventory(item);
         if (n > 0) this.stashBefore.put(item, n);
      }
      this.depotForItems = false;
      this.depotKind = 1;
      this.depot.deposit(this.depotAnchor(), this.required::containsKey);
      this.state = FarmBuilder.State.DEPOT;
      this.info(everything ? "Everything is gathered. Storing the materials in the outside chest first."
         : made ? "Storing what I made in the outside chest." : "Inventory is almost full. Storing materials in the outside chest.");
      return true;
   }

   /** Remembers what a stash trip moved into the chest and writes the stock list. */
   private void recordStash() {
      Map<class_1792, Integer> moved = new LinkedHashMap<>();
      this.stashBefore.forEach((item, before) -> {
         int gone = before - this.countInInventory(item);
         if (gone > 0) moved.put(item, gone);
      });
      this.stashBefore.clear();
      if (moved.isEmpty()) return;
      moved.forEach((item, n) -> this.stockLedger.merge(item, n, Integer::sum));
      this.note("Stored in the supply chest: %s.", describe(moved));
      this.writeStockList();
   }

   private void writeStockList() {
      try {
         StringBuilder sb = new StringBuilder("Farm Builder supply chest stock list (updated ").append(LocalDateTime.now().withNano(0)).append(")\n\n");
         sb.append("Chest position(s): ").append(this.depot.serialize().replace(';', ' ').replace(',', ' ')).append('\n');
         sb.append("\nPut into the chest by Farm Builder this job:\n");
         this.stockLedger.forEach((k, v) -> sb.append("  ").append(v).append("x ").append(k.method_63680().getString()).append('\n'));
         sb.append("\nIn the chest when last checked:\n");
         this.depot.totals().forEach((k, v) -> sb.append("  ").append(v).append("x ").append(k.method_63680().getString()).append('\n'));
         FarmFiles.writeAtomic(STOCK_FILE.toPath(), sb.toString());
      } catch (Exception e) {
         MeteorClient.LOG.debug("[Farm Builder] Stock list could not be written", e);
      }
   }

   private boolean vaultable(class_1792 item) {
      String id = class_7923.field_41178.method_10221(item).method_12832();
      if (!VaultPolicy.isValuable(id) || this.keepItem(item)) return false;
      // A few diamonds stay in hand for tool replacements.
      return !id.equals("diamond") || this.countInInventory(item) > 8;
   }

   private boolean tryVault() {
      if (!this.vaultValuables.get() || !this.outsideChest.get() || !this.storageReady || this.mode != Mode.FULL || this.origin == null
         || this.tickCount < this.nextVault || this.depot.chestCount() == 0) return false;
      this.nextVault = this.tickCount + 200;
      if (!this.nearFarm(48.0)) return false;
      if (this.gearUp.get() && this.gearList().stream().anyMatch(g -> !this.hasGear(g))) return false;
      boolean any = false;
      for (int i = 0; i < 36 && !any; i++) {
         class_1799 st = this.mc.field_1724.method_31548().method_5438(i);
         any = !st.method_7960() && this.vaultable(st.method_7909());
      }
      if (!any) return false;
      this.nextVault = this.tickCount + 6000;
      this.depotForItems = false;
      this.depotKind = 2;
      this.depot.deposit(this.depotAnchor(), this::vaultable);
      this.state = FarmBuilder.State.DEPOT;
      this.info("Locking valuables in the outside chest.");
      return true;
   }

   /** Materials to take out now: only what the chest is known to hold, in as many stacks as the inventory can spare. */
   private Map<class_1792, Integer> withdrawBatch() {
      Map<class_1792, Integer> wants = new LinkedHashMap<>();
      int budget = this.freeInventorySlots() - this.withdrawReserve.get();
      boolean unknown = this.depot.hasUnknown();
      for (Entry<class_1792, Integer> e : this.pending.entrySet()) {
         if (budget <= 0) break;
         class_1792 item = e.getKey();
         if (this.unbuyable.contains(item) || this.isGear(item)) continue;
         int stored = this.depot.stored(item);
         if (stored <= 0 && !unknown) continue;
         int want = stored > 0 ? Math.min(e.getValue(), stored) : Math.min(e.getValue(), 64);
         int stack = Math.max(1, new class_1799(item).method_7914());
         int slots = (want + stack - 1) / stack;
         if (slots > budget) {
            want = budget * stack;
            slots = budget;
         }
         wants.put(item, want);
         budget -= slots;
      }
      return wants;
   }

   private boolean tryWithdraw() {
      if (this.origin == null || this.depot.chestCount() == 0 || this.inventoryFull() || this.tickCount < this.nextWithdraw) {
         return false;
      }
      if (this.tickCount >= this.nextDiscover) {
         this.depot.discover(this.depotAnchor());
         this.nextDiscover = this.tickCount + 100;
      }
      Map<class_1792, Integer> wants = this.withdrawBatch();
      if (wants.isEmpty()) return false;
      this.depotForItems = false;
      this.depotKind = 0;
      this.withdrawItems.clear();
      this.withdrawItems.addAll(wants.keySet());
      this.withdrawBefore = this.heldCount(this.withdrawItems);
      this.nextWithdraw = this.tickCount + 100;
      this.depot.setReserve(this.withdrawReserve.get());
      this.depot.withdraw(this.depotAnchor(), wants);
      this.state = FarmBuilder.State.DEPOT;
      return true;
   }

   private void tickDepot() {
      Depot.Status st = this.depot.tick();
      this.drainDepotAlerts();
      if (st != Depot.Status.RUNNING) {
         if (this.storageSettingUp) {
            this.storageSettingUp = false;
            if (st == Depot.Status.FAILED || this.depot.chestCount() == 0) {
               this.giveUp("Outside supply chest setup failed: " + this.depot.failReason);
               return;
            }
            this.storageReady = true;
            this.saveJob();
            this.state = State.LOADING;
            this.note("Outside supply chest is ready. Farm materials will be taken from it when needed.");
            return;
         }
         if (st == Depot.Status.FAILED) {
            this.warning("%s", this.depot.failReason);
         }

         if (this.depotKind != 0) {
            int kind = this.depotKind;
            this.depotKind = 0;
            if (kind == 1) {
               this.recordStash();
               if (st == Depot.Status.FAILED) {
                  this.nextStash = this.tickCount + 6000;
                  this.note("Couldn't store the materials (%s). Building from the inventory instead.", this.depot.failReason);
               } else {
                  boolean everything = this.shopping.isEmpty();
                  if (everything) this.stockpiled = true;
                  this.nextStash = this.tickCount + 1200;
                  this.note("Materials stored in the outside chest (%d items in %d chest%s).",
                     this.depot.totals().values().stream().mapToInt(Integer::intValue).sum(), this.depot.chestCount(), this.depot.chestCount() == 1 ? "" : "s");
                  if (everything) this.ping("Everything the farm needs is gathered and stored. Building starts now.");
               }
            } else if (st != Depot.Status.FAILED) {
               this.note("Valuables locked in the outside chest.");
            }
            this.saveJob();
            this.buildStarted = false;
            this.lastPlanTick = -1000;
            this.state = FarmBuilder.State.PLAN;
            return;
         }

         if (!this.depotForItems) {
            this.lastPlanTick = -1000;
            int gained = this.heldCount(this.withdrawItems) - this.withdrawBefore;
            this.nextWithdraw = st == Depot.Status.FAILED || gained <= 0 ? this.tickCount + 600 : this.tickCount;
            this.buildStarted = false;
            this.state = FarmBuilder.State.PLAN;
         } else {
            boolean stillFull = this.mc.field_1724.method_31548().method_7376() == -1;
            this.emptyDeposits = stillFull ? this.emptyDeposits + 1 : 0;
            if (this.emptyDeposits >= 2) {
               this.note("Can't store anything more (no chests, no room). Stopping Get Items.");
               this.clearJob();
               this.toggle();
            } else {
               this.note("Stored farm materials at the farm (%d chest%s).", this.depot.chestCount(), this.depot.chestCount() == 1 ? "" : "s");
               this.saveJob();
               this.startItemsGathering();
            }
         }
      }
   }

   private void ping(String text) {
      String url = this.webhookUrl.get().trim();
      if (url.isEmpty() || text == null) return;
      StringBuilder sb = new StringBuilder(text);
      if (this.webhookCoords.get() && this.mc.field_1724 != null) {
         class_2338 at = this.mc.field_1724.method_24515();
         sb.append(" (at ").append(at.method_10263()).append(' ').append(at.method_10264()).append(' ').append(at.method_10260()).append(')');
      }
      this.notifier.send(url, sb.toString(), System.currentTimeMillis());
   }

   private void drainDepotAlerts() {
      for (String alert : this.depot.takeAlerts()) {
         this.tamperAlerts++;
         this.events.addLast(LocalTime.now().withNano(0) + "  Chest tripwire: " + alert);
         while (this.events.size() > 40) this.events.removeFirst();
         this.warning("Chest tripwire: %s", alert);
         this.ping("Chest tripwire: " + alert);
      }
   }

   private File priceFile() {
      String server = this.currentServer();
      String key = server.isEmpty() ? "local" : server.replaceAll("[^a-z0-9.-]", "_");
      if (key.length() > 60) key = key.substring(0, 60);
      return new File(MeteorClient.FOLDER, "farm-builder-prices-" + key + ".txt");
   }

   private void ensurePriceBook() {
      String key = this.priceFile().getName();
      if (key.equals(this.pricesFor)) return;
      this.savePriceBook();
      this.pricesFor = key;
      try {
         File f = this.priceFile();
         this.prices.load(f.isFile() && f.length() < 262144 ? Files.readString(f.toPath(), StandardCharsets.UTF_8) : "");
      } catch (Exception e) {
         this.prices.load("");
      }
   }

   private void savePriceBook() {
      if (this.pricesFor.isEmpty() || this.prices.size() == 0) return;
      try {
         FarmFiles.writeAtomic(new File(MeteorClient.FOLDER, this.pricesFor).toPath(), this.prices.serialize());
      } catch (Exception e) {
         MeteorClient.LOG.debug("[Farm Builder] Price book could not be saved", e);
      }
   }

   private void note(String fmt, Object... args) {
      String msg = String.format(fmt, args);
      this.memory.event(msg, System.currentTimeMillis());
      this.events.addLast(LocalTime.now().withNano(0) + "  " + msg);

      while (this.events.size() > 40) {
         this.events.removeFirst();
      }

      this.info("%s", msg);
   }

   private void writeReport() {
      try {
         StringBuilder sb = new StringBuilder();
         sb.append("Farm Builder report (updated ").append(LocalDateTime.now().withNano(0)).append(")\n\n");

         for (String l : this.statusLines()) {
            sb.append(l).append('\n');
         }

         sb.append("\nStored at the farm (").append(this.depot.chestCount()).append(" chests):\n");
         this.depot.totals().forEach((k, v) -> sb.append("  ").append(v).append("x ").append(k.method_63680().getString()).append('\n'));
         if (!this.shopping.isEmpty()) {
            sb.append("\nStill missing:\n");
            this.shopping.forEach((k, v) -> sb.append("  ").append(v).append("x ").append(k.method_63680().getString()).append('\n'));
         }

         Set<class_1792> skipped = new HashSet<>(this.unbuyable);
         skipped.addAll(this.itemsSkipped);
         if (!skipped.isEmpty()) {
            sb.append("\nCouldn't get (skipped):\n");

            for (class_1792 i : skipped) {
               sb.append("  ").append(i.method_63680().getString()).append('\n');
            }
         }

         sb.append("\nWhat happened:\n");

         for (String e : this.events) {
            sb.append("  ").append(e).append('\n');
         }

         FarmFiles.writeAtomic(REPORT_FILE.toPath(), sb.toString());
      } catch (Exception var5) {
      }
   }

   private void printReport() {
      if (this.mc.field_1724 != null) {
         this.writeReport();

         for (String l : this.statusLines()) {
            this.info("%s", l);
         }

         if (this.depot.chestCount() > 0) {
            this.info("Storage: %s", describe(this.depot.totals()));
         }

         this.info("Full report: %s", REPORT_FILE.getPath());
      }
   }

   private boolean tickPickup() {
      IBaritone b = baritone();
      if (this.pickupTarget != null) {
         boolean done = !this.pickupTarget.method_5805() || this.tickCount > this.pickupUntil;
         if (!done) {
            return true;
         } else {
            if (this.pickupTarget.method_5805()) this.afk.pickupFailed(this.pickupTarget.method_5628(), System.currentTimeMillis());
            this.pickupTarget = null;
            if (b != null) {
               b.getPathingBehavior().cancelEverything();
            }

            this.reissue();
            return false;
         }
      } else if (this.tickCount >= this.nextPickupCheck && this.mc.field_1755 == null && b != null) {
         this.nextPickupCheck = this.tickCount + 40;
         boolean between = this.state == FarmBuilder.State.BUILD
            || this.state == FarmBuilder.State.CRAFT && !this.crafter.isClicking() && !this.crafter.isWalking();
         if (between && this.mc.field_1724.method_31548().method_7376() != -1) {
            class_1297 best = null;
            double bestDist = 10.0;
            var goal = b.getPathingBehavior().getGoal();
            class_2338 feet = this.mc.field_1724.method_24515();
            double currentCost = goal == null ? Double.NaN : goal.heuristic(feet.method_10263(), feet.method_10264(), feet.method_10260());

            for (class_1297 e : this.mc.field_1687.method_18112()) {
               if (e instanceof class_1542 item) {
                  class_1792 it = item.method_6983().method_7909();
                  if (this.afk.pickupBlocked(e.method_5628(), System.currentTimeMillis())) continue;
                  if (!this.required.containsKey(it) && this.drops.ignored(it, item.method_24515(), System.currentTimeMillis())) continue;
                  if (this.keepItem(it) || this.required.containsKey(it)) {
                     double d = this.mc.field_1724.method_5739(e);
                     boolean needed = this.required.getOrDefault(it, 0) > this.countInInventory(it)
                        || this.state == State.CRAFT && this.crafter.needsPickup(item.method_6983())
                        || Survival.ordinaryFood(item.method_6983()) && this.survival.foodCount() < this.overnight.foodReserve();
                     class_2338 at = e.method_24515();
                     double itemCost = goal == null ? Double.NaN : goal.heuristic(at.method_10263(), at.method_10264(), at.method_10260());
                     if (d < bestDist && FarmLogic.worthwhilePickup(needed, d, currentCost, itemCost)) {
                        bestDist = d;
                        best = e;
                     }
                  }
               }
            }

            if (best == null) {
               return false;
            } else {
               b.getPathingBehavior().cancelEverything();
               b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(best.method_24515()));
               this.pickupTarget = best;
               this.pickupUntil = this.tickCount + 200;
               this.nextPickupCheck = this.tickCount + 600;
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean inDanger() {
      if (this.mc.field_1724.field_6235 > 0) {
         return true;
      } else {
         for (class_1297 e : this.mc.field_1687.method_18112()) {
            if (e instanceof class_1569 && e.method_5805()) {
               double d = this.mc.field_1724.method_5739(e);
               if (d < (e instanceof class_1548 ? 6.0 : 4.5) && this.mc.field_1724.method_6057(e)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private void escapeMenu() {
      if (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) {
         this.crafter.interrupt();
      } else {
         Look.closeScreen();
      }
      // A safety close is not an AH failure: open the AH again once it is safe, unless a purchase was already confirmed.
      if (this.state == State.SHOP_WAIT_GUI || this.state == State.SHOP_SCAN || this.state == State.SHOP_WAIT_UPDATE
         || this.state == State.SHOP_WAIT_CONFIRM && !this.confirmClicked) {
         this.state = State.SHOP_OPEN;
      }

      this.equipCloseTick = -1;
      if (this.tickCount - this.lastMenuEscape > 100) {
         this.info("Mob coming, closing the menu to get ready.");
      }

      this.lastMenuEscape = this.tickCount;
   }

   private boolean checkPlayerAttack() {
      float health = this.mc.field_1724.method_6032();
      boolean tookDamage = this.lastHealth >= 0.0F && health < this.lastHealth;
      this.lastHealth = health;
      if (tookDamage && this.leaveIfPlayerHits.get()) {
         class_1657 attacker = null;
         class_1282 src = this.mc.field_1724.method_6081();
         if (src != null && src.method_5529() instanceof class_1657 p && p != this.mc.field_1724) {
            attacker = p;
         }

         if (attacker != null && !Friends.get().isFriend(attacker)) {
            String why = "Attacked by " + attacker.method_5477().getString() + ", logging out.";
            this.note("%s", why);
            this.ping(why);
            this.logOut(why);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void reissue() {
      IBaritone b = baritone();
      switch (this.state) {
         case RETURNING:
            if (b != null) {
               b.getPathingBehavior().cancelEverything();
            }

            this.returnWalking = false;
            break;
         case DEPOT:
         case TOOL_APPROACH:
            if (b != null) {
               b.getPathingBehavior().cancelEverything();
            }
            break;
         case CRAFT:
         case TOOL_REPAIR:
            this.crafter.reissue();
            break;
         case BUILD:
            if (b != null) {
               b.getPathingBehavior().cancelEverything();
            }

            this.buildStarted = false;
      }
   }

   /** Same as the reconnect path: remember the interrupted craft so tickReturning resumes it on arrival. */
   private void keepCurrentCraft() {
      if (this.state == State.JUNK_TRIP && this.junkCraft != null) {
         this.reconnectCraft = this.junkCraft;
         this.junkCraft = null;
      }
      if (this.state == State.CRAFT || this.state == State.TOOL_REPAIR) {
         this.reconnectCraft = this.crafter.snapshot();
         this.crafter.stop();
      }
   }

   private void comeBack(boolean respawned, boolean fresh) {
      if (this.state == FarmBuilder.State.LOADING) this.arriveThenLoad = true;
      this.chunkWaitTarget = null;
      this.afk.chunk(System.currentTimeMillis(), true);
      if (this.state != FarmBuilder.State.DONE) {
         IBaritone b = baritone();
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         if (this.baritonePaused && b != null) {
            b.getCommandManager().execute("resume");
         }

         this.baritonePaused = false;
         this.combat.reset();
         this.movement.reset();
         this.movement.applyBaritoneSettings();
         if (this.isShopScreen()) {
            this.mc.field_1724.method_7346();
         }

         if (respawned) {
            this.gearAhDone = false;
         }

         this.boat.reset();
         this.resumeFresh = fresh;
         this.returnWalking = false;
         this.homeTries = 0;
         this.homeNext = this.tickCount;
         this.gatheringReturn = false;
         this.gearDoneReturning = false;
         this.depositReturning = false;
         this.storageReturning = false;
      this.arriveThenLoad = false;
         this.lastReturnPosition = this.mc.field_1724 == null ? null : this.mc.field_1724.method_24515().method_10062();
         this.lastReturnDimension = this.mc.field_1687 == null ? null : this.currentDim();
         this.returnSettleUntil = this.tickCount + (respawned ? 100 : 20);
         this.state = FarmBuilder.State.RETURNING;
      }
   }

   private void tickReturning() {
      if (this.tickCount < this.returnSettleUntil) return;
      IBaritone b = baritone();
      boolean sameDim = this.jobDim == null || this.jobDim.equals(this.currentDim());
      double dist = this.origin == null ? 0.0 : Math.sqrt(this.mc.field_1724.method_24515().method_10262(this.origin));
      if (this.origin == null) { this.giveUp("The saved build origin is missing."); return; }
      class_2338 currentPosition = this.mc.field_1724.method_24515();
      String currentDimension = this.currentDim();
      boolean teleported = this.lastReturnPosition != null && (this.lastReturnPosition.method_10262(currentPosition) >= 64
         || !java.util.Objects.equals(this.lastReturnDimension, currentDimension));
      this.lastReturnPosition = currentPosition.method_10062();
      this.lastReturnDimension = currentDimension;
      String returnCommand = (this.gatheringReturn ? this.gatherHomeCommand.get() : this.homeCommand.get()).trim();
      if (this.gatheringReturn && this.homeTries == 0 && !returnCommand.isEmpty()) {
         this.sendHome(b, returnCommand);
         return;
      }
      if (this.gearDoneReturning && this.homeTries > 0 && (teleported || sameDim && dist <= 6.0 && this.tickCount >= this.homeNext)) {
         this.finishGear();
         return;
      }
      if (sameDim && dist <= 6.0) {
         if (!this.gearDoneReturning && !this.homeSet && this.mode != Mode.GEAR && !this.setHomeCommand.get().isBlank()
            && !this.atHomeAnchor()) {
            HomeNavigation.walk(b, this.origin, 2);
            this.returnWalking = true;
            return;
         }
         if (b != null && this.returnWalking) b.getPathingBehavior().cancelEverything();
         this.returnWalking = false;
         if (this.gearDoneReturning) return;
         boolean deposit = this.depositReturning;
         if (!this.homeSet && this.mode != Mode.GEAR && !this.setHomeCommand.get().isBlank()) {
            this.saveFarmHome();
            this.returnSettleUntil = this.tickCount + 40;
            return;
         }

         this.gatheringReturn = false;
         this.depositReturning = false;
         if (this.reconnectCraft != null) {
            Crafter.Job job = this.reconnectCraft;
            this.reconnectCraft = null;
            this.crafter.resume(job);
            this.state = this.replacementTargets.isEmpty() ? State.CRAFT : State.TOOL_REPAIR;
            return;
         }
         if (this.replacementReturning) {
            this.replacementReturning = false;
            this.replacementTargets.clear();
            this.nextToolCheck = this.tickCount + 100;
            if (this.interruptedCraft != null) {
               Crafter.Job job = this.interruptedCraft;
               this.interruptedCraft = null;
               this.crafter.resume(job);
               this.state = State.CRAFT;
            } else {
               this.buildStarted = false;
               this.state = State.PLAN;
            }
            return;
         }
         if (deposit) {
            this.startDeposit();
            return;
         }
         if (this.storageReturning) {
            this.storageReturning = false;
      this.arriveThenLoad = false;
            this.depot.ensure(this.depotAnchor());
            this.state = State.DEPOT;
            return;
         }
         if (this.arriveThenLoad) {
            this.arriveThenLoad = false;
            this.state = State.LOADING;
         } else if (this.resumeFresh || this.schematic == null && this.mode != Mode.GEAR) {
            this.beginWork();
         } else if (this.mode == Mode.FULL) {
            this.buildStarted = false;
            this.state = State.PLAN;
         } else {
            this.crafter.reissue();
            this.state = State.CRAFT;
         }
         return;
      }
      boolean preferHome = HomeNavigation.preferCommand(this.gatheringReturn, this.fastHomeReturn.get(), returnCommand, dist, this.homeMinDistance.get());
      if ((preferHome || !sameDim || dist > this.maxWalkBack.get()) && !returnCommand.isEmpty()) {
         if (FarmLogic.homeAttemptDue(this.gatheringReturn, !sameDim || dist > this.maxWalkBack.get(), this.homeTries, this.tickCount, this.homeNext)) {
            this.sendHome(b, returnCommand);
            return;
         }
         if (this.tickCount < this.homeNext) return;
      }

      if (!sameDim || dist > this.maxWalkBack.get()) {
         this.giveUp("The farm is " + (sameDim ? (int)dist + " blocks away" : "in another dimension") + " and the home command could not return there.");
         return;
      }
      if (this.boat.tick(this.origin)) {
         if (b != null && this.returnWalking) b.getPathingBehavior().cancelEverything();
         this.returnWalking = false;
      } else if (b != null && (!this.returnWalking || !b.getCustomGoalProcess().isActive())) {
         HomeNavigation.walk(b, this.origin, 3);
         this.returnWalking = true;
      }
   }

   private void sendHome(IBaritone b, String command) {
      if (b != null) b.getPathingBehavior().cancelEverything();
      Look.releaseMovementKeys();
      Look.stopMining();
      ChatUtils.sendPlayerMsg(command);
      this.homeTries++;
      this.homeNext = this.tickCount + this.homeWaitSeconds.get() * 20;
      this.returnWalking = false;
      this.note("Returning home with %s.", command);
   }

   private void travelSettings() {
      Settings s = BaritoneAPI.getSettings();
      s.blockReachDistance.value = this.movement.isCautious() ? 3.2F : 3.5F;
      s.allowBreak.value = true;
      s.allowPlace.value = true;
      s.buildIgnoreExisting.value = false;
      s.blocksToDisallowBreaking.value = FarmLogic.travelProtection(this.protectedBlocks);
      s.acceptableThrowawayItems.value = this.throwaway();
   }

   private List<class_1792> throwaway() {
      List<class_1792> out = new ArrayList<>();

      for (class_1792 i : List.of(
         class_1802.field_20412,
         class_1802.field_29025,
         class_1802.field_8831,
         class_1802.field_8328,
         class_1802.field_20407,
         class_1802.field_20401,
         class_1802.field_20394,
         class_1802.field_27021
      )) {
         if (!this.required.containsKey(i)) {
            out.add(i);
         }
      }

      return out;
   }

   private void giveUp(String why) {
      this.rejoinPending = false;
      this.hadWorld = false;
      this.sessionStopReason = why;
      this.writeOvernightHeartbeat("Stopped: " + why);
      this.note("Stopped: %s", why);
      this.ping("Stopped: " + why);
      this.writeReport();
      this.reconnect.setArmed(false);
      this.saveJob(false);
      if (this.isActive()) this.toggle();
   }

   private boolean keepItem(class_1792 item) {
      if (this.required.containsKey(item) || UPGRADE.containsKey(item) || UPGRADE.containsValue(item) || ARMOR_SLOT.containsKey(item)) {
         return true;
      } else if (item == class_1802.field_20412) {
         return this.countInInventory(item) <= 128;
      } else {
         return item != class_1802.field_8600 && item != class_1802.field_8858
            ? new class_1799(item).method_58694(class_9334.field_50075) != null
            : this.countInInventory(item) <= 64;
      }
   }

   @EventHandler(priority = 1000)
   private void protectStart(meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent event) {
      if (protectAutomationBreak(event.blockPos)) event.cancel();
   }

   @EventHandler(priority = 1000)
   private void protectFinish(meteordevelopment.meteorclient.events.entity.player.BreakBlockEvent event) {
      if (protectAutomationBreak(event.blockPos)) event.cancel();
   }

   private boolean atHomeAnchor() {
      return this.origin != null && this.mc.field_1724 != null && java.util.Objects.equals(this.jobDim, this.currentDim()) && java.util.Objects.equals(this.jobServer, this.currentServer())
         && !this.mc.field_1724.method_5799() && this.mc.field_1724.method_24828()
         && FarmProtection.homeAnchor(this.origin, this.mc.field_1724.method_24515());
   }

   private void saveFarmHome() {
      if (this.homeSet || this.mode == Mode.GEAR || this.setHomeCommand.get().isBlank() || !this.atHomeAnchor()) return;
      ChatUtils.sendPlayerMsg(this.setHomeCommand.get().trim());
      this.homeSet = true;
      this.saveJob();
      this.info("Saved home 1 at the farm corner.");
   }

   public static boolean protectAutomationBreak(class_2338 pos) {
      FarmBuilder f = instance;
      if (f == null || !f.isActive() || f.mc.field_1687 == null || f.origin == null) return false;
      IBaritone b = baritone();
      boolean automated = f.movement.needsPause() || f.survival.isBreathing()
         || b != null && b.getInputOverrideHandler().isInputForcedDown(baritone.api.utils.input.Input.CLICK_LEFT);
      if (!automated) return false;
      var w = f.mc.field_1687;
      if (!w.method_8393(pos.method_10263() >> 4, pos.method_10260() >> 4)) return true;
      if (!java.util.Objects.equals(f.jobDim, f.currentDim()) || !java.util.Objects.equals(f.jobServer, f.currentServer()) || !f.inFootprint(pos)) return false;
      class_2680 have = w.method_8320(pos);
      int x = pos.method_10263() - f.origin.method_10263(), y = pos.method_10264() - f.origin.method_10264(), z = pos.method_10260() - f.origin.method_10260();
      class_2680 want = x >= 0 && y >= 0 && z >= 0 && x < f.schematic.widthX() && y < f.schematic.heightY() && z < f.schematic.lengthZ()
         ? f.schematic.getDirect(x, y, z) : null;
      String id = class_7923.field_41175.method_10221(have.method_26204()).method_12832();
      boolean correct = want != null && !want.method_26215() && FarmLogic.matches(have, want);
      boolean fluid = !w.method_8316(pos).method_15769();
      for (net.minecraft.class_2350 d : net.minecraft.class_2350.values()) fluid |= !w.method_8316(pos.method_10093(d)).method_15769();
      return FarmProtection.locked(correct, w.method_8321(pos) != null, id, fluid);
   }

   private boolean inFootprint(class_2338 p) {
      IStaticSchematic s = this.schematic;
      return s != null && this.origin != null
         ? p.method_10263() >= this.origin.method_10263() - 1
            && p.method_10263() <= this.origin.method_10263() + s.widthX()
            && p.method_10260() >= this.origin.method_10260() - 1
            && p.method_10260() <= this.origin.method_10260() + s.lengthZ()
            && p.method_10264() >= this.origin.method_10264() - 1
            && p.method_10264() <= this.origin.method_10264() + s.heightY()
         : false;
   }

   private String currentDim() {
      return this.mc.field_1687 == null ? null : this.mc.field_1687.method_27983().method_29177().toString();
   }

   private void saveJob() {
      this.saveJob(this.isActive());
   }

   private void saveJob(boolean running) {
      if (this.state == FarmBuilder.State.WAIT_WORLD) return;
      if (!this.suppressJobSave && this.origin != null) {
         try {
            class_2338 st = this.crafter.stash();
            Crafter.Job craft = this.savedCraftJob != null ? this.savedCraftJob
               : this.state == State.CRAFT || this.state == State.TOOL_REPAIR ? this.crafter.snapshot()
               : this.state == State.JUNK_TRIP ? this.junkCraft : this.reconnectCraft;
            String goals = craft == null ? "" : craft.goals().entrySet().stream().map(e -> class_7923.field_41178.method_10221(e.getKey()) + "=" + e.getValue())
               .collect(java.util.stream.Collectors.joining(";"));
            if (this.state == State.TOOL_APPROACH && !this.replacementTargets.isEmpty()) {
               Map<class_1792, Integer> repairs = new LinkedHashMap<>();
               this.replacementTargets.values().forEach(item -> repairs.put(item, this.mc.field_1724 == null ? 1 : this.countInInventory(item) + 1));
               craft = new Crafter.Job(repairs, item -> false, this.oreFinding.get(), this.mineStall.get());
               goals = Crafter.encodeGoals(craft);
            }
            FarmJob.preserve(JOB_FILE.toPath(), JOB_BACKUP_FILE.toPath());
            FarmFiles.writeAtomic(
               JOB_FILE.toPath(),
               "version=4\nmode="
                  + this.mode.name()
                  + "\nfarm="
                  + this.farm.get().name()
                  + "\nx="
                  + this.origin.method_10263()
                  + "\ny="
                  + this.origin.method_10264()
                  + "\nz="
                  + this.origin.method_10260()
                  + "\ndim="
                  + (this.jobDim == null ? "" : this.jobDim)
                  + "\nserver=" + java.util.Base64.getEncoder().encodeToString(this.jobServer.getBytes(StandardCharsets.UTF_8))
                  + "\nrunning="
                  + running
                  + "\nhomeSet="
                  + this.homeSet
                  + "\ngearAhDone="
                  + this.gearAhDone
                  + "\nhomeAnchorVersion=1\nsafeBuild="
                  + this.safeBuild
                  + "\nstash="
                  + (st == null ? "" : st.method_10263() + "," + st.method_10264() + "," + st.method_10260())
                  + "\ntools=" + this.observedTools.values().stream().map(item -> class_7923.field_41178.method_10221(item).toString()).collect(java.util.stream.Collectors.joining(","))
                  + "\ncheckpointState=" + this.state.name()
                  + "\ncraftGoals=" + goals
                  + "\ninterruptedCraftGoals=" + Crafter.encodeGoals(this.interruptedCraft)
                  + "\ninterruptedCraftOreMode=" + (this.interruptedCraft == null ? "" : this.interruptedCraft.oreMode().name())
                  + "\ninterruptedCraftStall=" + (this.interruptedCraft == null ? 0 : this.interruptedCraft.stallSeconds())
                  + "\ncraftBatch=" + this.craftBatch
                  + "\ngearPreparing=" + this.gearPreparing
                  + "\nstorageCrafting=" + this.storageCrafting
                  + "\nstockpiled=" + this.stockpiled
                  + "\ncraftRepair=" + (this.state == State.TOOL_REPAIR || this.savedCraftRepair || !this.replacementTargets.isEmpty() && craft != null || this.state == State.JUNK_TRIP && this.junkResumeState == State.TOOL_REPAIR)
                  + "\nspent=" + this.spent
                  + "\nurl=" + java.util.Base64.getEncoder().encodeToString(this.url.get().getBytes(StandardCharsets.UTF_8))
                  + "\nfile=" + java.util.Base64.getEncoder().encodeToString(this.fileName.get().getBytes(StandardCharsets.UTF_8))
                  + "\ndepot="
                  + this.depot.serialize()
                  + "\n"
            );
            this.jobSaveFailed = false;
         } catch (Exception e) {
            MeteorClient.LOG.error("[Farm Builder] Job checkpoint could not be saved", e);
            if (!this.jobSaveFailed && this.mc.field_1724 != null) this.warning("Couldn't save progress: %s", e.getMessage());
            this.jobSaveFailed = true;
         }
      }
   }

   private boolean loadJob() {
      try {
         {
            Map<String, String> kv = FarmJob.recover(JOB_FILE.toPath(), JOB_BACKUP_FILE.toPath());
            String savedServer = new String(java.util.Base64.getDecoder().decode(kv.getOrDefault("server", "")), StandardCharsets.UTF_8);
            if (!savedServer.isEmpty() && !savedServer.equals(this.currentServer())) return false;
            this.jobServer = savedServer.isBlank() ? this.currentServer() : savedServer;
            this.mode = FarmBuilder.Mode.valueOf(kv.get("mode"));
            this.farm.set(FarmBuilder.Farm.valueOf(kv.get("farm")));
            this.origin = new class_2338(Integer.parseInt(kv.get("x")), Integer.parseInt(kv.get("y")), Integer.parseInt(kv.get("z")));
            String dim = kv.getOrDefault("dim", "");
            this.jobDim = dim.isEmpty() ? this.currentDim() : dim;
            this.homeSet = Boolean.parseBoolean(kv.getOrDefault("homeSet", "false"));
            this.gearAhDone = Boolean.parseBoolean(kv.getOrDefault("gearAhDone", "false"));
            this.safeBuild = Boolean.parseBoolean(kv.getOrDefault("safeBuild", "false"));
            this.depot.load(kv.getOrDefault("depot", ""));
            for (String id : kv.getOrDefault("tools", "").split(",")) {
               if (id.isBlank()) continue;
               class_1792 item = class_7923.field_41178.method_63535(net.minecraft.class_2960.method_60654(id));
               if (item != null && toolFamily(item) != null) this.observedTools.put(toolFamily(item), item);
            }
            this.spent = Math.max(0.0, Double.parseDouble(kv.getOrDefault("spent", "0")));
            if (!Double.isFinite(this.spent)) this.spent = 0.0;
            if (kv.containsKey("url")) this.url.set(new String(java.util.Base64.getDecoder().decode(kv.get("url")), StandardCharsets.UTF_8));
            if (kv.containsKey("file")) this.fileName.set(new String(java.util.Base64.getDecoder().decode(kv.get("file")), StandardCharsets.UTF_8));
            String st = kv.getOrDefault("stash", "");
            if (!st.isEmpty()) {
               String[] p = st.split(",");
               this.crafter.setStash(new class_2338(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
            }

            Map<class_1792, Integer> goals = new LinkedHashMap<>();
            for (Entry<String, Integer> entry : FarmJob.goals(kv.getOrDefault("craftGoals", "")).entrySet()) {
               class_1792 item = class_7923.field_41178.method_63535(net.minecraft.class_2960.method_60654(entry.getKey()));
               if (item == null || item == class_1802.field_8162 || !Crafter.knows(item)) throw new IllegalArgumentException("Saved crafting recipe is unavailable: " + entry.getKey());
               goals.put(item, entry.getValue());
            }
            this.savedCraftRepair = Boolean.parseBoolean(kv.getOrDefault("craftRepair", "false"));
            boolean repair = this.savedCraftRepair;
            if (repair) goals.keySet().forEach(item -> {
               String family = toolFamily(item);
               if (family != null) this.replacementTargets.put(family, item);
            });
            if (!goals.isEmpty()) this.savedCraftJob = new Crafter.Job(goals,
               item -> !repair && this.isGear(item) && this.hasGear(item), this.oreFinding.get(), this.mineStall.get());
            Map<class_1792, Integer> interruptedGoals = Crafter.decodeGoals(kv.getOrDefault("interruptedCraftGoals", ""));
            if (!interruptedGoals.isEmpty()) this.interruptedCraft = new Crafter.Job(interruptedGoals,
               item -> this.isGear(item) && this.hasGear(item), Crafter.OreFinding.valueOf(kv.get("interruptedCraftOreMode")),
               Integer.parseInt(kv.get("interruptedCraftStall")));
            this.craftBatch = Boolean.parseBoolean(kv.getOrDefault("craftBatch", "false"));
            if (this.craftBatch) this.lastBatch.addAll(goals.keySet());
            this.gearPreparing = Boolean.parseBoolean(kv.getOrDefault("gearPreparing", "false"));
            this.storageCrafting = Boolean.parseBoolean(kv.getOrDefault("storageCrafting", "false"));
            this.stockpiled = Boolean.parseBoolean(kv.getOrDefault("stockpiled", "false"));
            return true;
         }
      } catch (Exception e) {
         MeteorClient.LOG.warn("[Farm Builder] Could not restore the saved job", e);
         return false;
      }
   }

   private void clearJob() {
      try {
         Files.deleteIfExists(JOB_FILE.toPath());
         Files.deleteIfExists(JOB_BACKUP_FILE.toPath());
      } catch (Exception var2) {
      }
   }

   private void checkBuildProgress(IBaritone b) {
      int correct = this.countCorrect();
      this.forecast.record(this.buildTicks, correct);
      if (this.totalBlocks > 0) {
         int pct = correct * 100 / this.totalBlocks;
         while (this.milestone < 3 && pct >= (this.milestone + 1) * 25) {
            this.milestone++;
            this.ping("Farm build " + this.milestone * 25 + "% done (" + correct + "/" + this.totalBlocks + " blocks).");
         }
      }
      if (this.bestCorrect >= 0 && correct <= this.bestCorrect) {
         boolean shrinking = correct < this.bestCorrect - Math.max(4, this.bestCorrect / 200);
         boolean stalled = this.buildTicks - this.lastGainTick > 7200;
         if (shrinking || stalled) {
            if (!this.safeBuild) {
               this.safeBuild = true;
               this.warning(
                  shrinking
                     ? "Baritone started breaking finished blocks (%d -> %d). Switching to place-only so nothing gets undone."
                     : "No new blocks for 6 minutes (%d/%d). Switching to place-only mode.",
                  this.bestCorrect,
                  correct
               );
               b.getPathingBehavior().cancelEverything();
               this.buildStarted = false;
               this.lastGainTick = this.buildTicks;
            } else if (stalled) {
               this.giveUp("Building stalled with blocks still missing. The job was saved for Resume.");
            }
         }
      } else {
         this.bestCorrect = correct;
         this.lastGainTick = this.buildTicks;
         this.pauseResumes = 0;
      }
   }

   private int countCorrect() {
      IStaticSchematic s = this.schematic;
      if (s != null && this.origin != null) {
         int n = 0;

         for (int x = 0; x < s.widthX(); x++) {
            for (int y = 0; y < s.heightY(); y++) {
               for (int z = 0; z < s.lengthZ(); z++) {
                  class_2680 want = s.getDirect(x, y, z);
                  if (!want.method_26215()
                     && cost(want) != null
                     && FarmLogic.matches(this.mc.field_1687.method_8320(this.origin.method_10069(x, y, z)), want)) {
                     n++;
                  }
               }
            }
         }

         return n;
      } else {
         return 0;
      }
   }

   private class_2338 findSite(int w, int h, int l) {
      class_2338 me = this.mc.field_1724.method_24515();
      int r = this.siteRadius.get();
      int top = this.mc.field_1687.method_31600();
      class_2338 best = null;
      double bestScore = Double.MAX_VALUE;
      List<Integer> heights = new ArrayList<>();

      for (int dx = -r; dx <= r; dx += 4) {
         for (int dz = -r; dz <= r; dz += 4) {
            int x0 = me.method_10263() + dx;
            int z0 = me.method_10260() + dz;
            if (me.method_10263() < x0 - 2 || me.method_10263() > x0 + w + 1 || me.method_10260() < z0 - 2 || me.method_10260() > z0 + l + 1) {
               heights.clear();
               int water = 0;
               int trees = 0;
               int samples = 0;
               boolean bad = false;

               for (int sx = -1; sx <= w && !bad; sx += 4) {
                  for (int sz = -1; sz <= l && !bad; sz += 4) {
                     int x = x0 + Math.min(sx, w);
                     int z = z0 + Math.min(sz, l);
                     if (!this.mc.field_1687.method_8393(x >> 4, z >> 4)) {
                        bad = true;
                        break;
                     }

                     int y = this.mc.field_1687.method_8624(class_2903.field_13197, x, z);
                     class_2338 surface = new class_2338(x, y - 1, z);
                     class_2680 st = this.mc.field_1687.method_8320(surface);
                     samples++;
                     if (!st.method_26227().method_15769()) {
                        water++;
                     } else if (st.method_26164(class_3481.field_15503) || st.method_26164(class_3481.field_15475)) {
                        trees++;
                     } else if (!isNatural(st)) {
                        bad = true;
                     }

                     heights.add(y);
                  }
               }

               if (!bad && samples != 0 && water * 10 <= samples) {
                  heights.sort(null);
                  int min = heights.get(0);
                  int max = heights.get(heights.size() - 1);
                  int median = heights.get(heights.size() / 2);
                  if (max - min <= 8 && median + h <= top) {
                     double mean = heights.stream().mapToInt(i -> i).average().orElse(median);
                     double var = heights.stream().mapToDouble(i -> (i.intValue() - mean) * (i.intValue() - mean)).average().orElse(0.0);
                     double score = var * 2.0 + (max - min) * 4 + trees * 1.5 + water * 20 + Math.sqrt(dx * dx + dz * dz) * 0.15;
                     if (score < bestScore) {
                        bestScore = score;
                        best = new class_2338(x0, median, z0);
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private static boolean isNatural(class_2680 st) {
      return st.method_26164(class_3481.field_29822)
         || st.method_26164(class_3481.field_15466)
         || st.method_26164(class_3481.field_25806)
         || st.method_27852(class_2246.field_10255)
         || st.method_27852(class_2246.field_10477)
         || st.method_27852(class_2246.field_10491)
         || st.method_27852(class_2246.field_10460)
         || st.method_27852(class_2246.field_10295)
         || st.method_27852(class_2246.field_9979)
         || st.method_27852(class_2246.field_10344)
         || st.method_26164(class_3481.field_36265)
         || st.method_27852(class_2246.field_10479)
         || st.method_27852(class_2246.field_10214)
         || st.method_27852(class_2246.field_10112)
         || st.method_26164(class_3481.field_20339)
         || st.method_27852(class_2246.field_28681)
         || st.method_27852(class_2246.field_37576)
         || st.method_27852(class_2246.field_46282)
         || st.method_27852(class_2246.field_46283)
         || st.method_27852(class_2246.field_16999)
         || st.method_27852(class_2246.field_10029)
         || st.method_27852(class_2246.field_10424)
         || st.method_27852(class_2246.field_10211);
   }

   private Map<class_1792, Integer> skippedNeeds() {
      Map<class_1792, Integer> out = new LinkedHashMap<>();

      for (Entry<class_1792, Integer> e : this.required.entrySet()) {
         if (this.unbuyable.contains(e.getKey())) {
            out.put(e.getKey(), e.getValue());
         }
      }

      return out;
   }

   private boolean nothingLeftToGet() {
      for (class_1792 item : this.required.keySet()) {
         if (!this.unbuyable.contains(item)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Clears natural blocks out of the farm's area before the first block is placed. Returns true while it is busy.
    * Only runs on a fresh site (nothing of the farm built yet), never touches block entities or player-made blocks,
    * and gives up on clearing after three tries so terrain left over is handled by the builder as before.
    */
   private boolean tickClearSite(IBaritone b, IBuilderProcess builder) {
      if (this.clearing) {
         boolean timedOut = this.tickCount - this.clearStartTick > 20 * 60 * 8;
         if (builder.isActive() && !builder.isPaused() && !timedOut) return true;
         this.clearing = false;
         b.getPathingBehavior().cancelEverything();
         if (timedOut) this.warning("Clearing the farm area took too long; building now and clearing the rest as it goes.");
      }
      if (!this.clearSiteFirst.get() || this.safeBuild || this.schematic == null || this.origin == null || this.clearAttempts >= 3) return false;

      IStaticSchematic s = this.schematic;
      int toClear = 0, matching = 0;
      boolean fluid = false;
      class_2338 foreign = null;
      String foreignName = null;
      for (int x = 0; x < s.widthX(); x++) {
         for (int y = 0; y < s.heightY(); y++) {
            for (int z = 0; z < s.lengthZ(); z++) {
               class_2338 p = this.origin.method_10069(x, y, z);
               class_2680 have = this.mc.field_1687.method_8320(p);
               if (have.method_26215()) continue;
               if (!have.method_26227().method_15769()) {
                  fluid = true;
                  continue;
               }
               class_2680 want = s.getDirect(x, y, z);
               if (!want.method_26215() && FarmLogic.matches(have, want)) {
                  matching++;
                  continue;
               }
               String id = class_7923.field_41175.method_10221(have.method_26204()).method_12832();
               if (this.mc.field_1687.method_8321(p) != null || !FarmLogic.naturalBlock(id)) {
                  if (foreign == null) {
                     foreign = p;
                     foreignName = id.replace('_', ' ');
                  }
               } else {
                  toClear++;
               }
            }
         }
      }

      // Part of the farm already stands (resumed job): don't wipe the area, let the builder fix what is wrong.
      if (matching > 0) return false;
      if (foreign != null) {
         this.giveUp(String.format("Can't clear the farm area: a %s at %d %d %d looks player-made. Pick another spot or clear it yourself, then Resume Saved Farm.",
            foreignName, foreign.method_10263(), foreign.method_10264(), foreign.method_10260()));
         return true;
      }
      if (toClear == 0) {
         this.clearAttempts = 3;
         return false;
      }
      if (fluid) {
         this.info("Water or lava in the farm area; skipping the clear-first step and clearing as it builds.");
         this.clearAttempts = 3;
         return false;
      }

      this.closeShop();
      this.configureBaritone();
      BaritoneAPI.getSettings().allowBreak.value = true;
      class_2338 far = this.origin.method_10069(s.widthX() - 1, s.heightY() - 1, s.lengthZ() - 1);
      builder.clearArea(this.origin, far);
      this.clearAttempts++;
      this.clearing = true;
      this.clearStartTick = this.tickCount;
      this.info("Site check: %d natural blocks in the farm area, nothing player-made. Clearing them before building.", toClear);
      this.wait = 20;
      return true;
   }

   private void tickBuild() {
      IBaritone b = baritone();
      if (b == null) {
         this.error("Baritone isn't loaded.");
         this.toggle();
      } else {
         IBuilderProcess builder = b.getBuilderProcess();
         if (!this.buildStarted && this.tickClearSite(b, builder)) {
            return;
         }
         if (!this.buildStarted) {
            this.closeShop();
            this.configureBaritone();
            if (!builder.build(this.schematicFile.getName(), this.schematicFile, this.origin)) {
               this.error("Baritone refused the schematic.");
               this.toggle();
            } else {
               this.buildStarted = true;
               this.wait = 40;
            }
         } else if (builder.isActive() && !builder.isPaused()) {
            this.buildTicks++;
            if (this.buildTicks >= this.nextProgressCheck) {
               this.nextProgressCheck = this.buildTicks + 600;
               this.checkBuildProgress(b);
            }
         } else {
            this.plan();
            this.shopping.keySet().removeAll(this.unbuyable);
            if (this.required.isEmpty()) {
               if (builder.isActive()) {
                  b.getPathingBehavior().cancelEverything();
               }

               this.state = FarmBuilder.State.DONE;
            } else if (this.tryWithdraw()) {
               b.getPathingBehavior().cancelEverything();
               this.buildStarted = false;
            } else if (this.buy.get() && this.useAh.get() && !this.shopping.isEmpty() && !this.inventoryFull()) {
               this.info("Out of materials, going shopping.");
               this.current = null;
               this.state = FarmBuilder.State.SHOP_OPEN;
            } else {
               if (this.gather.get() && !this.shopping.isEmpty() && !this.inventoryFull()) {
                  Map<class_1792, Integer> goals = new LinkedHashMap<>();

                  for (Entry<class_1792, Integer> e : this.shopping.entrySet()) {
                     if (!this.unbuyable.contains(e.getKey())) {
                        goals.put(e.getKey(), this.countInInventory(e.getKey()) + Math.min(e.getValue(), 64));
                        if (goals.size() >= 4) {
                           break;
                        }
                     }
                  }

                  if (!goals.isEmpty()) {
                     b.getPathingBehavior().cancelEverything();
                     this.lastBatch.clear();
                     this.lastBatch.addAll(goals.keySet());
                     this.craftBatch = true;
                     this.buildStarted = false;
                     this.crafter.start(goals, i -> this.isGear(i) && this.hasGear(i), this.oreFinding.get(), this.mineStall.get());
                     this.info("Out of materials, gathering %s.", describe(goals));
                     this.state = FarmBuilder.State.CRAFT;
                     return;
                  }
               }

               if (builder.isActive() && builder.isPaused()) {
                  if (!this.shopping.isEmpty()) {
                     this.giveUp(this.inventoryFull()
                        ? "Inventory is full. Clear slots, then Resume Saved Farm."
                        : "Missing materials: " + describe(this.shopping) + ". Enable gathering or supply them, then Resume Saved Farm.");
                  } else {
                     int correct = this.countCorrect();
                     if (correct > this.bestCorrect) {
                        this.bestCorrect = correct;
                        this.pauseResumes = 0;
                        this.buildRestarts = 0;
                     }
                     FarmLogic.BuildRetry retry = FarmLogic.buildRetry(++this.pauseResumes);
                     switch (retry) {
                        case RESUME -> {
                           this.configureBaritone();
                           builder.resume();
                           this.wait = 200;
                        }
                        case RESTART -> {
                           b.getPathingBehavior().cancelEverything();
                           this.buildStarted = false;
                           this.wait = 100;
                           this.note("Baritone paused without a material shortage. Replanning the unfinished build.");
                        }
                        case STOP -> this.giveUp("Baritone couldn't find a way to finish the remaining blocks. Saved for Resume; staying connected.");
                     }
                  }
               } else if (this.nothingLeftToGet()) {
                  this.state = FarmBuilder.State.DONE;
               } else {
                  int correct = this.countCorrect();
                  if (correct > this.bestCorrect) {
                     this.bestCorrect = correct;
                     this.lastGainTick = this.buildTicks;
                     this.buildRestarts = 0;
                  }

                  if (++this.buildRestarts > 4) {
                     this.giveUp("Baritone couldn't finish the remaining blocks after four restarts; the job was saved for Resume.");
                  } else {
                     this.buildStarted = false;
                  }
               }
            }
         }
      }
   }

   private void configureBaritone() {
      Settings s = BaritoneAPI.getSettings();
      s.allowInventory.value = true;
      s.allowBreak.value = this.breakWrong.get() && !this.safeBuild;
      s.buildIgnoreExisting.value = this.safeBuild;
      s.antiCheatCompatibility.value = true;
      s.buildIgnoreProperties.value = new ArrayList<>(FarmLogic.IGNORED_PROPERTIES);
      s.breakCorrectBlockPenaltyMultiplier.value = 1000.0;
      s.blockReachDistance.value = this.movement.isCautious() ? 3.2F : 3.5F;
      s.rightClickSpeed.value = 5;
      FarmLogic.configureBuilderSettings(s);
      s.layerOrder.value = false;
      s.blocksToDisallowBreaking.value = new ArrayList<>();
      s.maxYLevelWhileMining.value = 2031;
      s.allowPlace.value = true;
      s.acceptableThrowawayItems.value = this.throwaway();
      List<class_2248> skip = new ArrayList<>();

      for (class_1792 item : this.unbuyable) {
         if (item instanceof class_1747 bi) {
            skip.add(bi.method_7711());
         }
      }

      s.buildSkipBlocks.value = skip;
   }

   private boolean isShopScreen() {
      return this.mc.field_1755 instanceof class_465 && !(this.mc.field_1755 instanceof class_490);
   }

   private void closeShop() {
      if (this.isShopScreen()) {
         this.mc.field_1724.method_7346();
      }
   }

   private void click(class_1703 handler, int slotId) { this.click(handler, slotId, this.controlClick.get()); }

   private void click(class_1703 handler, int slotId, int button) {
      if (handler != this.mc.field_1724.field_7512 || slotId < 0 || slotId >= handler.field_7761.size()
         || handler.method_7611(slotId).field_7871 instanceof class_1661 || !handler.method_34255().method_7960()) return;
      Look.expectScreen(this.guiTimeout.get() + 40);
      this.mc.field_1761.method_2906(handler.field_7763, slotId, button, class_1713.field_7790, this.mc.field_1724);
   }

   private double priceOf(class_1799 st) {
      class_9290 lore = st.method_58694(class_9334.field_49632);
      if (lore == null) {
         return -1.0;
      } else {
         String own = this.priceKeyword.get().trim().toLowerCase(Locale.ROOT);
         List<String> labels = new ArrayList<>();
         if (own.chars().anyMatch(Character::isLetter)) {
            labels.add(own);
         }

         labels.addAll(List.of("price", "cost", "buy for", "preis", "kosten", "kaufpreis", "bin", "$", "€"));

         for (String key : labels) {
            for (class_2561 line : lore.comp_2400()) {
               String s = line.getString();
               int at = s.toLowerCase(Locale.ROOT).indexOf(key);
               if (at >= 0) {
                  double v = AuctionUi.price(key.equals("$") || key.equals("€") ? s.substring(at) : s.substring(at + key.length()), this.priceFormat.get());
                  if (v > 0.0) {
                     return v;
                  }
               }
            }
         }

         return -1.0;
      }
   }

   private static double parsePrice(String text) { return FarmLogic.parsePrice(text); }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable var1) {
         return null;
      }
   }

   private static String money(double v) {
      if (v >= 1.0E9) {
         return String.format(Locale.ROOT, "$%.2fB", v / 1.0E9);
      } else if (v >= 1000000.0) {
         return String.format(Locale.ROOT, "$%.2fM", v / 1000000.0);
      } else {
         return v >= 1000.0 ? String.format(Locale.ROOT, "$%.1fK", v / 1000.0) : String.format(Locale.ROOT, "$%.0f", v);
      }
   }

   private static String describe(Map<class_1792, Integer> items) {
      StringBuilder sb = new StringBuilder();

      for (Entry<class_1792, Integer> e : items.entrySet()) {
         if (!sb.isEmpty()) {
            sb.append(", ");
         }

         sb.append(e.getValue()).append("x ").append(class_7923.field_41178.method_10221(e.getKey()).method_12832());
      }

      return sb.toString();
   }

   private class AutoResume {
      @EventHandler
      private void observeMenu(Post event) {
         if (++FarmBuilder.this.auctionSnapshotTicks % 10 == 0) {
            try { FarmBuilder.this.captureAuction(); }
            catch (RuntimeException e) { MeteorClient.LOG.debug("[Farm Builder] AH inspection snapshot failed", e); }
         }
      }

      @EventHandler
      private void onJoin(GameJoinedEvent event) {
         FarmBuilder.this.afk.resetWorld();
         FarmBuilder.this.chunkWaitTarget = null;
         if (!FarmBuilder.this.isActive() && FarmBuilder.this.jobRunning()) {
            FarmBuilder.this.pendingResume = true;
            FarmBuilder.this.toggle();
         }
      }
   }

   public static enum Farm {
      SweetBerries("Sweet Berries (240M/h)", "sweet_berries.litematic"),
      Custom("Custom", null);

      private final String title;
      public final String resource;

      private Farm(String title, String resource) {
         this.title = title;
         this.resource = resource;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private static enum Mode {
      FULL,
      ITEMS,
      GEAR;
   }

   private static enum State {
      IDLE,
      WAIT_WORLD,
      RETURNING,
      DEPOT,
      LOADING,
      PLAN,
      CRAFT,
      SHOP_OPEN,
      SHOP_WAIT_GUI,
      SHOP_SCAN,
      SHOP_WAIT_UPDATE,
      SHOP_WAIT_CONFIRM,
      SHOP_AFTER_BUY,
      BUILD,
      DONE,
      TOOL_APPROACH,
      TOOL_REPAIR,
      JUNK_TRIP;
   }
}
