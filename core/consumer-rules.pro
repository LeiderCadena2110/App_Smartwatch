# Reglas de R8 que se aplican a las apps que usan :core (reloj y celular).
# :core no usa reflexión ni serialización automática: los modelos se leen y escriben a mano con org.json,
# así que R8 puede renombrar sus clases sin romper los datos guardados.
