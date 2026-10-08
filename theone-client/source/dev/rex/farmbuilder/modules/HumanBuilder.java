package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1282;
import net.minecraft.class_1657;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_239;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2561;
import net.minecraft.class_2661;
import net.minecraft.class_2680;
import net.minecraft.class_7923;
import net.minecraft.class_2902.class_2903;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_3965;
import net.minecraft.class_465;
import net.minecraft.class_490;

/**
 * Donut builder: loads the bundled Sweet Berries schematic (or any file), buys what is missing
 * from the auction house (cheapest listing, never above the price cap), posts /orders orders for
 * anything the AH does not have, then places the blocks one by one with smoothed aiming.
 *
 * Every tick handler is wrapped: a game or server difference turns the module off with a message
 * instead of crashing the client. Every new menu is printed to latest.log ("[HumanBuilder] screen")
 * so the menu labels can be tuned from a log.
 */
public class HumanBuilder extends Module {
   private static final int MAX_ATTEMPTS = 6;
   private static final String BUNDLED = "sweet_berries.litematic";

   private static final File SUPPLY_FILE = new File(MeteorClient.FOLDER, "farm-builder-supply.txt");
   private static final File PLACE_FILE = new File(MeteorClient.FOLDER, "human-builder-place.txt");
   private static final File JOB_FILE = new File(MeteorClient.FOLDER, "human-builder-job.txt");
   private static final File STATUS_FILE = new File(MeteorClient.FOLDER, "human-builder-overnight.txt");
   private static final long JOB_MAX_AGE_MS = 20L * 3600L * 1000L;

   private enum Phase { BUILD, SCAN_STORAGE, SHOP_NEXT, SHOP_OPEN, SHOP_WAIT_GUI, SHOP_SCAN, SHOP_WAIT_PAGE, SHOP_CONFIRM,
      SHOP_AFTER, ORDER, COLLECT, HOTBAR, STASH, FETCH, RETURN }

   private final SettingGroup sg = this.settings.getDefaultGroup();
   private final SettingGroup sgShop = this.settings.createGroup("Donut");

   private final Setting<String> fileName = this.sg.add(new StringSetting.Builder().name("file-name")
      .description("Schematic inside the .minecraft/schematics folder. Empty = the built-in Sweet Berries farm.")
      .defaultValue("").build());
   private final Setting<Boolean> atLooking = this.sg.add(new BoolSetting.Builder().name("origin-at-looking")
      .description("Anchor the schematic's corner on top of the block you are aiming at when you turn the module on. Off uses the block you stand on.")
      .defaultValue(true).build());
   private final Setting<Integer> speed = this.sg.add(new IntSetting.Builder().name("speed")
      .description("How fast it aims and places: 1 careful, 3 normal, 5 as fast as it goes.").defaultValue(3).range(1, 5).sliderRange(1, 5).build());
   private final Setting<Double> reach = this.sg.add(new DoubleSetting.Builder().name("reach")
      .description("How far from your eyes a block may be placed.").defaultValue(4.0).range(2.0, 4.5).sliderRange(2.5, 4.5).build());

   private final Setting<Boolean> buyMissing = this.sgShop.add(new BoolSetting.Builder().name("buy-missing")
      .description("Buy the materials you don't have from the auction house before building.").defaultValue(true).build());
   private final Setting<String> ahCommand = this.sgShop.add(new StringSetting.Builder().name("ah-search-command")
      .description("Command that opens the AH search. {item} becomes the item name.").defaultValue("/ah {item}").build());
   private final Setting<Boolean> fullStacks = this.sgShop.add(new BoolSetting.Builder().name("buy-full-stacks")
      .description("Always buy a full stack when the AH has one at or under the price cap, even if you need fewer. Falls back to smaller listings only when no full stack exists.").defaultValue(true).build());
   private final Setting<Boolean> priceGuard = this.sgShop.add(new BoolSetting.Builder().name("price-guard")
      .description("Learn the usual price of each item per server and never buy a listing far above it.").defaultValue(true).build());
   private final Setting<Double> priceLimit = this.sgShop.add(new DoubleSetting.Builder().name("price-limit")
      .description("A listing counts as too expensive above this many times the usual price (the median of what the AH showed).").defaultValue(2.0).range(1.1, 10.0).sliderRange(1.2, 5.0).build());
   private final Setting<Double> maxPrice = this.sgShop.add(new DoubleSetting.Builder().name("max-price-each")
      .description("Never buy a listing above this price per single item. 0 = no limit.").defaultValue(3000000.0).min(0.0).sliderMax(10000000.0).build());
   private final Setting<Integer> ahPages = this.sgShop.add(new IntSetting.Builder().name("ah-search-pages")
      .description("How many extra AH pages to look through for the best listing (full stacks are often a few pages down).").defaultValue(8).range(0, 40).sliderRange(0, 20).build());
   private final Setting<Boolean> orderMissing = this.sgShop.add(new BoolSetting.Builder().name("order-if-not-on-ah")
      .description("Post a /orders order for items the AH doesn't have (or only has above the price cap).").defaultValue(true).build());
   private final Setting<Double> orderPrice = this.sgShop.add(new DoubleSetting.Builder().name("order-price-override")
      .description("Price per item to offer in an order. 0 = automatic: the usual price learned from the AH plus 15% (no order is placed for an item whose usual price is unknown).").defaultValue(0.0).min(0.0).sliderMax(1000000.0).build());
   private final Setting<String> ordersCommand = this.sgShop.add(new StringSetting.Builder().name("orders-command")
      .description("Command that opens the orders menu.").defaultValue("/orders").build());
   private final Setting<Integer> orderWait = this.sgShop.add(new IntSetting.Builder().name("order-wait-minutes")
      .description("How long to keep checking /orders for delivered items before giving up on them.").defaultValue(30).range(1, 600).sliderRange(1, 120).build());
   private final Setting<Integer> clickDelay = this.sgShop.add(new IntSetting.Builder().name("click-delay")
      .description("Ticks to wait before each menu click.").defaultValue(6).range(1, 40).sliderRange(2, 20).build());
   private final Setting<Integer> guiTimeout = this.sgShop.add(new IntSetting.Builder().name("gui-timeout")
      .description("Ticks to wait for a menu to open or change before giving up.").defaultValue(80).range(20, 400).sliderRange(40, 200).build());

   private final SettingGroup sgStore = this.settings.createGroup("Supply Chest");
   private final Setting<Boolean> useChest = this.sgStore.add(new BoolSetting.Builder().name("use-supply-chest")
      .description("Keep the materials in a chest next to the farm (a double chest when it can) and take them out as the build needs them.").defaultValue(true).build());
   private final Setting<Integer> lookahead = this.sgStore.add(new IntSetting.Builder().name("withdraw-lookahead")
      .description("How many upcoming blocks' worth of materials to take out of the chest in one trip.").defaultValue(400).range(20, 3000).sliderRange(50, 1500).build());
   private final Setting<Integer> reserveSlots = this.sgStore.add(new IntSetting.Builder().name("keep-free-slots")
      .description("Inventory slots a chest trip leaves free.").defaultValue(3).range(1, 20).sliderRange(1, 10).build());

   private final SettingGroup sgNight = this.settings.createGroup("Overnight Safety");
   private final Setting<String> webhook = this.sgNight.add(new StringSetting.Builder().name("discord-webhook")
      .description("Discord webhook URL. Posts when the build finishes, stops, you die, a player hits you, you run out of materials or it gets stuck. Empty = off.").defaultValue("").build());
   private final Setting<Boolean> webhookCoords = this.sgNight.add(new BoolSetting.Builder().name("webhook-include-location")
      .description("Add your coordinates to webhook messages.").defaultValue(false).build());
   private final Setting<Boolean> milestones = this.sgNight.add(new BoolSetting.Builder().name("progress-pings")
      .description("Post to the webhook at 25%, 50% and 75% built.").defaultValue(true).build());
   private final Setting<Boolean> leaveOnPlayerHit = this.sgNight.add(new BoolSetting.Builder().name("leave-if-player-hits")
      .description("Log out right away if another player (not a Meteor friend) hits you.").defaultValue(true).build());
   private final Setting<Integer> lowHealth = this.sgNight.add(new IntSetting.Builder().name("log-out-health")
      .description("Log out if your health drops to this or lower while something is hurting you. 0 = off.").defaultValue(5).range(0, 19).sliderRange(0, 19).build());
   private final Setting<Integer> stallMinutes = this.sgNight.add(new IntSetting.Builder().name("stuck-minutes")
      .description("If nothing gets built, bought or fetched for this long, retry once, then stop and ping. 0 = off.").defaultValue(12).range(0, 240).sliderRange(0, 60).build());
   private final Setting<Boolean> rebuy = this.sgNight.add(new BoolSetting.Builder().name("rebuy-when-out")
      .description("When a material runs out (and the chest has none), plan and buy it again.").defaultValue(true).build());
   private final Setting<Integer> heartbeat = this.sgNight.add(new IntSetting.Builder().name("heartbeat-minutes")
      .description("Write human-builder-overnight.txt in the Meteor folder this often, even while disconnected. 0 = off.").defaultValue(1).range(0, 60).sliderRange(0, 10).build());
   private final Setting<Integer> sessionHours = this.sgNight.add(new IntSetting.Builder().name("session-limit-hours")
      .description("Stop after this many real hours. 0 = no limit.").defaultValue(0).range(0, 72).sliderRange(0, 12).build());
   private final Setting<Boolean> logoutWhenDone = this.sgNight.add(new BoolSetting.Builder().name("log-out-when-done")
      .description("Leave the server once the build is finished.").defaultValue(false).build());

   private final Setting<String> homeCommand = this.sgNight.add(new StringSetting.Builder().name("home-cmd")
      .description("Teleport back to the farm after a respawn or rejoin when it is far away. Empty = walk.").defaultValue("/home 1").build());
   private final Setting<String> setHomeCommand = this.sgNight.add(new StringSetting.Builder().name("sethome-cmd")
      .description("Sent once when it reaches the build corner so home-cmd can bring it back. Empty = don't.").defaultValue("/sethome 1").build());
   private final Setting<String> joinCommand = this.sgNight.add(new StringSetting.Builder().name("join-command")
      .description("Sent after rejoining, for servers that start you in a lobby. Empty = none.").defaultValue("").build());
   private final Setting<Integer> returnRadius = this.sgNight.add(new IntSetting.Builder().name("return-radius")
      .description("When you are farther than this from the build corner, go back to it first.").defaultValue(96).range(24, 2000).sliderRange(32, 400).build());
   private final Setting<Integer> maxWalkBack = this.sgNight.add(new IntSetting.Builder().name("max-walk-back")
      .description("If /home fails, walk back only when the farm is at most this many blocks away.").defaultValue(2000).range(16, 20000).sliderRange(100, 5000).build());
   private final Setting<Double> budget = this.sgNight.add(new DoubleSetting.Builder().name("budget")
      .description("Most money this run may spend on the AH and orders. 0 = no limit.").defaultValue(0.0).min(0.0).sliderMax(100000000.0).build());

   private final SettingGroup sgPanel = this.settings.createGroup("Dashboard");
   private final Setting<Boolean> useLitematica = this.sgPanel.add(new BoolSetting.Builder().name("litematica-ghost")
      .description("Show the build as Litematica ghost blocks (needs the Litematica and MaLiLib mods). Off or missing: a simple green outline.").defaultValue(true).build());
   private final Setting<Boolean> showPanel = this.sgPanel.add(new BoolSetting.Builder().name("show-dashboard")
      .description("Draw the TheOne status dashboard on screen while this module is on.").defaultValue(true).build());
   private final Setting<Boolean> compactPanel = this.sgPanel.add(new BoolSetting.Builder().name("compact-dashboard")
      .description("Only the header, one activity line and the progress bar.").defaultValue(false).build());
   private final Setting<Integer> panelX = this.sgPanel.add(new IntSetting.Builder().name("dashboard-x")
      .description("Distance from the left edge of the screen.").defaultValue(6).range(0, 2000).sliderRange(0, 600).build());
   private final Setting<Integer> panelY = this.sgPanel.add(new IntSetting.Builder().name("dashboard-y")
      .description("Distance from the top edge of the screen.").defaultValue(6).range(0, 2000).sliderRange(0, 400).build());
   private final HudPanel hud = new HudPanel();
   private HudPanel.Model hudModel;
   private int hudTick = -100;

