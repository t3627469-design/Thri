package dev.rex.farmbuilder.modules;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.LayerMode;
import fi.dy.masa.malilib.util.LayerRange;
import java.io.File;
import net.minecraft.class_2338;

/**
 * Shows the build through Litematica's own renderer, so it looks exactly like a Litematica schematic:
 * real translucent block models, colored where the world differs. Only loaded when Litematica is installed;
 * the caller catches the NoClassDefFoundError when it isn't.
 */
final class LitematicaBridge {
   private static SchematicPlacement placement;
   private static boolean layerTouched;
   private static LayerMode previousMode;

   private LitematicaBridge() {
   }

   /** Places the schematic with its minimum corner at {@code corner}. Returns null on success, otherwise the reason. */
   static String show(File file, class_2338 corner) {
      LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file.toPath());
      if (schematic == null) return "Litematica couldn't read " + file.getName();
      hide();
      Configs.Visuals.ENABLE_RENDERING.setBooleanValue(true);
      Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(true);
      Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.setBooleanValue(true);
      SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
      SchematicPlacement p = SchematicPlacement.createFor(schematic, corner, "TheOne Build", true, true);
      manager.addSchematicPlacement(p, false);
      Box box = p.getEclosingBox();
      if (box != null && box.getPos1() != null && box.getPos2() != null) {
         int mx = Math.min(box.getPos1().method_10263(), box.getPos2().method_10263());
         int my = Math.min(box.getPos1().method_10264(), box.getPos2().method_10264());
         int mz = Math.min(box.getPos1().method_10260(), box.getPos2().method_10260());
         // Litematica's origin is not always the lowest corner; shift it so the lowest corner sits on ours.
         if (mx != corner.method_10263() || my != corner.method_10264() || mz != corner.method_10260()) {
            class_2338 origin = new class_2338(2 * corner.method_10263() - mx, 2 * corner.method_10264() - my, 2 * corner.method_10260() - mz);
            p.setOrigin(origin, text -> {
            });
         }
      }
      placement = p;
      return null;
   }

   /** Shows only the schematic layer at world height y, like Litematica's own single layer mode. */
   static void showLayer(int y) {
      LayerRange range = DataManager.getRenderLayerRange();
      if (!layerTouched) {
         previousMode = range.getLayerMode();
         layerTouched = true;
      }
      // switching the mode re-renders the whole schematic once; moving to another layer only refreshes the two layers involved
      if (range.getLayerMode() != LayerMode.SINGLE_LAYER) range.setLayerMode(LayerMode.SINGLE_LAYER, false);
      if (range.getLayerSingle() != y) range.setLayerSingle(y);
   }

   /** Back to the layer mode that was set before the build started (every layer unless the player had changed it). */
   static void showAllLayers() {
      if (!layerTouched) return;
      layerTouched = false;
      LayerMode mode = previousMode == null ? LayerMode.ALL : previousMode;
      previousMode = null;
      DataManager.getRenderLayerRange().setLayerMode(mode, false);
   }

   static void hide() {
      try {
         showAllLayers();
      } catch (Throwable ignored) {
      }
      if (placement == null) return;
      try {
         DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement, false);
      } finally {
         placement = null;
      }
   }
}
