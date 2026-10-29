# RMXP-Android-Ports: mide los tirones. Cada cuadro que tarda más de 50 ms se
# anota en el registro de Android (adb logcat -s mkxp) con cuánto de ese tiempo
# fue el recolector de basura de Ruby (GC). Solo lee relojes; no cambia el juego.
module RmxpMedir
  def self.ahora
    Process.clock_gettime(Process::CLOCK_MONOTONIC, :millisecond)
  end
  @antes = ahora
  @gc_ms = GC.stat(:time)
  @gc_mayor = GC.stat(:major_gc_count)
  System.puts("RMXP GC inicio: #{GC.stat(:heap_available_slots)} espacios " \
              "(con el ajuste deben ser 800000 o mas)")

  def self.cuadro
    t = ahora
    gc = GC.stat(:time)
    mayor = GC.stat(:major_gc_count)
    dur = t - @antes
    if dur > 50
      System.puts("RMXP TIRON #{dur} ms: GC #{gc - @gc_ms} ms, limpiezas grandes #{mayor - @gc_mayor}")
    end
    @antes = t
    @gc_ms = gc
    @gc_mayor = mayor
  end
end

class << Graphics
  alias_method :rmxp_medir_update, :update
  def update(*args)
    r = rmxp_medir_update(*args)
    RmxpMedir.cuadro
    r
  end
end
