# PSystem's JSON parser predates UTF-8 Ruby: C1 controls need Unicode escapes.
# Match only the known range in that parser, leaving other regular expressions untouched.
if defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS
  $RGSS_SCRIPTS.each do |entry|
    next unless entry.is_a?(Array) && entry[1].to_s.include?("PSystem_Utilities") && entry[3].is_a?(String)
    entry[3] = entry[3].gsub('\\x7f-\\x9f', '\\u007f-\\u009f')
  end
end

# BGM Memory uses Ruby 1.8's method-level `next` as an early exit.
# Only the reported statement is changed; loop/block `next` elsewhere stays intact.
if defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS
  $RGSS_SCRIPTS.each do |entry|
    next unless entry.is_a?(Array) && entry[1].to_s.strip == "BGM Memory" && entry[3].is_a?(String)
    lines = entry[3].lines
    next unless lines[17] && lines[17].match?(/\A\s*next(?:\s|;|$)/)
    lines[17] = lines[17].sub(/\A(\s*)next(?=\s|;|$)/, '\\1return')
    entry[3] = lines.join
  end
end
