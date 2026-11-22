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
--
-- Parque oficial de máquinas (D-51): 10 máquinas, objetivo semanal 16.
-- Los pedidos y las partidas se cargan desde la aplicación. El perfil
-- "demo" del backend (semanas del R2.2) solo carga datos si la base no
-- tiene máquinas: para usarlo, elimina antes este parque.
-- =====================================================================

INSERT INTO rol (nombre, descripcion) VALUES
    ('PLANIFICADOR', 'Jefe de planeamiento: carga, configura, genera, cierra y registra incidencias'),
    ('CONSULTA',     'Solo lectura de la planificación vigente y de la información permitida');

INSERT INTO usuario (id_rol, nombre_completo, usuario_login, contrasena, modo_interaccion, estado) VALUES
    ((SELECT id_rol FROM rol WHERE nombre = 'PLANIFICADOR'), 'Carlos T.', 'carlos',
     '$2a$10$CiDhEtK.8Oqo5dPMgph9feBbFkXSDE/1OZytiYa.L8EMI6hs.lZSq', 'EXPERTO', 'ACTIVO'),
    ((SELECT id_rol FROM rol WHERE nombre = 'CONSULTA'), 'Programador de tintorería', 'consulta',
     '$2a$10$yzxU5L5VypsLxYYvYBkgduRDy4tPlErVhZnivE4GDvhD2MDDXAhzq', 'EXPERTO', 'ACTIVO');

-- ---------------------------------------------------------------------
-- Parque oficial de máquinas (D-51)
-- La compatibilidad usa la capacidad (número de la barca) y las
-- tonalidades aceptadas; la letra de la barca es informativa.
-- ---------------------------------------------------------------------
INSERT INTO maquina_tintoreria (codigo, capacidad, clasificacion_color, objetivo_semanal, barca, estado) VALUES
    ('M600-1', 600, 'C', 16, '600 C', 'OPERATIVA'),
    ('M600-2', 600, 'C', 16, '600 C', 'OPERATIVA'),
    ('M400-1', 400, 'C', 16, '400 C', 'OPERATIVA'),
    ('M400-2', 400, 'C', 16, '400 C', 'OPERATIVA'),
    ('M450-1', 450, 'B', 16, '450 B', 'OPERATIVA'),
    ('M300-1', 300, 'B', 16, '300 B', 'OPERATIVA'),
    ('M300-2', 300, 'B', 16, '300 B', 'OPERATIVA'),
    ('M200-1', 200, 'C', 16, '200 C', 'OPERATIVA'),
    ('M200-2', 200, 'C', 16, '200 C', 'OPERATIVA'),
    ('M200-3', 200, 'C', 16, '200 C', 'OPERATIVA');

INSERT INTO maquina_tonalidad_aceptada (id_maquina, tonalidad)
SELECT m.id_maquina, t.tonalidad
FROM maquina_tintoreria m
JOIN (VALUES
    ('M600-1', 'BLANCO'), ('M600-1', 'CLARO'), ('M600-1', 'MEDIO'), ('M600-1', 'OSCURO'),
    ('M600-2', 'BLANCO'), ('M600-2', 'CLARO'), ('M600-2', 'MEDIO'), ('M600-2', 'OSCURO'),
    ('M400-1', 'BLANCO'), ('M400-1', 'CLARO'), ('M400-1', 'MEDIO'), ('M400-1', 'OSCURO'),
    ('M400-2', 'BLANCO'), ('M400-2', 'CLARO'), ('M400-2', 'MEDIO'), ('M400-2', 'OSCURO'),
    ('M450-1', 'BLANCO'), ('M450-1', 'CLARO'), ('M450-1', 'MEDIO'),
    ('M300-1', 'BLANCO'),
    ('M300-2', 'BLANCO'), ('M300-2', 'CLARO'),
    ('M200-1', 'BLANCO'), ('M200-1', 'CLARO'), ('M200-1', 'MEDIO'), ('M200-1', 'OSCURO'),
    ('M200-2', 'BLANCO'), ('M200-2', 'CLARO'), ('M200-2', 'MEDIO'), ('M200-2', 'OSCURO'),
    ('M200-3', 'BLANCO'), ('M200-3', 'CLARO'), ('M200-3', 'MEDIO'), ('M200-3', 'OSCURO')
) AS t (codigo, tonalidad) ON t.codigo = m.codigo;
