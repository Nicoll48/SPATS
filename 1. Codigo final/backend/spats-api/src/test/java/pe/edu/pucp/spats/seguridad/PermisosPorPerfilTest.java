package pe.edu.pucp.spats.seguridad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import pe.edu.pucp.spats.comun.dominio.ClasificacionColor;
import pe.edu.pucp.spats.maquina.controller.MaquinaController;
import pe.edu.pucp.spats.maquina.dto.MaquinaDtos.MaquinaResponse;
import pe.edu.pucp.spats.maquina.entity.EstadoMaquina;
import pe.edu.pucp.spats.maquina.service.MaquinaService;
import pe.edu.pucp.spats.seguridad.config.PropiedadesSeguridad;
import pe.edu.pucp.spats.seguridad.config.SeguridadConfig;

/** HU-02: Consulta solo lee; las operaciones que modifican exigen el perfil Planificador (RF05, RF06). */
@WebMvcTest(MaquinaController.class)
@Import(SeguridadConfig.class)
@EnableConfigurationProperties(PropiedadesSeguridad.class)
@TestPropertySource(properties = {
        "spats.seguridad.jwt-secreto=clave-de-prueba-de-al-menos-32-caracteres",
        "spats.seguridad.duracion-inactividad=30m",
        "spats.seguridad.origenes-permitidos=http://localhost:5173"})
class PermisosPorPerfilTest {

    static final String CUERPO = """
            {"capacidad": 400, "clasificacionColor": "C", "objetivoSemanal": 16,
             "estado": "OPERATIVA"}""";

    @Autowired MockMvc mvc;
    @MockitoBean MaquinaService servicio;

    MaquinaResponse maquina() {
        return new MaquinaResponse(1, "M400-1", new BigDecimal("400"), ClasificacionColor.C, 16,
                "400 C", EstadoMaquina.OPERATIVA, EstadoMaquina.OPERATIVA);
    }

    @Test
    @DisplayName("Sin sesión: 401 con mensaje en español")
    void sinSesion() throws Exception {
        mvc.perform(get("/api/maquinas"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.codigo").value("SESION_INVALIDA"));
    }

    @Test
    @DisplayName("Consulta puede leer")
    void consultaLee() throws Exception {
        when(servicio.buscar(any())).thenReturn(List.of(maquina()));
        mvc.perform(get("/api/maquinas").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTA"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].codigo").value("M400-1"));
    }

    @Test
    @DisplayName("Consulta no puede modificar: 403")
    void consultaNoModifica() throws Exception {
        mvc.perform(put("/api/maquinas/1").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CONSULTA"))))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    @DisplayName("Planificador puede modificar")
    void planificadorModifica() throws Exception {
        when(servicio.modificar(anyInt(), any())).thenReturn(maquina());
        mvc.perform(put("/api/maquinas/1").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
           .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Datos inválidos: 400 con la lista de errores por campo")
    void validacion() throws Exception {
        mvc.perform(put("/api/maquinas/1").contentType(MediaType.APPLICATION_JSON).content("{\"objetivoSemanal\": 0}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PLANIFICADOR"))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
           .andExpect(jsonPath("$.errores").isArray());
    }
}
