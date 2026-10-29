# RMXP-Android-Ports: "Ahorrar espacio". Las imágenes y la música (Graphics/, Audio/)
# se quedan dentro del APK y el motor las lee de ahí. Los scripts del juego que
# preguntan si un archivo existe (File.exist?, FileTest.exist?, Dir.glob...) miran la
# carpeta, así que aquí se les dice que también existen los de rmxp_ligero.txt.
# Si el juego se creó sin esa opción, este archivo no hace nada.
if File.file?("rmxp_ligero.txt")
  module RmxpLigero
    RAIZ = File.expand_path(".").tr("\\", "/").downcase + "/"
    # El load_data original del motor: algunos plugins lo redefinen con un solo argumento
    LOAD_DATA = Kernel.method(:load_data)
    ARCHIVOS = {} # ruta en minúsculas => ruta real, relativas a la carpeta del juego
    File.foreach("rmxp_ligero.txt", encoding: "UTF-8") do |l|
      l = l.chomp
      ARCHIVOS[l.downcase] = l unless l.empty?
    end
    CARPETAS = {} # carpeta en minúsculas => nombres de sus archivos
    ARCHIVOS.each_value do |r|
      dir, nombre = File.split(r)
      (CARPETAS[dir.downcase] ||= []) << nombre
    end

    # Ruta relativa a la carpeta del juego, en minúsculas (nil si está fuera)
    def self.rel(path)
      path = path.to_path if path.respond_to?(:to_path)
      return nil unless path.is_a?(String)
      p = File.expand_path(path).tr("\\", "/").downcase
      return "" if p + "/" == RAIZ
      p.start_with?(RAIZ) ? p[RAIZ.size..-1] : nil
    rescue StandardError
      nil
    end

    def self.archivo?(path)
      r = rel(path)
      !r.nil? && ARCHIVOS.key?(r)
    end

    # Ruta real dentro del APK si el archivo está ahí y no en la carpeta (nil si no)
    def self.real(path)
      r = rel(path)
      r && !File.rmxp_ligero_exist?(path) ? ARCHIVOS[r] : nil
    end

    # Contenido del archivo, leído del APK por el motor (load_data en bruto)
    def self.datos(real)
      LOAD_DATA.call(real, true).force_encoding(Encoding::BINARY)
    end

    def self.hijos(dir)
      r = rel(dir)
      r.nil? ? [] : (CARPETAS[r.chomp("/")] || [])
    end

    # Archivos del APK que coinciden con un patrón de Dir.glob (solo patrones relativos,
    # también dentro de Dir.chdir, como hace Dir.get de Essentials)
    def self.glob(patrones, flags)
      base = rel(".")
      return [] if base.nil?
      base = base.empty? ? "" : base.chomp("/") + "/"
      Array(patrones).flat_map do |pat|
        pat = pat.to_s.tr("\\", "/")
        next [] if pat.start_with?("/") || pat =~ /\A[A-Za-z]:/
        pre = pat[/\A(\.\/)*/]
        cuerpo = pat[pre.size..-1]
        f = flags | File::FNM_PATHNAME | File::FNM_EXTGLOB | File::FNM_CASEFOLD
        ARCHIVOS.each_value.filter_map do |r|
          next unless r.downcase.start_with?(base)
          resto = r[base.size..-1]
          pre + resto if File.fnmatch(cuerpo, resto, f)
        end
      end
    end
  end

  [File, FileTest].each do |mod|
    class << mod
      %i[exist? exists? file? readable?].each do |m|
        next unless method_defined?(m)
        viejo = :"rmxp_ligero_#{m}"
        alias_method viejo, m
        define_method(m) { |f, *a| send(viejo, f, *a) || RmxpLigero.archivo?(f) }
      end
    end
  end

  # Algunos scripts abren o leen la imagen para ver si existe (pbGetFileChar de Essentials
  # lee 1 byte). Para los archivos del APK, abrir o leer devuelve su contenido. Se cubren
  # File.open/new, Kernel#open, IO/File.read, binread, readlines, foreach y File.size.
  require "stringio"
  module RmxpLigero
    def self.lectura?(args, kw)
      mode = args[0].is_a?(String) || args[0].is_a?(Integer) ? args[0] : (kw[:mode] || "r")
      mode.is_a?(Integer) ? (mode & (File::WRONLY | File::RDWR)) == 0 : mode.to_s !~ /[wa+]/
    end

    # Un StringIO con el archivo del APK, o nil si hay que usar el disco normal
    def self.abrir(path, args, kw)
      return nil unless path.is_a?(String) || path.respond_to?(:to_path)
      return nil if path.is_a?(String) && path.start_with?("|") # Kernel#open de un comando
      return nil unless lectura?(args, kw)
      r = real(path)
      r && StringIO.new(datos(r))
    end

    def self.con_bloque(io, blk)
      return io unless blk
      begin
        blk.call(io)
      ensure
        io.close
      end
    end
  end

  class << File
    %i[open new].each do |m|
      viejo = :"rmxp_ligero_#{m}"
      alias_method viejo, m
      define_method(m) do |path, *args, **kw, &blk|
        io = RmxpLigero.abrir(path, args, kw)
        io ? RmxpLigero.con_bloque(io, blk) : send(viejo, path, *args, **kw, &blk)
      end
    end

    %i[size size?].each do |m|
      viejo = :"rmxp_ligero_#{m}"
      alias_method viejo, m
      define_method(m) do |path|
        real = RmxpLigero.real(path)
        real ? RmxpLigero.datos(real).bytesize : send(viejo, path)
      end
    end
  end

  # File hereda de IO: esto cubre IO.read y File.read (y los demás)
  class << IO
    %i[read binread].each do |m|
      viejo = :"rmxp_ligero_#{m}"
      alias_method viejo, m
      define_method(m) do |path, *a, **kw|
        real = path.is_a?(String) || path.respond_to?(:to_path) ? RmxpLigero.real(path) : nil
        next send(viejo, path, *a, **kw) unless real
        data = RmxpLigero.datos(real)
        len, off = a[0], a[1] || 0
        len ? data[off, len] : data[off..-1]
      end
    end

    alias_method :rmxp_ligero_readlines, :readlines
    def readlines(path, *a, **kw)
      real = RmxpLigero.real(path)
      real ? RmxpLigero.datos(real).each_line(*a, **kw).to_a : rmxp_ligero_readlines(path, *a, **kw)
    end

    alias_method :rmxp_ligero_foreach, :foreach
    def foreach(path, *a, **kw, &blk)
      real = RmxpLigero.real(path)
      return rmxp_ligero_foreach(path, *a, **kw, &blk) unless real
      lineas = RmxpLigero.datos(real).each_line(*a, **kw)
      return lineas unless blk
      lineas.each(&blk)
      nil
    end
  end

  module Kernel
    alias_method :rmxp_ligero_kopen, :open
    private def open(path, *args, **kw, &blk)
      io = RmxpLigero.abrir(path, args, kw)
      io ? RmxpLigero.con_bloque(io, blk) : rmxp_ligero_kopen(path, *args, **kw, &blk)
    end
  end

  class << Dir
    alias_method :rmxp_ligero_glob, :glob
    def glob(pat, *args, **kw, &blk)
      res = rmxp_ligero_glob(pat, *args, **kw)
      if kw[:base].nil?
        flags = args[0].is_a?(Integer) ? args[0] : (kw[:flags] || 0)
        res = (res + RmxpLigero.glob(pat, flags)).uniq
      end
      return res unless blk
      res.each(&blk)
      nil
    end

    def [](*pats, **kw)
      glob(pats.size == 1 ? pats[0] : pats, **kw)
    end

    alias_method :rmxp_ligero_entries, :entries
    def entries(dir, *a, **kw)
      (rmxp_ligero_entries(dir, *a, **kw) + RmxpLigero.hijos(dir)).uniq
    end

    alias_method :rmxp_ligero_children, :children
    def children(dir, *a, **kw)
      (rmxp_ligero_children(dir, *a, **kw) + RmxpLigero.hijos(dir)).uniq
    end

    alias_method :rmxp_ligero_foreach, :foreach
    def foreach(dir, *a, **kw, &blk)
      lista = entries(dir, *a, **kw)
      return lista.each unless blk
      lista.each(&blk)
      nil
    end
  end
end