   private final Reconnect reconnect = new Reconnect(this.settings);
   private final Combat combat = new Combat(this.settings);
   private final Survival survival = new Survival(this.settings, this.combat);
   private final Notifier notifier = new Notifier();
   private final Depot depot = new Depot();
   private long sessionStart;
   private long nextHeartbeat;
   private int deathsNotified;
   private float lastHealth = -1.0F;
   private boolean threatCache;
   private int threatTick;
   private int threatSince = -1;
   private int milestoneDone;
   private int lastProgress;
   private int stalls;
   private int nextReplan;
   private int rejoins;
   private boolean stopped;
   private boolean sessionLive;
   private boolean initPending;
   private int buyMode;
   private final Map<class_1792, Integer> failures = new HashMap<>();
   private final Map<class_1792, Integer> pendingBase = new HashMap<>();
   private double pageCheapest = -1.0;
   private boolean ghostOwned;
   private boolean began;
   private int hotbarTo = -1;
   private final Approach approach = new Approach();
   private String doing = "";
   private long lastWalkInfo;
   private class_2338 stuckAt;
   private int stuckTicks;
   private int asideTries;
   private final PriceBook prices = new PriceBook();
   private String pricesFor = "";
   private final Set<Long> seenPages = new HashSet<>();
   private File schematicFile;
   private boolean ghostHinted;
   private final Set<class_1792> noted = new HashSet<>();
   private boolean announced;
   private boolean needInit;
   private boolean offline;
   private int worldSeen;
   private boolean homeSet;
   private boolean joinSent;
   private int homeTries;
   private int returnStep;
   private int returnDeadline;
   private int walkDeadline;
   private int errors;
   private int lastError;
   private double spent;
   private double pendingPrice;
   private int confirmAt;
   private final Map<class_1792, Integer> required = new LinkedHashMap<>();
   private final List<String> lastMenu = new ArrayList<>();
   private class_2338 origin;
   private int minX, maxX, minY, maxY, minZ, maxZ;
   private Phase stashReturn = Phase.BUILD;
   private boolean chestsOk = true;
   private boolean bought;
   private int nextFetch;
   private int fetchBefore;
   private final Random random = new Random();
   private final Map<class_2338, class_2680> wanted = new HashMap<>();
   private final Planner planner = new Planner(this.wanted);
   private int shownLayer = Integer.MIN_VALUE;
   private class_2338 aimTarget;
   private int aimTicks;
   private final Map<class_2338, Integer> attempts = new HashMap<>();
   private final Map<class_2338, Integer> attemptTick = new HashMap<>();
   private int noneTicks;
   private int pendLayer = Integer.MIN_VALUE;
   private int pendSince;
   private class_2338 walkGoal;
   private final Reposition reposition = new Reposition();
   private int climbTicks;
   private int walkTicks;
   private final Map<class_1792, Integer> shopping = new LinkedHashMap<>();
   private final Map<class_1792, Integer> toOrder = new LinkedHashMap<>();
   private final Map<class_1792, Integer> pending = new LinkedHashMap<>();
   private final Map<class_1792, Integer> purchases = new HashMap<>();
   private final Set<String> seenScreens = new HashSet<>();
   private Phase phase = Phase.BUILD;
   private class_1792 current;
   private int delay;
   private int total;
   private int skipped;
   private long lastNag;
   private int lastTodo = Integer.MAX_VALUE;
   private int ticks;
   private int wait;
   private int timeout;
   private int pages;
   private int pendingCount;
   private int lastSync;
   private int deadline;
   private boolean confirmClicked;
   private long navFingerprint;
   private int step;
   private int stepTimeout;
   private int hotbarFrom = -1;
   private int hotbarStep;
   private int nextCollect;
   private long orderDeadline;
   private boolean lateWarned;
   /** Items whose order never filled: not ordered again automatically, so a stale open order isn't doubled. */
   private final Set<class_1792> abandoned = new HashSet<>();
   private boolean failed;

   public HumanBuilder() {
      super(dev.rex.stealth.Spooky.category(), "human-builder",
         "TheOne Client: buys the schematic's materials on the Donut AH (cheapest first, capped), orders what's missing, then builds block by block.");
      this.depot.setNoPlace(this::inFootprint);
      this.runInMainMenu = true;
   }

   @Override
   public void onActivate() {
      boolean rejoin = this.sessionLive;
      this.sessionLive = true;
      this.failed = false;
      this.stopped = false;
      if (!rejoin) {
         this.stalls = 0;
         this.errors = 0;
         this.spent = 0.0;
         this.homeSet = false;
         this.homeTries = 0;
         this.milestoneDone = 0;
         this.lastHealth = -1.0F;
         this.combat.reset();
         this.survival.reset();
         this.deathsNotified = this.survival.deaths();
         this.reconnect.reset();
         this.rejoins = 0;
         this.total = 0;
         this.announced = false;
         this.sessionStart = System.currentTimeMillis();
         this.nextHeartbeat = 0;
      }
      this.reconnect.setArmed(true);
      this.needInit = true;
      this.worldSeen = -1;
      this.phase = Phase.BUILD;
      this.wait = 0;
      this.doing = "";
      this.approach.reset();
      this.depot.cancel();
   }

   /** Loads the schematic and starts shopping, or goes back to the farm first if it is far away. */
   private void begin() {
      this.needInit = false;
      this.planner.clear();
      this.wanted.clear();
      this.attempts.clear();
      this.attemptTick.clear();
      this.shownLayer = Integer.MIN_VALUE;
      this.shopping.clear();
      this.toOrder.clear();
      this.purchases.clear();
      this.seenScreens.clear();
      this.required.clear();
      this.chestsOk = true;
      this.bought = false;
      this.nextFetch = 0;
      this.nextReplan = 0;
      this.lastProgress = this.ticks;
      this.lastTodo = Integer.MAX_VALUE;
      this.threatCache = false;
      this.current = null;
      this.delay = 0;
      this.skipped = 0;
      this.joinSent = false;
      this.stuckAt = null;
      this.climbTicks = 0;
      this.walkGoal = null;
      this.reposition.reset();
      try {
         this.switchOff(FarmBuilder.class, "farm-builder");
         this.switchOff(OnlyBuild.class, "only-build");
         Approach.lockSettings();
         this.began = true;
         this.load();
         this.saveJob();
         if (this.planner.isEmpty()) {
            this.error("Nothing to build: the schematic has no placeable blocks.");
            this.endSession();
            this.toggle();
            return;
         }
         this.showGhost();
         this.total = this.planner.size();
         this.info("Building %d blocks.", this.total);
         if (!this.announced) {
            this.announced = true;
            this.ping("Started building " + this.total + " blocks.");
         }
         IBaritone b = baritone();
         if (b != null) b.getPathingBehavior().cancelEverything();
         this.initPending = true;
         if (this.farFromBuild()) {
            this.returnStep = 0;
            this.homeTries = 0;
            this.phase = Phase.RETURN;
            return;
         }
         this.continueInit();
      } catch (Throwable t) {
         this.fail(t);
      }
   }

   /** The part of starting up that needs the player to be at the farm: read the chest, plan, shop. */
   private void continueInit() {
      this.initPending = false;
      if (this.chestOn()) {
         this.depot.discover(this.depotAnchor());
         if (this.depot.chestCount() > 0) {
            this.depot.withdraw(this.depotAnchor(), new LinkedHashMap<>());
            this.phase = Phase.SCAN_STORAGE;
            return;
         }
      }
      this.startShopping();
   }

   /** Whichever builder was started last wins: switch the other off instead of refusing to start. */
   private void switchOff(Class<? extends Module> other, String label) {
      try {
         Module m = (Module) Modules.get().get(other);
         if (m != null && m.isActive()) {
            m.toggle();
            this.info("Turned %s off so human-builder can run.", label);
         }
      } catch (Throwable t) {
         System.out.println("[HumanBuilder] couldn't switch " + label + " off: " + t);
      }
   }

   private void endSession() {
      this.sessionLive = false;
   }

   private boolean farFromBuild() {
      if (this.origin == null || this.mc.field_1724 == null) return false;
      double dx = this.mc.field_1724.method_23317() - (this.origin.method_10263() + 0.5);
      double dz = this.mc.field_1724.method_23321() - (this.origin.method_10260() + 0.5);
      int r = this.returnRadius.get();
      return dx * dx + dz * dz > (double) r * r;
   }

   private boolean atCorner() {
      if (this.origin == null || this.mc.field_1724 == null) return false;
      class_2338 at = this.mc.field_1724.method_24515();
      return Math.abs(at.method_10263() - this.origin.method_10263()) <= 2 && Math.abs(at.method_10260() - this.origin.method_10260()) <= 2
         && Math.abs(at.method_10264() - this.origin.method_10264()) <= 3 && this.mc.field_1724.method_24828() && !this.mc.field_1724.method_5799();
   }

   private void tickReturn() {
      if (!this.farFromBuild()) {
         this.approach.stop();
         this.doing = "";
         this.info("Back at the farm.");
         if (this.initPending) {
            this.initPending = false;
            this.continueInit();
         } else this.phase = Phase.BUILD;
         return;
      }
      IBaritone b = baritone();
      switch (this.returnStep) {
         case 0 -> {
            if (!this.joinSent && !this.joinCommand.get().isBlank()) {
               this.joinSent = true;
               ChatUtils.sendPlayerMsg(this.joinCommand.get().trim());
               this.pause(100);
               return;
            }
            String home = this.homeCommand.get().trim();
            if (!home.isEmpty() && this.homeTries < 3) {
               this.homeTries++;
               ChatUtils.sendPlayerMsg(home.startsWith("/") ? home : "/" + home);
               this.returnDeadline = this.ticks + 220;
               this.returnStep = 1;
               return;
            }
            double dx = this.mc.field_1724.method_23317() - this.origin.method_10263();
            double dz = this.mc.field_1724.method_23321() - this.origin.method_10260();
            if (Math.sqrt(dx * dx + dz * dz) > this.maxWalkBack.get()) {
               this.stopRun("The farm is too far to walk back to and /home didn't work.", true, false);
               return;
            }
            this.walkDeadline = this.ticks + 20 * 60 * 25;
            this.returnStep = 2;
         }
         case 1 -> {
            if (this.ticks >= this.returnDeadline) this.returnStep = 0;
         }
         default -> {
            if (this.ticks > this.walkDeadline) {
               this.stopRun("Couldn't walk back to the farm in 25 minutes.", true, false);
               return;
            }
            this.doing = this.approach.walk(this.origin, 4);
            if (this.approach.takeSwitched()) this.warning("Baritone isn't getting closer, so I'm walking back by hand.");
            this.lastProgress = this.ticks;
         }
      }
   }

   /** Draws the build through Litematica when it is installed, otherwise leaves the simple outline on. */
   private void showGhost() {
      if (!this.useLitematica.get() || this.schematicFile == null || this.origin == null) {
         HumanBuilderPreview.suppress = false;
         return;
      }
      try {
         String problem = LitematicaBridge.show(this.schematicFile, this.origin);
         if (problem == null) {
            this.ghostOwned = true;
            this.shownLayer = Integer.MIN_VALUE;
            int low = this.planner.lowestLayerY();
            if (low != Integer.MIN_VALUE) {
               this.shownLayer = low;
               try {
                  LitematicaBridge.showLayer(low);
               } catch (Throwable ignored) {
               }
            }
            HumanBuilderPreview.suppress = true;
            this.info("Showing the build with Litematica ghost blocks.");
         } else {
            HumanBuilderPreview.suppress = false;
            this.warning("%s. Using the simple outline.", problem);
         }
      } catch (Throwable t) {
         HumanBuilderPreview.suppress = false;
         if (!this.ghostHinted) {
            this.ghostHinted = true;
            this.info("Put Litematica and MaLiLib in your mods folder to see the build as real ghost blocks. Using the simple outline.");
         }
      }
   }

   private void hideGhost() {
      if (!this.ghostOwned) return;
      this.ghostOwned = false;
      HumanBuilderPreview.suppress = false;
      try {
         LitematicaBridge.hide();
      } catch (Throwable ignored) {
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
      } catch (Exception ignored) {
      }
   }

