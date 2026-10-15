package com.dmh.accounts.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Cliente Feign de users-service (endpoints internos, resueltos por Eureka). */
@FeignClient(name = "users-service", configuration = InternalFeignConfig.class)
public interface UsersClient {

    @GetMapping("/internal/users/{id}/summary")
    UserSummary summary(@PathVariable("id") Long id);

    record UserSummary(Long id, String firstName, String lastName) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }
}
