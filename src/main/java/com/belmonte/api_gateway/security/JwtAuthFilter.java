package com.belmonte.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Filtro global: antes de reenviar la peticion a un microservicio valida el token JWT
 * y comprueba que el rol tenga permiso para ese endpoint (ver ReglasAcceso).
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    private static final String HEADER_EMAIL = "X-User-Email";
    private static final String HEADER_ROL = "X-User-Rol";
    private static final String HEADER_ID = "X-User-Id";
    private static final String HEADER_REF = "X-User-Ref";

    private final JwtService jwtService;
    private final ReglasAcceso reglasAcceso;

    @Autowired
    public JwtAuthFilter(JwtService jwtService, ReglasAcceso reglasAcceso) {
        this.jwtService = jwtService;
        this.reglasAcceso = reglasAcceso;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String metodo = request.getMethod().name();
        String ruta = request.getPath().value();

        //endpoints publicos (login y registro): pasan sin token
        if (reglasAcceso.esPublica(metodo, ruta)) {
            return chain.filter(exchange.mutate().request(sinCabecerasDeUsuario(request)).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return responderError(exchange, HttpStatus.UNAUTHORIZED, "No autenticado: falta el token");
        }

        Claims claims;
        try {
            claims = jwtService.leerToken(header.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            return responderError(exchange, HttpStatus.UNAUTHORIZED, "No autenticado: el token es invalido o esta vencido");
        }

        String rol = claims.get("rol", String.class);
        if (rol == null || !reglasAcceso.permite(metodo, ruta, rol)) {
            return responderError(exchange, HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta accion");
        }

        //datos del usuario para los microservicios (se reemplazan los que haya enviado el cliente)
        ServerHttpRequest conUsuario = request.mutate()
                .headers(headers -> {
                    quitarCabecerasDeUsuario(headers);
                    headers.set(HEADER_EMAIL, claims.getSubject());
                    headers.set(HEADER_ROL, rol);
                    headers.set(HEADER_ID, String.valueOf(claims.get("userId")));
                    if (claims.get("idReferencia") != null) {
                        headers.set(HEADER_REF, String.valueOf(claims.get("idReferencia")));
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(conUsuario).build());
    }

    //se ejecuta antes de los filtros que enrutan la peticion
    @Override
    public int getOrder() {
        return -100;
    }

    //evita que un cliente se haga pasar por otro enviando estas cabeceras a mano
    private ServerHttpRequest sinCabecerasDeUsuario(ServerHttpRequest request) {
        return request.mutate().headers(this::quitarCabecerasDeUsuario).build();
    }

    private void quitarCabecerasDeUsuario(HttpHeaders headers) {
        headers.remove(HEADER_EMAIL);
        headers.remove(HEADER_ROL);
        headers.remove(HEADER_ID);
        headers.remove(HEADER_REF);
    }

    private Mono<Void> responderError(ServerWebExchange exchange, HttpStatus estado, String mensaje) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(estado);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] cuerpo = ("{\"error\":\"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(cuerpo)));
    }
}