   private boolean chestOn() {
      return this.useChest.get() && this.chestsOk;
   }

   /** Works out what is missing and starts buying it, or goes straight to building. */
   private void startShopping() {
      if (this.buyMissing.get()) this.planShopping();
      this.phase = this.shopping.isEmpty() ? this.afterShoppingPhase() : Phase.SHOP_NEXT;
   }

   @Override
   public void onDeactivate() {
      Approach.unlockSettings();
      boolean connected = this.mc.field_1724 != null && this.mc.field_1687 != null;
      if (connected) {
         this.sessionLive = false;
         this.reconnect.setArmed(false);
      }
      this.approach.stop();
      if (!this.began) return;
      try {
         this.depot.cancel();
         this.savePriceBook();
         IBaritone b = baritone();
         if (b != null) b.getPathingBehavior().cancelEverything();
      } catch (Throwable ignored) {
      }
      if (connected) {
         this.hideGhost();
         this.began = false;
      }
   }

   private void fail(Throwable t) {
      this.failed = true;
      this.endSession();
      String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + t.getMessage();
      System.out.println("[HumanBuilder] failed: " + t);
      t.printStackTrace();
      this.error("%s", why);
      this.toggle();
   }

   private void log(String line) {
      System.out.println("[HumanBuilder] " + line);
   }

   // ---------------------------------------------------------------- loading

