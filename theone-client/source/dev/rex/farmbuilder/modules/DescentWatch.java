package dev.rex.farmbuilder.modules;

final class DescentWatch {
   private int started;
   private int progressed;
   private double lowestY;

   void start(int tick, double y) { this.started = tick; this.progressed = tick; this.lowestY = y; }
   void observe(int tick, double y) {
      if (Double.isFinite(y) && y <= this.lowestY - 1) { this.lowestY = y; this.progressed = tick; }
   }
   boolean expired(int tick) { return tick - this.progressed > 1200 || tick - this.started > 12000; }
}
