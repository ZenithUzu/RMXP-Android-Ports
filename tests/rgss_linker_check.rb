# Minimal reproduction of the startup contract used by RGSS Linker + Audio(FmodEx).
module Audio
  @calls = []
  class << self; attr_reader :calls; end
  [:bgm, :bgs, :me, :se].each do |channel|
    [:play, :stop, :fade].each do |operation|
      define_singleton_method("#{channel}_#{operation}") { |*args| @calls << [channel, operation, args]; nil }
    end
  end
  def self.bgm_pos; 1.25; end
  def self.bgs_pos; 2.5; end
end
module System
  @messages = []
  class << self; attr_reader :messages; def puts(text); @messages << text; end; end
end
$RGSS_SCRIPTS = [[0, 'H-mode7', '', 'Win32API.new("MGC_Hmode7.dll", "render", "", "")']]
load File.expand_path('../android/patches/rmxp-rgss-linker.rb', __dir__)
load File.expand_path('../android/patches/rmxp-win32.rb', __dir__)
def check(value); raise 'check failed' unless value; end
check(!Object.const_defined?(:FmodEx)) # no global behavior change for games without the DLL
core = Win32API.new('RGSS Linker.dll', 'RGSSLinker_Initialize', 'p', 'i')
check(core.call('RGSS104E.dll') == 1)
check(Win32API.new('other.dll', 'RGSSLinker_Initialize').call == 0)
check(Win32API.new('RGSS_FmodEx.dll', 'Init_FmodEx').call == 1)
check(FmodEx.init(32) && !FmodEx.init(32))
# The game's Audio wrapper must not recurse back into itself through the adapter.
module Audio
  def self.bgm_play(*args); FmodEx.bgm_play(*args); end
end
sound = Audio.bgm_play('Audio/BGM/Test', 80, 110)
check(sound.is_a?(FmodEx::Sound))
check(Audio.calls.last == [:bgm, :play, ['Audio/BGM/Test', 80, 110, 0.0]])
check(sound.get_position == 1250)
sound.set_position(2500)
check(Audio.calls.last.last.last == 2.5)
sound.pause(true);check(sound.paused? && !sound.playing?)
sound.pause(false);check(!sound.paused? && sound.playing?)
sound.set_volume(150);check(sound.get_volume == 100)
check(sound.set_loop_points(10, 30).nil?)
old = sound
sound = FmodEx.bgm_play('Other')
count = Audio.calls.length
check(!old.stop && Audio.calls.length == count) # stale handles must not stop the replacement
check(sound.stop && !sound.playing?)
FmodEx.bgs_play('Rain');FmodEx.bgs_fade(500);FmodEx.bgs_stop
FmodEx.me_play('Victory');FmodEx.se_play('Select');FmodEx.se_stop
begin
  FmodEx.bgm_play('Memory',100,100,true,'bytes')
  raise 'accepted an unsupported memory stream'
rescue FmodEx::Error
end
class Game_System; attr_accessor :hm7; end
system = Game_System.new; system.hm7 = true
check(system.hm7 == false)
system = Marshal.load(Marshal.dump(system));check(system.hm7 == false)
check(Win32API.new('unknown.dll','UnimplementedCall').call == 0)
puts 'RGSS Linker/FmodEx bootstrap, native audio forwarding and flat H-mode7: OK'