   private void load() throws Exception {
      File dir = new File(this.mc.field_1697, "schematics");
      Files.createDirectories(dir.toPath());
      String name = this.fileName.get().trim();
      File file;
      if (name.isEmpty() || name.equalsIgnoreCase(BUNDLED)) {
         file = FarmFiles.resolve(dir, "farm-builder-" + BUNDLED);
         if (!file.isFile()) {
            try (InputStream in = HumanBuilder.class.getResourceAsStream("/farms/" + BUNDLED)) {
               if (in == null) throw new IllegalStateException("The built-in farm is missing from the jar.");
               Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      } else {
         file = FarmFiles.resolve(dir, name);
         if (!file.isFile()) throw new IllegalStateException("No file at " + file.getPath());
      }
      this.schematicFile = file;
      Optional<ISchematicFormat> format = BaritoneAPI.getProvider().getSchematicSystem().getByFile(file);
      if (format.isEmpty()) throw new IllegalStateException("Baritone can't read " + file.getName() + ".");
      IStaticSchematic s;
      try (InputStream in = new FileInputStream(file)) {
         s = format.get().parse(in);
      }
      if (s == null || s.widthX() <= 0 || s.heightY() <= 0 || s.lengthZ() <= 0
         || (long) s.widthX() * s.heightY() * s.lengthZ() > 4000000L)
         throw new IllegalStateException("Schematic is empty or larger than 4 million blocks.");

      class_2338 origin = this.chooseOrigin();
      this.origin = origin;
      this.minX = origin.method_10263() - 1;
      this.maxX = origin.method_10263() + s.widthX();
      this.minY = origin.method_10264() - 1;
      this.maxY = origin.method_10264() + s.heightY();
      this.minZ = origin.method_10260() - 1;
      this.maxZ = origin.method_10260() + s.lengthZ();
      for (int y = 0; y < s.heightY(); y++) {
         for (int z = 0; z < s.lengthZ(); z++) {
            for (int x = 0; x < s.widthX(); x++) {
               class_2680 want = s.getDirect(x, y, z);
               if (!placeable(want)) continue;
               class_2338 pos = origin.method_10069(x, y, z);
               this.planner.add(pos);
               this.wanted.put(pos, want);
            }
         }
      }
   }

   private boolean inFootprint(class_2338 p) {
      return this.origin != null && p.method_10263() >= this.minX && p.method_10263() <= this.maxX
         && p.method_10260() >= this.minZ && p.method_10260() <= this.maxZ
         && p.method_10264() >= this.minY && p.method_10264() <= this.maxY;
   }

   private String currentDim() {
      return this.mc.field_1687 == null ? null : this.mc.field_1687.method_27983().method_29177().toString();
   }

   private String currentServer() {
      net.minecraft.class_642 server = this.mc.method_1558();
      return server == null || server.field_3761 == null ? "" : server.field_3761.trim().toLowerCase(Locale.ROOT);
   }

   private FarmLocation readSaved(File file) {
      try {
         if (!file.isFile()) return null;
         FarmLocation spot = FarmLocation.read(file.toPath());
         return spot.matches(this.currentDim(), this.currentServer()) ? spot : null;
      } catch (Exception e) {
         return null;
      }
   }

   private class_2338 chooseOrigin() {
      FarmLocation job = JOB_FILE.isFile() && System.currentTimeMillis() - JOB_FILE.lastModified() < JOB_MAX_AGE_MS ? this.readSaved(JOB_FILE) : null;
      if (job != null) {
         this.info("Resuming the build at %s.", where(job.origin()));
         return job.origin();
      }
      FarmLocation saved = this.readSaved(PLACE_FILE);
      if (saved != null) return saved.origin();
      return this.origin();
   }

   /** Remembers the build corner so a rejoin after a kick carries on at the same farm. */
   private void saveJob() {
      try {
         if (this.origin != null) new FarmLocation(this.origin, this.currentDim(), this.currentServer()).save(JOB_FILE.toPath());
      } catch (Exception ignored) {
      }
   }

   private void clearJob() {
      if (JOB_FILE.exists()) JOB_FILE.delete();
   }

   private void ping(String text) {
      String url = this.webhook.get().trim();
      if (url.isEmpty() || text == null) return;
      StringBuilder sb = new StringBuilder("[TheOne] ").append(text);
      if (this.webhookCoords.get() && this.mc.field_1724 != null) sb.append(" (at ").append(where(this.mc.field_1724.method_24515())).append(')');
      this.notifier.send(url, sb.toString(), System.currentTimeMillis());
   }

   /** Ends the run with a message; keepJob leaves the corner saved so Start picks the farm up again. */
   private void stopRun(String why, boolean keepJob, boolean logOut) {
      if (this.stopped) return;
      this.stopped = true;
      this.endSession();
      if (this.spent > 0.0) why = why + " Spent " + String.format(Locale.ROOT, "%,.0f", this.spent) + ".";
      this.warning("%s", why);
      this.ping(why);
      if (!keepJob) this.clearJob();
      this.writeStatus(why);
      if (logOut) this.logOut(why);
      else if (this.isActive()) this.toggle();
   }

   private void logOut(String why) {
      this.reconnect.setArmed(false);
      if (this.isActive()) this.toggle();
      try {
         AutoReconnect meteor = (AutoReconnect) Modules.get().get(AutoReconnect.class);
         if (meteor != null && meteor.isActive()) meteor.toggle();
      } catch (Throwable ignored) {
      }
      if (this.mc.field_1724 != null) this.mc.field_1724.field_3944.method_52781(new class_2661(class_2561.method_43470("[Human Builder] " + why)));
   }

   private void writeStatus(String activity) {
      try {
         long now = System.currentTimeMillis();
         FarmFiles.writeAtomic(STATUS_FILE.toPath(), "Human Builder overnight status\nUpdated: " + Instant.ofEpochMilli(now)
            + "\nSession minutes: " + Math.max(0, now - this.sessionStart) / 60000L
            + "\nActivity: " + activity + "\nBlocks left: " + this.planner.size() + " of " + this.total
            + "\nDeaths: " + this.survival.deaths() + " | Rejoins: " + this.rejoins
            + "\nReconnect: " + this.reconnect.status()
            + "\nFree slots: " + (this.mc.field_1724 == null ? -1 : this.freeSlots())
            + "\nChest items: " + this.depot.totals().values().stream().mapToInt(Integer::intValue).sum() + "\n");
      } catch (Exception ignored) {
      }
   }

   private boolean checkPlayerAttack() {
      float health = this.mc.field_1724.method_6032();
      boolean tookDamage = this.lastHealth >= 0.0F && health < this.lastHealth;
      this.lastHealth = health;
      if (!tookDamage) return false;
      class_1282 src = this.mc.field_1724.method_6081();
      class_1657 attacker = null;
      if (src != null && src.method_5529() instanceof class_1657 p && p != this.mc.field_1724) attacker = p;
      if (attacker != null && this.leaveOnPlayerHit.get() && !Friends.get().isFriend(attacker)) {
         this.stopRun("Attacked by " + attacker.method_5477().getString() + ", logging out.", true, true);
         return true;
      }
      if (this.lowHealth.get() > 0 && health <= this.lowHealth.get()) {
         this.stopRun("Health down to " + (int) health + ", logging out.", true, true);
         return true;
      }
      return false;
   }

   private class_2338 depotAnchor() {
      FarmLocation spot = this.readSaved(SUPPLY_FILE);
      if (spot != null) return spot.origin();
      int x = this.origin.method_10263() - 4;
      int z = this.origin.method_10260() - 4;
      int y = this.mc.field_1687.method_8393(x >> 4, z >> 4) ? this.mc.field_1687.method_8624(class_2903.field_13197, x, z) : this.origin.method_10264();
      return new class_2338(x, y, z);
   }

   private class_2338 origin() {
      if (this.atLooking.get()) {
         class_239 hit = this.mc.field_1724.method_5745(6.0, 1.0F, false);
         if (hit instanceof class_3965 block && hit.method_17783() == class_239.class_240.field_1332)
            return block.method_17777().method_10084();
      }
      return this.mc.field_1724.method_24515();
   }

   private static boolean placeable(class_2680 s) {
      if (s == null || s.method_26215() || !s.method_26227().method_15769()) return false;
      if (s.method_28498(class_2741.field_12533) && s.method_11654(class_2741.field_12533) == class_2756.field_12609) return false;
      if (s.method_28498(class_2741.field_12483) && s.method_11654(class_2741.field_12483) == class_2742.field_12560) return false;
      return s.method_26204().method_8389() != class_1802.field_8162;
   }

   // ---------------------------------------------------------------- shopping plan

   private int countInInventory(class_1792 item) {
      class_1661 inv = this.mc.field_1724.method_31548();
      int n = 0;
      for (int i = 0; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         if (!st.method_7960() && st.method_7909() == item) n += st.method_7947();
      }
      return n;
   }

   private void planShopping() {
      Map<class_1792, Integer> need = new LinkedHashMap<>(this.planner.remainingItems());
      this.shopping.clear();
      this.required.clear();
      this.required.putAll(need);
      if (this.chestOn() && this.depot.chestCount() == 0) {
         int haveChests = this.countInInventory(class_1802.field_8106) + this.pending.getOrDefault(class_1802.field_8106, 0);
         if (haveChests < 2) this.shopping.put(class_1802.field_8106, 2 - haveChests);
      }
      for (Map.Entry<class_1792, Integer> e : need.entrySet()) {
         int missing = e.getValue() - this.countInInventory(e.getKey()) - this.depot.stored(e.getKey()) - this.pending.getOrDefault(e.getKey(), 0);
         if (missing > 0) this.shopping.put(e.getKey(), missing);
      }
      if (this.shopping.isEmpty()) {
         this.info("You already have every material (inventory + chest).");
      } else {
         StringBuilder sb = new StringBuilder();
         for (Map.Entry<class_1792, Integer> e : this.shopping.entrySet())
            sb.append(e.getValue()).append("x ").append(name(e.getKey())).append(", ");
         this.info("Missing: %s", sb.substring(0, sb.length() - 2));
      }
   }

   private static String name(class_1792 item) {
      return item.method_63680().getString();
   }

   // ---------------------------------------------------------------- menu helpers

   private boolean shopScreenOpen() {
      return this.mc.field_1755 instanceof class_465 && !(this.mc.field_1755 instanceof class_490);
   }

   private void closeMenu() {
      if (this.mc.field_1755 != null) Look.closeScreen();
   }

   private boolean click(class_1703 handler, int slotId, int button, class_1713 type) {
      if (handler != this.mc.field_1724.field_7512 || slotId < 0 || slotId >= handler.field_7761.size()) return false;
      if (type == class_1713.field_7790 && !handler.method_34255().method_7960()) return false;
      this.mc.field_1761.method_2906(handler.field_7763, slotId, button, type, this.mc.field_1724);
      return true;
   }

   private boolean clickMenu(class_1703 handler, int slotId) {
      return this.click(handler, slotId, 0, class_1713.field_7790);
   }

   private static double priceOf(class_1799 st) {
      for (String line : AuctionUi.text(st).split("\n")) {
         String low = line.toLowerCase(Locale.ROOT);
         for (String key : new String[] {"price", "cost", "$"}) {
            int at = low.indexOf(key);
            if (at < 0) continue;
            String rest = key.equals("$") ? line.substring(at) : line.substring(at + key.length());
            double v = AuctionUi.price(rest, AuctionUi.PriceFormat.Auto);
            if (v > 0.0) return v;
         }
      }
      return -1.0;
   }

   /**
    * Price of one single item. A lore line that states an "each / per item" price wins when it agrees with
    * the listing total divided by its count; otherwise it is the listing total divided by the count.
    */
   private static double unitPriceOf(class_1799 st) {
      double total = priceOf(st);
      double byCount = total > 0.0 ? total / Math.max(1, st.method_7947()) : -1.0;
      for (String line : AuctionUi.text(st).split("\n")) {
         String low = line.toLowerCase(Locale.ROOT);
         for (String key : new String[] {"per item", "per unit", "per piece", "each", "/ea"}) {
            int at = low.indexOf(key);
            if (at < 0) continue;
            double v = AuctionUi.price(line.substring(at + key.length()), AuctionUi.PriceFormat.Auto);
            if (!(v > 0.0)) {
               String before = line.substring(0, at);
               int open = before.lastIndexOf('(');
               v = AuctionUi.price(open >= 0 ? before.substring(open + 1) : before, AuctionUi.PriceFormat.Auto);
            }
            if (!(v > 0.0)) continue;
            if (byCount <= 0.0 || v >= byCount / 2.0 && v <= byCount * 2.0) return v;
         }
      }
      return byCount;
   }

   private int findAction(class_1703 handler, AuctionUi.Action action, String keyword) {
      List<AuctionUi.Control> controls = new ArrayList<>();
      for (class_1735 slot : handler.field_7761) {
         class_1799 stack = slot.method_7677();
         if (!stack.method_7960()) controls.add(new AuctionUi.Control(slot.field_7874, AuctionUi.text(stack),
            slot.field_7871 instanceof class_1661, priceOf(stack) > 0));
      }
      return AuctionUi.choose(controls, action, keyword, -1);
   }

   /** Slot of the container button whose title (first line) is or contains the keyword; -1 if none. */
   private int findByName(class_1703 handler, String keyword) {
      int best = -1;
      for (class_1735 slot : handler.field_7761) {
         if (slot.field_7871 instanceof class_1661) continue;
         class_1799 stack = slot.method_7677();
         if (stack.method_7960()) continue;
         String first = AuctionUi.normalize(AuctionUi.text(stack).lines().findFirst().orElse(""));
         if (first.equals(AuctionUi.normalize(keyword))) return slot.field_7874;
         if (best < 0 && AuctionUi.words(first, keyword)) best = slot.field_7874;
      }
      return best;
   }

   private long fingerprint(class_1703 handler) {
      long hash = AuctionUi.stableText(this.mc.field_1755 == null ? "" : this.mc.field_1755.method_25440().getString()).hashCode();
      for (class_1735 slot : handler.field_7761) {
         if (slot.field_7871 instanceof class_1661) continue;
         class_1799 stack = slot.method_7677();
         hash = hash * 31 + slot.field_7874;
         hash = hash * 31 + stack.method_7947();
         hash = hash * 31 + AuctionUi.stableText(AuctionUi.text(stack)).hashCode();
      }
      return hash;
   }

   private void dumpScreen(String tag) {
      if (this.mc.field_1755 == null || this.mc.field_1724 == null) return;
      class_1703 handler = this.mc.field_1724.field_7512;
      String key = tag + "|" + this.mc.field_1755.method_25440().getString() + "|" + handler.field_7763;
      if (this.seenScreens.size() > 400) this.seenScreens.clear();
      if (!this.seenScreens.add(key)) return;
      StringBuilder sb = new StringBuilder("screen [" + tag + "] title='" + this.mc.field_1755.method_25440().getString() + "'");
      for (class_1735 slot : handler.field_7761) {
         if (slot.field_7871 instanceof class_1661) continue;
         class_1799 stack = slot.method_7677();
         if (stack.method_7960()) continue;
         String text = AuctionUi.text(stack).replace('\n', ' ');
         sb.append("\n   slot ").append(slot.field_7874).append(": ").append(text.length() > 140 ? text.substring(0, 140) : text);
      }
      this.log(sb.toString());
      this.lastMenu.clear();
      for (String line : sb.toString().split("\n")) this.lastMenu.add(line.trim());
   }

   // ---------------------------------------------------------------- tick

   @EventHandler
   private void onTick(Post event) {
      if (this.failed) return;
      try {
         this.tick();
      } catch (Throwable t) {
         if (this.ticks - this.lastError > 6000) this.errors = 0;
         this.lastError = this.ticks;
         if (++this.errors >= 6) {
            this.fail(t);
            return;
         }
         System.out.println("[HumanBuilder] recovered from " + t);
         t.printStackTrace();
         this.warning("Hit a snag (%s). Retrying.", t.getClass().getSimpleName());
         try {
            this.depot.cancel();
            this.closeMenu();
         } catch (Throwable ignored) {
         }
         this.phase = Phase.BUILD;
         this.wait = 40;
      }
   }

   private void tick() {
      long now = System.currentTimeMillis();
      this.reconnect.tick();
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         if (!this.offline) {
            this.offline = true;
            this.depot.cancel();
            this.phase = Phase.BUILD;
            this.needInit = true;
         }
         this.beat(now, "Disconnected; " + this.reconnect.status());
         return;
      }
      this.ticks++;
      if (this.ticks % 40 == 0 && this.began) Approach.lockSettings();
      Look.tickScreens();
      if (this.offline) {
         this.offline = false;
         this.worldSeen = this.ticks;
      }
      if (this.needInit) {
         if (this.worldSeen < 0) this.worldSeen = this.ticks;
         if (this.ticks - this.worldSeen < 60) return;
         this.begin();
         return;
      }
      if (this.reconnect.consumeRejoined()) {
         this.rejoins++;
         this.ping("Back in the game after a disconnect. Carrying on.");
      }
      if (this.sessionHours.get() > 0 && now - this.sessionStart >= this.sessionHours.get() * 3600000L) {
         this.stopRun("Session time limit reached.", true, false);
         return;
      }
      this.beat(now, this.activity());
      if (this.ticks % 20 == 0 && Modules.get().isActive(FarmBuilder.class)) {
         this.stopRun("farm-builder was started; stopping so they don't fight over Baritone.", true, false);
         return;
      }

      if (this.survival.handleDeathScreen()) {
         this.combat.reset();
         if (this.survival.deaths() > this.deathsNotified) {
            this.deathsNotified = this.survival.deaths();
            this.ping("Died (" + this.deathsNotified + " so far).");
         }
         return;
      }
      if (this.survival.tooManyDeaths()) {
         this.stopRun("Died " + this.survival.deaths() + " times, stopping.", true, false);
         return;
      }
      if (this.survival.consumeRespawned()) {
         this.depot.cancel();
         this.phase = Phase.BUILD;
         this.nextFetch = 0;
         this.nextReplan = 0;
         this.lastProgress = this.ticks;
      }
      if (this.checkPlayerAttack()) return;

      if (this.ticks - this.threatTick >= 4) {
         this.threatCache = this.combat.threatIncoming();
         this.threatTick = this.ticks;
      }
      boolean threat = this.threatCache;
      if (!threat) this.threatSince = -1;
      else if (this.threatSince < 0) this.threatSince = this.ticks;
      else if (this.ticks - this.threatSince > 300 && !this.combat.isFighting()) threat = false;
      if (threat && this.mc.field_1755 instanceof class_465 && this.phase != Phase.HOTBAR) this.closeMenu();
      boolean survivalBusy = this.survival.tick();
      boolean fighting = (this.mc.field_1755 == null || this.combat.ownsScreen()) && this.combat.tick();
      if (fighting || survivalBusy || threat && this.phase == Phase.BUILD) {
         this.lastProgress = this.ticks;
         return;
      }
      this.watchdog();
      if (this.stopped) return;
      this.milestones();
      if (this.wait > 0) {
         this.wait--;
         return;
      }
      switch (this.phase) {
         case BUILD -> this.tickBuild();
         case SCAN_STORAGE -> this.tickScanStorage();
         case STASH -> this.tickStash();
         case FETCH -> this.tickFetch();
         case HOTBAR -> this.tickHotbar();
         case SHOP_NEXT -> this.tickShopNext();
         case SHOP_OPEN -> this.tickShopOpen();
         case SHOP_WAIT_GUI -> this.tickShopWaitGui();
         case SHOP_SCAN -> this.tickShopScan();
         case SHOP_WAIT_PAGE -> this.tickShopWaitPage();
         case SHOP_CONFIRM -> this.tickShopConfirm();
         case SHOP_AFTER -> this.tickShopAfter();
         case ORDER -> this.tickOrder();
         case COLLECT -> this.tickCollect();
         case RETURN -> this.tickReturn();
      }
   }

   private void beat(long now, String activity) {
      if (this.heartbeat.get() <= 0 || now < this.nextHeartbeat) return;
      this.nextHeartbeat = now + this.heartbeat.get() * 60000L;
      this.writeStatus(activity);
   }

   private void progressed() {
      this.lastProgress = this.ticks;
      this.stalls = 0;
   }

   private void watchdog() {
      int limit = this.stallMinutes.get() * 1200;
      if (!this.pending.isEmpty() && System.currentTimeMillis() < this.orderDeadline) {
         this.lastProgress = this.ticks;
         return;
      }
      if (limit <= 0 || this.ticks - this.lastProgress < limit) return;
      this.stalls++;
      this.lastProgress = this.ticks;
      this.depot.cancel();
      IBaritone b = baritone();
      if (b != null) b.getPathingBehavior().cancelEverything();
      this.closeMenu();
      if (this.stalls >= 2) {
         this.stopRun("Stuck for " + this.stallMinutes.get() * 2 + " minutes (" + this.activity() + "). Stopped; Start Build resumes the same farm.", true, false);
         return;
      }
      this.warning("Nothing built for %d minutes (%s). Retrying from the top.", this.stallMinutes.get(), this.activity());
      this.ping("Stuck on: " + this.activity() + ". Retrying.");
      this.attempts.clear();
      this.nextFetch = 0;
      this.nextReplan = 0;
      this.phase = Phase.BUILD;
   }

   private void milestones() {
      if (!this.milestones.get() || this.total <= 0) return;
      int pct = (int) (100L * (this.total - this.planner.size()) / this.total);
      int step = pct >= 75 ? 75 : pct >= 50 ? 50 : pct >= 25 ? 25 : 0;
      if (step > this.milestoneDone) {
         this.milestoneDone = step;
         this.ping("Build " + step + "% done (" + (this.total - this.planner.size()) + "/" + this.total + ").");
      }
   }

   private void pause(int ticks) {
      this.wait = ticks + this.random.nextInt(3);
   }

   // ---------------------------------------------------------------- shopping

   private int freeSlots() {
      int n = 0;
      for (int i = 0; i < 36; i++) if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) n++;
      return n;
   }

   private boolean stashable() {
      for (int i = 0; i < 36; i++) {
         class_1799 st = this.mc.field_1724.method_31548().method_5438(i);
         if (!st.method_7960() && st.method_7909() != class_1802.field_8106 && this.required.containsKey(st.method_7909())) return true;
      }
      return false;
   }

   private void startStash(Phase back) {
      this.closeMenu();
      IBaritone b = baritone();
      if (b != null) b.getPathingBehavior().cancelEverything();
      this.stashReturn = back;
      this.depot.setReserve(this.reserveSlots.get());
      this.depot.deposit(this.depotAnchor(), i -> i != class_1802.field_8106 && this.required.containsKey(i));
      this.phase = Phase.STASH;
   }

   private void drainAlerts() {
      for (String alert : this.depot.takeAlerts()) this.warning("Chest: %s", alert);
   }

   private void tickScanStorage() {
      Depot.Status st = this.depot.tick();
      this.drainAlerts();
      if (st == Depot.Status.RUNNING) return;
      if (st == Depot.Status.FAILED) this.warning("Couldn't read the supply chest: %s", this.depot.failReason);
      this.startShopping();
   }

