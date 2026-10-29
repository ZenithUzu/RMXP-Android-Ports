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

  module Hooks
    def update(*args)
      result = super
      RmxpPantallaLog.frame
      result
    end

    def brightness=(value)
      before = brightness
      result = super
      RmxpPantallaLog.brightness_changed if brightness != before
      result
    end

    def freeze(*args)
      RmxpPantallaLog.write("freeze inicio")
      result = super
      RmxpPantallaLog.frozen = true
      RmxpPantallaLog.write("freeze fin")
      result
    end

    def transition(*args)
      RmxpPantallaLog.write("transition inicio", "duracion=#{args.first || 'predeterminada'}")
      completed = false
      result = super
      RmxpPantallaLog.frozen = false
      completed = true
      result
    ensure
      RmxpPantallaLog.write(completed ? "transition fin" : "transition interrumpida")
    end

    [:fadein, :fadeout].each do |operation|
      define_method(operation) do |*args|
        RmxpPantallaLog.write("#{operation} inicio", "duracion=#{args.first}")
        completed = false
        result = super(*args)
        completed = true
        result
      ensure
        RmxpPantallaLog.write(completed ? "#{operation} fin" : "#{operation} interrumpido")
      end
    end
  end
end

Graphics.singleton_class.prepend(RmxpPantallaLog::Hooks)
RmxpPantallaLog.write("diagnostico activo v1")
