module System
  @messages = []
  class << self
    attr_accessor :fail
    attr_reader :messages
    def puts(text)
      raise 'logger unavailable' if @fail
      @messages << text
    end
  end
end
module Graphics
  @brightness = 255
  @frame_count = 0
  @calls = []
  class << self
    attr_accessor :brightness
    attr_reader :frame_count, :calls
    def update
      @frame_count += 1
      :updated
    end
    def freeze; @calls << :freeze; :frozen; end
    def transition(*args); @calls << args; :transitioned; end
    def fadein(duration); @brightness = 255; duration; end
    def fadeout(duration)
      raise ArgumentError, 'bad duration' if duration < 0
      @brightness = 0
      duration
    end
  end
end
load File.expand_path('../android/patches/rmxp-pantalla-log.rb', __dir__)
def check(value); raise 'check failed' unless value; end
# Essentials y Relict guardan un alias de Graphics.update y después lo redefinen.
# Un prepend hace que ese alias vuelva a entrar en el observador indefinidamente.
module Graphics
  class << self
    alias game_update update
    def update
      @calls << :game_update
      game_update
    end
    alias game_transition transition
    def transition(*args)
      @calls << :game_transition
      game_transition(*args)
    end
  end
end
check(Graphics.freeze == :frozen)
check(Graphics.transition(12, 'mask.png', 30) == :transitioned)
check(Graphics.calls == [:freeze, :game_transition, [12, 'mask.png', 30]])
check(Graphics.fadeout(3) == 3 && Graphics.brightness == 0)
check(Graphics.fadein(7) == 7 && Graphics.brightness == 255)
Graphics.brightness = 0
check(System.messages.any? { |s| s.include?('brightness=: brillo=0') })
100.times { check(Graphics.update == :updated) }
check(Graphics.frame_count == 100)
check(Graphics.calls.count(:game_update) == 100)
check(System.messages.count { |s| s.include?('estado:') } == 1)
begin
  Graphics.fadeout(-1)
  raise 'expected original error'
rescue ArgumentError => e
  check(e.message == 'bad duration')
end
check(System.messages.any? { |s| s.include?('fadeout interrumpido') })
check(System.messages.any? { |s| s.include?('screen_log_check.rb:') })
check(System.messages.any? { |s| s.include?('transition fin:') && s.include?('congelada=false') })
System.fail = true
check(Graphics.fadein(2) == 2)
Graphics.brightness = 1
check(Graphics.update == :updated)