   private void tickStash() {
      Depot.Status st = this.depot.tick();
      this.drainAlerts();
      if (st == Depot.Status.RUNNING) return;
      if (st == Depot.Status.FAILED) {
         this.warning("Chest storage failed: %s. Building from the inventory instead.", this.depot.failReason);
         this.chestsOk = false;
      } else {
         this.info("Stored materials in the supply chest (%d chest%s).", this.depot.chestCount(), this.depot.chestCount() == 1 ? "" : "s");
      }
      this.phase = this.stashReturn;
      this.pause(10);
   }

   private void tickFetch() {
      Depot.Status st = this.depot.tick();
      this.drainAlerts();
      if (st == Depot.Status.RUNNING) return;
      int gained = this.heldRequired() - this.fetchBefore;
      if (st == Depot.Status.FAILED) this.warning("Chest trip failed: %s", this.depot.failReason);
      if (gained > 0) this.progressed();
      this.nextFetch = this.ticks + (st == Depot.Status.FAILED || gained <= 0 ? 600 : 20);
      this.phase = Phase.BUILD;
      this.pause(10);
   }

   private int heldRequired() {
      int n = 0;
      for (class_1792 item : this.required.keySet()) n += this.countInInventory(item);
      return n;
   }

   /** Takes the next stretch of the build's materials out of the chest; false if there is nothing to take. */
   private boolean tryFetch(class_1792 wantedItem) {
      if (!this.chestOn() || this.ticks < this.nextFetch || this.depot.stored(wantedItem) <= 0 || this.freeSlots() <= this.reserveSlots.get()) return false;
      Map<class_1792, Integer> upcoming = new LinkedHashMap<>();
      for (class_2338 p : this.planner.upcoming(this.lookahead.get())) {
         upcoming.merge(this.wanted.get(p).method_26204().method_8389(), 1, Integer::sum);
      }
      upcoming.merge(wantedItem, 0, Integer::sum);
      Map<class_1792, Integer> wants = new LinkedHashMap<>();
      for (Map.Entry<class_1792, Integer> e : upcoming.entrySet()) {
         int want = Math.min(Math.max(e.getValue(), e.getKey() == wantedItem ? 64 : 0) - this.countInInventory(e.getKey()), this.depot.stored(e.getKey()));
         if (want > 0) wants.put(e.getKey(), want);
      }
      if (wants.isEmpty()) {
         this.nextFetch = this.ticks + 200;
         return false;
      }
      IBaritone b = baritone();
      if (b != null) b.getPathingBehavior().cancelEverything();
      this.fetchBefore = this.heldRequired();
      this.nextFetch = this.ticks + 100;
      this.depot.setReserve(this.reserveSlots.get());
      this.depot.withdraw(this.depotAnchor(), wants);
      this.phase = Phase.FETCH;
      return true;
   }

   private Phase afterShoppingPhase() {
      if (this.chestOn() && this.bought && this.stashable()) return Phase.STASH;
      return Phase.BUILD;
   }

   private void tickShopNext() {
      this.closeMenu();
      if (this.chestOn() && this.freeSlots() <= 3 && this.stashable() && !this.shopping.isEmpty()) {
         this.startStash(Phase.SHOP_NEXT);
         return;
      }
      this.current = null;
      for (Map.Entry<class_1792, Integer> e : this.shopping.entrySet()) {
         if (e.getValue() > 0) {
            this.current = e.getKey();
            break;
         }
      }
      if (this.current == null) {
         this.shopping.clear();
         this.afterShopping();
         return;
      }
      this.pages = 0;
      this.buyMode = 0;
      this.seenPages.clear();
      this.noted.remove(this.current);
      this.phase = Phase.SHOP_OPEN;
      this.pause(this.clickDelay.get());
   }

   private void afterShopping() {
      if (this.toOrder.isEmpty() && this.chestOn() && this.bought && this.stashable()) {
         this.startStash(Phase.BUILD);
         return;
      }
      if (!this.toOrder.isEmpty() && this.orderMissing.get()) {
         this.info("Not on the AH: %d item type(s). Posting orders.", this.toOrder.size());
         this.step = 0;
         this.pages = 0;
         this.stepTimeout = this.ticks + this.guiTimeout.get();
         this.phase = Phase.ORDER;
         return;
      }
      this.toOrder.clear();
      this.phase = Phase.BUILD;
   }

   /** The AH has nothing acceptable for the item: order it (if allowed) or skip it. */
   private void giveUpOnAh(String why) {
      this.warning("%s: %s. %s", name(this.current), why, this.orderMissing.get() && !this.abandoned.contains(this.current) ? "Will order it." : "Skipping it.");
      int need = this.shopping.getOrDefault(this.current, 0);
      this.shopping.remove(this.current);
      if (need > 0 && !this.abandoned.contains(this.current)) this.toOrder.put(this.current, need);
      this.closeMenu();
      this.phase = Phase.SHOP_NEXT;
      this.pause(this.clickDelay.get());
   }

   /** Something went wrong while buying (menu never opened, purchase never arrived): retry twice, then skip the item without ordering it. */
   private void failOnce(String why) {
      int n = this.failures.merge(this.current, 1, Integer::sum);
      this.closeMenu();
      if (n < 3) {
         this.warning("%s: %s. Trying again (%d/3).", name(this.current), why, n);
         this.phase = Phase.SHOP_OPEN;
         this.pause(40);
         return;
      }
      this.warning("%s: %s. Skipping it for now.", name(this.current), why);
      this.shopping.remove(this.current);
      this.failures.remove(this.current);
      this.phase = Phase.SHOP_NEXT;
      this.pause(this.clickDelay.get());
   }

   private void tickShopOpen() {
      if (this.mc.field_1755 != null) {
         this.closeMenu();
         this.pause(6);
         return;
      }
      String cmd = this.ahCommand.get().trim();
      if (cmd.isEmpty()) cmd = "/ah {item}";
      if (!cmd.startsWith("/")) cmd = "/" + cmd;
      ChatUtils.sendPlayerMsg(cmd.replace("{item}", name(this.current)));
      this.pages = 0;
      this.timeout = this.guiTimeout.get();
      this.phase = Phase.SHOP_WAIT_GUI;
   }

   private void tickShopWaitGui() {
      if (this.shopScreenOpen()) {
         this.dumpScreen("ah");
         this.pause(this.clickDelay.get());
         this.phase = Phase.SHOP_SCAN;
         return;
      }
      if (--this.timeout <= 0) this.failOnce("the AH didn't open");
   }

   private void tickShopScan() {
      if (!this.shopScreenOpen()) {
         this.phase = Phase.SHOP_OPEN;
         return;
      }
      class_1703 handler = this.mc.field_1724.field_7512;
      int need = this.shopping.getOrDefault(this.current, 0);
      class_1735 best = null;
      double bestScore = Double.MAX_VALUE;
      double cap = this.maxPrice.get();
      boolean supplyChest = this.current == class_1802.field_8106;
      // 0 = full stacks only, 1 = at least half a stack, 2 = anything. Each purchase starts at 0 again.
      int mode = supplyChest || !this.fullStacks.get() ? 2 : this.buyMode;
      double fair = this.usualPrice(handler);
      double ceiling = fair > 0.0 ? fair * this.priceLimit.get()
         : this.pageCheapest > 0.0 && this.priceGuard.get() && !supplyChest ? this.pageCheapest * this.priceLimit.get() * 1.5 : -1.0;
      boolean tooHigh = false;
      for (class_1735 slot : handler.field_7761) {
         if (slot.field_7871 instanceof class_1661) continue;
         class_1799 st = slot.method_7677();
         if (st.method_7960() || st.method_7909() != this.current) continue;
         int count = st.method_7947();
         int maxStack = Math.max(1, st.method_7914());
         double fill = maxStack <= 1 ? 1.0 : Math.min(1.0, count / (double) maxStack);
         if (supplyChest && count > Math.max(2, need)) continue;
         if (mode == 0 && count < maxStack) continue;
         if (mode == 1 && fill < 0.5) continue;
         double price = priceOf(st);
         double unit = unitPriceOf(st);
         if (!(price > 0.0) || !(unit > 0.0)) continue;
         if (cap > 0.0 && unit > cap) continue;
         if (ceiling > 0.0 && unit > ceiling) {
            tooHigh = true;
            continue;
         }
         if (this.budget.get() > 0.0 && this.spent + price > this.budget.get()) continue;
         // Below full stacks, a smaller listing only wins when it is clearly cheaper per item (a half stack ~23%).
         double score = mode == 0 ? unit : unit * (1.0 + 0.6 * (1.0 - fill));
         if (score < bestScore) {
            bestScore = score;
            best = slot;
         }
      }
      if (best != null) {
         this.pendingCount = this.countInInventory(this.current);
         this.pendingPrice = priceOf(best.method_7677());
         this.confirmAt = this.ticks + 40;
         this.confirmClicked = false;
         this.lastSync = handler.field_7763;
         this.deadline = this.ticks + this.guiTimeout.get() + 100;
         this.log("buying " + name(this.current) + " slot " + best.field_7874 + " unit=" + bestScore);
         this.clickMenu(handler, best.field_7874);
         this.timeout = this.guiTimeout.get();
         this.phase = Phase.SHOP_CONFIRM;
         this.pause(this.clickDelay.get());
         return;
      }
      if (this.pages < this.ahPages.get()) {
         int next = this.findAction(handler, AuctionUi.Action.NEXT, "next");
         if (next >= 0) {
            this.pages++;
            this.navFingerprint = this.fingerprint(handler);
            this.clickMenu(handler, next);
            this.timeout = this.guiTimeout.get();
            this.phase = Phase.SHOP_WAIT_PAGE;
            this.pause(this.clickDelay.get());
            return;
         }
      }
      if (tooHigh && this.noted.add(this.current)) {
         this.info("%s listings are far above the usual price (about %s each); not buying those.", name(this.current), money(fair));
      }
      if (mode < 2) {
         this.buyMode = mode + 1;
         this.info("No %s of %s at the right price; looking at %s.", mode == 0 ? "full stack" : "half stack", name(this.current), mode == 0 ? "half stacks and up" : "any listing");
         this.pages = 0;
         this.phase = Phase.SHOP_OPEN;
         this.pause(this.clickDelay.get());
         return;
      }
      this.giveUpOnAh((tooHigh ? "prices are far above the usual " + money(fair) : "no listing at or under " + (cap > 0.0 ? String.format(Locale.ROOT, "%,.0f", cap) : "any price")));
   }

   /**
    * The usual unit price of the current item: the learned median for this server, or, before enough has been seen,
    * the median of this page when it shows at least five listings. -1 when there is nothing to compare with.
    */
   private double usualPrice(class_1703 handler) {
      this.pageCheapest = -1.0;
      if (!this.priceGuard.get() || this.current == class_1802.field_8106) return -1.0;
      this.ensurePriceBook();
      String id = class_7923.field_41178.method_10221(this.current).method_12832();
      List<Double> units = new ArrayList<>();
      StringBuilder rows = new StringBuilder();
      this.pageCheapest = -1.0;
      for (class_1735 slot : handler.field_7761) {
         if (slot.field_7871 instanceof class_1661) continue;
         class_1799 st = slot.method_7677();
         if (st.method_7960() || st.method_7909() != this.current) continue;
         double unit = unitPriceOf(st);
         if (unit > 0.0) {
            units.add(unit);
            if (rows.length() < 900) rows.append(st.method_7947()).append("x $").append(String.format(Locale.ROOT, "%.2f", priceOf(st)))
               .append(" (").append(String.format(Locale.ROOT, "%.3f", unit)).append(" ea), ");
         }
      }
      if (this.seenPages.add(this.fingerprint(handler))) {
         this.log("listings of " + name(this.current) + ": " + (rows.length() == 0 ? "none" : rows));
         for (double u : units) this.prices.record(id, u);
         if (this.prices.takeDirty()) this.savePriceBook();
      }
      for (double u : units) if (this.pageCheapest < 0.0 || u < this.pageCheapest) this.pageCheapest = u;
      double fair = this.prices.fair(id);
      if (fair > 0.0) return fair;
      if (units.size() < 5) return -1.0;
      java.util.Collections.sort(units);
      int n = units.size();
      return n % 2 == 1 ? units.get(n / 2) : (units.get(n / 2 - 1) + units.get(n / 2)) / 2.0;
   }

