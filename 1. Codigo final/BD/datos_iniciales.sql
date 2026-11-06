-- =====================================================================
-- SPATS — Datos iniciales (ejecutar DESPUÉS de BD.sql, conectado a "tintoreria")
--
-- Perfiles y cuentas de acceso para empezar a usar el sistema. Las
-- contraseñas se guardan cifradas con BCrypt (Spring Security).
--
--   usuario    contraseña   perfil
--   carlos     planificar   Planificador (jefe de planeamiento)
--   consulta   consulta     Consulta (solo lectura)
--
-- ¡Cambia estas contraseñas antes de usar el sistema con datos reales!
-- Las máquinas, pedidos y partidas se cargan desde la aplicación (o con el
-- perfil "demo" del backend, que carga las semanas del R2.2).
-- =====================================================================

INSERT INTO rol (nombre, descripcion) VALUES
    ('PLANIFICADOR', 'Jefe de planeamiento: carga, configura, genera, cierra y registra incidencias'),
    ('CONSULTA',     'Solo lectura de la planificación vigente y de la información permitida');

INSERT INTO usuario (id_rol, nombre_completo, usuario_login, contrasena, modo_interaccion, estado) VALUES
    ((SELECT id_rol FROM rol WHERE nombre = 'PLANIFICADOR'), 'Carlos T.', 'carlos',
     '$2a$10$CiDhEtK.8Oqo5dPMgph9feBbFkXSDE/1OZytiYa.L8EMI6hs.lZSq', 'EXPERTO', 'ACTIVO'),
    ((SELECT id_rol FROM rol WHERE nombre = 'CONSULTA'), 'Programador de tintorería', 'consulta',
     '$2a$10$yzxU5L5VypsLxYYvYBkgduRDy4tPlErVhZnivE4GDvhD2MDDXAhzq', 'EXPERTO', 'ACTIVO');
