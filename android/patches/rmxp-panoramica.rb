# RMXP-Android-Ports: pantalla panorámica (experimental).
# El menú de opciones escribe rmxp_pantalla.txt con la proporción de la pantalla
# del teléfono (por ejemplo 2.22). Aquí ensanchamos la resolución del juego para
# que muestre más mapa a los lados en vez de dejar bordes negros. La altura no
# cambia, así que los textos y menús quedan del mismo tamaño.
if File.exist?('rmxp_pantalla.txt')
  RMXP_RATIO = File.read('rmxp_pantalla.txt').to_f.clamp(4.0 / 3, 2.4)

  module Graphics
    class << self
      alias_method :rmxp_resize_screen, :resize_screen

      # Cuando el juego pide su resolución (ej. 512x384), damos la misma altura y más ancho
      def resize_screen(width, height)
        wide = (height * RMXP_RATIO / 2).round * 2
        rmxp_resize_screen([width, wide].max, height)
      end
    end
  end

  Graphics.resize_screen(Graphics.width, Graphics.height)
end