   private void tickShopWaitPage() {
      if (!this.shopScreenOpen()) {
         if (--this.timeout <= 0) this.phase = Phase.SHOP_OPEN;
         return;
      }
      if (this.fingerprint(this.mc.field_1724.field_7512) != this.navFingerprint || --this.timeout <= 0) {
         this.pause(this.clickDelay.get());
         this.phase = Phase.SHOP_SCAN;
      }
   }

   private void tickShopConfirm() {
      if (this.countInInventory(this.current) > this.pendingCount) {
         this.phase = Phase.SHOP_AFTER;
         return;
      }
      if (this.shopScreenOpen() && !this.confirmClicked) {
         class_1703 handler = this.mc.field_1724.field_7512;
         this.dumpScreen("confirm");
         String title = this.mc.field_1755.method_25440().getString();
         int candidate = this.findAction(handler, AuctionUi.Action.CONFIRM, "confirm");
         boolean selected = false;
         boolean cancel = false;
         for (class_1735 slot : handler.field_7761) {
            if (slot.field_7871 instanceof class_1661) continue;
            class_1799 st = slot.method_7677();
            if (st.method_7960()) continue;
            if (st.method_7909() == this.current) selected = true;
            String text = AuctionUi.text(st);
            if (AuctionUi.words(text, "cancel") || AuctionUi.words(text, "abbrechen")) cancel = true;
         }
         boolean titled = AuctionUi.words(title, "confirm") || AuctionUi.words(title, "purchase");
         boolean changed = handler.field_7763 != this.lastSync;
         if (AuctionUi.confirmContext(titled, candidate >= 0, cancel, selected, changed, false)
            || candidate >= 0 && changed && this.ticks >= this.confirmAt) {
            this.clickMenu(handler, candidate);
            this.confirmClicked = true;
            this.deadline = this.ticks + 200;
            this.phase = Phase.SHOP_AFTER;
            this.pause(this.clickDelay.get());
            return;
         }
      }
      if (this.ticks >= this.deadline) this.phase = Phase.SHOP_AFTER;
   }

   private void tickShopAfter() {
      boolean arrived = this.countInInventory(this.current) > this.pendingCount;
      if (!arrived && this.ticks < this.deadline) return;
      if (!arrived) {
         this.failOnce("the purchase didn't reach your inventory");
         return;
      }
      int got = this.countInInventory(this.current) - this.pendingCount;
      int need = Math.max(0, this.shopping.getOrDefault(this.current, 0) - got);
      this.spent += Math.max(0.0, this.pendingPrice);
      this.info("Bought %dx %s (spent %s).", got, name(this.current), String.format(Locale.ROOT, "%,.0f", this.spent));
      this.bought = true;
      this.buyMode = 0;
      this.failures.remove(this.current);
      this.progressed();
      this.shopping.put(this.current, need);
      int n = this.purchases.merge(this.current, 1, Integer::sum);
      if (n > 400) {
         this.shopping.remove(this.current);
      }
      this.closeMenu();
      this.phase = need > 0 && n <= 400 ? Phase.SHOP_OPEN : Phase.SHOP_NEXT;
      this.pause(this.clickDelay.get());
   }

   // ---------------------------------------------------------------- /orders

   /** Price per item to offer in an order: the override, or the learned usual price +15%; -1 when neither is known. */
   private double orderUnitPrice(class_1792 item) {
      double price = this.orderPrice.get();
      if (!(price > 0.0)) {
         this.ensurePriceBook();
         double fair = this.prices.fair(class_7923.field_41178.method_10221(item).method_12832());
         if (!(fair > 0.0)) return -1.0;
         // whole dollars only: round the premium, but never go under the usual price (1.38 rounding down to 1 would not fill)
         price = Math.max(1.0, Math.max((double) Math.round(fair * 1.15), Math.ceil(fair)));
         // a whole-dollar order for a very cheap item can be many times its price: refuse it like the AH guard refuses listings
         if (this.priceGuard.get() && price > fair * this.priceLimit.get()) return -1.0;
      } else {
         price = Math.max(1.0, (double) Math.round(price));
      }
      double cap = this.maxPrice.get();
      return cap > 0.0 ? Math.min(price, cap) : price;
   }

   private void abortOrder(String why) {
      this.pages = 0;
      class_1792 item = this.toOrder.keySet().iterator().next();
      this.warning("Order for %s failed: %s", name(item), why);
      this.toOrder.remove(item);
      this.closeMenu();
      this.step = 0;
      this.stepTimeout = this.ticks + this.guiTimeout.get();
      if (this.toOrder.isEmpty()) this.finishOrders();
      else this.pause(10);
   }

   private void finishOrders() {
      this.closeMenu();
      this.phase = Phase.BUILD;
      if (this.chestOn() && this.bought && this.stashable()) {
         this.startStash(Phase.BUILD);
      }
      if (!this.pending.isEmpty()) {
         this.lateWarned = false;
         this.orderDeadline = System.currentTimeMillis() + this.orderWait.get() * 60_000L;
         this.nextCollect = this.ticks + 20 * 45;
      }
   }

   private void tickOrder() {
      if (this.toOrder.isEmpty()) {
         this.finishOrders();
         return;
      }
      class_1792 item = this.toOrder.keySet().iterator().next();
      int amount = this.toOrder.get(item);
      if (this.ticks > this.stepTimeout) {
         this.dumpScreen("order-step-" + this.step);
         this.abortOrder("menu step " + this.step + " didn't respond");
         return;
      }
      class_1703 handler = this.mc.field_1724.field_7512;
      boolean menu = this.shopScreenOpen();
      switch (this.step) {
         case 0 -> {
            double unitOrder = this.orderUnitPrice(item);
            if (!(unitOrder > 0.0)) {
               this.abortOrder("no usual price is known yet, or a whole-dollar order would be far above it; set order-price-override");
               return;
            }
            double cost = amount * unitOrder;
            if (this.budget.get() > 0.0 && this.spent + cost > this.budget.get()) {
               this.abortOrder("it would go over the budget");
               return;
            }
            if (this.mc.field_1755 != null) { this.closeMenu(); this.pause(6); return; }
            String cmd = this.ordersCommand.get().trim();
            if (!cmd.startsWith("/")) cmd = "/" + cmd;
            ChatUtils.sendPlayerMsg(cmd);
            this.next(1);
         }
         case 1 -> this.orderClick(menu, handler, "your orders", 2);
         case 2 -> this.orderClick(menu, handler, "new order", 3);
         case 3 -> this.orderClick(menu, handler, "item", 4);
         case 4 -> {
            if (!menu) return;
            this.dumpScreen("order-item");
            for (class_1735 slot : handler.field_7761) {
               if (slot.field_7871 instanceof class_1661) continue;
               if (!slot.method_7677().method_7960() && slot.method_7677().method_7909() == item) {
                  this.clickMenu(handler, slot.field_7874);
                  this.next(5);
                  return;
               }
            }
            int nextSlot = this.findAction(handler, AuctionUi.Action.NEXT, "next");
            if (nextSlot >= 0 && this.pages++ < 30) {
               this.clickMenu(handler, nextSlot);
               this.stepTimeout = this.ticks + this.guiTimeout.get();
               this.pause(this.clickDelay.get());
               return;
            }
            this.abortOrder("couldn't find " + name(item) + " in the item picker");
         }
         case 5 -> this.orderClick(menu, handler, "amount", 6);
         case 6 -> {
            if (this.mc.field_1755 != null) return;
            ChatUtils.sendPlayerMsg(String.valueOf(amount));
            this.log("order amount " + amount);
            this.next(7);
            this.pause(20);
         }
         case 7 -> this.orderClick(menu, handler, "price", 8);
         case 8 -> {
            if (this.mc.field_1755 != null) return;
            double price = this.orderUnitPrice(item);
            ChatUtils.sendPlayerMsg(String.valueOf((long) Math.max(1.0, price)));
            this.log("order price " + (long) price);
            this.next(9);
            this.pause(20);
         }
         case 9 -> this.orderClick(menu, handler, "confirm", 10);
         default -> {
            this.spent += amount * Math.max(1.0, this.orderUnitPrice(item));
            this.info("Order placed: %dx %s.", amount, name(item));
            this.ping("Order placed: " + amount + "x " + name(item) + ".");
            this.progressed();
            this.pending.merge(item, amount, Integer::sum);
            this.toOrder.remove(item);
            this.closeMenu();
            this.step = 0;
            this.pages = 0;
            this.stepTimeout = this.ticks + this.guiTimeout.get();
            this.pause(20);
         }
      }
   }

   private void next(int step) {
      this.step = step;
      this.stepTimeout = this.ticks + this.guiTimeout.get();
   }

   /** Clicks the menu button called keyword, then moves to the next step. */
   private void orderClick(boolean menu, class_1703 handler, String keyword, int nextStep) {
      if (!menu) return;
      this.dumpScreen("order-" + keyword);
      int slot = this.findByName(handler, keyword);
      if (slot < 0) return;
      if (this.clickMenu(handler, slot)) {
         this.next(nextStep);
         this.pause(this.clickDelay.get());
      }
   }

   // ---------------------------------------------------------------- collecting delivered orders

   private void tickCollect() {
      if (this.pending.isEmpty()) {
         this.closeMenu();
         this.phase = Phase.BUILD;
         return;
      }
      if (System.currentTimeMillis() > this.orderDeadline + 2L * this.orderWait.get() * 60_000L) {
         this.warning("Gave up on %d order(s) that never filled. They may still be open in /orders: cancel them there. I won't order those items again this session.", this.pending.size());
         this.ping("Gave up waiting for ordered items.");
         this.abandoned.addAll(this.pending.keySet());
         this.pending.clear();
         this.pendingBase.clear();
         this.closeMenu();
         this.phase = Phase.BUILD;
         return;
      }
      if (System.currentTimeMillis() > this.orderDeadline && !this.lateWarned) {
         this.lateWarned = true;
         this.warning("%d ordered item type(s) still aren't filled after %d minutes. The orders stay open in /orders and I keep checking every 5 minutes. Raise order-price-override if they never fill.",
            this.pending.size(), this.orderWait.get());
         this.ping("Orders not filled after " + this.orderWait.get() + " minutes; still waiting.");
      }
      if (this.ticks > this.stepTimeout) {
         this.closeMenu();
         this.nextCollect = this.ticks + 20 * 45;
         this.phase = Phase.BUILD;
         return;
      }
      class_1703 handler = this.mc.field_1724.field_7512;
      boolean menu = this.shopScreenOpen();
      switch (this.step) {
         case 0 -> {
            if (this.mc.field_1755 != null) { this.closeMenu(); this.pause(6); return; }
            String cmd = this.ordersCommand.get().trim();
            if (!cmd.startsWith("/")) cmd = "/" + cmd;
            ChatUtils.sendPlayerMsg(cmd);
            this.next(1);
         }
         case 1 -> this.orderClick(menu, handler, "your orders", 2);
         case 2 -> {
            if (!menu) return;
            this.dumpScreen("collect-list");
            for (class_1735 slot : handler.field_7761) {
               if (slot.field_7871 instanceof class_1661) continue;
               class_1799 st = slot.method_7677();
               if (!st.method_7960() && this.pending.containsKey(st.method_7909())) {
                  // count what we carry right before taking the delivery, so building or fetching in between can't skew it
                  this.pendingBase.clear();
                  for (class_1792 it : this.pending.keySet()) this.pendingBase.put(it, this.countInInventory(it));
                  this.clickMenu(handler, slot.field_7874);
                  this.next(3);
                  this.pause(this.clickDelay.get());
                  return;
               }
            }
            this.finishCollect();
         }
         case 3 -> {
            if (!menu) return;
            this.dumpScreen("collect-take");
            int button = this.findAction(handler, AuctionUi.Action.CONFIRM, "collect");
            for (class_1735 slot : handler.field_7761) {
               if (slot.field_7871 instanceof class_1661) continue;
               class_1799 st = slot.method_7677();
               if (!st.method_7960() && this.pending.containsKey(st.method_7909())) {
                  this.click(handler, slot.field_7874, 0, class_1713.field_7794);
                  this.pause(this.clickDelay.get());
                  return;
               }
            }
            if (button >= 0) this.clickMenu(handler, button);
            this.finishCollect();
         }
         default -> this.finishCollect();
      }
   }

