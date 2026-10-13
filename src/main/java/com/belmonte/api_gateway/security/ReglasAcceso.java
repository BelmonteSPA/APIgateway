package com.belmonte.api_gateway.security;

import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Set;

/**
 * Define que roles pueden llamar a cada endpoint.
 * Se evalua en orden y gana la primera regla que coincide (las especificas van antes que las generales).
 * Si ninguna coincide, se deniega.
 */
@Component
public class ReglasAcceso {

    private static final String ADMIN = "ADMIN";
    private static final String PASAJERO = "PASAJERO";
    private static final String CONDUCTOR = "CONDUCTOR";

    //metodo null = cualquier metodo
    private record Regla(String metodo, PathPattern patron, Set<String> roles) {

        boolean coincide(String metodoPeticion, PathContainer ruta) {
            return (metodo == null || metodo.equalsIgnoreCase(metodoPeticion)) && patron.matches(ruta);
        }
    }

    private static Regla regla(String metodo, String patron, String... roles) {
        return new Regla(metodo, PathPatternParser.defaultInstance.parse(patron), Set.of(roles));
    }

    //endpoints que no requieren token
    private static final List<Regla> PUBLICAS = List.of(
            regla("POST", "/api/v1/auth/login"),
            regla("POST", "/api/v1/auth/register")
    );

    //endpoints protegidos y roles permitidos
    private static final List<Regla> PROTEGIDAS = List.of(
            //ms-auth
            regla("GET", "/api/v1/auth/me", ADMIN, PASAJERO, CONDUCTOR),
            regla(null, "/api/v1/usuarios/**", ADMIN),

            //ms-pasajeros: el pasajero solo consulta su ficha; el resto es del administrador
            regla("GET", "/api/v1/pasajeros/{id:[0-9]+}", ADMIN, PASAJERO),
            regla(null, "/api/v1/pasajeros/**", ADMIN),

            //ms-conductores: el conductor solo consulta su ficha; el resto es del administrador
            regla("GET", "/api/v1/conductores/{id:[0-9]+}", ADMIN, CONDUCTOR),
            regla(null, "/api/v1/conductores/**", ADMIN),

            //ms-viajes
            regla("POST", "/api/v1/viajes", ADMIN, PASAJERO),
            regla("GET", "/api/v1/viajes/pasajero/{id:[0-9]+}", ADMIN, PASAJERO),
            regla("DELETE", "/api/v1/viajes/{id:[0-9]+}", ADMIN, PASAJERO),
            regla("GET", "/api/v1/viajes/{id:[0-9]+}", ADMIN, PASAJERO, CONDUCTOR),
            regla("GET", "/api/v1/viajes/conductor/**", ADMIN, CONDUCTOR),
            regla("PATCH", "/api/v1/viajes/{id:[0-9]+}/iniciar", ADMIN, CONDUCTOR),
            regla("PATCH", "/api/v1/viajes/{id:[0-9]+}/finalizar", ADMIN, CONDUCTOR),
            regla(null, "/api/v1/viajes/**", ADMIN)
    );

    public boolean esPublica(String metodo, String ruta) {
        PathContainer contenedor = PathContainer.parsePath(ruta);
        return PUBLICAS.stream().anyMatch(regla -> regla.coincide(metodo, contenedor));
    }

    public boolean permite(String metodo, String ruta, String rol) {
        PathContainer contenedor = PathContainer.parsePath(ruta);
        return PROTEGIDAS.stream()
                .filter(regla -> regla.coincide(metodo, contenedor))
                .findFirst()
                .map(regla -> regla.roles().contains(rol))
                .orElse(false);
    }
}
