# RMXP-Android-Ports: en un teléfono la "ventana" del juego siempre es la pantalla completa.
# Las opciones de tamaño de ventana de los juegos (Essentials: "Tamaño Pantalla" S/M/L/XL/
# Completa) cambiaban el tamaño de la ventana de SDL y la imagen se encogía a una esquina
# hasta reiniciar la app. Aquí esas funciones no hacen nada.
module Graphics
  class << self
    def resize_window(*); end
    def center(*); end
    def scale=(value); end
    def fullscreen=(value); end
    def fullscreen; true; end
  end
end
