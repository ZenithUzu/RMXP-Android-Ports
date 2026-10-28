# RMXP-Android-Ports: Win32API de mentira para Android. Los fangames viejos (Essentials
# v16-v18) llaman funciones de Windows para el teclado, el tamaño de ventana, leer Game.ini,
# etc. Aquí se responden las más comunes con lo equivalente; el resto devuelve 0.
class RmxpWin32API
  def initialize(dll, func, *_)
    @func = func.to_s.sub(/[AW]\z/, "") # GetPrivateProfileStringA -> GetPrivateProfileString
  end

  def call(*a)
    case @func
    when "GetAsyncKeyState", "GetKeyState"
      Input.pressex?(a[0].to_i) ? 0x8000 : 0 # el motor entiende los códigos de tecla de Windows
    when "GetPrivateProfileString"           # (sección, clave, por defecto, búfer, tamaño, archivo)
      v = RmxpWin32API.ini(a[5], a[0], a[1]) || a[2].to_s
      a[3].replace(v[0, a[4].to_i - 1].to_s.ljust(a[3].size, "\0")) if a[3].is_a?(String)
      v.size
    when "GetSystemMetrics" then a[0].to_i == 1 ? Graphics.height : Graphics.width
    when "GetWindowRect", "GetClientRect"
      a[1].replace([0, 0, Graphics.width, Graphics.height].pack("l4")) if a[1].is_a?(String)
      1
    when "GetCursorPos", "ScreenToClient"
      a.last.replace([0, 0].pack("l2")) if a.last.is_a?(String)
      1
    when "GetUserDefaultLangID", "GetUserDefaultLCID", "GetSystemDefaultLangID" then 0x0C0A
    when "MessageBox" then (System.puts(a[1].to_s) rescue nil); 1
    when "FindWindow", "FindWindowEx", "GetActiveWindow", "GetForegroundWindow", "SetWindowPos",
         "MoveWindow", "ShowWindow", "SetWindowLong", "SetWindowText", "ShowCursor", "keybd_event"
      1
    else 0
    end
  end
  alias Call call

  def self.ini(file, section, key)
    file = file.to_s.empty? ? "Game.ini" : File.basename(file.to_s.tr("\\", "/"))
    path = Dir.entries(".").find { |e| e.casecmp?(file) } or return nil
    cur = nil
    File.foreach(path, encoding: "UTF-8") do |l|
      l = l.scrub.strip
      if l =~ /\A\[(.+)\]\z/ then cur = $1
      elsif cur.to_s.casecmp?(section.to_s) && l =~ /\A([^=]+)=(.*)\z/ && $1.strip.casecmp?(key.to_s)
        return $2.strip
      end
    end
    nil
  rescue StandardError
    nil
  end
end

Object.send(:remove_const, :Win32API) if Object.const_defined?(:Win32API)
Win32API = RmxpWin32API
