package dev.mansano.auditoria.seguranca;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Autentica requisições com {@code Authorization: Bearer <token>}. */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    public TokenAuthFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokenService.validar(header.substring(7).trim()).ifPresent(u -> {
                var principal = UsuarioLogado.de(u);
                var auth = new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + u.getPapel().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        chain.doFilter(req, res);
    }
}
