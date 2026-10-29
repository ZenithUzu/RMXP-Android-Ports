# Android replacements for the RGSS Linker/FmodEx bootstrap used by older Essentials games.
# References: JoiPlay 1.00.69 changelog and enumag/joiplay-mkxp audio/win32api bindings.
# Capture Method objects before game scripts alias/redefine Audio: calling Audio dynamically recurses.
module RmxpRGSSLinker
  NATIVE = {}
  [:bgm, :bgs, :me, :se].each do |channel|
    [:play, :stop, :fade, :pos].each do |operation|
      name = :"#{channel}_#{operation}"
      NATIVE[name] = Audio.method(name) if Audio.respond_to?(name)
    end
  end

  def self.install_fmodex
    return if Object.const_defined?(:FmodEx)
    Object.const_set(:FmodEx, Adapter)
    System.puts("RMXP compat: RGSS Linker/FmodEx usa el audio nativo") if defined?(System)
  end

  module Adapter
    class Error < StandardError; end
    class Sound
      def initialize(file, volume = 100, pitch = 100, position = 0, looping = true, streaming = true, *extra)
        raise Error, "FMOD memory streams are not supported on Android" if extra[0] && !extra[0].empty?
        setup(looping ? :bgm : :se, file, volume, pitch, position)
      end
      def self.for_channel(channel, file, volume, pitch)
        sound = allocate
        sound.send(:setup, channel, file, volume, pitch, 0)
        sound
      end
      def setup(channel, file, volume, pitch, position)
        @channel, @file, @volume, @pitch = channel, file, volume, pitch
        @stopped, @paused = false, position.to_f < 0
        @position = [position.to_f, 0].max
        play unless @paused
      end
      private :setup
      def play
        args = [@file, @volume, @pitch]
        args << @position / 1000.0 if [:bgm, :bgs].include?(@channel)
        NATIVE.fetch(:"#{@channel}_play").call(*args)
      end
      private :play
      def stop
        return false if @stopped
        NATIVE.fetch(:"#{@channel}_stop").call
        @stopped = true
        true
      end
      def fade(ms)
        method = NATIVE[:"#{@channel}_fade"]
        return nil unless method
        method.call(ms.to_i)
        true
      end
      def get_position
        method = NATIVE[:"#{@channel}_pos"]
        return @position if @paused || @stopped || !method
        (method.call * 1000).round
      end
      def set_position(ms)
        @position = [ms.to_f, 0].max
        play unless @paused || @stopped
        @position
      end
      def set_volume(value)
        @position = get_position
        @volume = [[value.to_i, 0].max, 100].min
        play unless @paused || @stopped
        @volume
      end
      def get_volume; @volume; end
      def pause(value)
        return nil if @stopped
        if value && !@paused
          @position = get_position
          NATIVE.fetch(:"#{@channel}_stop").call
        elsif !value && @paused
          play
        end
        @paused = !!value
      end
      def paused?; @paused; end
      def playing?; !@stopped && !@paused; end
      # shortcut: native Audio reads embedded OGG loop tags; arbitrary FMOD loop points,
      # pan/frequency and independent extra channels need native APIs before they can be emulated.
      def set_loop_points(*); nil; end
      def set_looping(*); nil; end
      def looping?; [:bgm, :bgs].include?(@channel); end
      def set_frequency(*); nil; end
      def get_frequency; nil; end
      def set_pan(*); nil; end
      def get_pan; nil; end
      def length; nil; end
    end

    class << self
      def init(channels = 32)
        return false if @initialized
        @initialized = true
      end
      def set_loop_points(*); nil; end
    end
    [:bgm, :bgs, :me, :se].each do |channel|
      singleton_class.send(:define_method, :"#{channel}_play") do |file, volume = 100, pitch = 100, *extra|
        @sounds ||= {}
        # In-memory FMOD streams have no equivalent in the file-based native Audio API.
        raise Error, "FMOD memory streams are not supported on Android" if extra[1] && !extra[1].empty?
        @sounds[channel]&.instance_variable_set(:@stopped, true)
        @sounds[channel] = Sound.for_channel(channel, file, volume, pitch)
      end
      singleton_class.send(:define_method, :"#{channel}_sound") { (@sounds || {})[channel] }
      singleton_class.send(:define_method, :"#{channel}_stop") do
        sound = (@sounds || {}).delete(channel)
        sound ? sound.stop : NATIVE.fetch(:"#{channel}_stop").call
      end
      next if channel == :se
      singleton_class.send(:define_method, :"#{channel}_fade") do |ms|
        NATIVE.fetch(:"#{channel}_fade").call(ms.to_i)
      end
      singleton_class.send(:define_method, :"#{channel}_pos") do
        NATIVE[:"#{channel}_pos"]&.call || 0
      end
    end
  end
end

# Insurgence's H-mode7 uses a Windows-only renderer. JoiPlay falls back to flat maps.
# Only enable this reader override for packs that actually refer to that DLL.
if defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS.is_a?(Array) &&
    $RGSS_SCRIPTS.any? { |entry| entry.is_a?(Array) && entry[3].to_s.b.match?(/MGC[_ ]Hmode7/i) }
  module RmxpFlatHM7
    def hm7; false; end
  end
  tracer = TracePoint.new(:end) do |event|
    klass = event.self
    if klass.is_a?(Class) && klass.name == "Game_System" && klass.method_defined?(:hm7)
      klass.prepend(RmxpFlatHM7)
      tracer.disable
      System.puts("RMXP compat: H-mode7 usa mapas planos") if defined?(System)
    end
  end
  tracer.enable
end
