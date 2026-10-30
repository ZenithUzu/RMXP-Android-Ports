# Opt-in compatibility for Insurgence/Detergent and Ruby 1.8 Essentials packs.
# No original game scripts or saves are rewritten on disk.
module RmxpInsurgence
  ENABLED = File.file?("rmxp_insurgence.txt") && File.binread("rmxp_insurgence.txt").strip == "1"
end

if RmxpInsurgence::ENABLED
  raise "El perfil Insurgence necesita motor-v15 o posterior" unless System.respond_to?(:legacy_critical=)
  require "weakref"

  # The old BitmapCache WeakRef registers Ruby 1.8 finalizers that use Thread.critical.
  # Use MRI's maintained weak references, not locks from a GC/finalizer callback.
  if defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS.is_a?(Array)
    $RGSS_SCRIPTS.each do |entry|
      next unless entry.is_a?(Array) && entry[3].is_a?(String)
      source = entry[3]
      if entry[1].to_s.include?("BitmapCache") && source.include?("@@id_rev_map") && source.include?("Thread.critical")
        source = source.sub(/^class WeakRef\b.*?(?=^class WeakHashtable\b)/m) do |old|
          "\n" * old.count("\n") # Preserve reported script line numbers.
        end
      end
      entry[3] = source
    end
  end

  # These compatibility APIs are scoped to the experimental game process.
  class << Thread
    unless method_defined?(:critical)
      def critical; System.legacy_critical; end
      def critical=(value); System.legacy_critical = value; end
    end
    unless method_defined?(:exclusive)
      def exclusive
        previous = critical
        begin
          self.critical = true
          yield
        ensure
          self.critical = previous
        end
      end
    end
  end
  class Array
    def nitems; count { |item| !item.nil? }; end unless method_defined?(:nitems)
    alias_method :indexes, :values_at unless method_defined?(:indexes)
    alias_method :indices, :values_at unless method_defined?(:indices)
  end
  class Hash
    alias_method :index, :key unless method_defined?(:index)
  end
  class Object
    alias_method :type, :class unless method_defined?(:type)
  end
  class String
    alias_method :each, :each_line unless method_defined?(:each)
    def to_a; lines.to_a; end unless method_defined?(:to_a)
  end
  [File, FileTest, Dir].each do |klass|
    class << klass
      alias_method :exists?, :exist? unless method_defined?(:exists?)
    end
  end

  # Native libnsgif replaces GifLibrary's Windows DLL and temporary PNG extraction.
  # Loading failures still raise with the requested path, rather than hiding missing assets.
  module RmxpInsurgence::NativeGif
    def initialize(file, hue=0)
      raise ArgumentError, "GIF filename is nil" if file.nil?
      @rmxp_file, @rmxp_hue = file, hue
      @rmxp_native = Bitmap.new(file)
      @rmxp_native.hue_change(hue) if hue != 0
      @rmxp_native.play if @rmxp_native.animated?
      @rmxp_snapshots = {}
      @gifbitmaps, @gifdelays = [@rmxp_native], [0]
      @disposed = false
    end
    def bitmap; @rmxp_native; end
    def width; bitmap.width; end
    def height; bitmap.height; end
    def currentIndex; !@rmxp_still && bitmap.animated? ? bitmap.current_frame : 0; end
    def length; @rmxp_still ? 1 : bitmap.frame_count; end
    def totalFrames; length; end
    def [](index)
      return nil unless index.is_a?(Integer)
      index += length if index < 0
      return nil unless index >= 0 && index < length
      return bitmap if length == 1
      @rmxp_snapshots[index] ||= bitmap.snap_to_bitmap(index)
    end
    def each
      return enum_for(:each) unless block_given?
      length.times { |i| yield self[i] }
    end
    def frameDelay(index)
      return 1 unless bitmap.animated?
      rate = bitmap.frame_rate
      rate > 0 ? [Graphics.frame_rate.to_f / rate, 1].max : 1
    end
    def update; end # Native Bitmap advances GIF frames with the engine clock.
    def deanimate
      bitmap.goto_and_stop(0) if bitmap.animated?
      @rmxp_still = true
      bitmap
    end
    def disposed?; @disposed; end
    def dispose
      return if @disposed
      @rmxp_snapshots.each_value { |frame| frame.dispose unless frame.disposed? }
      bitmap.dispose unless bitmap.disposed?
      @disposed = true
    end
    def copy
      duplicate = self.class.new(@rmxp_file, @rmxp_hue)
      duplicate.deanimate if @rmxp_still
      duplicate
    end
  end

  gif_script = defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS.any? do |entry|
    entry.is_a?(Array) && entry[3].is_a?(String) &&
      entry[3].include?("class GifBitmap") && entry[3].include?("GifLibrary")
  end
  if gif_script
    native_gif_trace = TracePoint.new(:end) do |event|
      klass = event.self
      if klass.is_a?(Class) && klass.name == "GifBitmap"
        klass.prepend(RmxpInsurgence::NativeGif)
        native_gif_trace.disable
        System.puts("RMXP Insurgence: GIF nativo, sin gif.dll")
      end
    end
    native_gif_trace.enable
  end

  System.puts("RMXP Insurgence experimental: Ruby #{RUBY_VERSION}, WeakRef moderno, API 1.8 y GIF nativo")
end
