package com.dmh.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class AliasGeneratorTest {

    @RepeatedTest(20)
    void generaTresPalabrasDistintasSeparadasPorPunto() {
        AliasGenerator generator = new AliasGenerator(new ClassPathResource("alias-words.txt"));

        String alias = generator.generate();

        assertThat(alias).matches("[a-z]+\\.[a-z]+\\.[a-z]+");
        assertThat(alias.split("\\.")).doesNotHaveDuplicates();
    }

    @Test
    void usaSoloPalabrasDelArchivoIgnorandoComentariosYLineasVacias() {
        AliasGenerator generator = new AliasGenerator(resource("# comentario\nsol\n\nluna\n  Mar  \n"));

        String alias = generator.generate();

        assertThat(List.of(alias.split("\\."))).containsExactlyInAnyOrder("sol", "luna", "mar");
    }

    @Test
    void fallaSiElArchivoTieneMenosDeTresPalabras() {
        assertThatThrownBy(() -> new AliasGenerator(resource("sol\nluna\n")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ByteArrayResource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }
}