   private void finishCollect() {
      this.closeMenu();
      List<class_1792> done = new ArrayList<>();
      for (Map.Entry<class_1792, Integer> e : new ArrayList<>(this.pending.entrySet())) {
         class_1792 item = e.getKey();
         int have = this.countInInventory(item);
         int base = Math.min(this.pendingBase.getOrDefault(item, have), have);
         int delta = have - base;
         if (delta <= 0) continue;
         int left = e.getValue() - delta;
         this.info("Collected %dx %s from your orders.", delta, name(item));
         this.progressed();
         if (left <= 0) done.add(item);
         else this.pending.put(item, left);
      }
      for (class_1792 item : done) this.pending.remove(item);
      this.pendingBase.clear();
      this.step = 0;
      this.nextCollect = this.ticks + (System.currentTimeMillis() > this.orderDeadline ? 20 * 300 : 20 * 45);
      this.phase = Phase.BUILD;
   }

   // ---------------------------------------------------------------- hotbar

   private void tickHotbar() {
      switch (this.hotbarStep) {
         case 0 -> {
            if (!this.mc.field_1724.field_7498.method_34255().method_7960()) return;
            if (Look.openInventoryIfClosed()) {
               this.hotbarStep = 1;
               this.pause(4);
            }
         }
         case 1 -> {
            Look.clickSwapToHotbar(this.hotbarFrom, this.hotbarTo >= 0 ? this.hotbarTo : this.mc.field_1724.method_31548().method_67532());
            this.hotbarStep = 2;
            this.pause(4);
         }
         default -> {
            Look.closeScreen();
            this.hotbarFrom = -1;
            this.phase = Phase.BUILD;
            this.pause(3);
         }
      }
   }

   // ---------------------------------------------------------------- building

   private void tickBuild() {
      if (this.mc.field_1755 != null) return;

      if (this.ticks % 5 == 0) this.planner.sweep();
      if (this.planner.size() < this.lastTodo) this.progressed();
      this.lastTodo = this.planner.size();
      if (this.planner.isEmpty()) {
         int skippedAll = this.skipped + this.planner.skipped + this.planner.blocked;
         String done = "Done: " + (this.total - skippedAll) + " blocks placed, " + skippedAll + " skipped.";
         this.info("%s", done);
         this.stopRun("Build finished. " + done, false, this.logoutWhenDone.get());
         return;
      }
      if (!this.pending.isEmpty() && this.ticks >= this.nextCollect) {
         this.step = 0;
         this.stepTimeout = this.ticks + this.guiTimeout.get() + 100;
         this.nextCollect = this.ticks + 20 * 45;
         IBaritone pb = baritone();
         if (pb != null) pb.getPathingBehavior().cancelEverything();
         this.phase = Phase.COLLECT;
         return;
      }
      if (this.farFromBuild()) {
         IBaritone fb = baritone();
         if (fb != null) fb.getPathingBehavior().cancelEverything();
         this.returnStep = 0;
         this.homeTries = 0;
         this.phase = Phase.RETURN;
         return;
      }
      if (!this.homeSet && !this.setHomeCommand.get().isBlank() && this.atCorner()) {
         this.homeSet = true;
         String cmd = this.setHomeCommand.get().trim();
         ChatUtils.sendPlayerMsg(cmd.startsWith("/") ? cmd : "/" + cmd);
         this.info("Saved home at the build corner.");
      }
      if (this.delay > 0) this.delay--;

      IBaritone b = baritone();
      class_1661 inv = this.mc.field_1724.method_31548();
      class_1792 inHand = inv.method_5438(inv.method_67532()).method_7909();
      Set<class_1792> bar = new HashSet<>();
      for (int i = 0; i < 9; i++) bar.add(inv.method_5438(i).method_7909());
      Set<class_1792> carried = new HashSet<>(bar);
      for (int i = 9; i < 36; i++) carried.add(inv.method_5438(i).method_7909());
      // prefer what is in hand, then the hotbar, then the rest of the bag; blocks whose material is not on you come last
      ToDoubleFunction<class_1792> swap = it -> it == inHand ? -4.0 : bar.contains(it) ? 0.0 : carried.contains(it) ? 30.0 : 80.0;
      Planner.Choice choice = this.planner.choose(item -> true, swap, this.reach.get());
      if (choice.kind == Planner.Kind.NONE) {
         this.noneTicks++;
         this.approach.stop();
         this.walkGoal = null;
         if (b != null && b.getCustomGoalProcess().isActive()) b.getPathingBehavior().cancelEverything();
         this.doing = choice.why == Planner.Why.DONE ? "Placing blocks" : "Looking for a way to place the rest";
         Look.idleLook(this.random);
         return;
      }
      this.noneTicks = 0;
      this.syncLayer();
      if (choice.kind == Planner.Kind.WALK) {
         this.goToward(b, choice.pos, choice.underUs);
         return;
      }
      class_2338 target = choice.pos;
      Look.Placement placement = choice.placement;
      this.stuckAt = null;
      this.stuckTicks = 0;
      this.walkGoal = null;
      this.climbTicks = 0;
      this.reposition.reset();
      this.doing = "";
      this.approach.stop();
      if (b != null && b.getCustomGoalProcess().isActive()) b.getPathingBehavior().cancelEverything();

      class_1792 item = this.wanted.get(target).method_26204().method_8389();
      if (!this.hold(item)) {
         if (this.phase != Phase.BUILD) return;
         if (this.tryFetch(item)) return;
         if (this.replan(item)) return;
         Look.idleLook(this.random);
         long now = System.currentTimeMillis();
         if (now - this.lastNag > 15000L) {
            this.lastNag = now;
            if (this.pending.containsKey(item)) this.warning("Waiting for the %s order to be delivered.", name(item));
            else this.warning("Out of %s. Add more to your inventory.", name(item));
         }
         return;
      }

      int[] profile = Planner.speedProfile(this.speed.get());
      boolean aimed = Look.lookAt(placement.hit(), (float) profile[2]);
      for (int i = 1; i < profile[3] && !aimed; i++) aimed = Look.lookAt(placement.hit(), (float) profile[2]);
      // a block that is lined up but never gets clicked (something in the way, server refusing): rest it, then drop it
      if (!target.equals(this.aimTarget)) {
         this.aimTarget = target;
         this.aimTicks = 0;
      } else if (++this.aimTicks > 100) {
         this.aimTicks = 0;
         this.planner.stuck(target);
         return;
      }
      if (aimed && this.delay <= 0 && Look.crosshairOn(placement.against(), placement.side()) && Look.useCrosshairBlock()) {
         // count a refused placement as an attempt at most every half second, so a mob in the way for a moment doesn't use them up
         Integer last = this.attemptTick.get(target);
         if (last == null || this.ticks - last >= 10) {
            this.attemptTick.put(target, this.ticks);
            if (this.attempts.merge(target, 1, Integer::sum) >= MAX_ATTEMPTS) {
               this.planner.drop(target);
               this.skipped++;
            }
         }
         this.planner.placed(target);
         this.aimTicks = 0;
         this.asideTries = 0;
         this.delay = profile[0] + (profile[1] > profile[0] ? this.random.nextInt(profile[1] - profile[0] + 1) : 0);
      }
   }

   /**
    * Shows only the layer being built in Litematica and moves up when it is done. Going back down to a lower
    * layer (a hanging block that just got its support) waits a moment so the view doesn't flicker.
    */
   private void syncLayer() {
      int y = this.planner.activeLayerY();
      if (y == Integer.MIN_VALUE || !this.ghostOwned) return;
      if (y == this.shownLayer) {
         this.pendLayer = Integer.MIN_VALUE;
         return;
      }
      if (this.shownLayer != Integer.MIN_VALUE && y < this.shownLayer) {
         if (y != this.pendLayer) {
            this.pendLayer = y;
            this.pendSince = this.ticks;
            return;
         }
         if (this.ticks - this.pendSince < 60) return;
      }
      this.pendLayer = Integer.MIN_VALUE;
      this.shownLayer = y;
      try {
         LitematicaBridge.showLayer(y);
      } catch (Throwable ignored) {
      }
   }

   /** The blocks of the layer being built, for the fallback outline. */
   private List<class_2338> previewList() {
      return this.planner.view();
   }

   /** Out of reach: walk there (by hand if Baritone isn't getting closer); in reach but unplaceable: step aside, then give up on that block. */
   private void goToward(IBaritone b, class_2338 goal, boolean underUs) {
      if (!underUs && Approach.distance(goal) > this.reach.get() + 1.5) {
         this.stuckAt = null;
         this.stuckTicks = 0;
         this.reposition.reset();
         // standing right under (or beside) a block that is still out of reach: walking can't fix that, only climbing could
         if (Approach.horizontal(goal) <= 4.0) {
            if (++this.climbTicks > 30) {
               this.climbTicks = 0;
               this.walkGoal = null;
               this.approach.reset();
               this.warning("The block at %d %d %d is too high to reach from the ground. Resting it; stand higher or build up to it.", goal.method_10263(), goal.method_10264(), goal.method_10260());
               this.planner.stuck(goal);
               return;
            }
         } else {
            this.climbTicks = 0;
         }
         // a block that can't be walked to (no path, across a gap) is parked after a minute and a half instead of walking forever
         if (goal.equals(this.walkGoal)) {
            if (++this.walkTicks > 1800) {
               this.walkTicks = 0;
               this.walkGoal = null;
               this.approach.reset();
               this.planner.stuck(goal);
               return;
            }
         } else {
            this.walkGoal = goal;
            this.walkTicks = 0;
         }
         this.doing = this.approach.walk(goal, 3);
         long now = System.currentTimeMillis();
         if (now - this.lastWalkInfo > 20000L) {
            this.lastWalkInfo = now;
            this.info("%s", this.doing);
         }
         if (this.approach.takeSwitched()) this.warning("Baritone isn't getting closer, so I'm walking to the build by hand.");
         return;
      }
      this.approach.stop();
      this.doing = "Placing blocks";
      // close and supported but can't be clicked from here: one Baritone move at a time to another side, then rest the block
      if (this.reposition.tick(b, goal, underUs, null)) this.planner.stuck(goal);
   }

   /** Out of an item the chest can't supply: work out what's missing again and buy or order it. */
   private boolean replan(class_1792 item) {
      if (!this.rebuy.get() || !this.buyMissing.get() || this.pending.containsKey(item) || this.abandoned.contains(item) || this.ticks < this.nextReplan) return false;
      this.nextReplan = this.ticks + 20 * 180;
      this.planShopping();
      if (this.shopping.isEmpty()) return false;
      this.ping("Out of " + name(item) + ", buying more.");
      IBaritone b = baritone();
      if (b != null) b.getPathingBehavior().cancelEverything();
      this.phase = Phase.SHOP_NEXT;
      return true;
   }

