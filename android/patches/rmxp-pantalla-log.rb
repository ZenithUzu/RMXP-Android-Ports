# Diagnóstico del oscurecimiento: observa Graphics sin cambiar brillo ni transiciones.
module RmxpPantallaLog
  @frozen = false
  @last_state = 0
  @last_brightness = 0

  def self.frozen=(value)
    @frozen = value
  end

  def self.now
    Process.clock_gettime(Process::CLOCK_MONOTONIC)
  end

  def self.write(event, detail = "")
    source = caller_locations.find { |loc| loc.path != __FILE__ }
    System.puts("RMXP PANTALLA #{event}: brillo=#{Graphics.brightness} " \
                "congelada=#{@frozen} cuadro=#{Graphics.frame_count} #{detail} " \
                "origen=#{source}")
  rescue StandardError
    # Un fallo del diagnóstico no debe interrumpir el juego.
  end

  def self.brightness_changed
    t = now
    value = Graphics.brightness
    # Los fundidos pueden cambiar el brillo cada cuadro; registrar solo extremos o una muestra.
    if value == 0 || value == 255 || t - @last_brightness >= 1
      @last_brightness = t
      write("brightness=")
    end
  rescue StandardError
  end

  def self.frame
    t = now
    return if t - @last_state < 5
    @last_state = t
    write("estado")
  rescue StandardError
  end

  # Guardar la llamada antes de que los scripts del juego creen sus propios alias.
  def self.observe(operation, &observer)
    original = Graphics.method(operation)
    Graphics.define_singleton_method(operation) do |*args|
      observer.call(original, *args)
    end
  end

  observe(:update) do |original, *args|
    result = original.call(*args)
    frame
    result
  end

  observe(:brightness=) do |original, value|
    before = Graphics.brightness
    result = original.call(value)
    brightness_changed if Graphics.brightness != before
    result
  end

  observe(:freeze) do |original, *args|
    write("freeze inicio")
    result = original.call(*args)
    self.frozen = true
    write("freeze fin")
    result
  end

  observe(:transition) do |original, *args|
    write("transition inicio", "duracion=#{args.first || 'predeterminada'}")
    completed = false
    begin
      result = original.call(*args)
      self.frozen = false
      completed = true
      result
    ensure
      write(completed ? "transition fin" : "transition interrumpida")
    end
  end

  [:fadein, :fadeout].each do |operation|
    observe(operation) do |original, *args|
      write("#{operation} inicio", "duracion=#{args.first}")
      completed = false
      begin
        result = original.call(*args)
        completed = true
        result
      ensure
        write(completed ? "#{operation} fin" : "#{operation} interrumpido")
      end
    end
  end
end

RmxpPantallaLog.write("diagnostico activo v2")
