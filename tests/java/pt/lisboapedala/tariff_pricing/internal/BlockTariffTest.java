package pt.lisboapedala.tariff_pricing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BlockTariffTest {

    private static final BlockTariff ANNUAL = new BlockTariff(45, 15, new BigDecimal("0.50"));

    @Test
    void minutosIncluidosNaoSaoCobrados() {
        assertThat(ANNUAL.price(0)).isEqualByComparingTo("0");
        assertThat(ANNUAL.price(45)).isEqualByComparingTo("0");
    }

    @Test
    void cadaBlocoComecadoECobradoPorInteiro() {
        assertThat(ANNUAL.price(46)).isEqualByComparingTo("0.50");
        assertThat(ANNUAL.price(60)).isEqualByComparingTo("0.50");
        assertThat(ANNUAL.price(61)).isEqualByComparingTo("1.00");
        assertThat(ANNUAL.price(45 + 15 * 8)).isEqualByComparingTo("4.00");
    }

    @Test
    void parametrosInvalidosSaoRecusados() {
        assertThatThrownBy(() -> new BlockTariff(-1, 15, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockTariff(0, 0, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockTariff(0, 15, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BlockTariff(0, 15, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ANNUAL.price(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
