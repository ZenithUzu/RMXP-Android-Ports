pattern = <<~'RUBY'
  QUOTE = /"[^"\\\n\r\x00-\x1f\x7f-\x9f]*"/
RUBY
$RGSS_SCRIPTS = [[0, '198:PSystem_Utilities', nil, pattern], [1, 'Other', nil, pattern]]
load 'android/patches/rmxp-script-compat.rb'
raise 'unrelated script changed' unless $RGSS_SCRIPTS[1][3] == pattern
parser = eval($RGSS_SCRIPTS[0][3])
raise 'UTF-8 text lost' unless parser.match('"Pokémon"')[0] == '"Pokémon"'
raise 'control accepted' if parser.match('"bad' + "\u0085" + 'text"')
raise 'ASCII control accepted' if parser.match('"bad' + "\x01" + 'text"')
raise 'escaped quote accepted' if parser.match('"bad\\"text"')&.[](0) == '"bad\\"text"'
puts 'Legacy JSON regexp compatibility passed'
