# PSystem's JSON parser predates UTF-8 Ruby: C1 controls need Unicode escapes.
# Match only the known range in that parser, leaving other regular expressions untouched.
if defined?($RGSS_SCRIPTS) && $RGSS_SCRIPTS
  $RGSS_SCRIPTS.each do |entry|
    next unless entry.is_a?(Array) && entry[1].to_s.include?("PSystem_Utilities") && entry[3].is_a?(String)
    entry[3] = entry[3].gsub('\\x7f-\\x9f', '\\u007f-\\u009f')
  end
end