   /** An empty hotbar slot if there is one, so materials spread over the bar instead of swapping each other out. */
   private int freeHotbarSlot() {
      for (int i = 0; i < 9; i++) if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) return i;
      return -1;
   }

   /** True when the item is in the selected hotbar slot; otherwise starts moving it there. */
   private boolean hold(class_1792 item) {
      if (Look.selectHotbar((class_1799 s) -> s.method_7909() == item)) return true;
      class_1661 inv = this.mc.field_1724.method_31548();
      for (int i = 9; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         if (!st.method_7960() && st.method_7909() == item) {
            this.hotbarFrom = i;
            this.hotbarTo = this.freeHotbarSlot();
            this.hotbarStep = 0;
            this.phase = Phase.HOTBAR;
            return false;
         }
      }
      return false;
   }


   // ---------------------------------------------------------------- panel

   @EventHandler
   private void onRender(Render2DEvent event) {
      try {
         if (!this.showPanel.get() || this.mc.field_1724 == null || this.mc.field_1690.field_1842) return;
         if (this.hudModel == null || this.ticks - this.hudTick >= 10 || this.ticks < this.hudTick) {
            this.hudModel = this.dashModel();
            this.hudTick = this.ticks;
         }
         this.hud.render(event.drawContext, this.mc.field_1772, this.panelX.get(), this.panelY.get(), this.hudModel, this.compactPanel.get());
      } catch (Throwable t) {
         this.hudModel = null;
      }
   }

   private String badge() {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) return "OFFLINE";
      if (this.needInit) return "LOADING";
      if (this.combat.status() != null) return "COMBAT";
      if (this.survival.status() != null) return "SURVIVAL";
      return switch (this.phase) {
         case SHOP_NEXT, SHOP_OPEN, SHOP_WAIT_GUI, SHOP_SCAN, SHOP_WAIT_PAGE, SHOP_CONFIRM, SHOP_AFTER, ORDER, COLLECT -> "SHOPPING";
         case STASH, FETCH, SCAN_STORAGE -> "STORAGE";
         case RETURN -> "TRAVEL";
         default -> this.pending.isEmpty() || this.hotPlacing() ? "BUILDING" : "WAITING";
      };
   }

   private boolean hotPlacing() {
      return this.planner.size() < this.lastTodo || this.ticks - this.lastProgress < 200;
   }

   /** Everything the dashboard shows, rebuilt twice a second. */
   private HudPanel.Model dashModel() {
      HudPanel.Model m = new HudPanel.Model();
      m.title = "HUMAN BUILDER";
      m.badge = this.badge();
      m.badgeColor = HudPanel.badgeColor(m.badge);
      m.activity = this.activity();
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         m.subtitle = "Donut edition";
         m.row("Status", this.reconnect.status());
         return m;
      }
      int done = Math.max(0, this.total - this.planner.size());
      m.subtitle = this.total > 0 ? done * 100 / this.total + "% built" : "Donut edition";
      if (this.total > 0) m.bar("Build", done + "/" + this.total, done / (float) this.total, HudPanel.ACCENT);
      if (this.budget.get() > 0.0) m.bar("Budget", money(this.spent) + " / " + money(this.budget.get()), (float) (this.spent / this.budget.get()), HudPanel.AMBER);
      if (this.current != null && !this.shopping.isEmpty()) m.row("Buying", name(this.current) + " (" + this.shopping.getOrDefault(this.current, 0) + " left)");
      if (!this.toOrder.isEmpty() || !this.pending.isEmpty()) {
         m.row("Orders", this.toOrder.size() + " to place, " + this.pending.size() + " waiting");
      }
      if (this.depot.chestCount() > 0) {
         int n = this.depot.totals().values().stream().mapToInt(Integer::intValue).sum();
         m.row("Chest", n + " items in " + this.depot.chestCount() + (this.depot.chestCount() == 1 ? " chest" : " chests"));
      }
      if (this.origin != null) {
         double dx = this.mc.field_1724.method_23317() - this.origin.method_10263();
         double dz = this.mc.field_1724.method_23321() - this.origin.method_10260();
         m.row("Corner", where(this.origin) + "  (" + (int) Math.sqrt(dx * dx + dz * dz) + "m)");
      }
      if (this.spent > 0.0) m.row("Spent", money(this.spent));
      int mins = (int) Math.max(0, System.currentTimeMillis() - this.sessionStart) / 60000;
      m.chip("HP " + Math.round(this.mc.field_1724.method_6032()));
      m.chip("Food " + this.mc.field_1724.method_7344().method_7586());
      m.chip("Free " + this.freeSlots());
      m.chip("Up " + mins / 60 + "h" + mins % 60 + "m");
      if (this.combat.spotted() > 0) m.chip("Mobs " + this.combat.spotted());
      if (this.survival.deaths() > 0) m.chip("Deaths " + this.survival.deaths());
      if (this.rejoins > 0) m.chip("Rejoins " + this.rejoins);
      return m;
   }

   private static String money(double v) {
      if (v >= 1.0E9) return String.format(Locale.ROOT, "%.2fB", v / 1.0E9);
      if (v >= 1.0E6) return String.format(Locale.ROOT, "%.2fM", v / 1.0E6);
      if (v >= 1.0E3) return String.format(Locale.ROOT, "%.1fK", v / 1.0E3);
      return String.format(Locale.ROOT, "%.0f", v);
   }

   private String activity() {
      if (!this.isActive()) return "idle";
      String item = this.current == null ? "" : name(this.current);
      return switch (this.phase) {
         case BUILD -> (this.doing.isEmpty() || this.doing.equals("Placing blocks") ? "Building" : this.doing) + " layer " + this.planner.layerNumber() + "/" + this.planner.layerCount() + ": " + this.planner.size() + " blocks left of " + this.total
            + (this.pending.isEmpty() ? "" : " (waiting on " + this.pending.size() + " order(s))");
         case SCAN_STORAGE -> "Reading the supply chest";
         case SHOP_NEXT, SHOP_OPEN, SHOP_WAIT_GUI, SHOP_SCAN, SHOP_WAIT_PAGE, SHOP_CONFIRM, SHOP_AFTER -> "Buying " + item + " on the AH";
         case ORDER -> "Posting an order";
         case COLLECT -> "Collecting delivered orders";
         case HOTBAR -> "Moving materials to the hotbar";
         case RETURN -> "Going back to the farm";
         case STASH -> this.depot.status() == null ? "Storing materials" : this.depot.status();
         case FETCH -> this.depot.status() == null ? "Taking materials out of the chest" : this.depot.status();
      };
   }

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

   private static String where(class_2338 p) {
      return p.method_10263() + " " + p.method_10264() + " " + p.method_10260();
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WVerticalList col = theme.verticalList();
      title(theme, col, "◆ TheOne Client — Human Builder");
      col.add(theme.label("Status: " + this.activity()));

      section(theme, col, "Build");
      WHorizontalList run = theme.horizontalList();
      WButton start = run.add(theme.button("Start Build")).widget();
      start.action = () -> {
         this.clearJob();
         if (this.isActive()) this.toggle();
         this.toggle();
      };
      WButton resume = run.add(theme.button("Resume Build")).widget();
      resume.action = () -> {
         if (!this.isActive()) this.toggle();
      };
      WButton stop = run.add(theme.button("Stop")).widget();
      stop.action = () -> {
         this.clearJob();
         if (this.isActive()) this.toggle();
      };
      col.add(run);
      WHorizontalList place = theme.horizontalList();
      WButton here = place.add(theme.button("Set Build Here")).widget();
      here.action = () -> this.saveSpot(PLACE_FILE, this.mc.field_1724 == null ? null : this.mc.field_1724.method_24515(), "Build corner");
      WButton looking = place.add(theme.button("Set Build Where I'm Looking")).widget();
      looking.action = () -> {
         if (this.mc.field_1724 == null) return;
         class_239 hit = this.mc.field_1724.method_5745(8.0, 1.0F, false);
         if (hit instanceof class_3965 block && hit.method_17783() == class_239.class_240.field_1332)
            this.saveSpot(PLACE_FILE, block.method_17777().method_10084(), "Build corner");
         else this.error("Aim at a ground block within 8 blocks first.");
      };
      WButton clearPlace = place.add(theme.button("Aim When Starting")).widget();
      clearPlace.action = () -> {
         this.clearJob();
         if (PLACE_FILE.delete() || !PLACE_FILE.exists()) this.info("Saved build corner cleared; the corner is where you aim when you start.");
      };
      col.add(place);
      FarmLocation saved = this.mc.field_1687 == null ? null : this.readSaved(PLACE_FILE);
      col.add(theme.label(saved == null ? "Build corner: where you aim when you press Start (farm goes toward +X and +Z)"
         : "Build corner: " + where(saved.origin())));

      section(theme, col, "Supply chest");
      WHorizontalList supply = theme.horizontalList();
      WButton supplyHere = supply.add(theme.button("Set Supply Chest Here")).widget();
      supplyHere.action = () -> this.saveSpot(SUPPLY_FILE, this.mc.field_1724 == null ? null : this.mc.field_1724.method_24515(), "Supply chest spot");
      WButton supplyAuto = supply.add(theme.button("Auto Supply Spot")).widget();
      supplyAuto.action = () -> {
         if (SUPPLY_FILE.delete() || !SUPPLY_FILE.exists()) this.info("Supply chest spot cleared; it goes 4 blocks outside the farm corner.");
      };
      col.add(supply);
      FarmLocation spot = this.mc.field_1687 == null ? null : this.readSaved(SUPPLY_FILE);
      col.add(theme.label(spot == null ? "Supply chest: automatic (4 blocks outside the farm corner, double chest when it fits)"
         : "Supply chest: " + where(spot.origin())));
      if (this.depot.chestCount() > 0) {
         int n = this.depot.totals().values().stream().mapToInt(Integer::intValue).sum();
         col.add(theme.label("Stored: " + n + " items in " + this.depot.chestCount() + " chest" + (this.depot.chestCount() == 1 ? "" : "s")));
      }

      section(theme, col, "Donut shopping");
      col.add(theme.label("Buys the cheapest AH listing at or under " + String.format(Locale.ROOT, "%,.0f", this.maxPrice.get())
         + " each; anything the AH lacks is ordered at " + (this.orderPrice.get() > 0.0 ? String.format(Locale.ROOT, "%,.0f", this.orderPrice.get()) + " each." : "the usual price + 15%.")));
      for (Map.Entry<class_1792, Integer> e : this.shopping.entrySet())
         col.add(theme.label("  To buy: " + e.getValue() + "x " + name(e.getKey())));
      for (Map.Entry<class_1792, Integer> e : this.toOrder.entrySet())
         col.add(theme.label("  To order: " + e.getValue() + "x " + name(e.getKey())));
      for (Map.Entry<class_1792, Integer> e : this.pending.entrySet())
         col.add(theme.label("  Ordered, waiting: " + e.getValue() + "x " + name(e.getKey())));

      section(theme, col, "Overnight");
      col.add(theme.label(this.webhook.get().trim().isEmpty() ? "Discord pings: off (paste a webhook URL in Overnight Safety)"
         : Notifier.validUrl(this.webhook.get()) ? "Discord pings: on" : "Discord pings: the webhook URL isn't a discord.com webhook"));
      col.add(theme.label("Spent this run: " + String.format(Locale.ROOT, "%,.0f", this.spent)
         + (this.budget.get() > 0.0 ? " of " + String.format(Locale.ROOT, "%,.0f", this.budget.get()) : " (no budget set)")));
      col.add(theme.label("Deaths: " + this.survival.deaths() + "   Rejoins: " + this.rejoins + "   " + this.reconnect.status()));
      col.add(theme.label("Start Build forgets the saved corner; Resume Build (or a rejoin) keeps it. Status file: " + STATUS_FILE.getName()));
      WHorizontalList night = theme.horizontalList();
      WButton test = night.add(theme.button("Test Webhook")).widget();
      test.action = () -> {
         if (!Notifier.validUrl(this.webhook.get())) this.error("Set a discord.com webhook URL first.");
         else {
            this.ping("Test message from Human Builder.");
            this.info("Test message sent.");
         }
      };
      col.add(night);

      section(theme, col, "Tools");
      WHorizontalList tools = theme.horizontalList();
      WButton inspect = tools.add(theme.button("Print Last Menu")).widget();
      inspect.action = () -> {
         if (this.lastMenu.isEmpty()) this.info("No menu seen yet. Open the AH or /orders while the module is running.");
         else for (String line : this.lastMenu) this.info("%s", line);
      };
      WButton list = tools.add(theme.button("Material List")).widget();
      list.action = () -> {
         if (this.required.isEmpty()) this.info("Start the module once to compute the material list.");
         else this.required.forEach((k, v) -> this.info("%dx %s (have %d, chest %d)", v, name(k), this.mc.field_1724 == null ? 0 : this.countInInventory(k), this.depot.stored(k)));
      };
      col.add(tools);
      col.add(theme.label("Menu labels and slots are written to latest.log as [HumanBuilder] screen ..."));
      return col;
   }

   private void saveSpot(File file, class_2338 p, String what) {
      if (p == null || this.mc.field_1687 == null) {
         this.error("Join a world first.");
         return;
      }
      try {
         FarmLocation spot = new FarmLocation(p, this.currentDim(), this.currentServer());
         spot.save(file.toPath());
         if (file == PLACE_FILE) this.clearJob();
         this.info("%s saved at %s.", what, where(p));
      } catch (Exception e) {
         this.error("Couldn't save %s: %s", what.toLowerCase(Locale.ROOT), e.getMessage());
      }
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable t) {
         return null;
      }
   }
}
