package com.dmh.accounts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Se excluye el usuario en memoria por defecto de Spring Security: la autenticacion es solo por JWT.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
