package com.dmh.accounts.config;

import java.util.Locale;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.dmh.accounts.dto.TransactionResponse.Direction;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Por defecto Jackson convierte en silencio entre tipos ({@code "amount": "100"} se aceptaria
     * como numero). Se desactiva: cada campo tiene que llegar con su tipo, y si no, la peticion se
     * rechaza con 400.
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer strictJsonTypes() {
        return builder -> builder.postConfigurer(mapper -> {
            mapper.coercionConfigFor(LogicalType.Textual)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            for (LogicalType numeric : new LogicalType[] { LogicalType.Integer, LogicalType.Float }) {
                mapper.coercionConfigFor(numeric)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            }
        });
    }

    /** {@code ?direction=income} se acepta igual que {@code ?direction=INCOME}. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, Direction>() {
            @Override
            public Direction convert(String source) {
                return Direction.valueOf(source.trim().toUpperCase(Locale.ROOT));
            }
        });
    }
}
