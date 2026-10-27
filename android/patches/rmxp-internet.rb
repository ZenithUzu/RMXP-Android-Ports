# RMXP-Android-Ports: internet para fangames.
# Pokémon Essentials (v16-v18) descarga y envía datos con Win32API + wininet.dll,
# que no existe en Android. mkxp-z trae HTTPLite (HTTP y HTTPS nativos), así que
# cuando el juego define estas funciones, las cambiamos por versiones con HTTPLite.
# Essentials v19+ ya usa HTTPLite: el cambio le da el mismo resultado.
if defined?(HTTPLite)
  module RmxpInternet
    def self.get(url)
      res = HTTPLite.get(url.to_s)
      res[:status] == 200 ? res[:body] : ""
    rescue StandardError
      ""
    end

    def self.post(url, fields)
      res = HTTPLite.post(url.to_s, (fields || {}).to_h { |k, v| [k.to_s, v.to_s] })
      res[:status] == 200 ? res[:body] : ""
    rescue StandardError
      ""
    end

    def self.save(file, data)
      File.open(file, "wb") { |f| f.write(data) }
    end

    FUNCTIONS = {
      pbDownloadToString: proc { |url, *| get(url) },
      pbDownloadToFile:   proc { |url, file, *| save(file, get(url)) },
      pbPostToString:     proc { |url, fields, *| post(url, fields) },
      pbPostToFile:       proc { |url, fields, file, *| save(file, post(url, fields)) },
      pbPostData:         proc { |url, fields, *| post(url, fields) }
    }
  end

  # Los scripts del juego cargan después de este parche. Cada vez que el juego
  # define una de esas funciones, la reemplazamos justo después.
  class Object
    def self.method_added(name)
      impl = RmxpInternet::FUNCTIONS[name]
      return unless impl && !@rmxp_internet_busy
      @rmxp_internet_busy = true
      define_method(name) { |*args| impl.call(*args) }
      private name
      @rmxp_internet_busy = false
    end
  end
end
