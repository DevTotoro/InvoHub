package com.invohub.backend.infrastructure.config

import com.invohub.backend.domain.auth.AuthProperties
import com.invohub.backend.infrastructure.adapter.inbound.web.auth.SessionMaxAgeFilter
import com.invohub.backend.infrastructure.adapter.inbound.web.error.ProblemDetailWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfException
import org.springframework.security.web.savedrequest.NullRequestCache

/**
 * Session-based auth: a successful magic link verification stores the security context in the HTTP
 * session, which Spring Session keeps in Redis behind an HttpOnly SESSION cookie.
 *
 * Because the browser sends that cookie automatically, state-changing requests need a CSRF token: the
 * frontend reads the XSRF-TOKEN cookie (primed by GET /api/auth/csrf) and echoes it in X-XSRF-TOKEN.
 */
@Configuration
class SecurityConfig {
    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
        problemDetailWriter: ProblemDetailWriter,
        authProperties: AuthProperties,
    ): SecurityFilterChain =
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/auth/request", "/api/auth/verify", "/api/auth/csrf")
                    .permitAll()
                    // Only served when GraphiQL is enabled (local profile); its queries still need a session
                    .requestMatchers("/graphiql/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.csrf { it.spa() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            // Before the security context is loaded, so an expired session is never seen as signed in
            .addFilterBefore(SessionMaxAgeFilter(authProperties.sessionMaxAge), SecurityContextHolderFilter::class.java)
            .exceptionHandling {
                it
                    .authenticationEntryPoint { _, response, _ ->
                        problemDetailWriter.write(response, HttpStatus.UNAUTHORIZED, "Sign in to continue.")
                    }.accessDeniedHandler { _, response, e ->
                        val detail = if (e is CsrfException) "Missing or invalid CSRF token." else "Access denied."
                        problemDetailWriter.write(response, HttpStatus.FORBIDDEN, detail)
                    }
            }
            // Without a login page there is nothing to return to, and saving requests would create a
            // session for every unauthenticated call
            .requestCache { it.requestCache(NullRequestCache()) }
            .logout {
                it
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            }.formLogin { it.disable() }
            .httpBasic { it.disable() }
            .build()
}
